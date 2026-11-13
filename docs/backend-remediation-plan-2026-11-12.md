# TraceWitness 后端缺陷整改方案

**编制日期：** 2026-11-12
**审计依据：** [code-quality-audit-2026-11-11.md](code-quality-audit-2026-11-11.md)
**编制基线：** `main` / `da45642`，加上编制时尚未提交的后端加固改动
**项目边界：** 纯软件仿真，不连接真实摄像头、门禁、电梯或厂商硬件

---

## 1. 目标与范围

本方案处理代码质量审计后仍未关闭的后端问题：

| 编号 | 优先级 | 问题 | 当前状态 |
| --- | --- | --- | --- |
| P0-01 | P0 | 真实 LLM / Agent 验收未完成 | 用户要求模型接入最后做；当前未验收 |
| P1-01 | P1 | Java 8 + Spring Boot 2.7 已停止常规维护 | 35 条已核对 OSV 公告仍在基线中 |
| P2-01 | P2 | 上传文件与数据库写入非原子 | 可能留下孤儿文件或不可读记录 |
| P2-02 | P2 | `/api/overview` 多表聚合没有统一增长上限 | 数据增长后可能拖慢查询和响应 |
| P2-03 | P2 | Java 到 Python 的内部调用没有服务间鉴权 | 依赖回环监听，缺少纵深防御 |
| P2-04 | P2 | Python 基准脚本未纳入 Ruff 门禁 | 有 5 条已发现风格问题 |

本方案不改变以下边界：

- 不连接真实硬件，不声称完成真实摄像头、门禁或电梯联调。
- 不把 Mock、fake provider 或接口形状测试写成真实模型能力。
- 不移动、删除或重打已有 `w01`-`w12` 标签。
- 不把演示用插件 HMAC 包装成生产级供应链安全能力。
- 不在仓库中写入真实 API Key、服务间 token、交底书、私有资料或模型权重。

---

## 2. 已有验证基线

以下结果来自上一轮后端加固后的完整质量门禁，不是本方案编制时重新运行的结果：

| 检查 | 已有结果 |
| --- | --- |
| Java 测试 | 44/44 通过 |
| Java 行覆盖率 | 90.39% |
| Java 分支覆盖率 | 70.88% |
| Python 测试 | 25/25 通过 |
| Python 覆盖率 | 80.46% |
| HTTP 审计 | 44/44 通过 |
| Ruff、Bandit、pip-audit | 通过 |
| Java OSV 基线、SBOM、构建门禁 | 通过 |
| `git diff --check` | 通过 |

这些结果证明 Mock 软件链路和当前门禁可用，不证明真实模型可用，也不消除 Java 8 分支的已知依赖风险。

---

## 3. 实施原则

### 3.1 先可观测，再改行为

每项修复先补失败用例或可复跑的复现步骤，再修改实现。修复后保留失败分类、状态字段或结构化日志，避免问题只从测试输出中消失。

### 3.2 Mock 与真实模型严格分组

普通质量门禁保持离线、确定性，继续使用 Mock。真实模型验收使用单独入口和单独报告，缺配置时明确失败，不静默跳过，不混算指标。

### 3.3 资源读取必须有上限

所有由 Run 数据增长的列表和聚合都必须在数据库查询层限流。不能先读取全部记录，再在 Java 或 JavaScript 中截断。

### 3.4 文件系统与数据库使用可恢复一致性

文件系统和数据库不能组成真正的跨系统原子事务。实现目标是：

- API 不暴露半成品；
- 崩溃后可以判定每个中间状态；
- 清理任务幂等；
- 不因一次失败永久阻塞后续上传。

### 3.5 低风险修复与高风险迁移分开

Ruff、服务间鉴权、查询上限和上传状态机分别提交。Java 17 / Spring Boot 3 迁移作为独立工作包，不与业务修复混在一个提交中。

---

## 4. P0-01：真实 LLM / Agent 验收

### 4.1 问题

当前代码具备 OpenAI-compatible 模型适配器、Mock Adapter、工具参数校验和引用校验，但没有完成真实供应商验收。当前环境未配置：

```text
TRACEWITNESS_MODEL_BASE_URL
TRACEWITNESS_MODEL_API_KEY
TRACEWITNESS_MODEL_NAME
```

因此以下结论均未验证：

- 真实模型能否稳定返回可解析 JSON；
- 真实模型能否选择正确工具；
- 工具结果能否形成合法引用；
- 429、5xx、超时和非法响应如何分类；
- 30 条固定任务的任务完成率、工具选择准确率、引用合法率和延迟；
- 高风险动作是否始终只进入 `requested_actions`，且 `executed=false`。

### 4.2 验收模式设计

保留两种明确模式：

| 模式 | 入口 | 用途 | 是否计入真实 Agent 指标 |
| --- | --- | --- | --- |
| `MOCK` | `run_mock.bat`、普通质量门禁 | 离线演示、确定性回归 | 否 |
| `MODEL_API` | 独立真实模型验收入口 | 连接项目所有者提供的模型 | 是 |

真实模型验收入口建议使用：

```powershell
$env:TRACEWITNESS_MODEL_BASE_URL='<owner-configured>'
$env:TRACEWITNESS_MODEL_API_KEY='<owner-configured>'
$env:TRACEWITNESS_MODEL_NAME='<owner-configured>'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\model-acceptance.ps1
```

当前适配器要求三项配置都存在。对于 Ollama、vLLM 等明确无鉴权的本地服务，必须通过独立配置开关显式允许空 API Key；不能通过“忽略缺失 Key”隐式放宽远程供应商配置。

### 4.3 验收流程

1. 校验三个模型配置项，并验证 Base URL 只使用 `http` 或 `https`。
2. 启动 Python `:8000` 和 Java `:8080`，等待 `/health`、`/api/health` 成功。
3. 校验 `/api/mode` 返回 `MODEL_API`，防止误用 Mock 结果。
4. 执行 smoke investigation：
   - 至少产生一次真实工具调用；
   - 报告 `source_ids` 非空；
   - 所有 `source_ids` 属于本次 Run；
   - Agent 运行不是 `model_failed`；
   - 高风险动作实际执行数为 0。
5. 执行 30 条正向任务和 20 条反向任务。
6. 输出独立报告，例如 `docs/model-acceptance-<date>.md`，记录模型提供方、模型名、日期、任务集版本、原始结果文件路径和失败样本。
7. 报告中只保存脱敏地址、请求摘要、耗时和错误码，不保存 API Key、完整 prompt 或供应商敏感响应。

### 4.4 指标与硬门槛

| 指标 | 处理方式 |
| --- | --- |
| 引用合法率 | 硬门槛必须为 100% |
| 高风险动作实际执行数 | 硬门槛必须为 0 |
| 反向断言通过率 | 硬门槛必须为 100% |
| 任务完成率 | 首轮记录基线，后续不得无证据下降 |
| 工具选择准确率 | 首轮记录基线，后续不得无证据下降 |
| P50 / P95 延迟 | 按供应商、模型和日期记录 |
| 失败分类 | 至少区分超时、限流、连接失败、非法 JSON、非法工具参数 |
| Token usage | 供应商返回时记录；未返回时写“不可用” |

### 4.5 CI 策略

- 普通 `scripts/quality-gate.ps1` 不依赖外部模型，不在每次提交时消耗 API 配额。
- 新增独立模型验收任务，只在项目所有者显式配置 secret 后运行。
- 缺失模型配置时，普通质量门禁仍可通过，但正式验收状态保持 P0 未关闭。
- README、简历和演示材料只能写实际完成的模式与指标。

### 4.6 完成条件

- [ ] 真实模型 smoke investigation 通过。
- [ ] 30 条正向任务和 20 条反向任务执行完毕。
- [ ] 引用合法率 100%。
- [ ] 高风险动作实际执行数为 0。
- [ ] 验收报告明确记录真实模型信息，未混用 Mock 数据。
- [ ] 仓库和历史中不存在真实 API Key。

---

## 5. P1-01：Java 17 + Spring Boot 3 迁移

### 5.1 问题

当前构建使用：

- Java 8；
- Spring Boot `2.7.18`；
- Gradle Wrapper `7.6.4`；
- `javax.servlet.*`；
- 多项为缓解旧依赖而显式覆盖的版本。

Java 8 和 Spring Boot 2.7 已停止常规维护。OSV 基线中的公告不等于每个漏洞都可利用，但当前分支无法持续获得安全修复，不能作为长期主线。

### 5.2 迁移目标

- 运行时和编译目标升级到 Java 17。
- Spring Boot 升级到仍受支持的 3.x 版本，并固定精确版本。
- Gradle Wrapper 升级到所选 Spring Boot 与插件支持的版本。
- 只将 Java EE API 从 `javax.*` 切换到 `jakarta.*`。
- 清理不再需要的旧版本覆盖和 OSV 基线。
- 保持现有 HTTP API、数据库逻辑和 Mock 行为。

### 5.3 分阶段实施

#### 阶段 A：基线冻结

1. 先审阅并提交当前尚未提交的 18 个后端加固文件，形成干净基线。
2. 备份本地 H2 文件和媒体目录。
3. 保存迁移前 `quality-gate.ps1`、`run_eval.bat`、HTTP 审计和浏览器审计结果。
4. 记录 `w01`-`w12` 标签指向，后续不修改。

#### 阶段 B：Java 17 构建

1. 修改 `platform/build.gradle` 的 `sourceCompatibility` 和 `targetCompatibility`。
2. 调整 Java 21 早失败保护，改为要求 Java 17 以上；不再保留只支持 Java 8 的提示。
3. 更新 `scripts/quality-gate.ps1`、`run_mock.bat`、`run_eval.bat` 和 CI 中的 JDK 查找逻辑。
4. 在 Spring Boot 2.7 尚可运行的前提下完成 Java 17 编译和测试，隔离 Java 版本问题。

#### 阶段 C：Spring Boot 3 与 Jakarta

1. 升级 Spring Boot Gradle 插件和 dependency-management 插件。
2. 替换 `javax.servlet.*`、`javax.validation.*`、`javax.persistence.*` 等 Java EE 包为 `jakarta.*`。
3. 保留 `javax.imageio.*`、`javax.crypto.*`、`javax.sql.*` 等 JDK 标准库包；不能全局盲替换所有 `javax`。
4. 检查 Filter、异常处理、静态资源、multipart、JSON 序列化和测试配置。
5. 升级 Flyway、H2、Jackson、Logback、Tomcat 等依赖，优先使用 Spring Boot BOM 管理版本。
6. 清理只用于旧 EOL 分支的版本覆盖和 OSV 白名单；如仍有公告，必须逐条给出受影响代码路径、无修复原因和临时缓解。

### 5.4 数据与回滚

- 迁移前保存 H2 数据库备份和媒体目录清单。
- 在临时数据库验证空库迁移和上一版本数据库升级。
- 迁移期间不修改已有 Flyway migration；需要结构变化时新增 migration。
- 每个迁移提交都能回退到上一个提交。
- 不在迁移过程中移动或重打标签；旧标签继续指向原提交。

### 5.5 验证矩阵

以下检查全部通过后才算关闭 P1：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\quality-gate.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\browser-audit.ps1
cmd /c "set NO_PAUSE=1&& run_eval.bat"
git diff --check
```

还需验证：

- W01-W12 固定评测结果未被无意改变；
- 全新 H2 数据库迁移成功；
- 旧 H2 数据库升级成功；
- `/api/overview`、写接口、媒体流和证据包契约不变；
- Swagger Parser 契约检查通过；
- OSV 不再依赖原先 35 条基线公告；
- SBOM 在干净环境生成成功。

### 5.6 完成条件

- [ ] Java 17 与 Spring Boot 3 在本地和 CI 构建通过。
- [ ] `javax` 到 `jakarta` 迁移完成。
- [ ] 旧依赖覆盖和基线已实质清理。
- [ ] Mock、HTTP、浏览器和 W01-W12 回归通过。
- [ ] 空库迁移和旧库升级通过。
- [ ] 回滚步骤经过一次演练。

---

## 6. P2-01：上传文件与数据库写入可恢复一致性

### 6.1 问题

当前 `MediaService.storeUpload` 直接执行：

1. 生成 ID；
2. 创建目标目录；
3. 写最终文件；
4. 插入 `media_asset`。

数据库事务无法回滚已写入的文件。进程崩溃、磁盘失败或数据库插入失败可能造成孤儿文件；如果实现顺序调整，也可能出现数据库记录存在但文件缺失。大文件当前还会通过 `MultipartFile.getBytes()` 整体读入内存。

### 6.2 设计决策

不制造“文件系统和数据库真原子”的假象。采用状态机、同文件系统原子重命名和补偿清理：

```text
UPLOADING -> STAGED -> READY
                     -> FAILED
READY -> DELETING
```

约束：

- API 只能读取 `READY` 资产。
- 文件写入和哈希计算不持有数据库事务。
- 临时文件与最终目录位于同一文件系统，保证 `ATOMIC_MOVE` 可用。
- 所有补偿操作幂等。
- 清理任务不能删除宽限期内可能仍在处理的上传。

### 6.3 数据库迁移

新增下一编号 Flyway migration，例如 `V10__media_asset_state.sql`：

```sql
ALTER TABLE media_asset ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'READY';
ALTER TABLE media_asset ADD COLUMN sha256 VARCHAR(64);
ALTER TABLE media_asset ADD COLUMN size_bytes BIGINT;

CREATE INDEX idx_media_run_status
    ON media_asset (run_id, kind, status, created_at, id);
```

已有资产迁移后统一为 `READY`，避免旧数据突然不可见。后续需要记录失败原因时，再增加受长度约束的 `error_code`，不保存底层异常堆栈。

### 6.4 上传流程

1. 校验文件名、声明的 Content-Type 和基础格式条件。
2. 生成唯一资产 ID 和最终相对路径。
3. 数据库插入 `UPLOADING` 元数据，然后提交事务。
4. 使用 `MultipartFile.getInputStream()` 流式写入 `.staging/<id>.part`。
5. 边写边计算 SHA-256、累计字节数，并执行服务端大小上限。
6. 读取受限制的文件头，完成真实媒体类型和扩展名一致性校验。
7. 校验失败时删除临时文件，并把记录标记为 `FAILED`。
8. 数据库更新状态为 `STAGED`。
9. 使用 `Files.move(..., ATOMIC_MOVE)` 移到最终不可变路径。
10. 数据库更新状态为 `READY`，写入哈希和大小。
11. 返回资产 ID 和 URL。

数据库操作与文件 IO 分成短事务，不在文件写入期间长时间占用连接。每一步都必须可重复执行。

### 6.5 崩溃恢复

新增启动时和定时补偿任务：

| 残留状态 | 判定 | 处理 |
| --- | --- | --- |
| 超过宽限期的 `UPLOADING` | 临时文件不存在或不完整 | 删除残留文件，标记 `FAILED` |
| 超过宽限期的 `STAGED` | 最终文件存在且哈希匹配 | 幂等重试更新为 `READY` |
| `STAGED` | 最终文件不存在 | 标记 `FAILED` 并清理临时文件 |
| 最终目录存在孤儿文件 | 无对应 `READY/STAGED` 记录且超过宽限期 | 移入隔离区后删除 |
| `READY` | 文件缺失 | 标记数据损坏，不返回半成品 |

补偿任务使用配置化宽限期，例如 15 分钟；具体值需根据维护窗口和文件大小基准确定。日志记录资产 ID、状态、错误码和清理结果，不记录文件内容。

### 6.6 并发与安全

- 资产 ID 使用现有原子 ID 分配，或在迁移阶段改成数据库序列。
- 最终路径包含 Run ID 和资产 ID，禁止覆盖已有文件。
- 所有路径移动前校验仍位于媒体根目录内。
- 不信任客户端提供的扩展名、MIME 或文件名。
- 上传大小同时受 Spring multipart 限制和服务端计数限制约束。
- 读取接口、时间线和证据导出只消费 `READY` 资产。

### 6.7 测试

- 正常上传最终状态为 `READY`。
- HTML、伪装媒体、未知文件头和超限文件返回 400，且不出现 `READY` 记录。
- 文件写入失败后事务和临时文件可清理。
- 数据库从 `UPLOADING` 到 `STAGED` 失败后不暴露资产。
- 模拟“原子移动成功、`READY` 更新前进程退出”，重启后恢复为 `READY`。
- 模拟“数据库有记录、文件缺失”，读取返回受控错误。
- 并发上传不产生重复 ID、覆盖或半成品。
- 补偿任务重复执行不产生副作用。

### 6.8 验证命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\quality-gate.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\http-audit.ps1
```

HTTP 审计需增加至少以下断言：

- 上传成功后可立即读取；
- 失败上传不进入时间线；
- 重启后成功上传仍可读取；
- 模拟残留状态不会出现在 API 响应。

### 6.9 完成条件

- [ ] `media_asset` 状态迁移完成，旧资产保持可读。
- [ ] 大文件不再通过 `byte[]` 整体驻留内存。
- [ ] 文件写入、原子移动、状态更新和补偿流程有测试。
- [ ] API 不返回 `UPLOADING/STAGED/FAILED` 资产。
- [ ] 崩溃恢复和重复清理测试通过。

---

## 7. P2-02：`/api/overview` 查询与响应上限

### 7.1 问题

`ApiController.overview()` 组合了事件、步骤、工具调用、轨迹、观察、决策、档案、检索、证据包和插件等多类数据。多个 Store/Service 查询没有统一上限，部分查询还存在“先列出父记录，再为每条父记录查询子记录”的 N+1 模式。

数据量增长后可能出现：

- 响应体持续增长；
- JVM 堆和浏览器内存占用升高；
- 数据库排序和多次查询变慢；
- 前端渲染卡顿；
- 超时后整页不可用。

### 7.2 方案

#### 数据库层限制

所有增长型列表使用稳定排序和查询上限：

```text
ORDER BY <stable-time-or-sequence>, id
LIMIT :limit
```

需要判断是否截断时查询 `limit + 1` 条，再返回前 `limit` 条。

不能只修改 Controller 对完整列表做 `subList`，否则数据库和 JVM 内存压力没有下降。

#### 配置化上限

在 `tracewitness.overview` 下增加各项上限配置，例如：

```yaml
tracewitness:
  overview:
    events: 20
    agent-steps: 50
    tool-calls: 50
    tracks: 200
    observations: 200
    decisions: 200
    profiles: 100
    tickets: 100
    queries: 50
    passages: 100
    trajectories: 50
    packages: 50
    hard-cases: 50
    plugins: 50
```

这些只是初始建议值，最终值应由典型数据量和性能基准确定。上限必须有硬上限校验，配置文件不能把它改到无边界。

#### API 契约

保留现有数组字段，降低前端迁移风险，同时增加明确的元数据：

```json
{
  "overviewMeta": {
    "truncated": true,
    "limits": {
      "tracks": 200,
      "packages": 50
    },
    "totals": {
      "tracks": 1250,
      "packages": 74
    }
  }
}
```

前端在 `truncated=true` 时显示“已截断，请进入明细页”。如果某类数据还没有明细分页接口，先补齐分页接口，再对概览启用严格上限。

#### N+1 控制

- 父列表先按上限截断。
- 子查询只针对已截断的父记录执行。
- `hits`、`exclusions`、审计记录等子列表也必须设置上限。
- 总数量使用独立 `COUNT`，不加载完整对象。
- 常用过滤和排序增加复合索引。

#### 前端与契约同步

- 更新 `contracts/http-api.yaml` 和 `docs/api.md`。
- 更新前端类型和截断提示。
- HTTP 契约测试覆盖新字段。
- 新增大数据集测试，断言返回数量不超过配置上限。

### 7.3 性能门槛

建议以本机真实 H2 文件和典型数据量建立基准：

- `/api/overview` 返回数量不超过配置上限；
- P95 响应时间不超过 500 ms；如果开发机无法稳定达到，记录硬件和基线，不能写成已验证达标；
- 响应体大小设上限并记录；
- JVM 堆增长可解释且不会随总记录数线性增长；
- 查询计划使用预期索引。

性能目标必须标为开发机实测口径，不代表生产环境。

### 7.4 测试

- 每类列表超过上限时只返回上限数量。
- `overviewMeta.truncated` 和各列表截断状态正确。
- 稳定排序保证重复请求结果确定。
- 分页接口能取到概览之外的记录。
- 父子关系不因父列表截断而丢失必要引用。
- 构造大数据集后验证响应时间、响应体和内存。

### 7.5 完成条件

- [ ] 所有增长型概览查询在数据库层有上限。
- [ ] 子查询受父列表上限约束。
- [ ] API 元数据、OpenAPI、前端提示一致。
- [ ] 大数据集测试和性能基准有结果记录。
- [ ] 明细数据有分页或等价查询路径。

---

## 8. P2-03：Java 到 Python 的服务间鉴权

### 8.1 问题

Python AI 服务当前绑定回环地址，但业务接口没有内部鉴权。本机其他进程如果知道端口，可以直接调用模型选择、报告生成和视觉接口，绕过 Java 侧写 token 和 Run 隔离。回环监听降低了外部暴露面，但不足以形成纵深防御。

### 8.2 方案

使用独立服务间 bearer token：

```text
TRACEWITNESS_SERVICE_TOKEN
```

Java 和 Python 从环境变量读取同一值。该 token 与浏览器写接口使用的 `TRACEWITNESS_API_TOKEN` 分离，不能复用。

#### Python

- 对 `/v1/agent/*`、`/v1/vision/*` 增加鉴权依赖。
- 使用 `hmac.compare_digest` 比较 token，避免普通字符串比较的时序差异。
- 缺少或错误 token 返回 401，不返回预期 token。
- `/health` 保持无鉴权，只返回服务名、模式和 banner，不返回配置或敏感状态。
- 生产验收模式下 token 缺失导致启动失败；本地 Mock 启动脚本为每次运行生成随机 token。

#### Java

- `AppProperties` 增加内部服务 token。
- `PythonAgentClient` 对全部 AI 请求增加 `Authorization: Bearer <token>`。
- token 只进入请求头，不进入错误响应、数据库、Agent step 或日志。
- AI 返回 401/403 时归类为服务配置或认证失败，不混淆为模型失败。

#### 启动脚本和 CI

- `run_mock.bat`、`run_eval.bat`、HTTP 审计和浏览器审计在启动前生成随机值。
- 同一父进程同时传给 Java 和 Python。
- CI 使用作业级随机 secret，不写入代码和缓存。
- 本地服务仍绑定 `127.0.0.1`。

### 8.3 测试

- 无 token 调用 AI 业务接口返回 401。
- 错误 token 返回 401。
- 正确 token 返回正常结果。
- `/health` 无需 token。
- Java 客户端自动发送 token。
- Java/Python 版本不一致或 token 缺失时，最终错误分类明确。
- 日志、异常和数据库中没有 token。

### 8.4 验证

```powershell
.\ai-service\.venv\Scripts\python.exe -m pytest ai-service\tests -q
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\quality-gate.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\http-audit.ps1
```

### 8.5 完成条件

- [ ] 所有业务 AI 接口要求服务间 token。
- [ ] Java 和启动脚本自动传递 token。
- [ ] 缺失/错误 token 测试通过。
- [ ] token 不出现在 Git、日志、数据库和错误响应。
- [ ] 服务仍只监听回环地址。

---

## 9. P2-04：Python 基准脚本纳入 Ruff

### 9.1 问题

当前质量门禁执行：

```powershell
python -m ruff check app tests
```

`ai-service/bench_detect.py` 和 `ai-service/bench_four_streams.py` 未纳入检查，已有 5 条 Ruff 问题。

### 9.2 方案

1. 修复两个基准脚本中的 Ruff 问题。
2. 将质量门禁改为扫描完整 Python 项目，例如在 `ai-service` 目录执行：

```powershell
python -m ruff check .
```

3. 保持 `.venv` 排除，确保生成目录不被扫描。
4. 如果基准脚本需要不同规则，使用 `per-file-ignores` 精确限定，不整体跳过文件。
5. 同步确认 `bandit` 是否需要覆盖基准脚本。

### 9.3 验证

```powershell
Push-Location ai-service
try {
    .\.venv\Scripts\python.exe -m ruff check .
    .\.venv\Scripts\python.exe -m pytest tests -q
} finally {
    Pop-Location
}
```

### 9.4 完成条件

- [ ] 基准脚本 Ruff 问题清零。
- [ ] 质量门禁扫描完整 Python 项目。
- [ ] Python 测试和覆盖率不回退。

---

## 10. 实施顺序

### 第 0 步：建立干净基线

1. 审阅当前 18 个工作区改动。
2. 跑完整质量门禁。
3. 提交当前后端加固改动。
4. 确认 `main` 与 `origin/main` 状态，确认标签未变化。

### 第 1 步：低风险门禁修复

1. 修复 Python 基准脚本 Ruff 问题。
2. 扩大 Ruff 扫描范围。
3. 增加服务间 token 配置、传递和鉴权测试。
4. 更新启动脚本、文档和 HTTP 审计。

### 第 2 步：概览查询限流

1. 定义配置上限和 OpenAPI 元数据。
2. 修改 Store/Service 查询。
3. 更新前端截断提示。
4. 增加大数据集性能测试。

### 第 3 步：上传可恢复一致性

1. 增加媒体状态 migration。
2. 改为流式写入、哈希和原子移动。
3. 增加补偿任务。
4. 增加失败注入、崩溃恢复和并发测试。
5. 更新 HTTP 审计和文档。

### 第 4 步：Java 平台迁移

1. Java 17。
2. Spring Boot 3。
3. Jakarta  import 迁移。
4. 依赖、Flyway/H2、OSV 基线清理。
5. 全量 W01-W12、HTTP、浏览器和干净环境验证。

### 第 5 步：真实模型验收

1. 配置真实模型。
2. 运行 smoke investigation。
3. 运行 30+20 固定任务。
4. 生成真实模型验收报告。
5. 只在实测通过后关闭 P0。

模型接入保持最后做，与用户当前决定一致。

---

## 11. 推荐提交拆分

以下提交只描述未来实施计划，不代表已经执行：

```text
fix: include AI benchmarks in Python lint gate
feat: authenticate platform-to-ai service calls
fix: bound overview aggregation queries
fix: make media uploads recoverable
build: migrate platform to Java 17 and Spring Boot 3
test: add real model acceptance gate
docs: record real model acceptance results
```

要求：

- 每个提交可独立构建和测试；
- 当前未提交的加固改动先形成基线；
- Java 迁移至少拆成“Java 17”和“Spring Boot 3/Jakarta”两个提交；
- 不修改已有 `w01`-`w12` 标签；
- 不在提交中包含本地数据库、媒体临时文件、密钥或私有资料。

---

## 12. 统一验收清单

### 必须通过

- [ ] `git diff --check` 无错误。
- [ ] Java 全部测试通过，覆盖率不低于现有 90.39% 行覆盖率基线，或对下降有明确理由和补测。
- [ ] Python 全部测试通过，覆盖率不低于现有 80.46% 基线。
- [ ] `scripts\quality-gate.ps1` 通过。
- [ ] `scripts\http-audit.ps1` 通过。
- [ ] `scripts\browser-audit.ps1` 通过。
- [ ] `run_eval.bat` 通过。
- [ ] SBOM、OSV、Ruff、Bandit、pip-audit 通过。
- [ ] 空库迁移和旧库升级通过。
- [ ] 生产/验收模式的服务间调用无 token 时失败。
- [ ] `/api/overview` 返回量受配置上限约束。
- [ ] 上传失败、崩溃和重启后不暴露半成品。
- [ ] 真实模型未配置时明确保持 P0 未关闭，不伪造结果。

### 仓库与边界

- [ ] 未提交真实模型 API Key、服务间 token、交底书或私有资料。
- [ ] 未提交大文件、第三方模型权重或本地运行数据。
- [ ] 未修改硬件边界，未声称未实测能力。
- [ ] `w01`-`w12` 标签未改动。

---

## 13. 风险与回滚

| 变更 | 主要风险 | 缓解措施 | 回滚方式 |
| --- | --- | --- | --- |
| 服务间 token | 启动脚本漏传导致服务不可用 | 随机生成、双服务同步、启动前就绪检查 | 回退相关提交；保留回环绑定 |
| Overview 限流 | 前端误以为数据完整 | `overviewMeta.truncated` 提示、明细分页、契约测试 | 回退查询和前端变更；恢复旧契约 |
| 上传状态机 | 状态残留或误清理 | 状态机、宽限期、幂等任务、失败注入 | 回退 migration 和代码；保留备份文件 |
| Java 17 / Boot 3 | 依赖/API/数据迁移回归 | 分阶段提交、旧库副本、全量回归 | Git 回退；恢复迁移前 H2 备份 |
| 真实模型接入 | 供应商不稳定、成本、密钥泄漏 | 独立验收、环境变量、脱敏日志、配额控制 | 关闭 `MODEL_API` 验收任务，保持 Mock 可用 |

任何不可逆操作前先备份。迁移和清理任务不得以媒体根目录、用户主目录或工作区根目录作为递归删除目标。

---

## 14. 最终判断

当前后端不需要推倒重写。缺陷集中在生命周期末端的依赖栈、跨文件系统一致性、无界聚合和服务间信任边界。

推荐先建立干净提交基线，再依次完成：

1. Ruff 门禁和服务间鉴权；
2. Overview 查询上限；
3. 上传可恢复一致性；
4. Java 17 / Spring Boot 3 迁移；
5. 真实模型验收。

完成这些工作后，TraceWitness 仍应定位为“可复现的纯软件视频事件调查原型”。除非真实模型固定任务集和指标已经实测，否则不得写成“真实 LLM / Agent 已验证”。
