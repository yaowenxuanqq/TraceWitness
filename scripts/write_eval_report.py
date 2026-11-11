"""Write docs/eval-report.md and docs/resume.md from this run's outputs."""

import json
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "platform" / "build" / "test-results" / "test"
NOTES = ROOT / "build" / "eval"
DOCS = ROOT / "docs"


def read_text(path):
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8", errors="replace").strip()


def scenario_counts():
    rows = []
    for folder in ("positive", "reverse"):
        directory = ROOT / "eval" / folder
        if not directory.exists():
            continue
        for path in sorted(directory.glob("*.json")):
            cases = json.loads(path.read_text(encoding="utf-8"))
            rows.append((folder, path.name, len(cases)))
    holdout = ROOT / "eval" / "holdout"
    holdout_count = len(list(holdout.glob("*.json"))) if holdout.exists() else 0
    return rows, holdout_count


def junit_totals():
    suites = []
    if not RESULTS.exists():
        return suites
    for path in sorted(RESULTS.glob("TEST-*.xml")):
        root = ET.parse(path).getroot()
        suites.append({
            "name": root.attrib.get("name", path.stem),
            "tests": int(root.attrib.get("tests", "0")),
            "failures": int(root.attrib.get("failures", "0")),
            "errors": int(root.attrib.get("errors", "0")),
            "skipped": int(root.attrib.get("skipped", "0")),
        })
    return suites


def suite_passed(suites, suffix):
    for suite in suites:
        if suite["name"].endswith(suffix):
            return suite["failures"] == 0 and suite["errors"] == 0 and suite["tests"] > 0
    return False


def fresh_xml():
    stamp = NOTES / "this-run.txt"
    if not stamp.exists() or not RESULTS.exists():
        return False
    stamp_time = stamp.stat().st_mtime
    xmls = list(RESULTS.glob("TEST-*.xml"))
    if not xmls:
        return False
    return all(path.stat().st_mtime + 1 >= stamp_time for path in xmls)


def main():
    rows, holdout_count = scenario_counts()
    current = fresh_xml()
    suites = junit_totals() if current else []
    tests = sum(item["tests"] for item in suites)
    failures = sum(item["failures"] for item in suites)
    errors = sum(item["errors"] for item in suites)
    skipped = sum(item["skipped"] for item in suites)
    gradle_exit = read_text(NOTES / "gradle-exit.txt") or "missing"
    pytest_exit = read_text(NOTES / "pytest-exit.txt") or "missing"
    bench_exit = read_text(NOTES / "bench-exit.txt") or "missing"
    pytest_text = read_text(NOTES / "pytest.txt") or "pytest did not run"
    bench_text = read_text(NOTES / "detect-bench.txt") or "detect bench did not run"
    four_exit = read_text(NOTES / "four-exit.txt") or "missing"
    four_text = read_text(NOTES / "four-streams.txt") or "four-stream bench did not run"
    vector_text = read_text(NOTES / "vector-bench.txt") or "vector bench did not run"
    eval_passed = current and suite_passed(suites, "EvalSuiteWeek11Test")
    plugin_passed = current and suite_passed(suites, "PluginPolicyTest")

    positive = sum(count for folder, _name, count in rows if folder == "positive")
    reverse = sum(count for folder, _name, count in rows if folder == "reverse")
    scored_positive = sum(count for folder, name, count in rows if folder == "positive" and name != "w07.json" and name != "w11.json")
    agent_list_only = sum(count for folder, name, count in rows if name == "w07.json")
    plugin_cases = sum(count for folder, name, count in rows if name == "w11.json")

    lines = [
        "# 评测报告",
        "",
        "本文件由 `run_eval.bat` 调用 `scripts/write_eval_report.py` 写出。下面的数字来自同一次命令留下的测试结果和标准输出。",
        "",
        "## 平台测试",
        "",
        "| 项 | 值 |",
        "| --- | --- |",
        "| 本次测试结果 | %s |" % ("新结果" if current else "没有新的 JUnit 结果，不采用旧文件"),
        "| Gradle 退出码 | %s |" % gradle_exit,
        "| pytest 退出码 | %s |" % pytest_exit,
        "| 检测计时退出码 | %s |" % bench_exit,
        "| 测试方法 | %d |" % tests,
        "| 失败 | %d |" % failures,
        "| 错误 | %d |" % errors,
        "| 跳过 | %d |" % skipped,
        "| `EvalSuiteWeek11Test` | %s |" % ("通过" if eval_passed else "未通过或没有结果"),
        "| `PluginPolicyTest` | %s |" % ("通过" if plugin_passed else "未通过或没有结果"),
        "",
        "## 场景文件",
        "",
        "| 目录 | 文件 | 条数 |",
        "| --- | --- | --- |",
    ]
    for folder, name, count in rows:
        lines.append("| %s | `%s` | %d |" % (folder, name, count))
    lines.extend([
        "",
        "正向文件合计 %d 条，反向文件合计 %d 条。" % (positive, reverse),
        "",
        "口径：",
        "",
    ])
    if eval_passed:
        lines.append("- `EvalSuiteWeek11Test` 通过。其中 %d 条正向策略场景（`w08`、`w09`、`w10`）的期望字段与策略输出一致。`w07.json` 的 %d 条只检查工具列表非空，这次没有测量 Agent 任务完成率。反向 %d 条的期望字段与规则输出一致。" % (scored_positive, agent_list_only, reverse))
    else:
        lines.append("- `EvalSuiteWeek11Test` 没有通过。不把场景一致率写成通过。")
    if plugin_passed:
        lines.append("- `PluginPolicyTest` 通过。`w11.json` 的 %d 条验签期望与 `PluginPolicy` 一致。同一测试断言 1000 次验签在 2000 毫秒内完成；精确耗时没有单独打印。" % plugin_cases)
    else:
        lines.append("- `PluginPolicyTest` 没有通过。不把插件验签写成通过。")
    lines.extend([
        "- `eval/holdout/` 里有 %d 个场景文件。`HoldoutTest` 通过时，这 10 条按封存场景计入本次平台测试，开发场景文件没有因此改写。" % holdout_count,
        "- 一万条向量检索：" + vector_text,
        "",
        "## Python",
        "",
        "```text",
        pytest_text,
        "```",
        "",
        "```text",
        bench_text,
        "```",
        "",
        "四路检测退出码 %s。" % four_exit,
        "",
        "```text",
        four_text,
        "```",
        "",
        "## 未测项",
        "",
        "- 正向 Agent 任务完成率：未测。`w07.json` 没有真正跑调查闭环。",
        "- 演示视频：未录制。预演步骤在 [demo-script.md](demo-script.md)。",
        "- 真实模型调用：未测。这次评测不读取模型地址或密钥。",
        "",
    ])
    report = "\n".join(lines) + "\n"
    DOCS.mkdir(exist_ok=True)
    (DOCS / "eval-report.md").write_text(report, encoding="utf-8")

    if current and eval_passed and failures == 0 and errors == 0 and gradle_exit == "0" and pytest_exit == "0" and bench_exit == "0" and four_exit == "0" and "p95_ms=" in vector_text:
        resume_fact = "平台测试 %d 个方法全部通过。策略正向场景 %d 条与期望一致，反向断言 %d 条与期望一致。10 条调查任务只核对了工具列表，Agent 任务完成率未测。插件验签场景 %d 条与期望一致。" % (
            tests, scored_positive, reverse, plugin_cases)
    else:
        resume_fact = "本次评测没有形成全通过结果。Gradle 退出码 %s，失败 %d，错误 %d。简历不要引用完成率。" % (
            gradle_exit, failures, errors)
    resume = "\n".join([
        "# 简历描述",
        "",
        "下面这句只复述同一次 `run_eval.bat` 写进 [eval-report.md](eval-report.md) 的结果。",
        "",
        "> 独立实现视频事件调查平台 TraceWitness，Java 平台负责确定性业务决策、人员档案、检索轨迹、证据校验和插件验签，Python 服务负责检测适配和工具选择。调查结论只引用本次运行里工具返回的来源。高风险动作保持人工确认。",
        "",
        "> 实测：" + resume_fact,
        "",
    ])
    (DOCS / "resume.md").write_text(resume, encoding="utf-8")
    print("wrote docs/eval-report.md")
    print("wrote docs/resume.md")
    if not current or gradle_exit != "0" or pytest_exit != "0" or bench_exit != "0" or four_exit != "0" or "p95_ms=" not in vector_text or failures or errors:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
