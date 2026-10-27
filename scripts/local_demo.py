"""Run an isolated WBA demo with real MySQL, Redis and the configured AI provider.

Usage: python scripts/local_demo.py start | status | stop
Requires Docker Desktop, Java 25+, Maven, Node.js and Python 3.10+.
Runtime files and generated database passwords stay in the ignored .local directory.
"""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / '.local' / 'wba-demo'
FRONTEND = ROOT / 'unsw-comp9900-WBA-frontend'
BACKEND = ROOT / 'unsw-WBA-backend'
JAR = BACKEND / 'unsw-WBA-api' / 'target' / 'unsw-WBA-api-1.0.0.jar'
STATE_FILE = RUNTIME / 'state.json'
API_URL = 'http://127.0.0.1:8088'
PAGE_URL = 'http://127.0.0.1:5178'


def executable(name, candidates=()):
    found = shutil.which(name)
    if found:
        return found
    for candidate in candidates:
        if Path(candidate).is_file():
            return str(candidate)
    raise RuntimeError(f'{name} was not found. Add it to PATH and retry.')


def docker_executable():
    return executable('docker', [r'E:\Docker\DockerDesktop\resources\bin\docker.exe',
                                 r'C:\Program Files\Docker\Docker\resources\bin\docker.exe'])


def read_env(path):
    result = {}
    if not path.exists():
        return result
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        name, value = line.split('=', 1)
        value = value.strip()
        if len(value) > 1 and value[0] == value[-1] and value[0] in ('"', "'"):
            value = value[1:-1]
        result[name.strip()] = value
    return result


def demo_env():
    RUNTIME.mkdir(parents=True, exist_ok=True)
    path = RUNTIME / 'dependencies.env'
    if not path.exists():
        values = {key: secrets.token_hex(24) for key in (
            'WBA_DEMO_MYSQL_ROOT_PASSWORD', 'WBA_DEMO_MYSQL_PASSWORD', 'WBA_DEMO_REDIS_PASSWORD')}
        with path.open('x', encoding='utf-8') as handle:
            handle.write(''.join(f'{key}={value}\n' for key, value in values.items()))
    return read_env(path)


def compose(*args):
    return [docker_executable(), 'compose', '-p', 'wba-ai-demo', '--env-file',
            str(RUNTIME / 'dependencies.env'), '-f', str(ROOT / 'compose.demo.yml'), *args]


def logged(command, log_name, *, cwd=ROOT, env=None, timeout=180):
    with (RUNTIME / log_name).open('ab') as log:
        result = subprocess.run(command, cwd=cwd, env=env, stdout=log,
                                stderr=subprocess.STDOUT, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f'Command failed. See .local/wba-demo/{log_name}.')


def migrate_demo_bookings():
    """Only connect through this demo's Compose service; never use the original database settings."""
    def query(statement):
        result = subprocess.run(compose('exec', '-T', 'mysql', 'sh', '-c',
            'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u wba_demo -N -B unsw_wba'),
            cwd=ROOT, input=statement, text=True, capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError('The additive demo booking migration failed. Existing data was not deleted.')
        return result.stdout.strip()
    for column, definition in [('request_key', 'VARCHAR(64) DEFAULT NULL'), ('request_hash', 'CHAR(64) DEFAULT NULL'),
                               ('management_key', 'VARCHAR(64) DEFAULT NULL'), ('replacement_booking_id', 'BIGINT DEFAULT NULL')]:
        exists = query("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                       f"AND TABLE_NAME='assessment_booking' AND COLUMN_NAME='{column}';")
        if exists == '0':
            query(f'ALTER TABLE assessment_booking ADD COLUMN {column} {definition};')
    exists = query("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                   "AND TABLE_NAME='assessment_booking' AND INDEX_NAME='uk_booking_request';")
    if exists == '0':
        query('CREATE UNIQUE INDEX uk_booking_request ON assessment_booking (creator, request_key);')
    exists = query("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                   "AND TABLE_NAME='assessment_booking' AND INDEX_NAME='uk_booking_management';")
    if exists == '0':
        query('CREATE UNIQUE INDEX uk_booking_management ON assessment_booking (creator, management_key);')
    query((ROOT / 'scripts/demo/migrate_assessment_plan.sql').read_text(encoding='utf-8'))
    query((ROOT / 'scripts/demo/migrate_assistant_events.sql').read_text(encoding='utf-8'))
    query((ROOT / 'scripts/demo/ensure_insights_admin.sql').read_text(encoding='utf-8'))


def state():
    return json.loads(STATE_FILE.read_text(encoding='utf-8')) if STATE_FILE.exists() else {}


def save_state(value):
    STATE_FILE.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def listening(port):
    with socket.socket() as connection:
        connection.settimeout(0.5)
        return connection.connect_ex(('127.0.0.1', port)) == 0


def own_process(pid, marker):
    if os.name != 'nt':
        try:
            return marker in Path(f'/proc/{pid}/cmdline').read_bytes().decode(errors='replace')
        except OSError:
            return False
    # Verify the command line before stopping a PID that might have been reused.
    command = f'(Get-CimInstance Win32_Process -Filter "ProcessId = {int(pid)}").CommandLine'
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command', command],
                            capture_output=True, timeout=10)
    return marker.casefold() in result.stdout.decode(errors='replace').casefold()


def background(command, log_name, *, cwd, env):
    flags = (subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP) if os.name == 'nt' else 0
    with (RUNTIME / log_name).open('ab') as log:
        return subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                stdout=log, stderr=subprocess.STDOUT, creationflags=flags,
                                start_new_session=os.name != 'nt')


def wait_http(url, child):
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        if child.poll() is not None:
            raise RuntimeError('The local server exited. Check .local/wba-demo logs.')
        try:
            with urlopen(url, timeout=2):
                return
        except HTTPError:
            return  # A Spring 404 still proves the server has started.
        except (URLError, TimeoutError, OSError):
            time.sleep(1)
    raise RuntimeError('The local server did not become ready. Check .local/wba-demo logs.')


def start(skip_build):
    config = demo_env()
    current = state()
    if current:
        raise RuntimeError('A demo state already exists. Run status or stop before starting again.')
    for port in (8088, 5178, 13306, 16379):
        if listening(port):
            raise RuntimeError(f'Port {port} is occupied. No existing process was stopped.')
    try:
        result = subprocess.run([docker_executable(), 'info', '--format', '{{.ServerVersion}}'],
                                capture_output=True, timeout=12)
    except subprocess.TimeoutExpired:
        raise RuntimeError('Docker is not responding. Start Docker Desktop and retry.') from None
    if result.returncode:
        raise RuntimeError('Docker is not ready. Start Docker Desktop and retry.')

    java_candidates = []
    if os.environ.get('JAVA_HOME'):
        java_candidates.append(Path(os.environ['JAVA_HOME']) / 'bin' / 'java.exe')
    java_candidates.extend(sorted(Path(r'C:\Program Files\Eclipse Adoptium').glob('jdk-25*/bin/java.exe')))
    java = next((str(path) for path in java_candidates if Path(path).is_file()), None) or executable('java')
    node = executable('node', [r'C:\Program Files\nodejs\node.exe'])
    vite = FRONTEND / 'node_modules' / 'vite' / 'bin' / 'vite.js'
    if not vite.is_file():
        raise RuntimeError('Install frontend dependencies with npm install, then retry.')
    child_env = os.environ.copy()
    child_env['JAVA_HOME'] = str(Path(java).parent.parent)
    if not skip_build:
        maven = executable('mvn', [r'C:\maven\bin\mvn.cmd'])
        print('Building the local backend...', flush=True)
        logged([maven, '-B', '-ntp', '-pl', 'unsw-WBA-api', '-am', 'package',
                '-DskipTests', '-Dstyle.color=never'], 'build.log', cwd=BACKEND,
               env=child_env, timeout=300)
    if not JAR.is_file():
        raise RuntimeError('The backend JAR is missing. Run start without --skip-build.')

    print('Starting the isolated MySQL and Redis containers...', flush=True)
    # Never inherit the original deployment's database credentials or mail/calendar settings.
    logged(compose('up', '-d', '--wait', '--wait-timeout', '120'), 'dependencies.log')
    migrate_demo_bookings()
    local = read_env(ROOT / '.env')
    for key in list(child_env):
        if key.startswith('BOOKING_AI_'):
            del child_env[key]
    for key, value in local.items():
        if key.startswith('BOOKING_AI_'):
            child_env[key] = value
    child_env.setdefault('BOOKING_AI_ENABLED', 'false')
    child_env.update({
        'SERVER_ADDRESS': '127.0.0.1', 'SERVER_PORT': '8088',
        'SPRING_PROFILES_ACTIVE': 'demo', 'LOGGING_LEVEL': 'info',
        'SPRING_OUTPUT_ANSI_ENABLED': 'never',
        'SPRING_DATASOURCE_URL': 'jdbc:mysql://127.0.0.1:13306/unsw_wba?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Australia/Sydney',
        'SPRING_DATASOURCE_USERNAME': 'wba_demo',
        'SPRING_DATASOURCE_PASSWORD': config['WBA_DEMO_MYSQL_PASSWORD'],
        'SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE': '2',
        'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE': '10',
        'REDISSON_URI': 'redis://127.0.0.1:16379',
        'REDISSON_PASSWORD': config['WBA_DEMO_REDIS_PASSWORD'],
        'SPRING_MAIL_HOST': '127.0.0.1', 'SPRING_MAIL_USERNAME': '', 'SPRING_MAIL_PASSWORD': '',
        'GOOGLE_CALENDAR_CLIENT_ID': '', 'GOOGLE_CALENDAR_CLIENT_SECRET': '',
    })
    current = {}
    try:
        print('Starting the local API...', flush=True)
        backend = background([java, '-XX:ActiveProcessorCount=4', '-Xms128m', '-Xmx768m',
                              '-jar', str(JAR)], 'backend.log', cwd=BACKEND, env=child_env)
        current['backend'] = {'pid': backend.pid, 'marker': str(JAR)}
        save_state(current)
        wait_http(API_URL + '/', backend)
        print('Starting the local page...', flush=True)
        front_env = os.environ.copy()
        front_env['WBA_API_TARGET'] = API_URL
        front_env['VITE_API_BASE_URL'] = '/api'
        front_env['VITE_SSE_BASE_URL'] = '/api'
        # The model credential is exclusively a backend environment variable.
        for key in list(front_env):
            if key.startswith('BOOKING_AI_'):
                del front_env[key]
        frontend = background([node, str(vite), '--host', '127.0.0.1', '--port', '5178', '--strictPort'],
                              'frontend.log', cwd=FRONTEND, env=front_env)
        current['frontend'] = {'pid': frontend.pid, 'marker': str(vite)}
        save_state(current)
        wait_http(PAGE_URL, frontend)
    except BaseException:
        print('Startup did not finish. Run stop before retrying; logs are in .local/wba-demo.', flush=True)
        raise
    print(f'Ready: {PAGE_URL}\nDemo login: student@wba.test / WbaDemo!2026', flush=True)
    print('The AI query uses the model configured in .env; the demo account and slots are synthetic.')


def stop():
    for item in state().values():
        if own_process(item['pid'], item['marker']):
            if os.name == 'nt':
                subprocess.run(['powershell.exe', '-NoProfile', '-Command',
                                f'Stop-Process -Id {int(item["pid"])} -Force'], check=True)
            else:
                import signal
                os.kill(item['pid'], signal.SIGTERM)
    if STATE_FILE.exists():
        STATE_FILE.unlink()
    if (RUNTIME / 'dependencies.env').exists():
        logged(compose('stop'), 'dependencies.log')
    print('Demo stopped. Its database volume and generated passwords are retained.')


def status():
    print(json.dumps({'page': PAGE_URL, 'page_listening': listening(5178),
                      'api': API_URL, 'api_listening': listening(8088),
                      'mysql_listening': listening(13306), 'redis_listening': listening(16379),
                      'tracked_processes': state()}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'status', 'stop'])
    parser.add_argument('--skip-build', action='store_true', help='Reuse a previously built backend JAR.')
    args = parser.parse_args()
    try:
        if args.action == 'start':
            start(args.skip_build)
        elif args.action == 'stop':
            stop()
        else:
            status()
    except (RuntimeError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
