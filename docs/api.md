# API

平台进程提供这些 HTTP 接口。业务 ID 和时间由 Java 生成。下面只列当前已经接通的路径。

## 状态

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/api/health` | 进程存活 |
| GET | `/api/mode` | `MOCK_MODE` 或真实模式 |
| GET | `/api/overview` | 首页用的运行、事件、档案、检索、轨迹、证据包和难例 |

## 调查

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/api/events` | 本次 run 的摄像机事件 |
| GET | `/api/events/{id}` | 单条事件 |
| GET | `/api/agent/runs` | Agent 运行 |
| GET | `/api/agent/runs/{id}` | 单次运行 |
| GET | `/api/agent/runs/{id}/tool-calls` | 该次运行的工具调用 |
| GET | `/api/media` | 音频、片段和上传 |

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

