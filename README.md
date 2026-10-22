# WBA 预约管理平台

面向临床工作场所评估（Workplace-Based Assessment）的预约管理系统。本仓库保存现有课程团队项目的代码基线，作为后续产品迭代的起点。

当前支持学生预约评估时段、导师发布与管理时段、管理员维护用户及医院科室数据，并包含角色权限、通知和日历集成相关代码。当前基线尚未实现 AI 助手。

## 项目结构

- `unsw-comp9900-WBA-frontend/`：React、Vite 前端。
- `unsw-WBA-backend/`：Java、Spring Boot 多模块后端。
- `docker-compose.yml`：前端、后端、MySQL、Redis 的容器编排。
- `mysql-init.sql`：数据库表结构及医院、科室、角色、评估类型等基础字典。
- `.env.example`：部署所需环境变量示例。

## 本地准备

1. 将 `.env.example` 复制为 `.env`，填写独立的 MySQL 和 Redis 密码。
2. 如需邮件或 Google Calendar，填写自己的集成凭据；留空时这些集成功能不可用。
3. 检查 Compose 配置：`docker compose config --quiet`。
4. 配置 Docker 后，执行 `docker compose up --build`。前端默认地址为 `http://localhost:8000`，后端为 `http://localhost:8080`。

仓库中的 SQL 保留表结构和基础字典，原始用户、用户设置及预约样例未上传，尚未提供可登录的演示账户。完整启动验证与脱敏演示数据将在后续迭代处理。

若直接通过 IDE 启动后端，需在运行配置中设置 `SPRING_DATASOURCE_PASSWORD` 和 `REDISSON_PASSWORD`；IDE 不会自动读取根目录 `.env`。其他参数见后端的 `application-*.yml` 和 `application.properties`。

## 导入说明

本次导入保留原项目目录结构和团队署名，仅完成凭据环境变量化、敏感样例数据移除及仓库说明整理。原始文件保留在上传来源目录。本仓库的导入检查不代表已通过完整构建或端到端业务测试。

后端说明见 [unsw-WBA-backend/README.md](unsw-WBA-backend/README.md)。

## 产品迭代记录

- [AI 预约助手：现状与首版方案](docs/product/AI_BOOKING_MVP.md)：已有页面与接口、首版交互、实现顺序和 26 个待执行验收场景。
- [第 2 轮：学生预约查询基础](docs/product/ITERATION_02_SEARCH.md)：已实现日期及导师筛选、重复场次过滤和正确分页，含实际验证结果。
