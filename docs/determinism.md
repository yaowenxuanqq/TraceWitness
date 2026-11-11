# 确定性设计

**版本：** 第 1 周  
**范围：** 固定时间、种子和 ID。业务服务从第 2 周起按本文实现。

评测模式的权威时钟在 Java。Python 只使用调用方注入的时间，不自己生成业务时间。

## 1. 时钟

`clock_mode` 只有两种：

| 模式 | 当前业务时间 |
|---|---|
| `EVAL` | `epoch_start + tick * tick_ms` |
| `REAL` | Java `TimeSource` 在每个决策点读取一次墙钟，并立刻写入快照 |

`run_manifest.json` 在评测模式提供：

```json
{
  "run_id": "run-w01-tool-select-20261110",
  "scenario_id": "w01-tool-select",
  "clock_mode": "EVAL",
  "epoch_start": "2026-01-01T00:00:00.000Z",
  "tick_ms": 1000,
  "tick": 0,
  "master_seed": 20261110
}
```

时间用 UTC，格式 `yyyy-MM-ddTHH:mm:ss.SSSZ`。

Java 调用 Python 时必须传入：

- `run_id`
- `clock_mode`
- `request_timestamp`
- `tick`（仅 `EVAL`）

Python 可以返回 `diag_elapsed_ms`，它只表示进程内耗时，不能写入事件时间、轨迹时间或决策时间。业务时间由 Java 落库。

同一 `run_id` 的事件、轨迹、Agent 超时和后续插件健康检查使用这一套时钟。

## 2. 种子

`master_seed` 是十进制无符号整数。评测默认值是 `20261110`。

子系统种子：

```text
seed_u32(subsystem) = uint32_be(SHA256(utf8("{master_seed}:{subsystem}")) 的前 4 字节)
```

第 1 周使用的子系统名称：

| 子系统 | 用途 |
|---|---|
| `simulator` | 设备、遥测、门禁事件 |
| `detector` | 检测采样 |
| `tracker` | 跟踪 |
| `sampler` | 属性优选帧 |
| `llm` | 模型采样种子 |

`master_seed=20261110` 时，`llm` 种子是 `3169518213`。

模型调用同时固定：

- `temperature=0`
- 同一份 system prompt
- 同一份工具 JSON Schema
- 所配置模型的名称和版本

具体模型、地址和密钥由项目所有者配置，不进入仓库。

真实网络延迟、云端响应、模型措辞和墙钟抖动不要求逐字一致。业务决策只读取清单里的固定输入和已落库快照。

## 3. ID

业务 ID 不使用随机 UUID。

```text
tw.{entity}.{run_id}.{seq:06d}
```

- `entity`：`event`、`evidence`、`device`、`site`、`track`、`step`、`tool`、`agent`、`profile`、`trajectory`、`artifact`、`audit`
- `run_id`：只包含小写字母、数字和连字符
- `seq`：该 `run_id` 内从 `000001` 递增的计数器，由 Java 分配

`outbox` 使用数据库自增主键，不分配业务 ID。

评测 `run_id`：

```text
run-{scenario_id}-{master_seed}
```

`scenario_id` 同样只包含小写字母、数字和连字符。

真实模式 `run_id`：

```text
run-{yyyyMMdd'T'HHmmssSSS}
```

时间来自 `TimeSource`。同一毫秒内的下一次运行在末尾追加 `-2`、`-3`。

示例：`tw.tool.run-w01-tool-select-20261110.000001`

Agent 只能引用本次 `run_id` 中工具实际返回过的 ID。空引用只允许标记为证据不足。

## 4. 精度

中间计算保留至少 4 位小数，落库保留 2 位。距离、速度和相似度使用同一套舍入规则，单测容差是 ±0.005。第 2 周落地对应工具类。
