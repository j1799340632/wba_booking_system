# 五分钟演示与复现

用于本地作品集演示，所有账号、目标、场次和评分均为合成数据。

## 准备一次，反复使用

先在仓库根目录执行 `python scripts/local_demo.py status`。本地页面和 API 正在运行时可直接继续；尚未启动时执行 `python scripts/local_demo.py start`，不需要重新部署原 Docker 项目。AI 查询使用后端已有 DeepSeek 配置。

```powershell
python scripts/demo_showcase.py setup
python scripts/demo_showcase.py status
```

打开命令返回的 `.local/wba-demo/showcase/START_HERE.md`。该文件包含本轮实际日期、导师姓名、可复制的查询和逐步点击顺序。日期来自隔离 MySQL 的 Sydney 当前日期，首次生成的主场次在七天后；重复执行 setup 保留当前演示进度，不会向后移动预约日期。

| 角色 | 邮箱 | 密码 |
| --- | --- | --- |
| 学生 | showcase-student@wba.test | WbaDemo!2026 |
| 导师 | showcase-mentor@wba.test | WbaDemo!2026 |
| 管理员 | showcase-admin@wba.test | WbaDemo!2026 |

入口：[本地页面](http://127.0.0.1:5178)。三种角色建议使用不同浏览器配置或独立隐私窗口，各自登录，避免演示时混淆角色。

首次创建三名专用用户、六个单次场次、两条历史预约和一份个人目标。个人目标为 Mini-CEX 3 次、DOP 2 次，开始时间为七天前，截止时间为二十八天后，**不是官方课程配额**。历史预约分别为两天前已完成、昨天已确认但尚未记录结果；后者专供现场演示导师完成人工评估。

## 主流程与应观察到的结果

| 时间 | 页面与动作 | 检查结果 |
| --- | --- | --- |
| 0:00–0:40 | 学生 → Assessment Plan，说明目标与缺口 | 初始 1/5 完成，需要再请求 4 场；昨天已结束的 Confirmed 不算完成 |
| 0:40–1:50 | 学生 → Book Slots，复制本轮查询，Review session | 看到真实导师、实际日期、地点；确认勾选前不能提交；提交后为 Pending |
| 1:50–2:30 | 导师 → Upcoming Sessions，审批刚提交的未来预约 | 学生刷新后 Pending 变为 Confirmed，完成数仍为 1 |
| 2:30–3:30 | 导师完成昨天的预置 Confirmed 预约，人工填写演示分数及反馈 | 学生刷新后完成数变为 2，剩余 3；未来预约不能被提前完成 |
| 3:30–4:30 | 管理员 → AI Insights，刷新最近七天、下载摘要 | 本次真实查询产生运行观察，预览和确认不再调用模型；预置预约不伪造 SAVED 事件 |
| 4:30–5:00 | 说明方案取舍与评测边界 | 模型负责理解、真实服务负责校验和事务、用户确认后写入；固定用例表现不能替代真实用户效果 |

导师处理的是两条不同预约：审批的是刚创建的未来场次，完成的是历史合成场次。不要通过修改系统时间、提前完成未来场次或修改业务日期来制造同一场次的闭环。

AI Insights 汇总隔离 demo 中所有学生的操作，不能把整个看板当作本次演示独有的数据。运行失败、缺少用量或没有观察时保留未知/空数据，不能填充好看的指标。

加演可选无结果替代、查看本人预约和原子改期，具体日期及表达在动态演示稿中。推荐先演无结果替代，再演主流程，避免候选已经被主流程占用；不要同时确认主流程与加演中同一时段。取消或改期已确认场次仍受开始前 48 小时规则约束。

## 新一轮演示

运行约五天后 status 会建议刷新。即使未过期，已占用场次、已完成记录和历史观察也会保留，所以重新录屏前应准备新一轮。

```powershell
# 先退出专用演示账号，关闭其业务页面，停止正在进行的请求。
python scripts/demo_showcase.py cleanup
python scripts/demo_showcase.py setup
```

cleanup 会删除这三个专用账号的演示预约、目标、站内通知和观察事件。若要保留运行摘要，先在 AI Insights 下载；下载内容可能包含其他 demo 学生的聚合统计。原来的 `student@wba.test` 等账号及其记录会保留。

工具只使用 `wba-ai-demo` 的 Compose 服务，不接收任意数据库地址，不读取模型密钥，也不发送邮件或调用模型。归属依据为忽略目录中的清单、固定账号身份和每轮随机场次标记；清理前在事务中锁定有关记录并再次检查归属。保留 `.local/wba-demo/showcase/manifest.json`，不要手工移走后尝试“强制接管”同 ID 数据。

其他学生预约了专用场次、演示学生预约了其他导师的场次、专用管理员创建了其他资料或账号、账号姓名/角色被修改、场次位置标记被改动，都会使清理拒绝执行。工具不会扩大删除范围；检查关系并保留清单后再处理。若运行被中断，重试 setup 可恢复已提交的完整演示，未提交的 prepared 状态可用 cleanup 清理后重新创建。

## 本轮验证命令

```powershell
# 不加 --live 仅打印说明。
node scripts/demo/verify_showcase.cjs --live
```

需要 Python、Edge、Playwright 和 Docker CLI，可用 `WBA_PYTHON_EXE`、`WBA_PLAYWRIGHT_MODULE`、`WBA_DOCKER_EXE` 指定路径。验证要求尚未创建 showcase，避免覆盖正在使用的演示；运行会创建专用数据、测试清理边界及真实学生/导师/管理员页面，调用一次真实模型，最后清理验证数据并创建一份全新演示供人工使用。报告和截图位于忽略目录 `.local/wba-demo/browser/showcase-*`。

这是一套演示工具。具体业务规则和各轮验收见 [产品迭代记录](../product/ITERATION_11_AI_INSIGHTS.md)，产品表达见 [作品集案例](../portfolio/AI_BOOKING_CASE_STUDY.md)。
