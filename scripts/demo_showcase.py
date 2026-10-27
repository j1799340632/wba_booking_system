"""Persistent synthetic showcase for the isolated local demo; never calls an AI provider.

Run setup once, status to inspect it, cleanup to remove only its owned records.
Re-running setup preserves decisions made during a demonstration. Dates use Sydney.
"""
import argparse
from contextlib import contextmanager
from datetime import date, timedelta
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

import local_demo

ROOT = local_demo.ROOT
DIRECTORY = local_demo.RUNTIME / 'showcase'
MANIFEST = DIRECTORY / 'manifest.json'
GUIDE = DIRECTORY / 'START_HERE.md'
USERS = {'student': '995000000001', 'mentor': '995000000002', 'admin': '995000000003'}
ROLES = {'student': 4, 'mentor': 3, 'admin': 2}
SLOTS = [str(995000000101 + index) for index in range(6)]
BOOKINGS = ['995000001001', '995000001002']
PASSWORD = 'WbaDemo!2026'
# Same deliberately public synthetic password as scripts/demo/seed.sql.
PASSWORD_HASH = '$2a$10$u4VyS0LsWgQ6lVpeh1gDou8G738CAD5CMUTs6o0t3geGoz.4w4tG2'
BASE = 'http://127.0.0.1:5178'


def literal(value):
    """Hex literals avoid shell, SQL-mode and text-escaping ambiguities."""
    return "(CONVERT(X'" + str(value).encode('utf-8').hex() + "' USING utf8mb4) COLLATE utf8mb4_general_ci)"


def sql(statement):
    # Never reads the root .env, accepts a database URL, or uses the original Compose project.
    if not (local_demo.RUNTIME / 'dependencies.env').is_file():
        raise RuntimeError('Start the isolated demo with python scripts/local_demo.py start first.')
    result = subprocess.run(local_demo.compose('exec', '-T', 'mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u wba_demo -N -B unsw_wba'),
        input="SET time_zone='Australia/Sydney';\n" + statement,
        cwd=ROOT, capture_output=True, text=True, encoding='utf-8', timeout=30)
    if result.returncode:
        raise RuntimeError('Isolated demo SQL failed; no credentials or database contents are printed.')
    return result.stdout.strip()


@contextmanager
def command_lock():
    """OS lock releases automatically after a crashed process; no stale-lock deletion."""
    DIRECTORY.mkdir(parents=True, exist_ok=True)
    with (DIRECTORY / 'command.lock').open('a+b') as handle:
        handle.seek(0)
        if not handle.read(1):
            handle.write(b'0')
            handle.flush()
        handle.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            raise RuntimeError('Another showcase command is running. Retry after it finishes.') from None
        try:
            yield
        finally:
            handle.seek(0)
            if os.name == 'nt':
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(handle, fcntl.LOCK_UN)


def save_manifest(value):
    temporary = DIRECTORY / 'manifest.tmp'
    temporary.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
    temporary.replace(MANIFEST)


def load_manifest():
    if not MANIFEST.is_file():
        return None
    value = json.loads(MANIFEST.read_text(encoding='utf-8'))
    # All resource IDs are code constants, never destructive targets taken from an edited file.
    if (value.get('schema') != 1 or not re.fullmatch(r'[0-9a-f]{32}', value.get('marker', ''))
            or value.get('phase') not in ('prepared', 'ready')):
        raise RuntimeError('Unrecognized showcase manifest. No records were changed.')
    date.fromisoformat(value['seedDate'])
    return value


def identity(marker, kind):
    prefix = {'student': 'Showcase Student', 'mentor': 'Dr Showcase', 'admin': 'Showcase Admin'}[kind]
    return f'{prefix} {marker[:8]}'


def email(kind):
    return f'showcase-{kind}@wba.test'


def calendar(seed_date):
    today = date.fromisoformat(seed_date)
    shift = lambda days: (today + timedelta(days=days)).isoformat()
    return {'start': shift(-7), 'deadline': shift(28), 'primary': shift(7),
            'replacement': shift(8), 'alternative': shift(14), 'ended': shift(-1), 'completed': shift(-2)}


def boundary_queries(manifest=None):
    """Any collision or cross-account dependency blocks deletion rather than widening its scope."""
    users, slots, bookings = ','.join(USERS.values()), ','.join(SLOTS), ','.join(BOOKINGS)
    emails = ','.join(literal(email(kind)) for kind in USERS)
    related = f'(creator IN ({users}) OR assessment_slot_id IN ({slots}) OR id IN ({bookings}))'
    queries = {
        'users': f'SELECT COUNT(*) FROM user WHERE id IN ({users}) OR email IN ({emails})',
        'slots': f'SELECT COUNT(*) FROM assessment_slot WHERE id IN ({slots}) OR creator IN ({users})',
        'bookings': f'SELECT COUNT(*) FROM assessment_booking WHERE {related}',
        'plans': f'SELECT COUNT(*) FROM student_assessment_plan WHERE student_id IN ({users})',
        'events': f'SELECT COUNT(*) FROM assistant_event WHERE actor_id IN ({users})',
        'messages': f'SELECT COUNT(*) FROM message WHERE creator IN ({users}) OR receiver IN ({users})',
        'settings': f'SELECT COUNT(*) FROM user_settings WHERE id IN ({users}) OR user_id IN ({users})',
        'connections': f'SELECT COUNT(*) FROM google_calendar_connection WHERE user_id IN ({users}) OR creator IN ({users})',
    }
    for table in ('hospital', 'department', 'wba_type', 'user_role'):
        queries[f'{table}References'] = f'SELECT COUNT(*) FROM {table} WHERE creator IN ({users})'
    queries['accountReferences'] = f'SELECT COUNT(*) FROM user WHERE creator IN ({users})'
    if manifest:
        marker = manifest['marker']
        allowed = ' OR '.join(f'(id={id_} AND role_id={ROLES[kind]} AND email={literal(email(kind))} '
                               f'AND name={literal(identity(marker, kind))})' for kind, id_ in USERS.items())
        queries.update({
            'foreignUsers': f'SELECT COUNT(*) FROM user WHERE (id IN ({users}) OR email IN ({emails})) AND NOT ({allowed})',
            'foreignSlots': f'SELECT COUNT(*) FROM assessment_slot WHERE (id IN ({slots}) OR creator IN ({users})) '
                            f'AND NOT (id IN ({slots}) AND creator={USERS["mentor"]} AND location={literal("Showcase " + marker)})',
            'foreignBookings': f'SELECT COUNT(*) FROM assessment_booking WHERE {related} '
                               f'AND NOT (creator={USERS["student"]} AND assessment_slot_id IN ({slots}))',
            'foreignPlans': f'SELECT COUNT(*) FROM student_assessment_plan WHERE student_id IN ({USERS["mentor"]},{USERS["admin"]})',
            'foreignSettings': f'SELECT COUNT(*) FROM user_settings WHERE (id IN ({users}) OR user_id IN ({users})) '
                               f'AND NOT (id=user_id AND user_id IN ({users}))',
            'foreignMessages': f'SELECT COUNT(*) FROM message WHERE (creator IN ({users}) OR receiver IN ({users})) '
                               f'AND NOT (creator IN (0,{users}) AND receiver IN ({users}))',
        })
    return queries


def snapshot(manifest=None):
    queries = boundary_queries(manifest)
    statement = 'SELECT JSON_OBJECT(' + ','.join(f'{literal(k)},({q})' for k, q in queries.items()) + ');'
    return json.loads(sql(statement))


def assert_owned(manifest, counts):
    blocked = [key for key, value in counts.items() if value and
               (key.startswith('foreign') or key.endswith('References') or key == 'connections')]
    if blocked or (counts['users'] == 0 and any(counts.values())):
        raise RuntimeError('Ownership/dependency check blocked this command: ' + ', '.join(blocked or ['orphan records']) +
                           '. No records were changed; keep the manifest for investigation.')
    if counts['users'] not in (0, 3):
        raise RuntimeError('Some showcase accounts are missing. No records were changed.')


def setup():
    existing = load_manifest()
    if existing:
        counts = snapshot(existing)
        assert_owned(existing, counts)
        if counts['users'] != 3 or counts['slots'] != 6 or counts['bookings'] < 2 or counts['plans'] != 1:
            raise RuntimeError('Showcase is incomplete. Run cleanup before setup; existing records were not reset.')
        existing['phase'] = 'ready'  # Recover a committed transaction interrupted before the final file write.
        save_manifest(existing)
        write_guide(existing)
        return status(existing)
    if any(snapshot().values()):
        raise RuntimeError('A reserved showcase ID, email or dependency already exists. Nothing was overwritten.')
    today = sql("SELECT IF((SELECT COUNT(*) FROM demo_metadata WHERE id=1)=1,DATE_FORMAT(NOW(),'%Y-%m-%d'),'INVALID');")
    days = calendar(today)
    types = sql("SELECT name,id FROM wba_type WHERE name IN ('Mini-CEX','DOP') AND is_enable=1 AND is_delete=0;")
    types = dict(row.split('\t') for row in types.splitlines())
    if set(types) != {'Mini-CEX', 'DOP'} or not all(re.fullmatch(r'\d+', id_) for id_ in types.values()):
        raise RuntimeError('The demo needs enabled Mini-CEX and DOP reference types. No dictionaries were changed.')
    value = {'schema': 1, 'marker': uuid.uuid4().hex, 'seedDate': today, 'phase': 'prepared'}
    save_manifest(value)  # Journal before SQL; the entire seed below commits or rolls back together.
    statements = ['START TRANSACTION;']
    for kind, id_ in USERS.items():
        statements.append('INSERT INTO user(id,role_id,email,password,name,title,hospital,department,create_time) VALUES('
            f'{id_},{ROLES[kind]},{literal(email(kind))},{literal(PASSWORD_HASH)},{literal(identity(value["marker"], kind))},'
            f"{literal('Dr' if kind == 'mentor' else kind.title())},'Prince of Wales Hospital','General Medicine',NOW());")
        statements.append(f'INSERT INTO user_settings(id,user_id,is_receive_email,is_message_sound) VALUES({id_},{id_},0,0);')
    specs = [(days['primary'], '14:00', 'Mini-CEX', 0), (days['replacement'], '14:00', 'Mini-CEX', 0),
             (days['primary'], '15:00', 'DOP', 0), (days['ended'], '10:00', 'Mini-CEX', 1),
             (days['completed'], '10:00', 'Mini-CEX', 2), (days['alternative'], '14:00', 'Mini-CEX', 0)]
    for id_, (day, hour, type_, state) in zip(SLOTS, specs):
        start, end = f'{day} {hour}:00', f'{day} {hour[:3]}30:00'
        statements.append('INSERT INTO assessment_slot(id,start_time,end_time,repeat_type,next_available_time,hospital,'
            'department,location,type,status,creator,create_time,is_enable) VALUES('
            f"{id_},{literal(start)},{literal(end)},0,{literal(start)},'Prince of Wales Hospital','General Medicine',"
            f'{literal("Showcase " + value["marker"])},{literal(type_)},{state},{USERS["mentor"]},NOW(),1);')
    for id_, slot, state in zip(BOOKINGS, SLOTS[3:5], (1, 2)):
        result = f"82,'Synthetic showcase baseline, not a clinical judgement.',end_time" if state == 2 else 'NULL,NULL,NULL'
        statements.append('INSERT INTO assessment_booking(id,creator,assessment_slot_id,start_time,end_time,status,'
            f'score,feedback,completed_time,create_time) SELECT {id_},{USERS["student"]},id,start_time,end_time,{state},'
            f'{result},NOW() FROM assessment_slot WHERE id={slot};')
    goals = [{'typeId': int(types[name]), 'typeName': name, 'target': target} for name, target in (('Mini-CEX', 3), ('DOP', 2))]
    statements.append('INSERT INTO student_assessment_plan(student_id,start_date,deadline,goals_json,version,create_time,edit_time) '
        f'VALUES({USERS["student"]},{literal(days["start"])},{literal(days["deadline"])},{literal(json.dumps(goals))},1,NOW(),NOW());')
    statements.append('COMMIT;')
    sql('\n'.join(statements))
    value['phase'] = 'ready'
    save_manifest(value)
    write_guide(value)
    return status(value)


def cleanup():
    value = load_manifest()
    if not value:
        return {'status': 'not-configured', 'message': 'No ownership manifest; no database records were deleted.'}
    counts = snapshot(value)
    assert_owned(value, counts)
    # Lock relevant records/gaps, then repeat ALL safety predicates inside the deletion transaction.
    users, slots, bookings = ','.join(USERS.values()), ','.join(SLOTS), ','.join(BOOKINGS)
    queries = boundary_queries(value)
    predicates = [f'({q})=0' for key, q in queries.items()
                  if key.startswith('foreign') or key.endswith('References') or key == 'connections']
    predicates.append(f'({queries["users"]})={counts["users"]}')
    locks = [
        f'SELECT id FROM user WHERE id IN ({users}) OR creator IN ({users}) OR email IN (' +
        ','.join(literal(email(k)) for k in USERS) + ') FOR UPDATE;',
        f'SELECT id FROM assessment_slot WHERE creator IN ({users}) OR id IN ({slots}) FOR UPDATE;',
        f'SELECT id FROM assessment_booking WHERE creator IN ({users}) OR assessment_slot_id IN ({slots}) OR id IN ({bookings}) FOR UPDATE;',
        f'SELECT id FROM message WHERE creator IN ({users}) OR receiver IN ({users}) FOR UPDATE;',
        f'SELECT id FROM user_settings WHERE id IN ({users}) OR user_id IN ({users}) FOR UPDATE;',
        f'SELECT student_id FROM student_assessment_plan WHERE student_id IN ({users}) FOR UPDATE;',
        f'SELECT id FROM assistant_event WHERE actor_id IN ({users}) FOR UPDATE;',
        f'SELECT id FROM google_calendar_connection WHERE user_id IN ({users}) OR creator IN ({users}) FOR UPDATE;',
    ]
    locks += [f'SELECT id FROM {table} WHERE creator IN ({users}) FOR UPDATE;'
              for table in ('hospital', 'department', 'wba_type', 'user_role')]
    delete = [f'DELETE FROM assistant_event WHERE actor_id IN ({users}) AND @showcase_safe=1;',
              f'DELETE FROM message WHERE (creator IN ({users}) OR receiver IN ({users})) AND @showcase_safe=1;',
              f'DELETE FROM student_assessment_plan WHERE student_id IN ({users}) AND @showcase_safe=1;',
              f'DELETE FROM assessment_booking WHERE creator={USERS["student"]} AND assessment_slot_id IN ({slots}) AND @showcase_safe=1;',
              f'DELETE FROM assessment_slot WHERE id IN ({slots}) AND @showcase_safe=1;',
              f'DELETE FROM user_settings WHERE user_id IN ({users}) AND @showcase_safe=1;',
              f'DELETE FROM user WHERE id IN ({users}) AND @showcase_safe=1;']
    result = sql('\n'.join(['SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;', 'START TRANSACTION;', *locks,
                            'SET @showcase_safe=(' + ' AND '.join(predicates) + ');', *delete,
                            "SELECT CONCAT('SHOWCASE_SAFE=',@showcase_safe);", 'COMMIT;']))
    if 'SHOWCASE_SAFE=1' not in result.splitlines() or any(snapshot().values()):
        raise RuntimeError('Cleanup did not pass its final safety check. Keep the manifest; retry after investigation.')
    MANIFEST.unlink()
    if GUIDE.exists():
        GUIDE.unlink()
    return {'status': 'cleaned', 'removed': counts, 'message': 'Only owned showcase records removed; existing demo data preserved.'}


def status(value=None):
    value = value or load_manifest()
    if not value:
        return {'status': 'not-configured', 'next': 'python scripts/demo_showcase.py setup'}
    counts = snapshot(value)
    assert_owned(value, counts)
    today = sql("SELECT DATE_FORMAT(NOW(),'%Y-%m-%d');")
    stale = (date.fromisoformat(today) - date.fromisoformat(value['seedDate'])).days >= 5
    return {'status': value['phase'], 'seedDate': value['seedDate'], 'todaySydney': today,
            'refreshRecommended': stale, 'counts': counts, 'page': BASE,
            'accounts': {k: email(k) for k in USERS}, 'publicPassword': PASSWORD, 'guide': str(GUIDE),
            'note': 'Setup preserves previous decisions. Log out of showcase accounts, cleanup, then setup for a fresh run.'}


def write_guide(value):
    days = calendar(value['seedDate'])
    mentor = identity(value['marker'], 'mentor')
    question = f'请找 {days["primary"]} 14点到16点的 Mini-CEX，只要 {mentor}，医院 Prince of Wales Hospital，科室 General Medicine'
    alternative = f'请找 {days["primary"]} 18点到19点的 Mini-CEX，只要 {mentor}，医院 Prince of Wales Hospital，科室 General Medicine'
    text = f'''# WBA 五分钟演示：本轮实际日期与账号

生成基准日：{value['seedDate']}，业务时区 Australia/Sydney。全部为合成演示数据。
setup 重跑会保留已有操作。重新演示前先退出这三个账号，再 cleanup → setup；约五天后建议刷新场次。

页面：{BASE}。三种角色建议使用不同浏览器配置或独立隐私窗口，各自登录，避免演示时混淆角色。

| 角色 | 邮箱 | 密码 |
| --- | --- | --- |
| 学生 | {email('student')} | {PASSWORD} |
| 导师 | {email('mentor')} | {PASSWORD} |
| 管理员 | {email('admin')} | {PASSWORD} |

1. **0:00–0:40 说明问题与个人目标。** 学生进入 Assessment Plan：演示目标为 {days['start']} 至 {days['deadline']} 完成 3 次 Mini-CEX、2 次 DOP，这是个人示例目标，并非课程要求。初始 Completed=1、Remaining=4、To request=4。已结束待完成的确认预约不算完成。
2. **0:40–1:50 查询和确认预约。** 学生进入 Book Slots，复制下句。正常结果包含 {days['primary']} 14:00 场次；选择 Review session，核对导师、日期和地点，勾选后 Confirm booking request。只在确认后写入 Pending。若已有演示预约，先用 My Bookings 查看，勿把占用后的结果当失败。

   ```text
   {question}
   ```

3. **1:50–2:30 人工审批。** 导师进入 Upcoming Sessions，找到刚生成的未来预约，Approve → 勾选 → Confirm approve。学生 Refresh 后看到 Confirmed；Completed 仍为 1，不能提前完成未来场次。
4. **2:30–3:30 完成与进度联动。** 导师找到 {days['ended']} 10:00 的预置 Confirmed 预约（编号 {BOOKINGS[0]}）。它是用于演示“已结束待完成”的合成记录，并非本次刚预约的未来场次。Complete，填 85 分和 `Synthetic demo: observed assessment completed.`，勾选后 Confirm complete。学生刷新 Assessment Plan，Completed 由 1 变为 2、Remaining 由 4 变为 3；未来预约仍只是临时覆盖。
5. **3:30–4:30 运行观测。** 管理员进入 AI Insights，Last 7 days / Apply and refresh，查看新发生的搜索、预览、确认、Token 和耗时，可 Download summary。数据包含隔离 demo 的所有学生，并非只属于本轮演示。setup 不调用模型，也不生成假事件；预置历史预约和导师操作不增加新预约保存数。
6. **4:30–5:00 说明边界。** 模型理解意图，服务端查询真实场次和验证规则，写入需要用户确认。固定合成用例通过情况、运行请求结果、真实用户价值必须分别说明。

可选加演（在主流程之前演示无结果，避免主场次已被占用）：

```text
{alternative}
```

应得到无完全匹配及明确改变时间或日期的替代建议；实际候选依赖当前占用状态。接受替代仍须核对确认。

可选改期：未来预约改到 {days['replacement']} 14:00–14:30，同类型同导师。使用“查看我的预约”选择该未来预约；再输入目标日期，Review reschedule 并确认。确认预约距开始不足 48 小时受限制，改期成功后原记录 Canceled、新记录 Pending。

## 可复现命令

```powershell
python scripts/local_demo.py status
python scripts/demo_showcase.py status
# 清理仅针对本工具持有归属标记的专用账号及其演示记录；会删除这些账号的演示操作。
python scripts/demo_showcase.py cleanup
python scripts/demo_showcase.py setup
```

若其他学生预约了这些场次，或专用账号操作了其他账号的场次/资料，清理会拒绝执行并保留清单；不自动扩大删除范围。
原有 student@wba.test 等账号、预约和目标不会被重置。此工具只连接 wba-ai-demo，不连接原始部署。
作品集说明见 docs/portfolio/AI_BOOKING_CASE_STUDY.md；通用演示流程见 docs/demo/SHOWCASE.md。
'''
    GUIDE.write_bytes(text.encode('utf-8'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('setup', 'status', 'cleanup'))
    args = parser.parse_args()
    try:
        with command_lock():
            result = {'setup': setup, 'status': status, 'cleanup': cleanup}[args.command]()
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
        print(f'Showcase command failed: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
