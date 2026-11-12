# 环境与启动

**核对日期：** 2026-11-10

## 目标机

- Windows 11
- 16GB 内存
- 无独显
- 非管理员权限
- 不要求 Docker、GPU、在线地图或真实硬件
- 模型使用项目所有者配置的 API。地址和密钥放在未提交的本地配置里

计划里的长期运行时是 Java 21。当前平台暂时按本机已安装的 Java 8 构建，使用 Spring Boot 2.7。前端构建才需要 Node。

## 2026-11-10 本机现状

| 组件 | 现状 | 第 1 周是否阻塞 |
|---|---|---|
| Python | 3.13.15 | 不阻塞 |
| Java | OpenJDK 1.8.0_504，平台当前使用这份本机 Java | 不额外下载 Java 21 |
| Node | v24.20.0 | 不阻塞。前端从第 2 周开始 |
| Git | 2.55.0.windows.5 | 已可用 |

写接口使用 `TRACEWITNESS_API_TOKEN`。不设置时，平台启动后在本机 `GET /api/session` 发放一次性 token。`scripts/http-audit.ps1` 用临时库检查审批、告警、回滚、难例、非法 JSON、404 和重复提交，不写 `data/local/`。

`run_mock.bat` 和 `run_eval.bat` 会寻找 Java 8。本机 `JAVA_HOME` 如果指向 Java 21，脚本改用 `JAVA_HOME_8`，或 `C:\Program Files\Eclipse Adoptium\jdk-8*`。找不到 Java 8 就停止，不用 PATH 上碰巧出现的 `java`。Gradle Wrapper 使用 7.6.4 的华为云镜像，因为 `services.gradle.org` 在这台机器上连接超时。依赖仓库同样先走阿里云，再回退 Maven Central。

直接运行 `platform\gradlew.bat` 时，`JAVA_HOME` 也必须指向 Java 8 到 Java 19。Gradle 7.6.4 不支持 Java 20/21；当前构建会在配置阶段给出明确错误，不再进入“测试类全部 ClassNotFoundException”的误导路径。

## 端口

| 进程 | 端口 |
|---|---|
| Java 平台 | 127.0.0.1:8080 |
| Python AI 服务 | 8000 |

`run_mock.bat` 会启动这两个服务。端口已有进程监听时，脚本停止并提示，避免接到别的程序上。

`run_mock.bat` 和 `run_eval.bat` 在 `ai-service\.venv` 不存在时会创建虚拟环境并安装固定依赖。干净 clone 可以直接执行评测，不依赖本机全局 Python 包。

## 目录

| 路径 | 是否提交 | 内容 |
|---|---|---|
| `models/`、`runtime/` | 否 | 本地权重或推理程序。仓库不使用、不下载 |
| `data/local/`、`logs/` | 否 | 运行数据。媒体文件在 `data/local/media/` |
| `private-docs/` | 否 | 未公开的内部材料 |
| `docs/` | 是 | 可公开的计划、许可证、确定性和环境说明 |

内部材料放在 `private-docs/`。该目录已在 `.gitignore` 中整目录排除。

## 启动方式

在仓库根目录双击，或在命令行执行：

| 脚本 | 作用 |
|---|---|
| `run_demo.bat` | 不启动服务，也不调用模型。真实模型仍由项目所有者配置 |
| `run_mock.bat` | 启动 Python 和 Java，跑通 mock 事件到报告，并标出 `MOCK_MODE` |
| `run_eval.bat` | 跑平台测试、Python 测试和检测计时，并写出 `docs/eval-report.md` |

脚本用 `%~dp0` 定位仓库根目录。失败时停留在 `pause`，避免双击后窗口立刻消失。成功时直接退出，方便以后串进评测。

模型 API 由项目所有者自行配置。
