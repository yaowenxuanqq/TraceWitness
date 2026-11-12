# 评测报告

本文件由 `run_eval.bat` 调用 `scripts/write_eval_report.py` 写出。下面的数字来自同一次命令留下的测试结果和标准输出。

## 平台测试

| 项 | 值 |
| --- | --- |
| 本次测试结果 | 新结果 |
| Gradle 退出码 | 0 |
| pytest 退出码 | 0 |
| 检测计时退出码 | 0 |
| 测试方法 | 39 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| `EvalSuiteWeek11Test` | 通过 |
| `PluginPolicyTest` | 通过 |

## 场景文件

| 目录 | 文件 | 条数 |
| --- | --- | --- |
| positive | `w07.json` | 10 |
| positive | `w08.json` | 5 |
| positive | `w09.json` | 5 |
| positive | `w10.json` | 10 |
| positive | `w11.json` | 5 |
| reverse | `w06.json` | 10 |
| reverse | `w08.json` | 5 |
| reverse | `w09.json` | 5 |

正向文件合计 35 条，反向文件合计 20 条。

口径：

- `EvalSuiteWeek11Test` 通过。其中 20 条正向策略场景（`w08`、`w09`、`w10`）的期望字段与策略输出一致。`w07.json` 的 10 条只检查工具列表非空，这次没有测量 Agent 任务完成率。反向 20 条的期望字段与规则输出一致。
- `PluginPolicyTest` 通过。`w11.json` 的 5 条验签期望与 `PluginPolicy` 一致。同一测试断言 1000 次验签在 2000 毫秒内完成；精确耗时没有单独打印。
- `eval/holdout/` 里有 1 个场景文件。`HoldoutTest` 通过时，这 10 条按封存场景计入本次平台测试，开发场景文件没有因此改写。
- 一万条向量检索：vectors=10000 dim=3 repeats=15 p50_ms=18 p95_ms=31 target_p95_ms=1000

## Python

```text
.......................                                                  [100%]
23 passed in 2.75s
```

```text
detect_fps_640=180.25 target=5
```

四路检测退出码 0。

```text
streams=4 frames=20 width=1920
fps=21.23,21.03,71.28,19.07
min=19.07 p50=21.13 p95=63.77 target_min=2
```

## 未测项

- 正向 Agent 任务完成率：未测。`w07.json` 没有真正跑调查闭环。
- 演示视频：未录制。预演步骤在 [demo-script.md](demo-script.md)。
- 真实模型调用：未测。这次评测不读取模型地址或密钥。

