# 模型许可证台账

**核对日期：** 2026-11-10  
**规则：** 不使用 AGPL，也不使用仅限非商业用途的模型。权重和密钥不进入 Git。

## 结论

模型由项目所有者配置 API。仓库不选择模型，不下载权重，也不保存地址或密钥。

下面的记录只说明曾经核对过的许可证，不是当前运行时。

## 台账

| 模型 | 来源 | 许可证 | 权重 SHA-256 | 训练数据来源 | 使用限制 | 可公开演示 |
|---|---|---|---|---|---|---|
| Qwen3-4B Q4_K_M | [Qwen/Qwen3-4B-GGUF](https://huggingface.co/Qwen/Qwen3-4B-GGUF)，文件 `Qwen3-4B-Q4_K_M.gguf` | Apache-2.0。仓库 `LICENSE` 版权声明为 Copyright 2025 Alibaba Cloud | `7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5` | Qwen3 模型卡与技术报告。供应商未公开完整训练语料 | 不是本项目运行时。本地文件如仍在 `models/llm/`，不提交 | 不作为本项目演示路径 |
| Qwen2.5-3B-Instruct GGUF | [Qwen/Qwen2.5-3B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF) | Qwen Research License Agreement，发布日期 2024-09-19。非商业研究或评估；商业使用需另行向 Alibaba Cloud 取得许可 | 未下载 | Qwen2.5 技术报告。供应商未公开完整训练语料 | 禁止作为本项目默认模型，禁止再分发权重 | 否 |
| synthetic-detect.onnx | 本仓库生成的 1×1 亮度卷积，无训练权重 | 随仓库代码，不是第三方权重 | `fca2418eea6507d645a0f845bb5e3de9d7749379692d3175add3c02311d1642c` | 无训练数据 | 只用于第 4 周合成画面的检测通路。换上项目所有者自己的 ONNX 检测模型后不再使用 | 是 |

## 数据边界

- 演示和评测优先使用合成视频。
- 其次才使用已明确授权的公开数据。
- 不使用公司真实监控数据，也不使用真实个人信息。
