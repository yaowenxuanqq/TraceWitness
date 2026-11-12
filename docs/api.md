# API

平台进程提供这些 HTTP 接口。业务 ID 和时间由 Java 生成。下面只列当前已经接通的路径。

机器可校验的完整契约在 [../contracts/http-api.yaml](../contracts/http-api.yaml)。Java 测试使用 OpenAPI 解析器检查全部路由、必填响应、路径参数和写接口 token scheme。

写接口只接受契约里的字段。多出来的字段、缺必填字段、类型不对或 JSON 不完整，返回 400。记录不属于当前 run，或 ID 不存在，返回 404。同一条记录重复提交，返回 409。

本机写操作需要 token。设置 `TRACEWITNESS_API_TOKEN` 可固定它；不设置时进程自己生成。浏览器打开首页后，`GET /api/session` 只在本机回环地址返回 token，页面把它放进 `X-Trace-Token`。同源的 `tw_token` cookie 同样有效。没有 token 的写请求返回 401。读接口不需要 token。

## 状态

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/api/health` | 进程存活 |
| GET | `/api/mode` | `MOCK_MODE` 或真实模式 |
| GET | `/api/overview` | 首页用的运行、事件、档案、检索、轨迹、证据包、插件和难例 |

## 调查

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/api/events` | 本次 run 的摄像机事件 |
| GET | `/api/events/{id}` | 单条事件 |
| GET | `/api/agent/runs` | Agent 运行 |
| GET | `/api/agent/runs/{id}` | 单次运行 |
| GET | `/api/agent/runs/{id}/tool-calls` | 该次运行的工具调用 |
| GET | `/api/media` | 音频、片段和上传 |

## 媒体上传

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/api/media/uploads` | 上传本机测试音视频。表单字段名是 `file` |

服务端只接受常见的 `audio/*` 和 `video/*` 容器。声明的 MIME 类型、文件名扩展名和文件头必须一致。`text/html`、`image/svg+xml`、可执行文件和无法识别的文件头返回 400。所有响应带 `X-Content-Type-Options: nosniff`，并带同源内容安全策略，避免上传文件被当作同源页面执行。

## 档案

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/api/profiles/tickets/{id}/decision` | 批准或驳回合并。正文是 `decision` 为 `approve` 或 `reject`，`actor` 为操作者 |

不可达或旅行时间冲突的工单不能通过这个接口改成合并。

## 检索与告警

检索结果在 `/api/overview` 的 `queries` 里。每条命中有 `explanation`。剔除项单独列在 `exclusions`。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/api/alerts/{id}/decision` | 确认或驳回非机动车告警。正文同上 |

确认告警不会把 `executed` 改成 true。出楼方向没有待确认告警。

## 轨迹与证据

`/api/overview` 的 `trajectories` 带每个门点的保留、抽稀和剔除原因。`companions` 是 60 秒窗内的同行。`packages` 里 `valid` 的包有 `digest`。`hardCases` 初始为待标注。`compares` 只有分数对比，`trained` 为 false。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/api/evidence/{id}/verify` | 校验证据包。正文可带 `manifest`；与库存清单不一致则 `valid` 为 false |
| POST | `/api/hard-cases/{id}/label` | 标注难例。`label` 为 `true_positive`、`true_negative`、`false_positive` 或 `false_negative` |
| GET | `/api/hard-cases/export` | 导出已标注难例。未标注的不出现 |

缺文件的证据包校验结果是 `evidence_missing`。已标注的难例不能再次标注。

## 插件

`/api/overview` 的 `plugins` 带槽位、验签原因和健康分。`rollbacks` 是已经发生的回滚。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/api/plugins/{id}/fault` | 对在用插件做签名故障注入，并回到上一版 |
| POST | `/api/plugins/rollback` | 人工回滚。正文是 `actor` |

签名失败的包 `loaded` 为 false，`hashChecked` 也为 false。不是在用槽位时，故障注入返回 409。没有上一版时，回滚返回 409。

