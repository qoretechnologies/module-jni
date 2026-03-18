cmake_minimum_required(VERSION 3.5)

set(module_name "jni")

project(qore-jni-module)

set (VERSION_MAJOR 2)
set (VERSION_MINOR 7)
set (VERSION_PATCH 0)

set (BYTE_BUDDY_JAR ${CMAKE_SOURCE_DIR}/byte-buddy/byte-buddy-1.14.19.jar)

set(PROJECT_VERSION "${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}")

# enable RPATH in macos bins (OS requires this in any case)
if (POLICY CMP0042)
    cmake_policy(SET CMP0042 NEW)
endif()

if(NOT Qore_DIR AND DEFINED ENV{QORE_GIT_ROOT})
    # Prefer build-tree QoreConfig for local debug builds without install.
    set(_qore_build_cmake "$ENV{QORE_GIT_ROOT}/qore/build/cmake")
    if(EXISTS "${_qore_build_cmake}/QoreConfig.cmake")
        set(Qore_DIR "${_qore_build_cmake}")
    endif()
endif()
find_package(Qore 2.0 REQUIRED)
find_package(JNI REQUIRED)
find_package(Java REQUIRED)
if (${Java_VERSION_MAJOR} LESS 21)
    message(FATAL_ERROR "This module requires Java 21+; the current Java version is ${Java_VERSION}.")
endif()
add_definitions(-DJAVA_VERSION_MAJOR=${Java_VERSION_MAJOR})
include(UseJava)

# Find Kotlin compiler for QoreKotlinCompiler support
set(KOTLIN_HOME "$ENV{KOTLIN_HOME}")
set(KOTLIN_COMPILER_JAR "")
set(_kotlin_candidates "")
if (KOTLIN_HOME)
    list(APPEND _kotlin_candidates "${KOTLIN_HOME}")
else()
    list(APPEND _kotlin_candidates "/opt/kotlin" "/opt/kotlinc")
endif()
foreach (_kotlin_dir IN LISTS _kotlin_candidates)
    if (EXISTS "${_kotlin_dir}/lib/kotlin-compiler.jar")
        set(KOTLIN_HOME "${_kotlin_dir}")
        set(KOTLIN_COMPILER_JAR "${_kotlin_dir}/lib/kotlin-compiler.jar")
        break()
    endif()
endforeach()
if (KOTLIN_COMPILER_JAR)
    message(STATUS "Found Kotlin compiler: ${KOTLIN_COMPILER_JAR}")
    set(HAVE_KOTLIN_COMPILER TRUE)
else()
    message(STATUS "Kotlin compiler not found - QoreKotlinCompiler will not be built")
    set(HAVE_KOTLIN_COMPILER FALSE)
endif()

find_package(BZip2 REQUIRED)

# Check for C++11
include(CheckCXXCompilerFlag)
CHECK_CXX_COMPILER_FLAG("-std=c++11" COMPILER_SUPPORTS_CXX11)
if(COMPILER_SUPPORTS_CXX11)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
else()
    message(FATAL_ERROR "The compiler ${CMAKE_CXX_COMPILER} has no C++11 support. Please use a different C++ compiler.")
endif()

SET(_qore_jni_sentinel ${CMAKE_CURRENT_BINARY_DIR}/qore-jni.sentinel)
SET(_qore_jni_compiler_sentinel ${CMAKE_CURRENT_BINARY_DIR}/qore-jni-compiler.sentinel)

# adds a make rule for java -> class generation annd class -> inc generation
MACRO(GENERATE_JAVA _input)
    GET_FILENAME_COMPONENT(_base ${_input} NAME_WE)
    GET_FILENAME_COMPONENT(_pkgdir ${_input} PATH)
    SET(_class_file ${_pkgdir}/${_base}.class)

    # generate rules to generate include files for primary classes
    SET(_inc_outfile ${CMAKE_CURRENT_BINARY_DIR}/JavaClass${_base}.inc)
    add_custom_command(
        OUTPUT ${_inc_outfile}
        COMMAND ${CMAKE_SOURCE_DIR}/make-inc ${CMAKE_CURRENT_BINARY_DIR}/${_class_file} ${_inc_outfile}
        DEPENDS jni-sentinel ${CMAKE_CURRENT_BINARY_DIR}/${_class_file}
        WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
        VERBATIM
    )

    SET(JAVA_JAR_CLASSES ${JAVA_JAR_CLASSES} ${_class_file})
    SET(JAVA_JAR_SRC ${JAVA_JAR_SRC} ${CMAKE_SOURCE_DIR}/src/java/${_input})
    SET(QORE_INC_FILES ${QORE_INC_FILES} ${_inc_outfile})

    # issue #3019: get list of source files for documentation
    string (REPLACE ";" " " JAVA_JAR_SRC_STR "${JAVA_JAR_SRC}")

    # generate rules for include files for inner classes (if any)
    # - numeric args (1, 2, etc.) are anonymous inner classes
    # - args starting with $ are named inner classes (e.g., $CleanupState)
    # - other args are separate top-level classes in the same file
    foreach (it ${ARGN})
        #message(STATUS "IT: ${it}")
        if (${it} MATCHES "^[0-9]+$")
            # Anonymous inner class (e.g., Hash$1)
            SET(_inc_infile ${_pkgdir}/${_base}$${it}.class)
            SET(_inc_outfile ${CMAKE_CURRENT_BINARY_DIR}/JavaClass${_base}_${it}.inc)
        elseif (${it} MATCHES "^\\$")
            # Named inner class (e.g., QoreObjectBase$CleanupState) - strip leading $
            string(SUBSTRING ${it} 1 -1 _inner_name)
            SET(_inc_infile ${_pkgdir}/${_base}$${_inner_name}.class)
            SET(_inc_outfile ${CMAKE_CURRENT_BINARY_DIR}/JavaClass${_base}_${_inner_name}.inc)
        else()
            # Separate top-level class in the same file (e.g., StaticEntry)
            SET(_inc_infile ${_pkgdir}/${it}.class)
            SET(_inc_outfile ${CMAKE_CURRENT_BINARY_DIR}/JavaClass${it}.inc)
        endif()
        add_custom_command(
            OUTPUT ${_inc_outfile}
            COMMAND ${CMAKE_SOURCE_DIR}/make-inc ${_inc_infile} ${_inc_outfile}
            DEPENDS jni-sentinel ${_inc_infile}
            WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
            VERBATIM
        )

        SET(JAVA_JAR_CLASSES ${JAVA_JAR_CLASSES} ${_inc_infile})
        SET(QORE_INC_FILES ${QORE_INC_FILES} ${_inc_outfile})
    endforeach()
ENDMACRO(GENERATE_JAVA)

# adds a make rule for jar -> inc generation
MACRO(GENERATE_JAR _input _output)
    GET_FILENAME_COMPONENT(_base ${_input} NAME_WE)
    GET_FILENAME_COMPONENT(_pkgdir ${_input} PATH)

    # generate rules to generate include files for primary classes
    SET(_inc_outfile ${CMAKE_CURRENT_BINARY_DIR}/${_output}.inc)
    add_custom_command(
        OUTPUT ${_inc_outfile}
        COMMAND ${CMAKE_SOURCE_DIR}/make-inc ${_input} ${_inc_outfile}
        DEPENDS jni-sentinel jni-compiler-sentinel ${_input}
        WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
        VERBATIM
    )

    SET(QORE_INC_FILES ${QORE_INC_FILES} ${_inc_outfile})
ENDMACRO(GENERATE_JAR)

# adds a make rule for java -> jar generation using qjava2jar
MACRO(MAKE_QJAR _output _classpath _root)
    add_custom_command(
        OUTPUT ${_output}
        COMMAND ${CMAKE_COMMAND} -E env QORE_MODULE_DIR=. ${QORE_EXECUTABLE} -l jni ${CMAKE_SOURCE_DIR}/bin/qjava2jar
            --root=${_root} -d ${CMAKE_CURRENT_BINARY_DIR} ${_output} ${ARGN} -cp ${_classpath}
        DEPENDS ${module_name} ${ARGN}
        WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
        VERBATIM
    )
ENDMACRO(MAKE_QJAR)

# Shared Qore stream adapter sources (compiled into each data provider jar)
set(QORE_STREAM_SOURCES
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/jni/QoreInputStreamWrapper.java
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/jni/QoreOutputStreamWrapper.java
)

# ${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/qore-dataprovider-excel.jar
set(EXCEL_JAR ${CMAKE_CURRENT_BINARY_DIR}/qore-dataprovider-excel.jar)
set(EXCEL_CLASSPATH ${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/poi-ooxml-full-5.5.0.jar:${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/poi-5.5.0.jar:${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/poi-ooxml-5.5.0.jar)
set(EXCEL_ROOT ${CMAKE_SOURCE_DIR}/src/java)
set(EXCEL_SOURCES_DIR ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/excel/)
set(EXCEL_SOURCES
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/excel/ExcelIterator.java
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/excel/ExcelWriter.java
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/excel/ExcelTestHelper.java
)

# Each data provider jar uses its own output subdirectory (-d jar-<name>) so that the shared
# QoreInputStream/OutputStreamWrapper class files don't collide between parallel builds.
# This eliminates the need for file-level cross-dependencies between jar custom commands,
# which caused cmake's Makefile generator to duplicate the build rule in each dependent
# target's build.make — leading to the same jar being built multiple times under make -jN.
add_custom_command(
    OUTPUT ${EXCEL_JAR}
    COMMAND ${CMAKE_COMMAND} -E env QORE_MODULE_DIR=. ${QORE_EXECUTABLE} -l jni ${CMAKE_SOURCE_DIR}/bin/qjava2jar
        --root=${EXCEL_ROOT} -d ${CMAKE_CURRENT_BINARY_DIR}/jar-excel ${EXCEL_JAR} ${EXCEL_SOURCES_DIR}
        ${QORE_STREAM_SOURCES} -cp ${EXCEL_CLASSPATH}
    COMMAND ${CMAKE_COMMAND} -E copy ${EXCEL_JAR} ${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/
    DEPENDS ${module_name} ${EXCEL_SOURCES} ${QORE_STREAM_SOURCES}
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
    VERBATIM
)

add_custom_target(qore-excel-jar
    DEPENDS ${EXCEL_JAR}
)

# install the Excel jar file manually
install(FILES ${EXCEL_JAR} DESTINATION ${QORE_USER_MODULES_DIR}/ExcelDataProvider/jar)

# Word data provider jar
set(WORD_JAR ${CMAKE_CURRENT_BINARY_DIR}/qore-dataprovider-word.jar)
set(WORD_CLASSPATH ${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/poi-ooxml-full-5.5.0.jar:${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/poi-5.5.0.jar:${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/poi-ooxml-5.5.0.jar)
set(WORD_ROOT ${CMAKE_SOURCE_DIR}/src/java)
set(WORD_SOURCES_DIR ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/word/)
set(WORD_SOURCES
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/word/WordIterator.java
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/word/WordWriter.java
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/word/WordTestHelper.java
)

add_custom_command(
    OUTPUT ${WORD_JAR}
    COMMAND ${CMAKE_COMMAND} -E env QORE_MODULE_DIR=. ${QORE_EXECUTABLE} -l jni ${CMAKE_SOURCE_DIR}/bin/qjava2jar
        --root=${WORD_ROOT} -d ${CMAKE_CURRENT_BINARY_DIR}/jar-word ${WORD_JAR} ${WORD_SOURCES_DIR}
        ${QORE_STREAM_SOURCES} -cp ${WORD_CLASSPATH}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/qlib/WordDataProvider/jar
    COMMAND ${CMAKE_COMMAND} -E copy ${WORD_JAR} ${CMAKE_SOURCE_DIR}/qlib/WordDataProvider/jar/
    DEPENDS ${module_name} ${WORD_SOURCES} ${QORE_STREAM_SOURCES}
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
    VERBATIM
)

add_custom_target(qore-word-jar
    DEPENDS ${WORD_JAR}
)

# install the Word jar file manually
install(FILES ${WORD_JAR} DESTINATION ${QORE_USER_MODULES_DIR}/WordDataProvider/jar)

# PowerPoint data provider jar
set(POWERPOINT_JAR ${CMAKE_CURRENT_BINARY_DIR}/qore-dataprovider-powerpoint.jar)
set(POWERPOINT_CLASSPATH ${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/poi-ooxml-full-5.5.0.jar:${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/poi-5.5.0.jar:${CMAKE_SOURCE_DIR}/qlib/ExcelDataProvider/jar/poi-ooxml-5.5.0.jar)
set(POWERPOINT_ROOT ${CMAKE_SOURCE_DIR}/src/java)
set(POWERPOINT_SOURCES_DIR ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/powerpoint/)
set(POWERPOINT_SOURCES
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/powerpoint/PowerPointIterator.java
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/powerpoint/PowerPointWriter.java
    ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/powerpoint/PowerPointTestHelper.java
)

add_custom_command(
    OUTPUT ${POWERPOINT_JAR}
    COMMAND ${CMAKE_COMMAND} -E env QORE_MODULE_DIR=. ${QORE_EXECUTABLE} -l jni ${CMAKE_SOURCE_DIR}/bin/qjava2jar
        --root=${POWERPOINT_ROOT} -d ${CMAKE_CURRENT_BINARY_DIR}/jar-powerpoint ${POWERPOINT_JAR} ${POWERPOINT_SOURCES_DIR}
        ${QORE_STREAM_SOURCES} -cp ${POWERPOINT_CLASSPATH}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/qlib/PowerPointDataProvider/jar
    COMMAND ${CMAKE_COMMAND} -E copy ${POWERPOINT_JAR} ${CMAKE_SOURCE_DIR}/qlib/PowerPointDataProvider/jar/
    DEPENDS ${module_name} ${POWERPOINT_SOURCES} ${QORE_STREAM_SOURCES}
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
    VERBATIM
)

add_custom_target(qore-powerpoint-jar
    DEPENDS ${POWERPOINT_JAR}
)

# install the PowerPoint jar file manually
install(FILES ${POWERPOINT_JAR} DESTINATION ${QORE_USER_MODULES_DIR}/PowerPointDataProvider/jar)

# ${CMAKE_SOURCE_DIR}/qlib/KafkaDataProvider/jar/qore-dataprovider-kafka.jar
set(KAFKA_JAR ${CMAKE_CURRENT_BINARY_DIR}/qore-dataprovider-kafka.jar)
set(KAFKA_CLASSPATH ${CMAKE_SOURCE_DIR}/qlib/KafkaDataProvider/jar/zstd-jni-1.5.6-9.jar:${CMAKE_SOURCE_DIR}/qlib/KafkaDataProvider/jar/lz4-java-1.8.0.jar:${CMAKE_SOURCE_DIR}/qlib/KafkaDataProvider/jar/slf4j-api-2.0.16.jar:${CMAKE_SOURCE_DIR}/qlib/KafkaDataProvider/jar/snappy-java-1.1.10.7.jar:${CMAKE_SOURCE_DIR}/qlib/KafkaDataProvider/jar/kafka-clients-3.9.0.jar)
set(KAFKA_ROOT ${CMAKE_SOURCE_DIR}/src/java)
set(KAFKA_SOURCES ${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/kafka/)

add_custom_command(
    OUTPUT ${KAFKA_JAR}
    COMMAND ${CMAKE_COMMAND} -E env QORE_MODULE_DIR=. ${QORE_EXECUTABLE} -l jni ${CMAKE_SOURCE_DIR}/bin/qjava2jar
        --root=${KAFKA_ROOT} -d ${CMAKE_CURRENT_BINARY_DIR}/jar-kafka ${KAFKA_JAR} ${KAFKA_SOURCES} -cp ${KAFKA_CLASSPATH}
    DEPENDS ${module_name} ${KAFKA_SOURCES}
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
    VERBATIM
)

#${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/kafka/QoreJavaConfig.java
#${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/kafka/QoreKafkaConsumer.java
#${CMAKE_SOURCE_DIR}/src/java/org/qore/dataprovider/kafka/QoreKafkaProducer.java

add_custom_target(qore-kafka-jar
    DEPENDS ${KAFKA_JAR}
)

# install the Kafka jar file manually
install(FILES ${KAFKA_JAR} DESTINATION ${QORE_USER_MODULES_DIR}/KafkaDataProvider/jar)

# adds a make rule for java -> class generation
MACRO(ADD_JAVA_SOURCE _input)
    GET_FILENAME_COMPONENT(_base ${_input} NAME_WE)
    GET_FILENAME_COMPONENT(_pkgdir ${_input} PATH)
    SET(_class_file ${_pkgdir}/${_base}.class)
    SET(JAVA_JAR_CLASSES ${JAVA_JAR_CLASSES} ${_class_file})
    SET(JAVA_JAR_SRC ${JAVA_JAR_SRC} ${CMAKE_SOURCE_DIR}/src/java/${_input})

    # issue #3158: get list of source files for documentation
    string (REPLACE ";" " " JAVA_JAR_SRC_STR "${JAVA_JAR_SRC}")

    # generate rules for inner classes (if any)
    foreach (it ${ARGN})
        SET(_inc_infile ${_pkgdir}/${_base}$${it}.class)
        SET(JAVA_JAR_CLASSES ${JAVA_JAR_CLASSES} ${_inc_infile})
    endforeach()
ENDMACRO(ADD_JAVA_SOURCE)

MACRO(ADD_JAR_TARGETS)
    foreach (it ${ARGN})
        file(GLOB _files "${CMAKE_CURRENT_BINARY_DIR}/${it}/*.class")
        foreach (_file ${_files})
            string(REPLACE "${CMAKE_CURRENT_BINARY_DIR}/" "" _class_file ${_file})
            SET(JAVA_JAR_CLASSES ${JAVA_JAR_CLASSES} ${_class_file})
            #message(STATUS "ADDED ${_class_file}")
        endforeach()
    endforeach()
ENDMACRO(ADD_JAR_TARGETS)

# adds a make rule for java -> class generation for the compiler
MACRO(ADD_JAVA_COMPILER_SOURCE _input)
    GET_FILENAME_COMPONENT(_base ${_input} NAME_WE)
    GET_FILENAME_COMPONENT(_pkgdir ${_input} PATH)
    SET(_class_file ${_pkgdir}/${_base}.class)

    # generate rules to generate include files for compiler classes
    SET(_inc_outfile ${CMAKE_CURRENT_BINARY_DIR}/JavaClass${_base}.inc)
    add_custom_command(
        OUTPUT ${_inc_outfile}
        COMMAND ${CMAKE_SOURCE_DIR}/make-inc ${_class_file} ${_inc_outfile}
        DEPENDS jni-compiler-sentinel ${_class_file}
        WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
        VERBATIM
    )
    SET(QORE_INC_FILES ${QORE_INC_FILES} ${_inc_outfile})
    SET(JAVA_JAR_COMPILER_CLASSES ${JAVA_JAR_COMPILER_CLASSES} ${_class_file})
    SET(JAVA_JAR_COMPILER_SRC ${JAVA_JAR_COMPILER_SRC} ${CMAKE_SOURCE_DIR}/src/java/${_input})

    # issue #3158: get list of source files for documentation
    string (REPLACE ";" " " JAVA_JAR_COMPILER_SRC_STR "${JAVA_JAR_COMPILER_SRC}")

    # generate rules for inner classes (if any)
    foreach (it ${ARGN})
        if (${it} MATCHES "^[0-9]+$")
            # Anonymous inner class (numbered): e.g., "1" -> BaseClass$1.class
            SET(_inc_infile ${_pkgdir}/${_base}$${it}.class)
            SET(_inc_outfile ${CMAKE_CURRENT_BINARY_DIR}/JavaClass${_base}_${it}.inc)
        elseif (${it} MATCHES "^\\$")
            # Named inner class (starts with $): e.g., "$SafeDiagnostic" -> BaseClass$SafeDiagnostic.class
            SET(_inc_infile ${_pkgdir}/${_base}${it}.class)
            string(SUBSTRING ${it} 1 -1 _inner_name)
            SET(_inc_outfile ${CMAKE_CURRENT_BINARY_DIR}/JavaClass${_base}_${_inner_name}.inc)
        else()
            # Standalone package-private class in the same source file
            SET(_inc_infile ${_pkgdir}/${it}.class)
            SET(_inc_outfile ${CMAKE_CURRENT_BINARY_DIR}/JavaClass${it}.inc)
        endif()

        add_custom_command(
            OUTPUT ${_inc_outfile}
            COMMAND ${CMAKE_SOURCE_DIR}/make-inc ${_inc_infile} ${_inc_outfile}
            DEPENDS jni-compiler-sentinel ${_inc_infile}
            WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
            VERBATIM
        )
        SET(JAVA_JAR_COMPILER_CLASSES ${JAVA_JAR_COMPILER_CLASSES} ${_inc_infile})
        SET(QORE_INC_FILES ${QORE_INC_FILES} ${_inc_outfile})
    endforeach()
ENDMACRO(ADD_JAVA_COMPILER_SOURCE)

# binaries to install
set (JNI_BIN_FILES
    bin/qjava2jar
    bin/qjavac
    bin/qkotlinc
    bin/download-kotlin-scripting-jars
)

# Java sources built in to the binary module with an indication of inner classes, if any
generate_java(org/qore/jni/QoreJavaApi.java)
generate_java(org/qore/jni/QoreExceptionWrapper.java $CleanupState)
generate_java(org/qore/jni/QoreException.java)
generate_java(org/qore/jni/QoreObjectBase.java $CleanupState)
generate_java(org/qore/jni/QoreJavaClassBase.java)
generate_java(org/qore/jni/QoreObject.java)
generate_java(org/qore/jni/QoreClosure.java $CleanupState)
generate_java(org/qore/jni/QoreObjectWrapper.java)
generate_java(org/qore/jni/QoreInvocationHandler.java $CleanupState)
generate_java(org/qore/jni/BooleanWrapper.java)
generate_java(org/qore/jni/ClassModInfo.java)
generate_java(org/qore/jni/QoreURLClassLoader.java 1 2)
generate_java(org/qore/jni/QoreRelativeTime.java)
generate_java(org/qore/jni/QoreClosureMarker.java)
generate_java(org/qore/jni/QoreClosureMarkerImpl.java)
generate_java(org/qore/jni/QoreJavaDynamicApi.java)
generate_java(org/qore/jni/Hash.java 1 2 3 4 5 6 7 8 9 10)
generate_java(org/qore/jni/JavaClassBuilder.java 1 2 StaticEntry)
generate_java(org/qore/jni/QoreJavaFileObject.java)
generate_java(org/qore/jni/QoreJavaObjectPtr.java)
add_java_source(org/qore/jni/KotlinMetadataHelper.java)
add_java_source(org/qore/jni/KotlinScriptEngine.java)
generate_jar(${BYTE_BUDDY_JAR} JavaJarByteBuddy)

# add Java sources without native methods
add_java_source(org/qore/lang/AbstractIterator.java)
add_java_source(org/qore/lang/HashListIterator.java)
add_java_source(org/qore/lang/reflection/Type.java)
add_java_source(org/qore/lang/sqlutil/Table.java)
add_java_source(org/qore/lang/sqlutil/AbstractTable.java 1 2 3 4 5)
add_java_source(org/qore/lang/sqlutil/AbstractSqlUtilBase.java)
add_java_source(org/qore/lang/AbstractDatasource.java)
add_java_source(org/qore/lang/mapper/Mapper.java)
add_java_source(org/qore/lang/mapper/MapperOutputRecordIterator.java)
add_java_source(org/qore/lang/tablemapper/RawSqlStatementOutboundMapper.java)
add_java_source(org/qore/lang/tablemapper/InboundIdentityTableMapper.java)
add_java_source(org/qore/lang/tablemapper/AbstractSqlStatementOutboundMapper.java)
add_java_source(org/qore/lang/tablemapper/InboundTableMapper.java)
add_java_source(org/qore/lang/tablemapper/SqlStatementOutboundMapper.java)
add_java_source(org/qore/lang/bulksqlutil/AbstractBulkOperation.java)
add_java_source(org/qore/lang/bulksqlutil/BulkRowCallback.java)
add_java_source(org/qore/lang/bulksqlutil/BulkInsertOperation.java)
add_java_source(org/qore/lang/bulksqlutil/BulkUpsertOperation.java)
add_java_source(org/qore/lang/dataprovider/AbstractDataProvider.java)
add_java_source(org/qore/lang/dataprovider/AbstractDataProviderType.java)
add_java_source(org/qore/lang/dataprovider/AbstractDataProviderRecordIterator.java)
add_java_source(org/qore/lang/dataprovider/AbstractDataProviderBulkRecordInterface.java)
add_java_source(org/qore/lang/dataprovider/AbstractDataField.java)
add_java_source(org/qore/lang/dataprovider/AbstractDataProviderBulkOperation.java)
add_java_source(org/qore/lang/dataprovider/AbstractDataProcessor.java)
add_java_source(org/qore/lang/qunit/Test.java)
add_java_source(org/qore/lang/qunit/TestCode.java)
add_java_source(org/qore/lang/qunit/TestCodeArgs.java)
add_java_source(org/qore/lang/qunit/TestCondition.java)
add_java_source(org/qore/lang/qunit/TestConditionArgs.java)
add_java_source(org/qore/lang/qunit/AbstractTestResult.java)
add_java_source(org/qore/lang/qunit/TestResultValue.java)
add_java_source(org/qore/lang/qunit/TestResultSuccess.java)
add_java_source(org/qore/lang/qunit/TestResultFailure.java)
add_java_source(org/qore/lang/qunit/TestResultExceptionType.java)
add_java_source(org/qore/lang/qunit/TestResultExceptionDetail.java)
add_java_source(org/qore/lang/qunit/TestResultExceptionRegexp.java)
add_java_source(org/qore/lang/qunit/TestResultExceptionSubstring.java)
add_java_source(org/qore/lang/LogCallback.java)
add_java_source(org/qore/lang/DatasourcePool.java)
add_java_source(org/qore/lang/AbstractSQLStatement.java)
add_java_source(org/qore/lang/HTTPClient.java)
add_java_source(org/qore/lang/soapclient/SoapClient.java)
add_java_source(org/qore/lang/restclient/RestClient.java)
add_java_source(org/qore/lang/mailmessage/Message.java)
add_java_source(org/qore/lang/mailmessage/Attachment.java)
add_java_source(org/qore/lang/mailmessage/Part.java)
add_java_source(org/qore/lang/smtpclient/SmtpClient.java)

add_java_compiler_source(org/qore/jni/compiler/CustomJavaFileObject.java)
add_java_compiler_source(org/qore/jni/compiler/PackageInternalsFinder.java)
add_java_compiler_source(org/qore/jni/compiler/QoreJavaClassObject.java)
add_java_compiler_source(org/qore/jni/compiler/QoreJavaCompiler.java JavaFileObjectImpl FileManagerImpl)
add_java_compiler_source(org/qore/jni/compiler/QoreJavaCompilerException.java $SafeDiagnostic)
add_java_compiler_source(org/qore/jni/compiler/QoreJavaFileManager.java)
add_java_compiler_source(org/qore/jni/compiler/CompilerOutput.java)
if (HAVE_KOTLIN_COMPILER)
    add_java_compiler_source(org/qore/jni/compiler/QoreKotlinCompiler.java)
    add_java_compiler_source(org/qore/jni/compiler/QoreKotlinCompilerException.java)
endif()

#message(STATUS "MESSAGE Src: ${JAVA_JAR_SRC}")
#message(STATUS "STR: ${JAVA_JAR_SRC_STR}")
#message(STATUS "MESSAGE Classes: ${JAVA_JAR_CLASSES}")
#message(STATUS "MESSAGE Inc: ${QORE_INC_FILES}")

set(CMAKE_JAVA_COMPILE_FLAGS -Xlint:unchecked -Xlint:deprecation -Xdiags:verbose --release 21)

add_custom_target(jni-sentinel
    DEPENDS ${JAVA_JAR_SRC} ${_qore_jni_sentinel}
)

add_custom_command(
    OUTPUT ${JAVA_JAR_CLASSES} ${_qore_jni_sentinel}
    COMMAND ${Java_JAVAC_EXECUTABLE} ${CMAKE_JAVA_COMPILE_FLAGS} -d ${CMAKE_CURRENT_BINARY_DIR} ${JAVA_JAR_SRC}
        -cp ${BYTE_BUDDY_JAR}
    COMMAND ${CMAKE_COMMAND} -E touch ${_qore_jni_sentinel}
    DEPENDS ${JAVA_JAR_SRC}
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
    VERBATIM
)

# Set up classpath for compiler jar - include kotlin-compiler.jar if available
set(COMPILER_CLASSPATH "qore-jni.jar")
if (HAVE_KOTLIN_COMPILER)
    set(COMPILER_CLASSPATH "${COMPILER_CLASSPATH}:${KOTLIN_COMPILER_JAR}")
endif()

add_custom_command(
    OUTPUT ${_qore_jni_compiler_sentinel} ${JAVA_JAR_COMPILER_CLASSES}
    COMMAND ${Java_JAVAC_EXECUTABLE} ${CMAKE_JAVA_COMPILE_FLAGS} -d ${CMAKE_CURRENT_BINARY_DIR}
        ${JAVA_JAR_COMPILER_SRC} -cp ${COMPILER_CLASSPATH}
    COMMAND ${CMAKE_COMMAND} -E touch ${_qore_jni_compiler_sentinel}
    DEPENDS ${JAVA_JAR_COMPILER_SRC} qore-jni.jar
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
    VERBATIM
)

add_custom_target(jni-compiler-sentinel
    DEPENDS ${JAVA_JAR_COMPILER_SRC} qore-jni.jar ${_qore_jni_compiler_sentinel}
)
# Ensure qore-jni.jar is built before compiling the compiler sources
add_dependencies(jni-compiler-sentinel qore-jni-jar)

add_jar_targets(
    org/qore/lang
    org/qore/lang/sqlutil
    org/qore/lang/mapper
    org/qore/lang/tablemapper
    org/qore/jni/QoreJavaDynamicApi.java
)

# generate jar manually
add_custom_command(
    OUTPUT qore-jni-qore.jar
    COMMAND ${Java_JAR_EXECUTABLE} cf qore-jni-qore.jar ${JAVA_JAR_CLASSES}
    DEPENDS ${JAVA_JAR_CLASSES} jni-sentinel
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
    VERBATIM
)

add_custom_target(qore-jni-qore-jar
    DEPENDS qore-jni-qore.jar
)

# combine jars into final jar
add_custom_command(
    OUTPUT qore-jni.jar
    COMMAND ${CMAKE_SOURCE_DIR}/make-jar -o qore-jni.jar ${BYTE_BUDDY_JAR}
        ${CMAKE_CURRENT_BINARY_DIR}/qore-jni-qore.jar
    DEPENDS ${BYTE_BUDDY_JAR} qore-jni-qore-jar
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
    VERBATIM
)

add_custom_target(qore-jni-jar
    DEPENDS qore-jni.jar
)

# build and install this jar file so that java code using native classes can be compiled
add_custom_target(qore-jni ALL DEPENDS qore-jni-jar qore-jni-compiler.jar qore-excel-jar qore-kafka-jar qore-word-jar qore-powerpoint-jar)
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/qore-jni.jar DESTINATION ${CMAKE_INSTALL_PREFIX}/share/qore/java)

if (HAVE_KOTLIN_COMPILER)
    add_jar(qore-jni-compiler ${JAVA_JAR_COMPILER_SRC}
        INCLUDE_JARS qore-jni.jar ${KOTLIN_COMPILER_JAR}
        DEPENDS qore-jni.jar
    )
else()
    add_jar(qore-jni-compiler ${JAVA_JAR_COMPILER_SRC}
        INCLUDE_JARS qore-jni.jar
        DEPENDS qore-jni.jar
    )
endif()

add_dependencies(qore-jni-compiler qore-jni)

install(FILES ${CMAKE_CURRENT_BINARY_DIR}/qore-jni-compiler.jar DESTINATION ${CMAKE_INSTALL_PREFIX}/share/qore/java)

install(PROGRAMS ${JNI_BIN_FILES}
    TYPE BIN
)

set(JAVA_TEST_JAR_SRC
    test/java/src/org/qore/jni/test/Callbacks.java
    test/java/src/org/qore/jni/test/Fields.java
    test/java/src/org/qore/jni/test/FloatConversions.java
    test/java/src/org/qore/jni/test/Methods.java
    test/java/src/org/qore/jni/test/A.java
    test/java/src/org/qore/jni/test/B.java
    test/java/src/org/qore/jni/test/C.java
    test/java/src/org/qore/jni/test/QoreJavaApiTest.java
    test/java/src/org/qore/jni/test/ClosureTest1.java
    test/java/src/org/qore/jni/test/ClosureTest2.java
    test/java/src/org/qore/jni/test/ClosureTest3.java
    test/java/src/org/qore/jni/test/StaticFields.java
    test/java/src/org/qore/jni/test/StaticMethods.java
    test/java/src/org/qore/jni/test/StringFactory.java
    test/java/src/org/qore/jni/test/QoreCallback.java
    test/java/src/org/qore/jni/test/OverloadedMethods.java
    test/java/src/org/qore/lang/test/QoreJavaLangApiTest.java
)

add_jar(qore-jni-test ${JAVA_TEST_JAR_SRC}
    INCLUDE_JARS qore-jni.jar
)

add_dependencies(qore-jni-test qore-jni)

add_custom_command(
    TARGET qore-jni-test
    POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_BINARY_DIR}/qore-jni-test.jar ${CMAKE_SOURCE_DIR}/test
)

set(QPP_SRC
    src/ql_jni.qpp
    src/QC_JavaArray.qpp
    src/QC_QoreInvocationHandler.qpp
)

set(CPP_SRC
    src/jni-module.cpp
    src/QoreJniClassMap.cpp
    src/defs.cpp
    src/Env.cpp
    src/GlobalReference.cpp
    src/Jvm.cpp
    src/Array.cpp
    src/Class.cpp
    src/Dispatcher.cpp
    src/Field.cpp
    src/Globals.cpp
    src/InvocationHandler.cpp
    src/Method.cpp
    src/JavaToQore.cpp
    src/QoreToJava.cpp
    src/QoreJniFunctionalInterface.cpp
    src/JniQoreClass.cpp
    src/QoreJdbcDriver.cpp
    src/QoreJdbcConnection.cpp
    src/QoreJdbcStatement.cpp
    src/QoreJdbcPreparedStatement.cpp
)

set(QMOD
    qlib/BusyLightDataProvider
    qlib/MqttDataProvider
    qlib/OpcUaDataProvider
    qlib/ExcelDataProvider
    qlib/KafkaDataProvider
    qlib/JakartaJmsDataProvider
    qlib/WordDataProvider
    qlib/PowerPointDataProvider
)

qore_wrap_qpp_value(QPP_SOURCES METALIST QPP_META_SRC ${QPP_SRC})

foreach (it ${QPP_SOURCES})
    GET_FILENAME_COMPONENT(_outfile ${it} NAME_WE)
    set(QPP_DOX ${QPP_DOX} ${CMAKE_CURRENT_BINARY_DIR}/${_outfile}.dox.h)
endforeach()

set(QORE_DOX_TMPL_SRC
    docs/mainpage.dox.tmpl
)

add_library(${module_name} MODULE ${CPP_SRC} ${QPP_SOURCES})
include_directories(${JNI_INCLUDE_DIRS})
include_directories(${CMAKE_SOURCE_DIR}/src)
target_include_directories(${module_name} PRIVATE $<BUILD_INTERFACE:${CMAKE_CURRENT_BINARY_DIR}/include>)

# add include file dependencies to Globals.cpp
set_source_files_properties(${CMAKE_SOURCE_DIR}/src/Globals.cpp
    PROPERTIES OBJECT_DEPENDS "${QORE_INC_FILES}"
)
# add include file dependency to QoreJniClassMap.cpp
set_source_files_properties(${CMAKE_SOURCE_DIR}/src/QoreJniClassMap.cpp
    PROPERTIES OBJECT_DEPENDS "${CMAKE_CURRENT_BINARY_DIR}/JavaClassQoreJavaDynamicApi.inc"
)

target_link_libraries(${module_name} ${JNI_LIBRARIES} ${BZIP2_LIBRARIES} ${QORE_LIBRARY})

set(MODULE_DOX_INPUT ${CMAKE_CURRENT_BINARY_DIR}/mainpage.dox ${JAVA_JAR_SRC_STR} ${QPP_DOX})
string(REPLACE ";" " " MODULE_DOX_INPUT "${MODULE_DOX_INPUT}")
#message(STATUS mdi: ${MODULE_DOX_INPUT})

if (DEFINED ENV{DOXYGEN_EXECUTABLE})
    set(DOXYGEN_EXECUTABLE $ENV{DOXYGEN_EXECUTABLE})
endif()

set(MODULE_DOX_INPUT ${CMAKE_BINARY_DIR} ${JAVA_JAR_SRC} ${JAVA_JAR_COMPILER_SRC})
qore_external_binary_module(${module_name} ${PROJECT_VERSION})
qore_user_modules("${QMOD}")

qore_external_user_module("qlib/MqttDataProvider" "")
qore_external_user_module("qlib/OpcUaDataProvider" "")
qore_external_user_module("qlib/BusyLightDataProvider" "MqttDataProvider")
qore_external_user_module("qlib/ExcelDataProvider" "")
qore_external_user_module("qlib/KafkaDataProvider" "")
qore_external_user_module("qlib/JakartaJmsDataProvider" "")
qore_external_user_module("qlib/WordDataProvider" "")
qore_external_user_module("qlib/PowerPointDataProvider" "")

qore_dist(${PROJECT_VERSION})

# install qpp metadata files
if (COMMAND qore_install_qpp_metadata)
    qore_install_qpp_metadata(${module_name} ${QPP_META_SRC})
else()
    install(FILES ${QPP_META_SRC}
            DESTINATION ${CMAKE_INSTALL_DATADIR}/qore/metadata)
endif()

qore_config_info()

if (DOXYGEN_FOUND)
    qore_wrap_dox(QORE_DOX_SRC ${QORE_DOX_TMPL_SRC})
    add_custom_target(QORE_MOD_DOX_FILES DEPENDS ${QORE_DOX_SRC})
    add_dependencies(docs-module QORE_MOD_DOX_FILES)
endif()
