# 部署

本机启动，不需要 Docker、GPU 或真实摄像机。

## 条件

- Windows
- Java 8。脚本不使用指向 Java 21 的 `JAVA_HOME`。可以设置 `JAVA_HOME_8`
- Python 3.13。第一次执行 `run_mock.bat` 时，如果 `ai-service\.venv` 不存在，脚本会创建它并安装 `ai-service\requirements.txt`
- 端口 8080 和 8000 空闲

模型地址和密钥不放进仓库。没有配置时使用 `MOCK_MODE`，页面会标出来。这条结果不计入真实 Agent 指标。

## 启动

在仓库根目录执行：

| 脚本 | 作用 |
| --- | --- |
| `run_mock.bat` | 启动 Python `:8000` 和 Java `:8080`，打开 mock 闭环 |
| `run_eval.bat` | 跑平台测试、Python 测试和检测计时，并写出 `docs/eval-report.md` |
| `run_demo.bat` | 不启动服务，也不调用模型 |

浏览器打开 `http://127.0.0.1:8080/`。Java 只监听 `127.0.0.1`，局域网地址访问不到。

数据库文件在 `data/local/`，媒体在 `data/local/media/`。这两个目录不提交。关掉两个命令行窗口即停止服务。再次启动会读回同一套 run 数据。

## 边界

- 不连接真实摄像头、门禁或电梯
- 插件验签使用仓库内的演示密钥，只用于这条受限演示，不表示生产签名体系
- 检测用仓库里的亮度卷积模型，不下载权重
