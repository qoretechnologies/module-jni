%{?_datarootdir: %global mydatarootdir %_datarootdir}
%{!?_datarootdir: %global mydatarootdir %{buildroot}/usr/share}

%global module_dir %{_libdir}/qore-modules
%global user_module_dir %{mydatarootdir}/qore-modules/

Name:           qore-jni-module
Version:        2.6.0
Release:        1
Summary:        Qorus Integration Engine - Qore jni module
License:        MIT
Group:          Productivity/Networking/Other
Url:            https://qoretechnologies.com
Source:         qore-jni-module-%{version}.tar.bz2
BuildRequires:  gcc-c++
%if 0%{?el7}
BuildRequires:  devtoolset-7-gcc-c++
%endif
BuildRequires:  cmake >= 3.5
BuildRequires:  qore-devel >= 2.0
BuildRequires:  qore-stdlib >= 2.0
BuildRequires:  qore >= 2.0
BuildRequires:  java-21-openjdk-devel
BuildRequires:  unzip
BuildRequires:  doxygen

%if 0%{?suse_version}
%if 0%{?sles_version} && %{?sles_version} <= 10
BuildRequires:  bzip2
%else
BuildRequires:  libbz2-devel
%endif
%else
BuildRequires:  bzip2-devel
%endif

Requires:       %{_bindir}/env
Requires:       qore >= 1.0
BuildRoot:      %{_tmppath}/%{name}-%{version}-build
Requires:       java-21-openjdk-devel
Requires:       java-21-openjdk
# Kotlin is optional - only needed for kotlin_eval() scripting support
# Basic Kotlin class support works with just kotlin-stdlib in the classpath
Suggests:       kotlin
%if 0%{?el8}
# disable automatic library dependencies due to broken java 11 lib handling in centos 8
AutoReqProv: no
%endif

%description
This package contains the jni module for the Qore Programming Language.

%prep
%setup -q

%build
%if 0%{?el7}
# enable devtoolset7
. /opt/rh/devtoolset-7/enable
%endif
export CXXFLAGS="%{?optflags}"
%if 0%{?suse_version}
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/lib64/jvm/jre/lib/server
cmake -DCMAKE_INSTALL_PREFIX=%{_prefix} -DCMAKE_BUILD_TYPE=RELWITHDEBINFO -DCMAKE_SKIP_RPATH=1 -DCMAKE_SKIP_INSTALL_RPATH=1 -DCMAKE_SKIP_BUILD_RPATH=1 -DCMAKE_PREFIX_PATH=${_prefix}/lib64/cmake/Qore -DJAVA_AWT_LIBRARY=/usr/lib64/jvm/jre/lib/libjawt.so .
%else
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/lib/jvm/jre/lib:/usr/lib/jvm/jre/lib/server
cmake -DCMAKE_INSTALL_PREFIX=%{_prefix} -DCMAKE_BUILD_TYPE=RELWITHDEBINFO -DCMAKE_SKIP_RPATH=1 -DCMAKE_SKIP_INSTALL_RPATH=1 -DCMAKE_SKIP_BUILD_RPATH=1 -DCMAKE_PREFIX_PATH=${_prefix}/lib64/cmake/Qore -DJAVA_AWT_LIBRARY=/usr/lib/jvm/jre/lib/libjawt.so .
%endif
make %{?_smp_mflags}
make %{?_smp_mflags} docs
sed -i 's/#!\/usr\/bin\/env qore/#!\/usr\/bin\/qore/' test/*.qtest

%install
make DESTDIR=%{buildroot} install %{?_smp_mflags}

%files
%{module_dir}
%{user_module_dir}
%{_bindir}/qjava2jar
%{_bindir}/qjavac
%{_bindir}/qkotlinc
%{_bindir}/download-kotlin-scripting-jars
%dir /usr/share/qore/java
/usr/share/qore/java/qore-jni-compiler.jar
/usr/share/qore/java/qore-jni.jar

%package doc
Summary: jni module for Qore
Group: Development/Languages/Other

%description doc
jni module for the Qore Programming Language.

This RPM provides API documentation, test and example programs

%files doc
%defattr(-,root,root,-)
%doc docs/jni test/*.qtest test/*.jar test/*.java

%changelog
* Sat Jan 4 2026 David Nichols <david@qore.org>
- updated to version 2.6.0
- added full Kotlin language integration support
- added kotlin_eval(), kotlin_scripting_available(), kotlin_scripting_retry() functions
- added download-kotlin-scripting-jars script for downloading scripting JARs from Maven
- added qkotlinc helper script for compiling Kotlin sources using Qore APIs
- added ExcelDataProvider module for reading and writing Excel spreadsheets
- added WordDataProvider module for reading and writing Word documents
- added PowerPointDataProvider module for reading and writing PowerPoint presentations
- added shared QoreInputStreamWrapper and QoreOutputStreamWrapper stream adapters
- added streaming write support for all data providers via getOutputStreamForLocation()
- enhanced qjava2jar to accept multiple source paths

* Sat Dec 28 2024 David Nichols <david@qore.org>
- updated to version 2.5.0
- requires Java 21+ for compatibility with Java 25
- replaced deprecated finalize() methods with java.lang.ref.Cleaner API

* Sun Oct 1 2023 David Nichols <david@qore.org>
- updated to version 2.4.0

* Sun Aug 27 2023 David Nichols <david@qore.org>
- updated to version 2.3.1

* Fri Aug 18 2023 David Nichols <david@qore.org>
- updated to version 2.3.0

* Thu Aug 3 2023 David Nichols <david@qore.org>
- updated to version 2.2.1

* Tue Aug 1 2023 David Nichols <david@qore.org>
- updated to version 2.2.0

* Sun Mar 19 2023 David Nichols <david@qore.org>
- updated to version 2.1.1

* Tue Jan 24 2023 David Nichols <david@qore.org>
- updated to version 2.1.0

* Mon Dec 19 2022 David Nichols <david@qore.org>
- updated to version 2.0.11

* Sat Oct 29 2022 David Nichols <david@qore.org>
- updated to version 2.0.10

* Sat Sep 17 2022 David Nichols <david@qore.org>
- updated to version 2.0.9

* Tue Aug 9 2022 David Nichols <david@qore.org>
- updated to version 2.0.8

* Thu Apr 21 2022 David Nichols <david@qore.org>
- updated to version 2.0.7

* Thu Jan 27 2022 David Nichols <david@qore.org>
- initial spec file for 2.0.6 release
