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

# build module and install
echo && echo "-- building module --"
mkdir -p ${MODULE_SRC_DIR}/build
cd ${MODULE_SRC_DIR}/build
cmake .. -DCMAKE_BUILD_TYPE=debug -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX}
make -j${MAKE_JOBS}
make install

# add Qore user and group
if ! grep -q "^qore:x:${QORE_GID}" /etc/group; then
    addgroup -g ${QORE_GID} qore
fi
if ! grep -q "^qore:x:${QORE_UID}" /etc/passwd; then
    adduser -u ${QORE_UID} -D -G qore -h /home/qore -s /bin/bash qore
fi

cd /home/qore

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

# run the tests
export QORE_MODULE_DIR=${MODULE_SRC_DIR}/qlib:${MODULE_SRC_DIR}/test/JakartaJms/lib:${QORE_MODULE_DIR}
cd ${MODULE_SRC_DIR}
for test in test/*.qtest; do
    # skip legacy jms test
    if [[ -z "$do_jms_test" && "$test" == test/jms.qtest ]]; then
        continue
    fi
    date
    gosu qore:qore qore $test -vv
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
