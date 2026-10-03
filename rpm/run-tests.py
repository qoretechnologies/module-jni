#!/usr/bin/python3
# Copyright (C) 2026 Qore Technologies, s.r.o.
# SPDX-License-Identifier: MIT
"""Run every available JNI suite against the selected build and record skips."""
import argparse
import json
from pathlib import Path
import re
import subprocess


def run(build):
    build = Path(build).resolve()
    api = subprocess.check_output(['/usr/bin/qore', '--latest-module-api'], text=True).strip()
    module = build / ('jni-api-' + api + '.qmod')
    if not module.is_file():
        raise ValueError('Missing JNI build module: ' + str(module))
    output = build / 'rpm-test-results'
    output.mkdir(exist_ok=False)
    status = {'tests': [], 'external_gates': ['test/jms.qtest requires a configured JMS application server']}
    try:
        suites = sorted(Path('test').glob('*.qtest'))
        if not suites:
            raise ValueError('JNI suite inventory is empty')
        for suite in suites:
            if suite.name == 'jms.qtest':
                continue
            command = ['/usr/bin/qore', '-b', '--enable-debug', '-l', str(module)]
            provider = build / 'qlib-qmod' / suite.stem / (suite.stem + '.qmod')
            if provider.is_file():
                command += ['-l', str(provider)]
            command += [str(suite), '-v']
            log_path = output / (suite.stem + '.log')
            with log_path.open('w') as log:
                process = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=240)
            text = log_path.read_text()
            print(text, end='', flush=True)
            status['tests'].append({'suite': str(suite), 'exit_code': process.returncode,
                'counts': re.findall(r'Ran (\d+) test cases?, (\d+) succeeded \((\d+) assertions?\)', text),
                'skips': re.findall(r'^Skipped: (.*)$', text, re.M)})
            process.check_returncode()
        status['exit_code'] = 0
    except BaseException as error:
        status.update(exit_code=1, error=repr(error))
        raise
    finally:
        (output / 'status.json').write_text(json.dumps(status, indent=2) + '\n')
    return status


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-dir', type=Path, required=True)
    args = parser.parse_args()
    run(args.build_dir)


if __name__ == '__main__':
    main()
