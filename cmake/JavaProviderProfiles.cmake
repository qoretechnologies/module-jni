# Declarative qualification for Java-backed Qore data providers.
#
# The runtime classpath remains declared exactly once by each module's
# %module-cmd(jni) add-relative-classpath directives.  This helper attaches one
# logging policy to that declaration and emits the common inventory consumed by
# source-tree, AOT-tree, install-tree, and fresh-process checks.
#
# Copyright 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT

option(QORE_JAVA_PROVIDER_SOURCE_ARCHIVE
    "Validate checksum-locked source archive JARs without Git metadata" OFF)

set(QORE_JAVA_PROVIDER_PROFILE_FILE
    "${CMAKE_CURRENT_BINARY_DIR}/java-provider-profiles.tsv")
file(WRITE "${QORE_JAVA_PROVIDER_PROFILE_FILE}" "# module\tlogging-policy\truntime-jar\n")

function(qore_java_provider_profile _module _logging_policy)
    if(NOT _logging_policy STREQUAL "none" AND
            NOT _logging_policy STREQUAL "slf4j-nop")
        message(FATAL_ERROR
            "Java provider ${_module} has unsupported logging policy '${_logging_policy}'")
    endif()

    set(_module_source "${CMAKE_SOURCE_DIR}/qlib/${_module}/${_module}.qm")
    if(NOT EXISTS "${_module_source}")
        message(FATAL_ERROR "Java provider source not found: ${_module_source}")
    endif()
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${_module_source}")

    file(STRINGS "${_module_source}" _classpath_lines
        REGEX "^[ \t]*%module-cmd\\(jni\\)[ \t]+(global-)?add-relative-classpath[ \t]+")
    if(NOT _classpath_lines)
        message(FATAL_ERROR "Java provider ${_module} declares no runtime JARs")
    endif()

    set(_seen_paths "")
    foreach(_line IN LISTS _classpath_lines)
        string(REGEX REPLACE
            "^[ \t]*%module-cmd\\(jni\\)[ \t]+(global-)?add-relative-classpath[ \t]+([^ \t]+).*$"
            "\\2" _runtime_jar "${_line}")
        if(_runtime_jar IN_LIST _seen_paths)
            message(FATAL_ERROR
                "Java provider ${_module} declares runtime JAR ${_runtime_jar} more than once")
        endif()
        list(APPEND _seen_paths "${_runtime_jar}")
        file(APPEND "${QORE_JAVA_PROVIDER_PROFILE_FILE}"
            "${_module}\t${_logging_policy}\t${_runtime_jar}\n")
    endforeach()

    set_property(GLOBAL APPEND PROPERTY QORE_JAVA_PROVIDER_PROFILE_MODULES "${_module}")
endfunction()

function(qore_finalize_java_provider_profiles)
    get_property(_modules GLOBAL PROPERTY QORE_JAVA_PROVIDER_PROFILE_MODULES)
    if(NOT _modules)
        message(FATAL_ERROR "No Java provider profiles were declared")
    endif()

    set(_unique_modules ${_modules})
    list(REMOVE_DUPLICATES _unique_modules)
    list(LENGTH _modules _module_count)
    list(LENGTH _unique_modules _unique_module_count)
    if(NOT _module_count EQUAL _unique_module_count)
        message(FATAL_ERROR "A Java provider dependency profile was declared more than once")
    endif()

    # Discover the declaration boundary independently of the profile list. A
    # new Java-backed module must not evade qualification merely because its
    # generated first-party JAR has no committed checksum entry.
    file(GLOB _module_sources CONFIGURE_DEPENDS
        "${CMAKE_SOURCE_DIR}/qlib/*/*.qm")
    foreach(_module_source IN LISTS _module_sources)
        file(STRINGS "${_module_source}" _classpath_lines
            REGEX "^[ \t]*%module-cmd\\(jni\\)[ \t]+(global-)?add-relative-classpath[ \t]+")
        if(_classpath_lines)
            get_filename_component(_module_name "${_module_source}" NAME_WE)
            if(NOT _module_name IN_LIST _modules)
                message(FATAL_ERROR
                    "Java-backed module ${_module_name} has runtime classpath directives but no dependency profile")
            endif()
        endif()
    endforeach()

    set(_qmod_targets "")
    # Bootstrap builds install binaries and sources before enabling AOT. Qore
    # intentionally creates no qmod targets in that first pass.
    if(QORE_BUILD_AOT_MODULES)
        foreach(_module IN LISTS _modules)
            if(NOT TARGET ${_module}-qmod)
                message(FATAL_ERROR "Java provider ${_module} has no qmod build target")
            endif()
            list(APPEND _qmod_targets ${_module}-qmod)
        endforeach()
    endif()

    find_program(QORE_JAVA_PROVIDER_PYTHON NAMES python3)
    if(NOT QORE_JAVA_PROVIDER_PYTHON)
        message(FATAL_ERROR "python3 is required to validate Java provider profiles")
    endif()
    set(_inventory_mode --require-committed)
    if(QORE_JAVA_PROVIDER_SOURCE_ARCHIVE)
        set(_inventory_mode --source-archive)
    endif()
    add_custom_target(validate-java-provider-profiles ALL
        COMMAND "${QORE_JAVA_PROVIDER_PYTHON}"
            "${CMAKE_SOURCE_DIR}/test/docker_test/validate-java-provider-profiles.py"
            --profiles "${QORE_JAVA_PROVIDER_PROFILE_FILE}"
            --root "${CMAKE_SOURCE_DIR}/qlib"
            --checksums "${CMAKE_SOURCE_DIR}/qlib/java-provider-dependencies.sha256"
            ${_inventory_mode}
        # qore-jni builds and stages every generated provider JAR, including
        # when qmod targets are absent. ALL targets alone do not order a
        # parallel build, and validation can also be requested directly.
        DEPENDS qore-jni ${_qmod_targets}
        VERBATIM
    )

    install(FILES "${QORE_JAVA_PROVIDER_PROFILE_FILE}"
        DESTINATION "share/qore-jni")
endfunction()
