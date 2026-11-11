@echo off
setlocal
cd /d "%~dp0"
set TRACEWITNESS_MODE=MOCK
set MOCK_MODE=1
set "REPO=%CD%"
set "TRACEWITNESS_DB=jdbc:h2:file:%REPO:\=/%/data/local/tracewitness"

echo MOCK_MODE
java -version
if errorlevel 1 (
  echo Java was not found on PATH.
  goto fail
)

call :require_free 8080
if errorlevel 1 goto fail
call :require_free 8000
if errorlevel 1 goto fail

if not exist "ai-service\.venv\Scripts\python.exe" (
  py -3.13 -m venv ai-service\.venv
  if errorlevel 1 goto fail
  ai-service\.venv\Scripts\python.exe -m pip install -i https://pypi.tuna.tsinghua.edu.cn/simple -r ai-service\requirements.txt
  if errorlevel 1 goto fail
)

start "tracewitness-ai" /D "%REPO%\ai-service" cmd /c ".venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000"
call :wait_url http://127.0.0.1:8000/health
if errorlevel 1 goto fail

start "tracewitness-platform" /D "%REPO%" cmd /c "platform\gradlew.bat bootRun"
call :wait_url http://127.0.0.1:8080/api/health
if errorlevel 1 goto fail

echo [mock] Open http://127.0.0.1:8080/
echo [mock] done. This run does not count as an Agent metric.
exit /b 0

:fail
echo [mock] failed
pause
exit /b 1

:require_free
netstat -ano | findstr "LISTENING" | findstr ":%1 " >nul
if not errorlevel 1 (
  echo Port %1 is already in use.
  exit /b 1
)
exit /b 0

:wait_url
set /a tries=0
:wait_loop
set /a tries+=1
curl.exe -fsS "%~1" >nul 2>&1
if not errorlevel 1 exit /b 0
if %tries% GEQ 90 (
  echo Timed out waiting for %~1
  exit /b 1
)
ping -n 2 127.0.0.1 >nul
goto wait_loop
