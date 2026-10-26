#!/bin/bash

set -e
set -x

export ENV_FILE=/tmp/env.sh

. ${ENV_FILE}

# setup MODULE_SRC_DIR env var
cwd=`pwd`
if [ -z "${MODULE_SRC_DIR}" ]; then
    if [ -e "$cwd/src/jni-module.cpp" ]; then
        MODULE_SRC_DIR=$cwd
    else
        MODULE_SRC_DIR=$WORKDIR/module-jni
    fi
fi
echo "export MODULE_SRC_DIR=${MODULE_SRC_DIR}" >> ${ENV_FILE}
echo "export QORE_CLASSPATH=${MODULE_SRC_DIR}/build/qore-jni.jar" >> ${ENV_FILE}

echo "export QORE_UID=1000" >> ${ENV_FILE}
echo "export QORE_GID=1000" >> ${ENV_FILE}

echo "export PAYARA_USER=qore" >> ${ENV_FILE}
echo "export PAYARA_HOME=/home/qore/glassfish4" >> ${ENV_FILE}
echo "export PAYARA_JAR=\${PAYARA_HOME}/glassfish/lib/gf-client.jar" >> ${ENV_FILE}

. ${ENV_FILE}

export MAKE_JOBS=4

# the tests must load the modules of this tree: reject relative module paths in them
${MODULE_SRC_DIR}/test/docker_test/check-module-paths.sh jni

# build module and install
echo && echo "-- building module --"
mkdir -p ${MODULE_SRC_DIR}/build
cd ${MODULE_SRC_DIR}/build
cmake .. -DCMAKE_BUILD_TYPE=debug -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX}
make -j${MAKE_JOBS}
ctest --output-on-failure -R '^(java_provider_profile_|generated_binding_registry$|make_inc_reproducibility$)'
make install

# Verify that source-owned provider presentation catalogs match this checkout.
${MODULE_SRC_DIR}/test/docker_test/check-i18n.sh
${MODULE_SRC_DIR}/test/docker_test/check-java-logging.sh

# add Qore user and group
if ! grep -q "^qore:x:${QORE_GID}" /etc/group; then
    addgroup -g ${QORE_GID} qore
fi
if ! grep -q "^qore:x:${QORE_UID}" /etc/passwd; then
    adduser -u ${QORE_UID} -D -G qore -h /home/qore -s /bin/bash qore
fi

cd /home/qore

# the tests write below test/ as the qore user: own the tree explicitly instead of relying on the runner's
# checkout being writable for every user
chown -R qore:qore ${MODULE_SRC_DIR} /home/qore

if [ -n "$do_jms_test" ]; then
    # download glassfish
    git clone -b master --single-branch --depth 1 https://${GLASSFISH_REPO_AUTH}@git.qoretechnologies.com/infrastructure/glassfish4.git
    rm -rf glassfish4/.git

    # own everything by the qore user
    chown -R qore:qore ${MODULE_SRC_DIR} /home/qore

    # start glassfish
    echo && echo "-- starting Payara --"
    gosu qore:qore ${PAYARA_HOME}/bin/asadmin start-domain domain1
    sleep 5

    # create Payara queue named abc, needed for the tests
    gosu qore:qore ${PAYARA_HOME}/bin/asadmin create-jms-resource --restype javax.jms.Queue abc
fi

# copy built data provider JARs to source tree for testing
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-excel.jar ${MODULE_SRC_DIR}/qlib/ExcelDataProvider/jar/
mkdir -p ${MODULE_SRC_DIR}/qlib/WordDataProvider/jar
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-word.jar ${MODULE_SRC_DIR}/qlib/WordDataProvider/jar/
mkdir -p ${MODULE_SRC_DIR}/qlib/PowerPointDataProvider/jar
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-powerpoint.jar ${MODULE_SRC_DIR}/qlib/PowerPointDataProvider/jar/
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-kafka.jar ${MODULE_SRC_DIR}/qlib/KafkaDataProvider/jar/
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-ods.jar ${MODULE_SRC_DIR}/qlib/OdsDataProvider/jar/
mkdir -p ${MODULE_SRC_DIR}/qlib/OdtDataProvider/jar
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-odt.jar ${MODULE_SRC_DIR}/qlib/OdtDataProvider/jar/
mkdir -p ${MODULE_SRC_DIR}/qlib/OdpDataProvider/jar
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-odp.jar ${MODULE_SRC_DIR}/qlib/OdpDataProvider/jar/
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-email.jar ${MODULE_SRC_DIR}/qlib/EmailDataProvider/jar/
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-ics.jar ${MODULE_SRC_DIR}/qlib/IcsDataProvider/jar/
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-vcf.jar ${MODULE_SRC_DIR}/qlib/VcfDataProvider/jar/
mkdir -p ${MODULE_SRC_DIR}/qlib/VisioDataProvider/jar
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-visio.jar ${MODULE_SRC_DIR}/qlib/VisioDataProvider/jar/
cp ${MODULE_SRC_DIR}/build/qore-dataprovider-access.jar ${MODULE_SRC_DIR}/qlib/AccessDataProvider/jar/

# download Artemis Jakarta JMS client JAR for JMS data provider tests
ARTEMIS_JAR_DIR=${MODULE_SRC_DIR}/test/JakartaJms/lib
mkdir -p ${ARTEMIS_JAR_DIR}
if [ -n "${ARTEMIS_JAKARTA_JAR_URL}" ]; then
    echo && echo "-- downloading Artemis Jakarta JMS client JAR --"
    curl -fSL --retry 3 -o ${ARTEMIS_JAR_DIR}/artemis-jakarta-client-all.jar "${ARTEMIS_JAKARTA_JAR_URL}"
    # verify the download is a valid JAR (ZIP archive)
    if ! file ${ARTEMIS_JAR_DIR}/artemis-jakarta-client-all.jar | grep -q "archive data"; then
        echo "ERROR: downloaded file is not a valid JAR archive"
        rm -f ${ARTEMIS_JAR_DIR}/artemis-jakarta-client-all.jar
        exit 1
    fi
fi

# download the PostgreSQL JDBC driver for the jdbc driver and JDBC data provider tests against the PostgreSQL
# service; the version is pinned and the download is verified with its SHA-256 (Maven Central publishes a SHA-1
# for this artifact, 67f8093e8d8104c74bbf588392ac3229803f5d17, which the file this SHA-256 was taken from matched)
PGJDBC_VERSION=42.7.7
PGJDBC_SHA256=157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460
FLYWAY_TEST_ARGS=
if [[ "${QORE_DB_CONNSTR}" == pgsql:* ]]; then
    echo && echo "-- downloading the PostgreSQL JDBC driver ${PGJDBC_VERSION} --"
    export PGJDBC_JAR=${MODULE_SRC_DIR}/build/postgresql-${PGJDBC_VERSION}.jar
    curl -fSL --retry 3 -o ${PGJDBC_JAR} \
        https://repo1.maven.org/maven2/org/postgresql/postgresql/${PGJDBC_VERSION}/postgresql-${PGJDBC_VERSION}.jar
    if ! echo "${PGJDBC_SHA256}  ${PGJDBC_JAR}" | sha256sum -c -; then
        echo "ERROR: ${PGJDBC_JAR} does not have the expected SHA-256"
        rm -f ${PGJDBC_JAR}
        exit 1
    fi
    # a jdbc datasource string for the PostgreSQL service (ex: from pgsql:postgres/omq@postgres%postgres)
    export QORE_DB_CONNSTR_JDBC_PGSQL=$(qore -e 'hash<auto> h = parse_datasource(ENV.QORE_DB_CONNSTR);
        print(sprintf("jdbc:%s/%s@ignored{url=postgresql://%s:%d/%s,classpath=%s}", h.user, h.pass ?? "",
            h.host ?? "localhost", h.port ?? 5432, h.db, ENV.PGJDBC_JAR));')
    # the Flyway tests need the driver on Flyway's global classpath: the test adds JDBC_DB_CLASSPATH to it when
    # the FLYWAY_TEST_DRIVER_CLASSPATH parse define is set (no other test uses the define)
    export JDBC_DB_CLASSPATH=${PGJDBC_JAR}
    FLYWAY_TEST_ARGS=-DFLYWAY_TEST_DRIVER_CLASSPATH
fi

# run the tests
export QORE_MODULE_DIR=${MODULE_SRC_DIR}/qlib:${MODULE_SRC_DIR}/test/JakartaJms/lib:${QORE_MODULE_DIR}
cd ${MODULE_SRC_DIR}
# the tests must load the module that this job built and installed, not one that the image already has
loaded=$(qore -l jni -e 'printf("%s", get_module_hash().jni.filename);')
if ! grep -qxF "${loaded}" ${MODULE_SRC_DIR}/build/install_manifest.txt; then
    echo "ERROR: the tests would load '${loaded}', which this job did not install" >&2
    exit 1
fi
echo "-- the tests load ${loaded}"

for test in test/*.qtest; do
    # skip legacy jms test
    if [[ -z "$do_jms_test" && "$test" == test/jms.qtest ]]; then
        continue
    fi
    date
    gosu qore:qore qore --enable-debug ${FLYWAY_TEST_ARGS} $test -vv
    RESULTS="$RESULTS $?"
    date
done

# run Jakarta JMS unit tests (no broker needed)
echo && echo "-- running Jakarta JMS unit tests --"
for test in test/JakartaJms/unit/*.qtest; do
    date
    gosu qore:qore qore --enable-debug $test -vv
    RESULTS="$RESULTS $?"
    date
done

# run Jakarta JMS integration tests (requires Artemis service)
if [ -n "${JMS_CONNECTION}" ] && [ -f "${ARTEMIS_JAR_DIR}/artemis-jakarta-client-all.jar" ]; then
    echo && echo "-- running Jakarta JMS integration tests against ${JMS_CONNECTION} --"
    export ARTEMIS_CLASSPATH=${ARTEMIS_JAR_DIR}/artemis-jakarta-client-all.jar
    for test in test/JakartaJms/integration/*.qtest; do
        date
        gosu qore:qore qore --enable-debug $test -vv
        RESULTS="$RESULTS $?"
        date
    done
fi

# check the results
for R in $RESULTS; do
    if [ "$R" != "0" ]; then
        exit 1 # fail
    fi
done
