#!/usr/bin/python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Check that missing fixtures skip but configured JDBC failures fail tests."""
import argparse
import os
from pathlib import Path
import subprocess


def check(build):
    api = subprocess.check_output(['/usr/bin/qore', '--latest-module-api'], text=True).strip()
    module = Path(build).resolve() / ('jni-api-' + api + '.qmod')
    command = ['/usr/bin/qore', '-b', '--enable-debug', '-l', str(module), 'test/jdbc.qtest', '-v']
    env = {key: value for key, value in os.environ.items() if not key.startswith('QORE_DB_CONNSTR_JDBC_')}
    absent = subprocess.run(command, env=env, capture_output=True, text=True, timeout=60)
    absent.check_returncode()
    assert absent.stdout.count('Skipped:') == 3, absent.stdout
    assert 'ERROR:' not in absent.stdout + absent.stderr, absent.stdout + absent.stderr
    # An unknown protocol has no driver and fails before any network connection.
    env['QORE_DB_CONNSTR_JDBC_PGSQL'] = 'jdbc:test/test@qore-no-such-protocol:fixture'
    broken = subprocess.run(command, env=env, capture_output=True, text=True, timeout=60)
    assert broken.returncode != 0, broken.stdout + broken.stderr
    assert 'Skipped: pgsqlTest' not in broken.stdout, broken.stdout
    assert 'No suitable driver' in broken.stdout + broken.stderr, broken.stdout + broken.stderr
    for suite, case, variable, value in (
        ('jni', 'Qore Java API test', 'QORE_DB_CONNSTR', 'jdbc:test/test@qore-no-such-protocol:fixture'),
        ('MqttDataProvider', 'MQTT integration test', 'MQTT_CONNECTION', 'qore-no-such-fixture'),
        ('OpcUaDataProvider', 'OPC UA integration test', 'OPCUA_CONNECTION', 'qore-no-such-fixture'),
        ('BusyLightDataProvider', 'BusyLight test', 'BUSYLIGHT_CONNECTION', 'qore-no-such-fixture'),
    ):
        command = ['/usr/bin/qore', '-b', '--enable-debug', '-l', str(module),
            'test/' + suite + '.qtest', '--include=' + case, '-v']
        fixture_env = dict(env)
        fixture_env.pop(variable, None)
        absent = subprocess.run(command, env=fixture_env, capture_output=True, text=True, timeout=90)
        absent.check_returncode()
        assert 'Skipped: ' + case in absent.stdout, absent.stdout + absent.stderr
        assert 'ERROR:' not in absent.stdout + absent.stderr, absent.stdout + absent.stderr
        fixture_env[variable] = value
        broken = subprocess.run(command, env=fixture_env, capture_output=True, text=True, timeout=90)
        assert broken.returncode != 0, broken.stdout + broken.stderr
        assert 'Skipped: ' + case not in broken.stdout, broken.stdout
    print('JDBC fixture selection: absent configuration skips; configured driver failure fails')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-dir', type=Path, required=True)
    check(parser.parse_args().build_dir)
