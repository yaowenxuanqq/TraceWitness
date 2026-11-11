# 契约

第 2 周的可执行工具只有 `search_events`。其余 P0 工具只保留参数形状，平台目录会把它们标成未实现。

Agent 报告形状见 `agent-report.schema.json`。空 `source_ids` 只允许和 `insufficient_evidence: true` 一起出现。业务时间不在这些契约里生成，由 Java 调用时传入。
