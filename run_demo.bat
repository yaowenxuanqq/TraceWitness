@echo off
setlocal
cd /d "%~dp0"

echo [demo] 真实模型未验收。
echo [demo] 需要项目所有者设置 TRACEWITNESS_MODEL_BASE_URL、TRACEWITNESS_MODEL_API_KEY、TRACEWITNESS_MODEL_NAME。
echo [demo] 当前没有这些配置，本脚本不启动服务，也不把结果写成通过。
echo [demo] Mock 演示使用 run_mock.bat。
exit /b 2
