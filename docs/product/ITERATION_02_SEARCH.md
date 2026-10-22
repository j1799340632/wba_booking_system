# 第 2 轮：学生预约查询基础

记录日期：2026-10-22。开发分支：`feat/booking-search`。

本轮实现日期、导师与业务字典的组合筛选，修复重复时段查询和筛选后分页，为后续自然语言查询提供可调用的业务能力。尚未接入模型或修改预约写入流程。

## 用户可见变化

Book Slots 页面新增导师姓名、From、To 三项输入。原有医院、科室、评估类型继续使用，搜索后翻页保留全部已应用条件，Reset 同时清空新增条件。日期和时间按学校业务时区 `Australia/Sydney` 输入与展示，浏览器所在时区不会转换这些本地时间字面值。

重复时段以指定查询范围内的实际场次作为候选：模板首次发生日期已过去，但后续场次仍有效时可以查到；待导师确认及已确认预约占用的场次不再出现在候选中。列表仍按时段模板分页，每条记录的 `availableTimeList` 列出匹配的实际场次。

例如，导师从 10 月 1 日起每天 14:00–14:30 开放时段，学生查询 10 月 27 日 13:00–17:00：现在应返回 10 月 27 日的实际场次，而不是因为模板结束时间在过去就排除整条记录。

## 查询接口约定

沿用 `GET /student/assessmentBooking/getAvailableAssessmentSlotList`，返回分页结构仍包含 `records`、`total`、`current`、`size`、`pages`。

| 参数 | 本轮行为 |
| --- | --- |
| `supervisorId` | 精确匹配导师 ID；ID 保持 Long/字符串精度 |
| `supervisorName` | 新增姓名子串筛选；同名导师均可匹配，同时指定 ID 时取交集 |
| `hospital`、`department`、`type` | 保留原有精确匹配 |
| `startTime`、`endTime` | 整个实际场次须落在此范围内，不能只满足部分重叠 |
| 时间格式 | 支持 `2026-10-27T13:00`、含秒 ISO 格式，以及原有空格分隔的 `2026-10-27 13:00:00` |
| 未传日期 | 从业务时区当前时间起查询未来 30 天；这替代了原来没有明确查询窗口的行为 |
| 只传开始时间 | 结束时间默认为开始时间后 30 天 |
| 只传结束时间 | 从当前业务时间查询至该结束时间 |
| 查询范围 | 最多 90 天；开始不得晚于或等于结束，已开始的场次不返回 |
| 分页 | 在所有候选完成实际场次过滤和排序后分页，`total` 为有匹配场次的时段模板数 |
| 排序 | 按当前窗口内第一个可约场次排序，同时间按 ID 稳定排序；未要求时间排序时保留按 ID 倒序 |
| `pageQuantity=-1` | 保留原有不分页模式 |

每条学生查询结果新增 `timeZone` 字段，表示业务时区。`nextAvailableTime` 为当前查询窗口内第一个匹配场次，而不是直接使用可能过时的存储值。

## 实现位置

- [StudentSlotSearchService](../../unsw-WBA-backend/unsw-WBA-service/src/main/java/edu/unsw/service/StudentSlotSearchService.java)：组合 SQL 筛选、实际场次过滤、占用排除、排序与分页。
- [BookingTimeConfig](../../unsw-WBA-backend/unsw-WBA-core/src/main/java/edu/unsw/core/config/BookingTimeConfig.java)：业务查询时钟，默认使用 `Australia/Sydney`；没有全局修改 JVM 时区或转换历史数据。
- [RruleUtils](../../unsw-WBA-backend/unsw-WBA-service/src/main/java/edu/unsw/service/utils/RruleUtils.java)：重复场次保持本地钟表时长，避免计算结果依赖宿主机时区。
- [Book Slots 页面](../../unsw-comp9900-WBA-frontend/src/pages/Main/Home/Student/BookSlot/index.jsx)：新增筛选及原有分页、重置衔接。
- [bookingSearch.js](../../unsw-comp9900-WBA-frontend/src/utils/bookingSearch.js)：请求条件、范围检查和业务时间展示。

后端采用 SQL 先缩小候选模板范围，再按最多 90 天的窗口展开场次并在内存中分页。这能保证筛选后的分页结果正确；后续数据量较大时，需要根据实际规模考虑场次索引或物化，而不是提前截取第一页后再筛选。

## 已执行验证

| 检查 | 结果 | 范围 |
| --- | --- | --- |
| Maven 六模块编译与测试 | 通过，17 项测试，0 失败 | 13 项新增查询测试及 4 项原有测试；API 模块同时编译 |
| 前端单元测试 | 通过，6 项测试 | 参数、翻页条件、重置、日期范围、跨时区展示及空值 |
| 前端生产构建 | 通过 | Vite 生产构建，保留项目既有的大包体积提示 |
| 新工具及测试文件 ESLint | 通过 | `bookingSearch.js`、`bookingSearch.test.js` |
| 页面 ESLint | 仍有 1 项原有问题 | 初次加载 effect 调用设置 loading 状态的函数，触发 `react-hooks/set-state-in-effect`；其他检查通过，未在项目中禁用该规则 |
| 浏览器交互及视觉检查 | 通过，使用模拟接口 | 桌面 1440px、手机 390px；组合筛选、下一页、重置、反向时间阻止请求；无页面异常 |

测试代码：

- [StudentSlotSearchServiceTest](../../unsw-WBA-backend/unsw-WBA-service/src/test/java/edu/unsw/service/StudentSlotSearchServiceTest.java) 使用独立 H2 内存库执行真实 MyBatis 查询，覆盖组合条件、同名导师、停用/删除、过期模板、预约占用、过滤后分页、排序、边界、默认范围、ID 精度、参数绑定与夏令时时长。
- [前端测试](../../unsw-comp9900-WBA-frontend/test/bookingSearch.test.js) 可通过 `npm test` 执行。

后端复现命令（在后端目录中）：

```text
mvn -B -ntp -pl unsw-WBA-api -am test
```

前端复现命令（在前端目录中）：

```text
npm test
npm run build
```

上述结果不等同于真实 MySQL、既有 Docker 数据或完整业务链路的验证。本轮未重新部署、未调用模型，也未发送通知或创建预约。

## 下一轮

实现只读自然语言查询：服务端解析需求、校验条件、必要时追问，再调用本轮查询能力展示真实候选。确认预约仍需在后续轮次补齐提交合法性、冲突、幂等和通知结果处理。
