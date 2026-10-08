RPM source preparation
======================

Copyright 2026 Qore Technologies, s.r.o.

The RPM build uses the same pinned Java and Kotlin dependency inventory as
the Debian packages. Third-party runtime JARs retain their original bytes;
matching source archives, POM files and upstream notices travel in a separate
source component. The optional private Kotlin compiler does not replace the
system compiler or alternatives.

Prepare verified vendor inputs using ``debian/prepare-vendor.py`` as described
in ``debian/README.source``. Then create the aggregate source component::

    python3 rpm/prepare-vendor.py --vendor vendor --cache /path/to/cache \
        --manifest /path/to/vendor-sources.json

The resulting manifest pins the generator, input inventory and copyright
catalog. Compare it with ``rpm/vendor-sources.json`` before preparing a release.
The qore-packaging source tool verifies these committed pins and the aggregate
archive checksum. A missing or corrupt aggregate is an error; no network
access is needed during rpmbuild.

``debian/install-notices.py`` accepts ``--runtime-root`` and ``--kotlin-root``
for an RPM staging directory and ``--docdir`` for the distribution's absolute
documentation prefix (for example, ``/usr/share/doc/packages`` on openSUSE).
The prefix is resolved inside each staging root. With no arguments it retains its Debian package
paths. It extracts verbatim upstream notices and provenance without modifying
runtime JARs. Test both preparation and notice installation with::

    python3 -B -W error -m unittest discover -s rpm -v

Packages and installation
-------------------------

The multi-distribution recipe builds on Fedora 44, Enterprise Linux 10 and
openSUSE Leap 16. The runtime package ``qore-jni-module`` contains the bridge,
22 compiled provider modules, source modules, compiler metadata, translations,
runtime JARs and third-party notices. It needs a headless JRE, fonts and font
libraries, including for server-side office-document processing.

Install the bridge from a configured Qore RPM repository with::

    dnf install qore-jni-module
    # openSUSE:
    zypper install qore-jni-module
    qore -b --enable-debug -l jni -nX 'printf("JNI module loaded\n")'

``qore-jni-tools`` adds ``qjavac``, ``qjava2jar`` and import migration tools.
``qore-jni-kotlin`` adds ``qkotlinc`` and the private pinned Kotlin compiler.
Both optional packages require the JDK; the bridge runtime does not.
``qore-jni-module-doc`` supplies the generated HTML API documentation.
The configured JVM major is 25 on Fedora 44 and 21 on Leap and Enterprise Linux.

Qualification
-------------

Candidate builds and installed SDK/runtime tests passed on all three targets.
Each build and SDK run executes 37 Qore suites (619 cases, 8,574 assertions);
the minimal runtime runs 23 suites (465 cases, 4,248 assertions). Build checks
also cover 13 CTests, vendor and metadata validation, 14 RPM helper tests,
strict documentation, fixture failure propagation and translation completeness.
Installed checks cover a compiled consumer, headless spreadsheet imports,
all 22 AOT providers, separate debug symbols and Qore sources, unchanged JAR
bytes, and verbatim upstream notices. Canonical committed-source rebuilds and
OBS qualification are recorded separately by ``qore-packaging``.

Live JDBC servers, JMS, configured external MQTT/OPC UA services and BusyLight
hardware remain separate integration gates. Missing optional configuration is
reported as skipped; an explicitly configured but unusable fixture must fail.

Approved external diagnostics are narrowly documented in ``qore-packaging``:
``evidence/jni-external-diagnostics-20261002.json`` (JVM/glibc controls),
``evidence/jni-compiler-diagnostics-20261002.json`` (GCC 16), and
``evidence/jni-awt-diagnostic-20261003.json`` (OpenJDK 21 checked-JNI font shaping).
The JVM checking flag, compiler flags and diagnostic output remain enabled.
