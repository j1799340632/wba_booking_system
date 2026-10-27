"""Run the fixed AI intent evaluation suite. Real provider calls require --live.

Build and test the backend first with Maven. No database credentials or user records are used.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

from local_demo import ROOT, read_env, executable


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--live', action='store_true', help='Explicitly enable real model calls (provider usage applies).')
    parser.add_argument('--output', default='.local/wba-demo/evaluation.json')
    parser.add_argument('--suite', default='evals/booking_intents.json')
    args = parser.parse_args()
    suite = (ROOT / args.suite).resolve()
    if not suite.is_relative_to((ROOT / 'evals').resolve()):
        raise RuntimeError('Evaluation suites must be inside the evals directory.')
    tests = json.loads(suite.read_text(encoding='utf-8'))
    if not args.live:
        print(json.dumps({'planned_cases': len(tests['cases']), 'referenceTime': tests['referenceTime'],
                          'real_model_calls': 0, 'instruction': 'Use --live to evaluate with your configured backend provider.'}))
        return 0
    output = (ROOT / args.output).resolve()
    # Evaluation artifacts belong to the ignored runtime tree, never to tracked source files.
    if not output.is_relative_to((ROOT / '.local').resolve()):
        raise RuntimeError('Evaluation output must be inside the ignored .local directory.')
    output.parent.mkdir(parents=True, exist_ok=True)
    candidates = []
    for path in (ROOT / 'unsw-WBA-backend').glob('*/target/surefire-reports/TEST-*.xml'):
        tree = ET.parse(path)
        for prop in tree.findall('./properties/property'):
            if prop.get('name') == 'java.class.path':
                candidates.append(prop.get('value', ''))
    if not candidates:
        raise RuntimeError('Run the backend Maven tests first to prepare the evaluation classpath.')
    classpath = max(candidates, key=len)
    local = read_env(ROOT / '.env')
    if not local.get('BOOKING_AI_API_KEY'):
        raise RuntimeError('Configure BOOKING_AI_API_KEY in the ignored root .env first.')
    env = os.environ.copy()
    for key in list(env):
        if key.startswith('BOOKING_AI_'):
            del env[key]
    env.update({key: value for key, value in local.items() if key.startswith('BOOKING_AI_')})
    java_candidates = []
    if os.environ.get('JAVA_HOME'):
        java_candidates.append(Path(os.environ['JAVA_HOME']) / 'bin/java.exe')
    java_candidates.extend(sorted(Path(r'C:\Program Files\Eclipse Adoptium').glob('jdk-25*/bin/java.exe')))
    java = next((str(path) for path in java_candidates if path.is_file()), None) or executable('java')
    command = [java, '-Dfile.encoding=UTF-8', '-cp', classpath, str(ROOT / 'evals/BookingIntentEvaluation.java'), str(suite), str(output)]
    # Key is passed only in the process environment, never command arguments or output.
    result = subprocess.run(command, cwd=ROOT, env=env, timeout=1200)
    if result.returncode:
        return result.returncode
    report = json.loads(output.read_text(encoding='utf-8'))
    print(json.dumps({key: report[key] for key in ['planned', 'completed', 'passed', 'casePassRate', 'medianMs', 'p95Ms', 'totalTokens']}))
    return 0 if report['completed'] == report['planned'] and report['passed'] == report['planned'] else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
