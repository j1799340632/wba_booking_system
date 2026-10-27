# 项目运行与迭代指南

以下命令均在仓库根目录执行。

面向临床工作场所评估（Workplace-Based Assessment）的预约管理系统。本仓库保存现有课程团队项目的代码基线，作为后续产品迭代的起点。

当前支持学生预约评估时段、导师发布与管理时段、管理员维护用户及医院科室数据，并包含角色权限、通知和日历集成相关代码。原始基线不包含 AI 功能，本地开发版已加入 AI 查询、确认预约、查看本人预约、确认取消、原子改期及个人评估规划。AI 对话需配置 DeepSeek；目标、进度计算和规划页面可独立使用。

## 项目结构

- `unsw-comp9900-WBA-frontend/`：React、Vite 前端。
- `unsw-WBA-backend/`：Java、Spring Boot 多模块后端。
- `docker-compose.yml`：前端、后端、MySQL、Redis 的容器编排。
- `mysql-init.sql`：数据库表结构及医院、科室、角色、评估类型等基础字典。
- `.env.example`：部署所需环境变量示例。

## 本地准备

确保 Docker Desktop 已运行，安装 Java 25+、Maven、Node.js 和 Python 3.10+。根目录 `.env` 中填写 DeepSeek 配置后执行：

```powershell
python scripts/local_demo.py start
```

打开 [本地演示页面](http://127.0.0.1:5178)，使用 `student@wba.test` / `WbaDemo!2026` 登录，进入 **Book Slots**。演示账号和时段均为模拟数据。输入“帮我预约下周二下午的 Mini-CEX，优先 Dr Chen，其他导师也可以”，选择 **Review session**，核对并勾选确认后提交。成功请求为 Pending，等待导师审批。

管理已有预约可输入“查看我的预约”“取消我的预约”或“把我 [原预约日期] 的预约改到 [目标日期] 下午”（日期请按实际预约调整）。选择具体预约后核对并确认；改期失败会保留原预约，成功后原记录显示 Cancelled、新预约显示 Pending。已确认预约距开始不足 48 小时不能取消或改期。

评估规划从学生菜单 **Assessment Plan** 进入，也可在助手中输入“帮我做评估规划”。首次设置自己的评估类型、数量、开始日期和截止日期；系统按真实记录区分已完成、已确认、待审批和缺口，显示每周节奏及可约场次。目标没有预置课程配额，保存目标不会自动预约。

计划中的 **Your next actions** 显示待审批、48 小时内开始、正在进行及已结束但尚未完成的具体预约，同时提示截止日期和剩余缺口。点击 **View booking** 定位本人记录；导师审批或完成评估后，点击 **Refresh** 更新清单。正在进行的已确认场次属于临时覆盖，只有标记完成后才计入已完成数量。

导师可在 **Upcoming Sessions** 核对实际预约后审批、拒绝或取消；已结束的已确认场次可录入分数和反馈。每次操作校验本人归属、预约版本和状态，并保存站内通知。学生刷新计划后看到相应进度；导师在 **Completed Assessments** 修改结果会保留原完成日期。

管理员可用隔离演示账号 `admin@wba.test` / `WbaDemo!2026` 登录 **AI Insights**，按 Sydney 日期查看助手请求结果、确认保存与重试、P50/P95 耗时、模型调用与已报告的 Token，并下载聚合摘要。从本轮更新后开始记录；早先操作不补造数据，运行结果也不代表意图准确率。

AI 查询没有完全匹配的可约场次时，会单独展示替代建议，例如同一时间窗延后一周、原日期改选其他时间或导师。每组只改变一个条件，保留评估类型、医院和科室筛选；用户核对并明确接受变化后才预约，新预约和改期均支持。

此入口使用独立的 `wba-ai-demo` Compose 项目、数据库卷及端口，自动生成数据库密码并加载后端 AI 配置。查看状态执行 `python scripts/local_demo.py status`，停止执行 `python scripts/local_demo.py stop`。详细条件与验证记录见[第 4 轮联调说明](product/ITERATION_04_LOCAL_DEMO.md)。

需要录屏或做面试展示时，运行 `python scripts/demo_showcase.py setup` 创建独立的三角色演示账号和按 Sydney 当前日期生成的场次。命令会给出本轮实际日期、可复制的查询及五分钟演示稿；重复 setup 保留已有操作，cleanup 只清理归属检查通过的专用演示数据。详见[演示与复现](demo/SHOWCASE.md)及[AI 产品经理作品集案例稿](portfolio/AI_BOOKING_CASE_STUDY.md)。

原有完整 Docker 编排的启动方式仍为：

1. 首次启动时将 `.env.example` 复制为 `.env`，填写独立的 MySQL 和 Redis 密码；若 `.env` 已存在，只补充相应字段，保留已填的模型配置。
2. 如需邮件或 Google Calendar，填写自己的集成凭据；留空时这些集成功能不可用。
3. 检查 Compose 配置：`docker compose config --quiet`。
4. 配置 Docker 后，执行 `docker compose up --build`。前端默认地址为 `http://localhost:8000`，后端为 `http://localhost:8080`。

`mysql-init.sql` 保留表结构和基础字典，原始用户、用户设置及预约样例未上传。新增的演示账号与时段由 `compose.demo.yml` 单独加载，原有完整编排不加载这些演示数据。

若直接通过 IDE 启动后端，需在运行配置中设置 `SPRING_DATASOURCE_PASSWORD` 和 `REDISSON_PASSWORD`；IDE 不会自动读取根目录 `.env`。其他参数见后端的 `application-*.yml` 和 `application.properties`。

## 导入说明

本次导入保留原项目目录结构和团队署名，仅完成凭据环境变量化、敏感样例数据移除及仓库说明整理。原始文件保留在上传来源目录。本仓库的导入检查不代表已通过完整构建或端到端业务测试。

后端说明见 [unsw-WBA-backend/README.md](../unsw-WBA-backend/README.md)。

## 产品迭代记录

- [AI 预约助手：现状与首版方案](product/AI_BOOKING_MVP.md)：已有页面与接口、首版交互、实现顺序和 26 个待执行验收场景。
- [第 2 轮：学生预约查询基础](product/ITERATION_02_SEARCH.md)：已实现日期及导师筛选、重复场次过滤和正确分页，含实际验证结果。
- [第 3 轮：只读 AI 预约查询](product/ITERATION_03_AI_SEARCH.md)：本地实现、DeepSeek 配置、模拟验证及 3 条真实接口冒烟记录，尚未推送或部署。
- [第 4 轮：本地完整链路联调](product/ITERATION_04_LOCAL_DEMO.md)：演示启动入口、模拟账号与 MySQL 时段、真实 DeepSeek 和浏览器验证。
- [第 5 轮：确认式预约 Agent](product/ITERATION_05_CONFIRMED_BOOKING.md)：真实预约、并发冲突、幂等重试、持久站内通知与浏览器故障注入验证。
- [第 6 轮：预约管理](product/ITERATION_06_BOOKING_MANAGEMENT.md)：自然语言查看本人预约、确认取消、原子改期、历史保留与重试验证。
- [第 7 轮：个人评估规划](product/ITERATION_07_ASSESSMENT_PLAN.md)：保存个人目标、周期内进度、每周节奏、真实场次推荐与确认预约。
- [第 8 轮：无结果替代建议](product/ITERATION_08_ALTERNATIVE_SESSIONS.md)：明确标注单项条件变化、真实可约候选与用户确认，支持新预约和改期。
- [第 9 轮：评估计划跟进](product/ITERATION_09_ASSESSMENT_FOLLOW_UPS.md)：本人待办、截止日期提示、具体预约跳转和进行中统计，含状态刷新与时区验证。
- [第 10 轮：导师决策与评估完成闭环](product/ITERATION_10_SUPERVISOR_WORKFLOW.md)：归属和版本校验、确认审批、完成评估、保留日期的成绩修改与学生进度联动。
- [第 11 轮：AI 运行效果看板](product/ITERATION_11_AI_INSIGHTS.md)：真实操作记录、明确的统计口径、耗时与 Token 用量、管理员权限和聚合导出。
- [第 12 轮：可复现演示与作品集表达](product/ITERATION_12_SHOWCASE.md)：三角色持久演示数据、动态日期的五分钟演示稿、安全清理与有证据边界的案例稿。
- [AI 查询评测报告](evaluation/BOOKING_AI.md)：40 条主评测优化前后对比及 10 条新增表达，含逐条结果、耗时和 Token 用量。
