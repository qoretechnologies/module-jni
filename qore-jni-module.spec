# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
%global source_date_epoch_from_changelog 1
%global use_source_date_epoch_as_buildtime 1
%if v"%{rpmversion}" >= v"4.20"
%global build_mtime_policy clamp_to_source_date_epoch
%else
%global clamp_mtime_to_source_date_epoch 1
%endif
%bcond_without tests
%bcond_without docs
%if 0%{?fedora} >= 44
%global java_major 25
%else
%global java_major 21
%endif
%if 0%{?suse_version}
%global java_home %{_libdir}/jvm/java-%{java_major}-openjdk
# The java-* alias belongs to the JDK; the headless runtime owns jre-*.
%global java_runtime_home %{_libdir}/jvm/jre-%{java_major}-openjdk
%else
%global java_home %{_prefix}/lib/jvm/java-%{java_major}-openjdk
%global java_runtime_home %{java_home}
%endif
# Third-party signed JAR bytes are pinned and must remain unchanged.
%global __brp_java_repack_jars %{nil}
%global _find_debuginfo_dwz_opts %{nil}
Name: qore-jni-module
Version: 3.0.0
Release: 1%{?dist}
Summary: Bidirectional Java bridge and Java data providers for Qore
License: MIT AND Apache-2.0 AND BSD-2-Clause AND BSD-3-Clause AND EPL-2.0 AND LGPL-2.1-or-later AND LGPL-3.0-or-later AND MPL-1.1 AND MPL-2.0 AND LicenseRef-Public-Domain AND LicenseRef-OpenPDF-notices
URL: https://github.com/qoretechnologies/module-jni
Source0: %{name}-%{version}.tar.xz
Source1: qore-jni-vendor-%{version}.tar.xz
BuildRequires: cmake >= 3.24
BuildRequires: make
BuildRequires: gcc-c++
BuildRequires: java-%{java_major}-openjdk-devel
BuildRequires: qore-devel >= 3.0.0~
BuildRequires: qore-rpm-macros >= 3.0.0~
BuildRequires: python3 >= 3.11
BuildRequires: unzip
BuildRequires: zip
%if 0%{?suse_version}
BuildRequires: libbz2-devel
BuildRequires: libfreetype6
BuildRequires: libharfbuzz0
BuildRequires: dejavu-fonts
Requires: libfreetype6
Requires: libharfbuzz0
Requires: dejavu-fonts
%else
BuildRequires: bzip2-devel
BuildRequires: freetype
BuildRequires: harfbuzz
BuildRequires: dejavu-sans-fonts
Requires: freetype
Requires: harfbuzz
Requires: dejavu-sans-fonts
%endif
BuildRequires: fontconfig
Requires: fontconfig
# JNI embeds the JVM built for this major; no JDK is needed to use the bridge.
Requires: java-%{java_major}-openjdk-headless
%if %{with tests}
BuildRequires: git
BuildRequires: qore-misc-tools >= 3.0.0~
BuildRequires: qore-python-module
BuildRequires: qore-xml-module
%endif
%if %{with docs}
BuildRequires: doxygen
%if 0%{?suse_version}
BuildRequires: util-linux
%else
BuildRequires: util-linux-core
%endif
%endif
# Private Java dependency inventory; upstream coordinates remain in provenance.json.
Provides: bundled(mvn(com.digitalpetri.fsm:strict-machine)) = 1.0.0
Provides: bundled(mvn(com.digitalpetri.netty:netty-channel-fsm)) = 1.0.2
Provides: bundled(mvn(com.fasterxml.jackson.core:jackson-annotations)) = 2.15.2
Provides: bundled(mvn(com.fasterxml.jackson.core:jackson-annotations)) = 2.15.3
Provides: bundled(mvn(com.fasterxml.jackson.core:jackson-annotations)) = 2.17.2
Provides: bundled(mvn(com.fasterxml.jackson.core:jackson-core)) = 2.15.2
Provides: bundled(mvn(com.fasterxml.jackson.core:jackson-core)) = 2.15.3
Provides: bundled(mvn(com.fasterxml.jackson.core:jackson-core)) = 2.17.2
Provides: bundled(mvn(com.fasterxml.jackson.core:jackson-databind)) = 2.15.2
Provides: bundled(mvn(com.fasterxml.jackson.core:jackson-databind)) = 2.15.3
Provides: bundled(mvn(com.fasterxml.jackson.core:jackson-databind)) = 2.17.2
Provides: bundled(mvn(com.fasterxml.jackson.dataformat:jackson-dataformat-toml)) = 2.15.2
Provides: bundled(mvn(com.fasterxml.jackson.dataformat:jackson-dataformat-xml)) = 2.15.3
Provides: bundled(mvn(com.fasterxml.jackson.dataformat:jackson-dataformat-yaml)) = 2.17.2
Provides: bundled(mvn(com.fasterxml.jackson.datatype:jackson-datatype-jsr310)) = 2.15.2
Provides: bundled(mvn(com.fasterxml.woodstox:woodstox-core)) = 6.5.1
Provides: bundled(mvn(com.github.albfernandez:juniversalchardet)) = 2.5.0
Provides: bundled(mvn(com.github.andrewoma.dexx:collection)) = 0.7
Provides: bundled(mvn(com.github.ben-manes.caffeine:caffeine)) = 3.1.8
Provides: bundled(mvn(com.github.librepdf:openpdf)) = 1.3.32
Provides: bundled(mvn(com.github.luben:zstd-jni)) = 1.5.6~9
Provides: bundled(mvn(com.github.mangstadt:vinnie)) = 2.0.2
Provides: bundled(mvn(com.github.virtuald:curvesapi)) = 1.08
Provides: bundled(mvn(com.google.errorprone:error_prone_annotations)) = 2.21.1
Provides: bundled(mvn(com.googlecode.ez-vcard:ez-vcard)) = 0.12.1
Provides: bundled(mvn(com.healthmarketscience.jackcess:jackcess)) = 4.0.7
Provides: bundled(mvn(com.sun.istack:istack-commons-runtime)) = 4.1.2
Provides: bundled(mvn(com.zaxxer:SparseBitSet)) = 1.3
Provides: bundled(mvn(commons-beanutils:commons-beanutils)) = 1.11.0
Provides: bundled(mvn(commons-beanutils:commons-beanutils)) = 1.9.4
Provides: bundled(mvn(commons-cli:commons-cli)) = 1.5.0
Provides: bundled(mvn(commons-codec:commons-codec)) = 1.17.0
Provides: bundled(mvn(commons-codec:commons-codec)) = 1.17.1
Provides: bundled(mvn(commons-codec:commons-codec)) = 1.19.0
Provides: bundled(mvn(commons-collections:commons-collections)) = 3.2.2
Provides: bundled(mvn(commons-digester:commons-digester)) = 2.1
Provides: bundled(mvn(commons-io:commons-io)) = 2.16.1
Provides: bundled(mvn(commons-io:commons-io)) = 2.17.0
Provides: bundled(mvn(commons-io:commons-io)) = 2.20.0
Provides: bundled(mvn(commons-logging:commons-logging)) = 1.1.1
Provides: bundled(mvn(commons-logging:commons-logging)) = 1.2
Provides: bundled(mvn(commons-logging:commons-logging)) = 1.3.5
Provides: bundled(mvn(commons-validator:commons-validator)) = 1.10.1
Provides: bundled(mvn(io.netty:netty-buffer)) = 4.1.133.Final
Provides: bundled(mvn(io.netty:netty-codec)) = 4.1.133.Final
Provides: bundled(mvn(io.netty:netty-common)) = 4.1.133.Final
Provides: bundled(mvn(io.netty:netty-handler)) = 4.1.133.Final
Provides: bundled(mvn(io.netty:netty-resolver)) = 4.1.133.Final
Provides: bundled(mvn(io.netty:netty-transport)) = 4.1.133.Final
Provides: bundled(mvn(io.netty:netty-transport-native-unix-common)) = 4.1.133.Final
Provides: bundled(mvn(jakarta.activation:jakarta.activation-api)) = 2.1.3
Provides: bundled(mvn(jakarta.jms:jakarta.jms-api)) = 3.1.0
Provides: bundled(mvn(jakarta.mail:jakarta.mail-api)) = 2.1.3
Provides: bundled(mvn(jakarta.xml.bind:jakarta.xml.bind-api)) = 4.0.2
Provides: bundled(mvn(net.bytebuddy:byte-buddy)) = 1.18.10
Provides: bundled(mvn(net.rootdev:java-rdfa)) = 1.0.0~BETA1
Provides: bundled(mvn(net.sf.jasperreports:jasperreports)) = 6.21.3
Provides: bundled(mvn(org.apache.avro:avro)) = 1.12.0
Provides: bundled(mvn(org.apache.camel:camel-api)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-base)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-base-engine)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-bean)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-browse)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-cluster)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-controlbus)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-core)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-core-catalog)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-core-engine)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-core-languages)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-core-model)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-core-processor)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-core-reifier)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-dataformat)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-dataset)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-direct)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-file)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-health)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-language)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-log)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-main)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-management-api)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-mock)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-ref)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-rest)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-saga)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-scheduler)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-seda)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-stub)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-support)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-timer)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-tooling-model)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-util)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-util-json)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-validator)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-xml-io)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-xml-io-util)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-xml-jaxb)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-xml-jaxp)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-xml-jaxp-util)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-xpath)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-xslt)) = 4.8.0
Provides: bundled(mvn(org.apache.camel:camel-yaml-io)) = 4.8.0
Provides: bundled(mvn(org.apache.commons:commons-collections4)) = 4.2
Provides: bundled(mvn(org.apache.commons:commons-collections4)) = 4.4
Provides: bundled(mvn(org.apache.commons:commons-collections4)) = 4.5.0
Provides: bundled(mvn(org.apache.commons:commons-compress)) = 1.26.2
Provides: bundled(mvn(org.apache.commons:commons-compress)) = 1.28.0
Provides: bundled(mvn(org.apache.commons:commons-csv)) = 1.10.0
Provides: bundled(mvn(org.apache.commons:commons-csv)) = 1.12.0
Provides: bundled(mvn(org.apache.commons:commons-lang3)) = 3.10
Provides: bundled(mvn(org.apache.commons:commons-lang3)) = 3.14.0
Provides: bundled(mvn(org.apache.commons:commons-lang3)) = 3.18.0
Provides: bundled(mvn(org.apache.commons:commons-lang3)) = 3.20.0
Provides: bundled(mvn(org.apache.commons:commons-math3)) = 3.6.1
Provides: bundled(mvn(org.apache.jena:jena-base)) = 4.10.0
Provides: bundled(mvn(org.apache.jena:jena-core)) = 4.10.0
Provides: bundled(mvn(org.apache.jena:jena-iri)) = 4.10.0
Provides: bundled(mvn(org.apache.kafka:kafka-clients)) = 3.9.0
Provides: bundled(mvn(org.apache.logging.log4j:log4j-api)) = 2.24.3
Provides: bundled(mvn(org.apache.logging.log4j:log4j-to-slf4j)) = 2.24.3
Provides: bundled(mvn(org.apache.poi:poi)) = 5.5.0
Provides: bundled(mvn(org.apache.poi:poi-ooxml)) = 5.5.0
Provides: bundled(mvn(org.apache.poi:poi-ooxml-full)) = 5.5.0
Provides: bundled(mvn(org.apache.poi:poi-scratchpad)) = 5.5.0
Provides: bundled(mvn(org.apache.tika:tika-core)) = 3.0.0
Provides: bundled(mvn(org.apache.tika:tika-parser-html-module)) = 3.0.0
Provides: bundled(mvn(org.apache.tika:tika-parser-text-module)) = 3.0.0
Provides: bundled(mvn(org.apache.tika:tika-parser-xml-module)) = 3.0.0
Provides: bundled(mvn(org.apache.xmlbeans:xmlbeans)) = 5.3.0
Provides: bundled(mvn(org.bouncycastle:bcpkix-jdk18on)) = 1.84
Provides: bundled(mvn(org.bouncycastle:bcprov-jdk18on)) = 1.83
Provides: bundled(mvn(org.bouncycastle:bcprov-jdk18on)) = 1.84
Provides: bundled(mvn(org.bouncycastle:bcutil-jdk18on)) = 1.84
Provides: bundled(mvn(org.checkerframework:checker-qual)) = 3.37.0
Provides: bundled(mvn(org.codehaus.woodstox:stax2-api)) = 4.2.1
Provides: bundled(mvn(org.eclipse.angus:angus-activation)) = 2.0.2
Provides: bundled(mvn(org.eclipse.angus:angus-mail)) = 2.0.3
Provides: bundled(mvn(org.eclipse.jdt:ecj)) = 3.21.0
Provides: bundled(mvn(org.eclipse.milo:milo-sdk-client)) = 1.1.4
Provides: bundled(mvn(org.eclipse.milo:milo-sdk-core)) = 1.1.4
Provides: bundled(mvn(org.eclipse.milo:milo-sdk-server)) = 1.1.4
Provides: bundled(mvn(org.eclipse.milo:milo-stack-core)) = 1.1.4
Provides: bundled(mvn(org.eclipse.milo:milo-transport)) = 1.1.4
Provides: bundled(mvn(org.eclipse.paho:org.eclipse.paho.mqttv5.client)) = 1.2.5
Provides: bundled(mvn(org.eclipse.paho:org.eclipse.paho.mqttv5.common)) = 1.2.5
Provides: bundled(mvn(org.flywaydb:flyway-core)) = 10.20.0
Provides: bundled(mvn(org.flywaydb:flyway-database-postgresql)) = 10.20.0
Provides: bundled(mvn(org.freemarker:freemarker)) = 2.3.32
Provides: bundled(mvn(org.glassfish.jaxb:jaxb-core)) = 4.0.5
Provides: bundled(mvn(org.glassfish.jaxb:jaxb-runtime)) = 4.0.5
Provides: bundled(mvn(org.glassfish.jaxb:txw2)) = 4.0.5
Provides: bundled(mvn(org.jfree:jcommon)) = 1.0.23
Provides: bundled(mvn(org.jfree:jfreechart)) = 1.0.19
Provides: bundled(mvn(org.json:json)) = 20251224
Provides: bundled(mvn(org.jsoup:jsoup)) = 1.16.1
Provides: bundled(mvn(org.jsoup:jsoup)) = 1.18.1
Provides: bundled(mvn(org.jspecify:jspecify)) = 1.0.0
Provides: bundled(mvn(org.lz4:lz4-java)) = 1.8.0
Provides: bundled(mvn(org.mnode.ical4j:ical4j)) = 4.0.8
Provides: bundled(mvn(org.odftoolkit:odfdom-java)) = 0.13.0
Provides: bundled(mvn(org.roaringbitmap:RoaringBitmap)) = 1.0.0
Provides: bundled(mvn(org.slf4j:slf4j-api)) = 1.7.30
Provides: bundled(mvn(org.slf4j:slf4j-api)) = 2.0.12
Provides: bundled(mvn(org.slf4j:slf4j-api)) = 2.0.13
Provides: bundled(mvn(org.slf4j:slf4j-api)) = 2.0.16
Provides: bundled(mvn(org.slf4j:slf4j-api)) = 2.0.18
Provides: bundled(mvn(org.slf4j:slf4j-nop)) = 1.7.30
Provides: bundled(mvn(org.slf4j:slf4j-nop)) = 2.0.12
Provides: bundled(mvn(org.slf4j:slf4j-nop)) = 2.0.13
Provides: bundled(mvn(org.slf4j:slf4j-nop)) = 2.0.16
Provides: bundled(mvn(org.slf4j:slf4j-nop)) = 2.0.18
Provides: bundled(mvn(org.threeten:threeten-extra)) = 1.8.0
Provides: bundled(mvn(org.xerial.snappy:snappy-java)) = 1.1.10.7
Provides: bundled(mvn(org.yaml:snakeyaml)) = 2.2
Provides: bundled(mvn(xalan:serializer)) = 2.7.3
Provides: bundled(mvn(xerces:xercesImpl)) = 2.12.2
%{?qore_enable_aot_post}

%description
Native Java integration, JDBC, dynamic Java bindings and source and compiled
provider modules with runtime JARs, translations and compiler metadata.
Providers cover office documents, email, calendars, contacts, Avro, Tika,
JasperReports, JDBC, Flyway, Camel, MQTT, OPC UA, JMS and Kafka. Third-party
Java dependencies are checksum-pinned upstream binaries with source archives
and notices retained in the source package.

%package -n qore-jni-tools
Summary: Java compilation and import migration tools for Qore
BuildArch: noarch
License: MIT
Requires: %{name} = %{version}-%{release}
Requires: java-%{java_major}-openjdk-devel
%description -n qore-jni-tools
qjavac, qjava2jar and qjava-migrate-imports compile Java sources against
Qore APIs and migrate legacy imports.

%package -n qore-jni-kotlin
Summary: Kotlin compiler and scripting support for Qore JNI
BuildArch: noarch
License: MIT AND LicenseRef-Kotlin-notices
Requires: %{name} = %{version}-%{release}
Requires: java-%{java_major}-openjdk-devel
Requires: qore-process-module
Requires: curl
Requires: which
Provides: bundled(kotlin) = 2.3.0
%description -n qore-jni-kotlin
The pinned Kotlin 2.3.0 compiler, matching scripting dependencies and qkotlinc.
The private installation enables Kotlin compilation against dynamic Qore APIs
and JSR-223 scripting without changing the system kotlinc or alternatives.

%if %{with docs}
%package doc
Summary: API documentation for Qore Java integration
BuildArch: noarch
License: MIT
%description doc
HTML API references for the JNI bridge and its provider modules.
%endif

%prep
%autosetup -a 1
mv qore-jni-vendor-%{version}/vendor vendor
cmp debian/copyright qore-jni-vendor-%{version}/COPYRIGHT
cmp debian/java-dependencies.json qore-jni-vendor-%{version}/java-dependencies.json
%build
%{?set_build_flags}
. %{_rpmconfigdir}/qore/module-env.sh
unset CLASSPATH QORE_CLASSPATH QORE_JNI_CLASSPATH JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
export QORE_JNI_JVM_ARGS=-Xcheck:jni
export JAVA_HOME=%{java_home}
export KOTLIN_HOME="$PWD/build/vendor-kotlin/kotlinc"
export PATH="$JAVA_HOME/bin:$KOTLIN_HOME/bin:$PATH"
python3 -B -W error debian/prepare-vendor.py verify
python3 -B -W error debian/prepare-vendor.py unpack-kotlin --destination build/vendor-kotlin
qore_set_source_prefix_maps "%{qore_debug_source_dir}"
cmake -S . -B build -G 'Unix Makefiles' \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_FLAGS_RELEASE=-DNDEBUG \
  -DCMAKE_INSTALL_PREFIX=%{_prefix} \
  -DQore_DIR=%{_libdir}/cmake/Qore -DQORE_EXECUTABLE=/usr/bin/qore \
  -DQORE_QPP_EXECUTABLE=/usr/bin/qpp -DQORE_QCC_EXECUTABLE=/usr/bin/qcc \
  -DQORE_JAVA_PROVIDER_SOURCE_ARCHIVE=ON -DQORE_BUILD_AOT_MODULES=ON \
  -DQORE_JNI_STRICT_DOCS=ON \
  -DQORE_AOT_LINK_SOURCE_MODULES=OFF -DQORE_AOT_MODULE_OPT_LEVEL=3 \
  -DCMAKE_SKIP_RPATH=OFF -DCMAKE_SKIP_INSTALL_RPATH=OFF \
  -DCMAKE_INSTALL_RPATH="%{java_runtime_home}/lib/server" -DCMAKE_IGNORE_PREFIX_PATH=/usr/local \
  -DQORE_QM_METADATA_ENV:STRING="QORE_MODULE_DIR=$QORE_MODULE_DIR:$PWD/qlib;QORE_MODULE_DIR_ONLY=1;QORE_INCLUDE_DIR=;LD_LIBRARY_PATH=" \
  -DCMAKE_DISABLE_FIND_PACKAGE_Doxygen=%{!?with_docs:ON}%{?with_docs:OFF}
cmake --build build -- %{?_smp_mflags}
%if %{with docs}
cmake --build build --target docs -- %{?_smp_mflags}
python3 -B -W error test/test-doc-index.py --build-dir build -v
%endif
%install
DESTDIR=%{buildroot} cmake --install build
install -d %{buildroot}%{_datadir}/qore/java/kotlin
cp -a build/vendor-kotlin/kotlinc/. %{buildroot}%{_datadir}/qore/java/kotlin/
python3 -B -W error debian/install-notices.py \
  --runtime-root %{buildroot} --kotlin-root %{buildroot} --docdir %{_docdir}
%qore_install_aot_sources qlib
find %{buildroot}%{_libdir}/qore-modules -type f -name '*.qmod' -exec chmod 755 {} +
%if %{with docs}
install -d %{buildroot}%{_docdir}/%{name}-doc
cp -a build/docs %{buildroot}%{_docdir}/%{name}-doc/
hardlink -t -O %{buildroot}%{_docdir}/%{name}-doc
%endif
%check
%if %{with tests}
. %{_rpmconfigdir}/qore/module-env.sh
unset CLASSPATH QORE_CLASSPATH QORE_JNI_CLASSPATH JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
export JAVA_HOME=%{java_home}
export KOTLIN_HOME="$PWD/build/vendor-kotlin/kotlinc"
export PATH="$JAVA_HOME/bin:$KOTLIN_HOME/bin:$PATH"
python3 -B -W error -m unittest discover -s rpm -v
python3 -B -W error debian/tests/test_aot_metadata.py
python3 -B -W error debian/tests/test_vendor.py
ctest --test-dir build --output-on-failure
sh test/java/build-test-server.sh
"$KOTLIN_HOME/bin/kotlinc" -jvm-target 21 -cp build/qore-jni.jar \
  -d test/kotlin-test.jar test/KotlinQoreApiTest.kt test/KotlinTestClass.kt
export QORE_JNI_CLASSPATH="$PWD/build/qore-jni.jar:$PWD/build/qore-jni-compiler.jar"
export QORE_JNI_JVM_ARGS=-Xcheck:jni
python3 -B -W error rpm/check-jdbc-fixture.py --build-dir "$PWD/build"
python3 -B -W error rpm/run-tests.py --build-dir "$PWD/build"
qore-data-provider-i18n --no-color --check-source-tree --require-standard-locales \
  --require-complete-locales --output "$PWD/qlib"
%endif
%files
%license debian/copyright
%license %{_docdir}/%{name}/third-party-notices/
%doc README*
%{_libdir}/qore-modules/*
%{_datadir}/qore-modules/*
%{_datadir}/qore/metadata/*
%{_datadir}/qore/i18n/
%{_datadir}/qore-jni/
%dir %{_datadir}/qore/java
%{_datadir}/qore/java/qore-jni.jar
%{_datadir}/qore/java/qore-jni-compiler.jar
%files -n qore-jni-tools
%license debian/copyright
%{_bindir}/qjavac
%{_bindir}/qjava2jar
%{_bindir}/qjava-migrate-imports
%files -n qore-jni-kotlin
%license debian/copyright
%dir %{_docdir}/qore-jni-kotlin
%license %{_docdir}/qore-jni-kotlin/upstream-licenses/
%{_bindir}/qkotlinc
%{_bindir}/download-kotlin-scripting-jars
%{_datadir}/qore/java/kotlin/
%if %{with docs}
%files doc
%license debian/copyright
%doc %{_docdir}/%{name}-doc/
%endif
%changelog
* Sat Oct 03 2026 David Nichols <david@qore.org> - 3.0.0-1
- Align the JNI release with Qore 3.0 and consolidate unreleased 2.5 through 2.7 notes.

* Fri Oct 02 2026 David Nichols <david@qore.org> - 2.7.0-1
- Package the native bridge, AOT providers, metadata, translations and documentation.
- Pin Java and Kotlin inputs with source archives and complete upstream notices.
- Split optional Java tools and the private Kotlin compiler from the runtime.
