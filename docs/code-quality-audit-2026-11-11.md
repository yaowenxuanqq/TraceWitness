# TraceWitness 全项目代码质量审计

**审计日期：** 2026-11-11  
**代码基线：** `main` / `abf11c4`，审计修复前  
**审计范围：** W01-W12 实现、测试、接口契约、异常处理、并发与资源释放、前后端一致性、部署复现、文档和仓库卫生  
**产品边界：** 纯软件仿真，不连接真实摄像头、门禁、电梯或厂商硬件

---

## 1. 结论

TraceWitness 当前是可构建、可测试、可运行的软件仿真原型。本次审计实际执行了 Java、Python、官方一键评测、全新 H2 数据库迁移、HTTP 端到端检查和浏览器相关静态检查，并对 Git 历史、工作区和忽略规则做了隐私与密钥扫描。

结论分三层：

| 口径 | 结论 |
| --- | --- |
| W01-W12 代码实现可运行 | 是 |
| Mock 软件链路可演示、可评测 | 是 |
| 真实 LLM / Agent 能力已验证 | 否。真实模型配置缺失，`run_demo.bat` 退出码为 2。这是正式验收 P0，不能用 Mock 结果替代 |
| W12 正式交付完整 | 否。演示视频未录制，真实模型指标未测 |

本次修复了 8 个低风险且证据明确的代码问题，包括上传内容注入、安全响应头、Java 21 直接构建误导性失败、Python 请求体额外字段、插件清单敏感字段暴露、队列同步和异常日志；并新增 `scripts/http-audit.ps1`，把 22 项 HTTP 检查固化为可复跑脚本。未改动或移动 `w01`-`w12` 标签。

---

## 2. 实测环境与命令

### 2.1 环境

| 项 | 实测值 |
| --- | --- |
| 操作系统 | Windows 11 |
| Java | OpenJDK 1.8.0_504，Temurin |
| 机器默认 `JAVA_HOME` | JDK 21.0.2 |
| Gradle Wrapper | 7.6.4 |
| Python | 3.13.15，`ai-service/.venv` |
| Spring Boot | 2.7.18 |
| H2 | 2.1.214 |
| Flyway | 8.5.13 |
| ONNX Runtime | 1.31.1 |

### 2.2 已执行命令

```powershell
# Java 测试，项目基线 Java 8
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot'
.\platform\gradlew.bat -p platform clean test --no-daemon --console=plain

# Python 测试
.\ai-service\.venv\Scripts\python.exe -m pytest ai-service\tests -q

# 官方一键评测
cmd /c "set NO_PAUSE=1&& run_eval.bat"

# Java 21 直接构建的保护验证
$env:JAVA_HOME='C:\Users\rghf5\apps\jdk-21.0.2'
.\platform\gradlew.bat -p platform tasks --no-daemon

# 全新 H2 数据库 + HTTP 端到端
# 使用临时数据库和临时媒体目录启动 Java，Python 监听 127.0.0.1:8000

# Git 卫生
git ls-files
git log --all --name-only
git rev-list --objects --all | git cat-file --batch-check
rg -n -i 'BEGIN .* PRIVATE KEY|AKIA...|sk-...|ghp_...|api_key...'
```

---

## 3. 实测结果

### 3.1 自动化测试

| 检查 | 结果 |
| --- | --- |
| Java 测试 | **36/36 通过**，20 个测试类，0 失败，0 错误，0 跳过 |
| Python 测试 | **22/22 通过** |
| `run_eval.bat` | 退出码 0 |
| Gradle 退出码 | 0 |
| pytest 退出码 | 0 |
| 检测基准退出码 | 0 |
| 四路并发基准退出码 | 0 |
| 报告生成退出码 | 0 |

新增测试：

- `MediaTypesTest`：6 个上传类型、扩展名和文件头测试。
- `MediaControllerTest`：2 个上传拒绝和安全响应头测试。
- `test_request_validation.py`：5 个 Python 请求体严格校验测试。

### 3.2 全新数据库

临时 H2 文件库首次启动时，Flyway 成功执行 9 个迁移：

`V1__init.sql` 到 `V9__plugin.sql` 全部成功，应用正常监听 `127.0.0.1:8080`。

### 3.3 HTTP 端到端

`scripts/http-audit.ps1` 覆盖的只读主链路和上传安全共 **22/22 通过**：

- 首页、`app.js`、`styles.css`。
- `/api/health`、`/api/mode`、`/api/overview`。
- 事件详情、Agent 运行、工具调用。
- 媒体时间线、整段音频、事件帧图。
- 证据包正常校验、篡改校验。
- Python `/health`、`/v1/agent/select-tool`。
- Python 额外字段 400、视觉尺寸越界 400。
- HTML 上传 400。
- WAV 上传 200，媒体响应带 `nosniff` 和 CSP。

全新数据库上的有状态链路 **7/7 通过**：

- 人员合并审批成功，重复审批 409。
- 非机动车告警确认成功，重复确认 409，`executed` 仍为 false。
- 插件人工回滚成功，故障注入成功，无上一版时 409。
- 难例标注成功，重复标注 409，导出包含已标注项。
- 非法 JSON 400、未知 API 404、缺失事件 404。
- 插件清单不再暴露 `signature` 和 `payload`。

### 3.4 性能

| 指标 | 本次实测 | 计划目标 | 判断 |
| --- | --- | --- | --- |
| 单路 640 宽合成检测 | 322.54 fps | ≥5 fps | 通过 |
| 四路 1080p 并发最低 | 31.48 fps | 每路 ≥2 fps | 通过 |
| 10,000 条三维向量 P95 | 35 ms | <1000 ms | 通过 |

口径：检测模型是仓库内 176 字节的合成亮度卷积，不是训练过的目标检测模型；性能只代表开发机软件仿真，不代表真实模型或边缘设备。

---

## 4. P0：正式验收阻塞

### P0-01：真实 LLM / 真实 Agent 验收缺失

**证据**

- `run_demo.bat` 实测输出“真实模型未验收”，退出码 **2**。
- 当前环境没有设置 `TRACEWITNESS_MODEL_BASE_URL`、`TRACEWITNESS_MODEL_API_KEY`、`TRACEWITNESS_MODEL_NAME`。
- `docs/eval-report.md` 明确记录：真实模型调用未测；`w07.json` 的 10 条只核对工具列表，没有跑真实调查闭环。
- 现有测试证明 MockAdapter、本地 fake provider、工具参数校验和引用校验可用，不等于真实供应商可用。
- 30 条真实正向任务完成率、工具选择准确率、平均轮次、平均耗时、失败分类均未测。

**影响**

- 不能声明“真实 Agent 已验收”。
- 不能把 Mock 结果写成真实任务完成率或工具选择准确率。
- 这是 W07/W12 正式验收的 P0 阻塞，不是普通代码缺陷。

**根因**

- 项目边界允许纯软件 Mock 演示，但真实模型需要项目所有者提供 OpenAI-compatible 地址、密钥和模型名。
- 当前没有配置，也没有真实模型 smoke test 和固定任务集。

**修复方案**

1. 由项目所有者配置模型地址、密钥和模型名，不提交到仓库。
2. 实现真实 `run_demo.bat`：检查三项配置，启动 Python `:8000` 和 Java `:8080`，等待 `/health` 与 `/api/health`，打开页面；缺配置时明确失败。
3. 增加真实模型 smoke test：健康检查、选择 `search_events`、执行工具、生成带 `source_ids` 的报告、校验引用属于本次 run。
4. 用 30 条固定任务测量任务完成率、工具选择准确率、引用合法率、平均轮次、平均耗时和失败分类。
5. 用同一组任务分别跑 Mock、本地 fake provider 和真实 provider，报告中分组，不得混算。

**验证命令**

```powershell
$env:TRACEWITNESS_MODEL_BASE_URL='<owner-configured>'
$env:TRACEWITNESS_MODEL_API_KEY='<owner-configured>'
$env:TRACEWITNESS_MODEL_NAME='<owner-configured>'
cmd /c run_demo.bat
# 期望：退出码 0；页面显示 MODEL_API；/api/agent/runs/{id} 的来源均属于本次 run
```

**优先级：** P0。完成前，README、简历和验收结论只能写“Mock 软件原型”，不能写“真实 Agent 已验证”。

---

## 5. P1：高优先级

### P1-01：上传接口接受任意 Content-Type，存在同源内容注入风险（本次已修复）

**修复前证据**

```text
POST /api/media/uploads
file=evil.html, Content-Type: text/html
status=200, contentType=text/html

GET /api/media/assets/{id}/file
status=200
Content-Type: text/html
X-Content-Type-Options=(空)
```

**影响**

- 浏览器上传控件只接受 `video/*,audio/*`，后端没有验证，直接以声明类型内联返回。
- 若未来服务暴露到局域网或公网，恶意 HTML/SVG 可能形成同源脚本执行路径。
- 当前默认回环监听降低现实风险，但不能依赖前端限制。

**根因**

- `MediaService.storeUpload` 直接使用 `MultipartFile.getContentType()`。
- `MediaController.asset` 直接按库存 contentType 返回，没有 `nosniff` 和 CSP。

**修复**

- 新增 `MediaTypes`：只接受常见 `audio/*` 和 `video/*` 容器；MIME、扩展名和文件头必须一致。
- HTML、SVG、可执行文件、未知文件头和伪装成媒体的未知内容返回 400。
- 新增 `SecurityHeadersFilter`：统一设置 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer` 和同源 CSP。
- 新增 8 个 Java 测试覆盖拒绝 HTML、伪装 MP4、类型不匹配、响应头。

**验证**

```powershell
curl.exe -sS -F "file=@evil.html;type=text/html;filename=evil.html" http://127.0.0.1:8080/api/media/uploads
# 期望 400
curl.exe -sS -D - -F "file=@sample.wav;type=audio/wav;filename=sample.wav" http://127.0.0.1:8080/api/media/uploads
# 期望 200；后续 GET 带 nosniff 和 CSP
```

**回归结果：** HTML 上传 400；WAV 上传 200；媒体响应头通过。

### P1-02：HTTP 回归已补，浏览器回归仍缺（部分修复）

**证据**

- 本次已新增并实测 `scripts/http-audit.ps1`，22/22 通过。
- 7 项有状态检查仍是审计时临时执行，没有固化成脚本。
- 没有浏览器自动化回归，`run_eval.bat` 默认也不会调用 HTTP 脚本。
- 修改 Controller、页面或权限边界后，`run_eval.bat` 仍不能自动发现页面回归。

**影响**

- 审批、告警、插件回滚等有状态链路仍可能被后续重构悄悄破坏。
- 招聘方需要额外启动服务再手工执行脚本，不能只靠 `run_eval.bat`。

**修复方案**

1. 把 7 项有状态检查补进 `scripts/http-audit.ps1`，使用临时数据库或独立测试环境。
2. 增加可选浏览器回归：页面加载无 console error、证据校验、审批操作、390px 无横向溢出、axe-core 无 violation。
3. 在 `run_eval.bat` 增加 HTTP 检查开关，CI 中启用。

**验证命令**

```powershell
cmd /c "set NO_PAUSE=1&& set TRACEWITNESS_HTTP_AUDIT=1&& run_eval.bat"
# 或先启动双服务，再执行：
.\scripts\http-audit.ps1
# 期望：单元、接口、页面关键回归一次执行，任一失败退出码非 0
```

**优先级：** P1。HTTP 只读/上传部分已可复跑；有状态链路和浏览器回归仍缺。

### P1-03：W12 演示视频未录制（未解决）

**证据**

- `docs/demo-script.md` 只有预演步骤。
- `docs/eval-report.md` 明确记录“演示视频：未录制”。
- 项目计划 W12 要求 5-10 分钟演示。

**影响**

- 招聘方需要自行运行项目才能看到完整效果。
- 作品集缺少可直接分享的展示材料。

**根因**

- 演示视频依赖真实模型链路先完成，当前 P0 未关闭，录制会得到 Mock 画面或未验证的真实画面。

**修复方案**

- 真实模型 P0 完成后再录视频。
- 只使用合成数据，不出现密钥、真实监控画面或个人信息。
- 覆盖架构、Mock/真实模式区别、完整调查、工具引用、人员审批、告警确认、插件回滚、证据篡改失败、检测和检索指标。

**验证命令**

```powershell
# 录完后检查脚本、截图和视频元数据
Get-ChildItem docs\demo-script.md, docs\*.mp4
git status --short
```

**优先级：** P1。属于交付完整性，不阻塞代码运行。

---

## 6. P2：中优先级

### P2-01：H2 与 Flyway 版本组合有兼容警告（未解决）

**证据**

```text
H2 2.1.214 is newer than this version of Flyway
latest supported version of H2 is 2.1.210
```

**影响**

- 当前 9 个迁移成功，但升级路径未被官方支持组合覆盖。
- 未来 H2 小版本变化可能引入迁移失败。

**根因**

- Spring Boot 2.7.18 管理 H2 2.1.214，Flyway 8.5.13 明确支持到 2.1.210。

**修复方案**

- 固定 H2 为 2.1.210，或升级 Flyway 到支持 2.1.214 的版本。
- 增加空库迁移、旧库升级和失败回滚测试。

**验证命令**

```powershell
.\platform\gradlew.bat -p platform bootRun
# 期望启动日志不再出现 H2 newer than Flyway 警告
```

### P2-02：插件签名是演示级固定 HMAC（未解决）

**证据**

- `PluginPolicy.SIGN_KEY` 是仓库内固定字符串。
- 文档已说明不是生产供应链安全能力。
- 本次已修复 `listManifests` 不再向 `/api/overview` 返回完整 `payload` 和 `signature`，降低清单泄露面。

**影响**

- 任何能读源码的人都能伪造演示插件签名。
- 不能把该验签能力写成生产安全能力。

**根因**

- 为了让离线演示不依赖密钥管理，签名密钥直接写在 `PluginPolicy`。

**修复方案**

- 保持演示范围，README 和简历明确标注“非生产签名体系”。
- 长期改为非对称签名，仓库只保存公钥。
- 私钥放到本地秘密存储，不进入源码。

**验证命令**

```powershell
rg -n "SIGN_KEY" platform\src\main\java
.\platform\gradlew.bat -p platform test --tests "*PluginPolicyTest"
# 期望：测试通过；文档和源码都明确这是演示密钥
```

### P2-03：未选择开源许可证（未解决）

**证据**

- 根目录没有 `LICENSE`。
- README 写“No open-source license has been selected yet”。

**影响**

- 公开仓库的复用、修改和分发授权不明确。

**根因**

- 项目还没有确定开源策略；当前只保留了 README 的“All rights reserved”描述。

**修复方案**

- 选择 MIT、Apache-2.0 或其他许可证并添加 `LICENSE`。
- 同步更新 README 许可证说明和第三方依赖/模型台账。

**验证命令**

```powershell
Test-Path LICENSE
Select-String -Path README.md -Pattern "License|许可证"
# 期望：LICENSE 存在，README 与文件一致
```

### P2-04：构建依赖区域镜像和 Python 版本硬编码（部分保留）

**证据**

- Gradle distribution 使用华为云镜像。
- Maven 依赖优先阿里云，再回退 Maven Central。
- `run_eval.bat` / `run_mock.bat` 使用 `py -3.13` 创建虚拟环境。

**影响**

- 中国网络环境构建较快；海外评审或镜像故障时首次构建可能失败。
- 机器没有 `py -3.13` 时，脚本无法自动创建虚拟环境。

**根因**

- 脚本按当前开发机 Windows/中国网络环境优化，优先区域镜像并固定 Python 3.13。

**修复方案**

- 保留镜像，同时记录官方源回退和离线缓存做法。
- 脚本优先使用 `py -3.13`，不可用时回退到满足 `requirements.txt` 的 `python`，并在启动前打印版本。
- CI 使用固定官方源或可访问的镜像。

**验证命令**

```powershell
py -3.13 --version
cmd /c "set NO_PAUSE=1&& run_eval.bat"
# 期望：当前环境退出码 0；换无 py-3.13 的机器时回退逻辑生效
```

### P2-05：ID 序列读改写不是并发安全的（未解决）

**证据**

- `PlatformStore.nextId` 先 `select seq`，再 `insert/update`，没有行锁或数据库序列。
- 多个请求同时为同一实体申请 ID 时，理论上可能读到同一 seq。

**影响**

- 同一实体类型的并发写入可能主键冲突或丢 ID。
- 当前单用户回环演示风险低；浏览器同时发起多个审批或插件操作时风险上升。

**根因**

- 为保持 H2 简单性，ID 生成放在应用层。

**修复方案**

- 改为数据库原子自增：`update id_sequence set seq = seq + 1 ...` 后读取，或使用 H2/数据库序列。
- 对并发审批、告警、插件回滚增加并行测试。

**验证命令**

```powershell
.\platform\gradlew.bat -p platform test
# 修复后增加并发申请 ID 的测试；期望无主键冲突、无重复 ID
```

**本次已修：** `OutboxQueue` 的 `addAll` / `drain` 已改为同步方法，避免队列拷贝和清空竞态。

### P2-06：写接口没有认证，跨 run 写操作缺少隔离（未解决）

**证据**

- 审批、告警、证据、难例、插件接口没有身份认证或授权。
- 本轮已为 `GET /api/events/{id}`、`GET /api/agent/runs/{id}`、`GET /api/agent/runs/{id}/tool-calls` 增加当前 run 校验。
- 写接口仍按 ID 查找记录，未统一校验记录是否属于当前 `runId`。

**影响**

- 默认只监听 `127.0.0.1`，现实风险受限。
- 若未来暴露到局域网/公网，任何调用方都能审批、回滚或标注。
- 多 run 场景下，知道 ID 的调用方可能操作其他 run 的记录。

**根因**

- 项目定位为单机回环演示，未引入认证层；写服务直接按主键查记录，没有统一 run 所有者校验。

**修复方案**

- 增加本地 token 或会话认证。
- 所有写接口在服务层校验 `record.runId == currentRunId`。
- 增加未认证、跨 run、重复请求测试。

**验证命令**

```powershell
.\scripts\http-audit.ps1
# 修复后增加：未带 token 的写请求 401；跨 run ID 的写请求 404
```

### P2-07：Agent 异常被统一记成 `model_failed`（已加日志，行为保留）

**证据**

- `AgentService.investigate` 捕获 `RuntimeException` 后统一调用 `saveFailure(..., "model_failed", ...)`。
- 真实模型失败、工具执行异常、持久化异常在数据库里都表现为 `model_failed`。

**影响**

- 失败分类不够细，排障时容易误判。
- 本次已增加 `LOG.warn` 记录异常堆栈，避免完全静默。

**根因**

- 早期为了把模型失败统一转成“证据不足”报告，用了单一 catch 分支。

**修复方案**

- 区分 `model_failed`、`tool_failed`、`persistence_failed`、`citation_invalid`。
- 在 Agent 运行记录中保存失败阶段和工具名。

**验证命令**

```powershell
.\platform\gradlew.bat -p platform test --tests "*AgentWeek7Test"
# 修复后增加：模型、工具、持久化三类失败分别断言失败类型
```

### P2-08：Java Controller 请求体仍是宽松 `Map`（未解决）

**证据**

- `ApiController` 的 POST 接口使用 `@RequestBody Map<String, Object>`，额外字段被忽略。
- Python 侧本次已加严格字段、类型、尺寸和点字段校验。
- Java `ToolSchemas.reject` 已对工具参数做二次校验，但 Controller 顶层请求体没有同等级校验。

**影响**

- 调用方拼错字段时可能“请求成功但字段没生效”。
- 审批和插件接口的输入契约不够 fail-closed。

**根因**

- Controller 为了少写 DTO 直接接收 `Map`，Spring 默认忽略未知字段。

**修复方案**

- 为审批、告警、证据、难例、插件回滚定义请求 DTO。
- 使用 Bean Validation 或显式字段白名单，未知字段返回 400。
- 增加额外字段、错误类型、缺字段测试。

**验证命令**

```powershell
# 修复后对审批、告警、证据、难例、插件回滚分别发送额外字段
# 期望：全部返回 400，而不是 200
.\scripts\http-audit.ps1
```

### P2-09：插件清单敏感字段暴露（本次已修复）

**修复前证据**

- `/api/overview` 的 `plugins` 包含 `signature` 和 `payload`。

**影响**

- 页面不需要这些字段，暴露它们扩大演示密钥和载荷的可见面。

**根因**

- 清单查询复用了内部完整行映射，API 直接返回了数据库字段。

**修复**

- `PluginStore.listManifests` 在返回前移除 `signature` 和 `payload`。
- `findManifest` / `findSlot` 仍保留内部字段，供故障注入和测试使用。

**验证**

```powershell
(Invoke-RestMethod http://127.0.0.1:8080/api/overview).plugins[0].PSObject.Properties.Name
# 期望不包含 signature、payload
```

### P2-10：Python 请求体接受额外字段（本次已修复）

**修复前证据**

- `/v1/agent/select-tool` 顶层额外字段仍返回 200，被忽略。

**影响**

- 调用方拼错字段不会 fail closed。
- 视觉请求的 `frame_width` 没有上限，可能触发超大内存分配。

**根因**

- FastAPI 路由直接接收 `dict`，没有字段白名单、类型模型或尺寸边界。

**修复**

- 四个 Python 路由增加字段白名单。
- `task`、`tools`、`tool_results`、`model_seed`、`citation_hint`、`case_key`、`points`、`faults` 做类型校验。
- `frames` 2-30，`frame_width` / `frame_height` 64-4096，`points` 最多 10,000 条。
- `tick` 必须是非负整数，`run_id` / `request_timestamp` 必须是非空字符串。

**验证**

```powershell
# 额外字段、错误类型、超大宽度、缺点字段均应返回 400
.\ai-service\.venv\Scripts\python.exe -m pytest ai-service\tests\test_request_validation.py -q
```

### P2-11：Java 21 直接构建出现误导性 ClassNotFoundException（本次已修复）

**修复前证据**

- 机器 `JAVA_HOME` 指向 JDK 21 时，`gradlew.bat test` 编译成功，但 test worker 对全部测试类报 `ClassNotFoundException`，实际根因是 Gradle 7.6.4 不支持 Java 21。

**影响**

- 开发者容易误判为测试类丢失或构建缓存损坏。
- `run_eval.bat` 通过强制 Java 8 掩盖了直接 Gradle 路径的问题。

**根因**

- Gradle 7.6.4 只支持到 Java 19；直接 `gradlew.bat` 路径没有在配置阶段检查 `JAVA_HOME`。

**修复**

- `platform/build.gradle` 在配置阶段检查 Java 主版本，大于 19 时给出明确错误，提示设置 `JAVA_HOME` 到 JDK 8 或使用仓库脚本。

**验证**

```powershell
$env:JAVA_HOME='C:\Users\rghf5\apps\jdk-21.0.2'
.\platform\gradlew.bat -p platform tasks --no-daemon
# 期望：配置阶段明确失败，不再出现全部测试类 ClassNotFoundException
```

---

## 7. P3：低优先级与工程卫生

| 编号 | 证据 | 影响 | 根因 | 修复方案 | 验证命令 |
| --- | --- | --- | --- | --- | --- |
| P3-01 SVG 对比度 | 前次 axe 剩余 1 个 incomplete，涉及 7 个 SVG `text` 节点 | 无障碍审计不能完全自动通过，弱视用户可读性需要人工确认 | SVG 文字背景不是可自动推断的纯色块 | 给 SVG 增加明确背景矩形，固定文字和背景颜色 | 重跑 axe；人工截图检查 7 个文本节点 |
| P3-02 错误 message | `application.yml` 配置 `server.error.include-message: always` | 错误响应可能带内部实现细节 | 为调试方便全局开启 | 改为 `never`，或只在开发 profile 开启 | 触发 500/400 检查响应不含内部路径或 SQL |
| P3-03 outbox 全局更新 | `PlatformStore.publishRemaining` 更新所有 `PENDING` outbox | 多 run 时可能把别的 run 一起标记发布 | 早期只有固定单 run，方法没有 run 参数 | 给方法加 `run_id` 条件 | 建两个 run，发布 A 后检查 B 仍为 PENDING |
| P3-04 SVG 坐标插值 | `web/app.js` 的 `polyline` / `circle` 坐标来自服务端数值，未逐项 `esc` | 当前数据源是 Java 整数，风险低；外部导入轨迹时可能被注入属性 | 前端假定轨迹坐标总是数字 | 用 DOM API 设置属性或强制 `Number()` 校验 | 传入非数字轨迹坐标，检查不进入 SVG 属性 |
| P3-05 媒体格式白名单 | `MediaTypes` 只接受常见 MP4/MOV、WebM/MKV、WAV、MP3、OGG、FLAC、AAC、AVI、FLV、MPEG | 非常规但合法的音视频容器会被拒绝 | 安全修复优先覆盖常见浏览器格式 | 需要新格式时扩展 `MediaTypes` 和测试，不放宽成任意 `audio/*` / `video/*` | 对每种新增格式加魔数、扩展名和 MIME 测试 |

**优先级：** P3。以上问题不阻塞当前 Mock 演示，建议在公开部署或多人并发前处理。

---

## 8. W01-W12 覆盖矩阵

| 阶段 | 实现证据 | 现有测试/检查 | 结论 | 缺口 |
| --- | --- | --- | --- | --- |
| W01 合规与确定性 | 环境脚本、公开边界、`TimeSource` | `RestartAcceptanceTest`、`docs/determinism.md` | 通过 | 无真实硬件，符合边界 |
| W02 Mock 平台与骨架 | H2、Outbox、事件/设备/站点 | `RestartAcceptanceTest`、`Week2RulesTest` | 通过 | 多 run 并发未覆盖 |
| W03 媒体与 AOR | 时间线、WAV、帧图、上传 | `AorTimelineTest`、HTTP 媒体检查 | 通过 | 上传类型已收紧 |
| W04 检测与跟踪 | 合成 ONNX、短窗跟踪 | `test_vision.py`、单路/四路基准 | 通过（合成模型范围） | 不是真实检测模型 |
| W05 优选帧与回退 | 属性头、门控回退、跟踪中断、脱敏 | `test_attributes.py`、HTTP 结构化检查 | 通过 | 真实人脸/人体模型未接 |
| W06 端云路由 | `route-w06-1`、脚本云适配器 | `RoutePolicyTest`、`ReverseAssertionTest`、`w06.json` | 通过（脚本路由范围） | 真实云模型未接 |
| W07 Agent 主链路 | 六工具、引用校验、重试、高风险动作门 | `AgentWeek7Test`、`test_mock_adapter.py`、`w07.json` | 有条件通过 | **真实 LLM/Agent 未验收（P0）** |
| W08 人员档案 | 门点图、档案、审批、旅行冲突 | `ProfilePolicyTest`、`ProfileWeek8Test`、`w08.json` | 通过 | 无真实人脸底库 |
| W09 检索与告警 | 多图检索、硬/软条件、非机动车告警 | `RetrievalPolicyTest`、`RetrievalWeek9Test`、`w09.json` | 通过 | 脚本检测，不是真实视频 |
| W10 轨迹与证据 | 轨迹剔除、抽稀、同行、证据包、难例 | `TrajectoryPolicyTest`、`TrajectoryWeek10Test`、`w10.json` | 通过 | 证据包为演示级 |
| W11 插件验签与回滚 | HMAC 验签、ABI/资源检查、故障回滚 | `PluginPolicyTest`、`PluginWeek11Test`、`w11.json` | 通过（演示安全范围） | 固定演示密钥，不是生产签名 |
| W12 集成与交付 | `run_eval`、holdout、文档、标签 | `EvalSuiteWeek11Test`、`HoldoutTest`、`run_eval.bat` | 工程交付通过，正式验收未通过 | 真实模型、演示视频、HTTP 回归 |

---

## 9. 接口契约与前后端一致性

### 9.1 已一致

- `contracts/agent-report.schema.json` 使用 `additionalProperties: false`，Python 报告校验和 Java 引用校验都执行。
- 工具参数在 Python 侧按每工具 schema 校验，Java `ToolSchemas.reject` 再做一次顶层类型、必填和额外字段检查。
- 结构化观察只调用一次属性头；Java 和 Python 测试都断言 `attribute_calls == 1`。
- 高风险动作只进入 `requested_actions`，落库前经 `ReverseRules.evaluate("high_risk", ...)`，`executed` 固定 false。
- 前端所有主要文本通过 `esc()` 转义；上传控件只展示 `video/*` 或 `audio/*`。

### 9.2 仍需收紧

- Java Controller 顶层请求体仍是 `Map`，额外字段被忽略（P2-08）。
- 写接口缺少认证和统一 run 隔离（P2-06）。
- API 文档已补充上传白名单和安全响应头，但还没有机器可执行的 OpenAPI 契约。

---

## 10. 并发与资源释放

| 项 | 结论 |
| --- | --- |
| `TimeSource` | 已同步，业务时间读写一致 |
| `OutboxQueue` | 本次已改为同步 `addAll` / `drain` |
| `PlatformStore.nextId` | 读改写非原子，P2-05 |
| Python ONNX session | 本次已加双重检查锁，避免并发首次初始化重复加载 |
| 文件流 | Java 使用 try-with-resources；Python `urlopen` 使用上下文管理器 |
| HTTP 客户端 | Java `RestTemplate` 连接 3s、读取 60s；Python `urlopen` 30s |
| 上传大小 | Spring multipart 限制 32MB；本次加视觉尺寸和点数量上限 |

---

## 11. 部署与可复现性

### 11.1 已验证

- `run_eval.bat` 在本机工作区退出码 0。
- 最终代码提交 `71117d5` 的干净 clone 执行 `run_eval.bat` 退出码 0；脚本自动创建 `ai-service/.venv`、按 `requirements.txt` 安装固定依赖，并完成 Java 36、Python 22、检测基准、四路基准和报告生成。
- 全新 H2 文件库执行 9 个迁移成功。
- Python `.venv` 依赖完整，22 个测试通过。
- Java 8 下 36 个测试通过。
- Java 21 直接构建现在会给出明确错误。

### 11.2 仍需验证/改进

- `run_eval.bat` / `run_mock.bat` 硬编码 `py -3.13`（P2-04）。
- Gradle distribution 和 Maven 依赖优先区域镜像（P2-04）。
- `spring.web.resources.static-locations: file:web/` 依赖工作目录；`bootRun` 已设置 `workingDir = ..`，直接运行 jar 时需从仓库根启动。

---

## 12. 文档与仓库卫生

### 12.1 Git 跟踪范围

- `git ls-files` 中没有 `交底书/`、`private-docs/`、`runtime/`、`models/`、`logs/`、`data/local/`、`.venv/`、`build/` 下的文件。
- `.gitignore` 已整目录排除私有材料、密钥、本地运行数据、模型权重和构建产物。
- 工作区里的 `交底书/`、`private-docs/`、`_docx_text/`、`data/`、`logs/`、`models/`、`runtime/` 是本地未跟踪目录，不应提交。

### 12.2 密钥与私人资料

- 对已跟踪源码、文档、场景和脚本执行私钥、AWS key、GitHub token、Slack token、API key、secret、password 模式扫描，未发现匹配。
- Git 历史路径扫描未发现交底书、private-docs、runtime、models、`.env`、`.pem`、`.key`、`.p12`、secret、password、token、credential 误提交。
- 唯一固定密钥是 `PluginPolicy.SIGN_KEY` 演示 HMAC，已在文档标注为非生产能力（P2-02）。

### 12.3 大文件与模型权重

- Git 历史最大 blob 是 `platform/gradle/wrapper/gradle-wrapper.jar`，61,624 字节。
- 没有 MB 级模型权重、视频或数据集进入历史。
- `ai-service/app/synthetic_detect.onnx` 176 字节，SHA-256 为 `FCA2418EEA6507D645A0F845BB5E3DE9D7749379692D3175ADD3C02311D1642C`，与 `docs/model_licenses.md` 登记一致；这是合成亮度卷积，不是第三方权重。

### 12.4 标签

- `w01` 到 `w12` 共 12 个标签存在。
- 本次审计没有创建、移动、删除或重打任何 `w01`-`w12` 标签。

---

## 13. 本次已修复项

| 编号 | 修复 | 主要文件 | 验证 |
| --- | --- | --- | --- |
| F-01 | 上传 MIME/扩展名/文件头白名单 | `MediaTypes.java`、`MediaService.java` | `MediaTypesTest` 6/6；HTTP HTML 400、WAV 200 |
| F-02 | 统一安全响应头 | `SecurityHeadersFilter.java`、`MediaController.java` | `MediaControllerTest` 2/2；HTTP 头部检查 |
| F-03 | Java 21 配置阶段早失败 | `platform/build.gradle` | JDK 21 下明确报错 |
| F-04 | Python 请求体严格字段/类型/尺寸校验 | `ai-service/app/main.py`、`adapters.py` | `test_request_validation.py` 5/5 |
| F-05 | 插件清单移除 `signature` / `payload` | `PluginStore.java` | HTTP overview 字段检查 |
| F-06 | `OutboxQueue` 同步 | `OutboxQueue.java` | Java 全量测试 |
| F-07 | Agent 异常日志 | `AgentService.java` | Java 全量测试 |
| F-08 | 读取接口当前 run 隔离 | `ApiController.java` | Java 全量测试、HTTP 404 检查 |
| F-09 | 新增可复跑 HTTP 审计脚本 | `scripts/http-audit.ps1` | 脚本 22/22 通过 |

---

## 14. 未解决问题与建议顺序

### 第一优先级：P0 真实 Agent 验收

1. 配置 OpenAI-compatible 模型。
2. 实现真实 `run_demo.bat`。
3. 跑 30 条正向任务和 20 条反向断言。
4. 记录任务完成率、工具选择准确率、引用合法率和失败分类。
5. 重新生成评测报告和简历描述。

### 第二优先级：P1 交付与回归

1. 浏览器回归：无 console error、390px 无横向溢出、axe-core。
2. 有状态 HTTP 检查并入脚本，并接入 `run_eval.bat` 可选开关。
3. 录制 5-10 分钟演示视频。

### 第三优先级：P2 工程加固

1. 写接口认证与统一 run 隔离。
2. `nextId` 原子化。
3. Java Controller DTO + 额外字段拒绝。
4. H2/Flyway 版本组合。
5. Python 版本回退和镜像回退说明。
6. 选择许可证。

### 第四优先级：P3 展示与清理

1. SVG 对比度。
2. 错误 message 策略。
3. outbox 按 run 过滤。
4. 前端 SVG 属性数值校验。

---

## 15. 审计验收清单

### 已完成

- [x] Java 36/36 测试通过。
- [x] Python 22/22 测试通过。
- [x] `run_eval.bat` 退出码 0。
- [x] 最终代码提交干净 clone 的 `run_eval.bat` 退出码 0。
- [x] 全新 H2 数据库 9 个迁移成功。
- [x] `scripts/http-audit.ps1` 22/22 通过。
- [x] 全新库有状态链路 7/7 通过。
- [x] 上传 HTML 被拒绝，WAV 可上传并带安全响应头。
- [x] 插件清单不再暴露签名和载荷。
- [x] Java 21 直接构建给出明确错误。
- [x] Git 历史和当前跟踪文件未发现私有材料、密钥或大模型权重。
- [x] `w01`-`w12` 标签保持原样。

### 未完成

- [ ] 真实模型配置和真实 Agent smoke test。
- [ ] 30 条正向任务完成率与工具选择准确率。
- [ ] 5-10 分钟演示视频。
- [ ] 有状态 HTTP 检查并入脚本；浏览器自动化回归。
- [ ] 写接口认证与统一 run 隔离。
- [ ] H2/Flyway 兼容警告。
- [ ] 开源许可证。

---

## 16. 最终判断

TraceWitness 作为纯软件仿真原型，代码结构、确定性策略、证据引用、Mock 工具链、测试和仓库卫生达到可展示水平。本次审计修复了上传注入、响应头、请求体严格性和 Java 21 构建误导等明确问题，并补了对应测试。

当前不能对外声明“真实 LLM Agent 已验收”或“12 个阶段全部完成”。真实模型配置缺失属于正式验收 P0；在完成真实模型 smoke test 和 30 条固定任务前，所有 Agent 指标必须继续写“未测”。

---

## 17. 2026-11-12 质量门禁复审计

**复审计基线：** `fae3054`
**边界：** 用户明确把真实模型接入留到最后；本轮没有配置或调用真实模型，没有修改 `w01`-`w12` 标签。

### 17.1 实测结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Java | 39/39 通过 | `scripts\quality-gate.ps1`、`platform\build\test-results` |
| Java 行覆盖率 | 90.43% | JaCoCo XML/HTML，门限 60% |
| Python | 23/23 通过 | pytest 退出码 0 |
| Python 覆盖率 | 80.58% | `coverage-python.xml`，门限 70% |
| Python 静态安全 | Ruff、Bandit 通过 | `ruff check app tests`、`bandit -r app -q -ll` |
| Python 依赖 | 0 条已知漏洞 | pip-audit |
| Java 运行时依赖 | 40 个坐标已扫描；35 条已核对公告 | OSV querybatch，基线见 `security\osv-baseline.txt` |
| SBOM | Java、Python 均生成 | CycloneDX JSON |
| HTTP | 44/44 通过 | `scripts\http-audit.ps1` |
| 浏览器 | 交互、console error、axe、390px 通过 | `scripts\browser-audit.ps1` |
| 一键评测 | 退出码 0 | `run_eval.bat`；Java 39、Python 23、检测和四路基准通过 |
| H2/Flyway 兼容警告 | 0 条 | H2 `2.2.220` + Flyway `9.22.3` |

### 17.2 本轮修复

#### P1-05 OpenAPI 契约无效（已修复）

**证据：** 原 `contracts/http-api.yaml` 只有部分写接口，所有 operation 缺 `responses`，多个 `{id}` 未声明路径参数，只有首个写接口声明 token；旧测试只做字符串包含。
**影响：** 前端、审计脚本和文档可能出现接口漂移，结构错误无法在 CI 阻断。
**根因：** 契约从局部说明演化，没有解析器级测试。
**修复：** 覆盖 24 条实际路由，补 response、path parameter、`TraceToken` security scheme 和 multipart schema；`HttpContractFileTest` 改用 Swagger Parser 校验。
**验证：** `.\platform\gradlew.bat test --tests com.tracewitness.platform.HttpContractFileTest`。
**优先级：** P1，已完成。

#### P1-06 HTTP 安全检查覆盖倒退（已修复）

**证据：** `fae3054` 的脚本覆盖写接口，但缺 CSP、安全头、静态资源、HTML/WAV 上传、Python 字段边界和工具选择。
**影响：** 重构后关键安全回归会静默离开门禁。
**根因：** 写接口加固与旧只读审计脚本分开演进。
**修复：** 合并为 44 项统一脚本，脚本自行启动临时服务、临时 H2 和临时媒体目录，结束后清理进程与临时文件。
**验证：** `powershell -File .\scripts\http-audit.ps1`，结果 `TOTAL pass=44 fail=0`。
**优先级：** P1，已完成。

#### P1-07 依赖基线落后（部分修复，仍为 P1）

**证据：** Spring Boot `2.7.18` 已停止常规维护。Java 8 分支仍有 35 条 OSV 公告，集中在 Spring Framework `5.3.39` 和 Logback `1.2.13`。
**影响：** 部分公告属于 Spring MVC 静态资源、路径处理和 multipart 代码路径；本机回环绑定降低暴露面，但不能据此声明“无漏洞”。
**根因：** Java 8 + Spring Boot 2.7 兼容分支已到生命周期末端；Boot 3 需要 Java 17 和 `javax` 到 `jakarta` 迁移。
**本轮修复：** Tomcat `9.0.123`、Jackson `2.23.0`、SnakeYAML `2.7`、Logback `1.2.13`、Log4j API `2.26.1`、Flyway `9.22.3`、H2 `2.2.220`；Python 依赖升级到无已知漏洞版本。
**剩余方案：** 单独迁移到 Java 17 + Spring Boot 3，切换 Jakarta API，逐周重跑 W01-W12、HTTP、浏览器和一键评测；在此之前保留 OSV 基线和 P1 风险声明。
**验证：** `powershell -File .\scripts\java-osv-audit.ps1` 只允许基线内公告，新公告失败；`pip-audit -r ai-service\requirements.txt` 为 0。
**优先级：** P1，未完全关闭。

#### P2-09 质量门禁缺失（已修复）

**证据：** 原仓库没有 CI、覆盖率、静态分析、SBOM 或可复跑浏览器门禁。
**影响：** 修复质量和依赖风险依赖人工记忆。
**根因：** 交付脚本只跑测试，没有工程门禁层。
**修复：** 新增 `scripts\quality-gate.ps1`、`scripts\browser-audit.ps1`、`.github\workflows\quality.yml`、JaCoCo/SBOM 配置、Python dev 依赖和 Ruff/Bandit/pip-audit。
**验证：** 本地完整门禁通过；CI 在 Windows、Java 8、Python 3.13 上执行同一命令。
**优先级：** P2，已完成。

#### P2-10 许可证选择缺失（仍未关闭）

**证据：** 仓库没有 `LICENSE`；README 当前写“No open-source license has been selected”。
**影响：** 公开复用和第三方分发边界不明确。
**根因：** 这是项目所有者的法律选择，不能由代码审计擅自决定。
**方案：** 所有者选择专有许可或开源许可证后加入 `LICENSE`，再补第三方许可证清单。
**优先级：** P2，需所有者决定。

#### P3-04 文档、错误信息、泛型警告与 axe（已修复）

**证据：** 架构文档仍写模型适配器不发请求；部署文档仍要求先启动双服务；`include-message` 为 `always`；TrajectoryService 有 unchecked varargs；axe 对 SVG 文本给出 incomplete。
**影响：** 文档误导、错误响应泄露内部消息、编译警告和可访问性人工检查残留。
**修复：** 文档同步；错误 message/stacktrace 改为 `never`；移除泛型 varargs；轨迹 SVG 增加确定背景、文字色和 aria 隐藏重复文本；编译开启 `-Xlint:unchecked -Xlint:deprecation -Werror`。
**验证：** 浏览器门禁 axe violations 0；Java 全量构建 0 警告；文档命令与实际脚本一致。
**优先级：** P3，已完成。

### 17.3 P0 真实模型验收仍未完成

**证据：** 当前未设置 `TRACEWITNESS_MODEL_BASE_URL`、`TRACEWITNESS_MODEL_API_KEY`、`TRACEWITNESS_MODEL_NAME`；`run_demo.bat` 仍明确退出 2。
**影响：** 真实 Qwen/其他供应商的任务完成率、工具选择准确率、引用合法率和延迟均未验证。
**根因：** 用户明确要求把模型接入留到最后；本轮只验证 Mock、本地 fake provider 和 OpenAI-compatible 请求形状。
**方案：** 最后阶段配置真实 API，运行 smoke test、固定任务集和失败分类；缺配置时继续保留 P0，不伪造结果。
**优先级：** P0，用户主动延期，当前仍未关闭。

### 17.4 最终判断

本轮把接口契约、HTTP 回归、覆盖率、静态分析、依赖审计、SBOM、浏览器回归和文档门禁补齐。纯软件仿真链路可以复跑，Python 依赖当前无已知漏洞；Java 8/Spring Boot 2.7 分支仍有 35 条已核对公告，必须通过 Java 17 + Spring Boot 3 迁移才能实质关闭。真实模型能力仍未验收，不得写成已验证。
