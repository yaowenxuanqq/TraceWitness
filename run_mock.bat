@echo off
setlocal
cd /d "%~dp0"
set TRACEWITNESS_MODE=MOCK
set MOCK_MODE=1
set "REPO=%CD%"
set "TRACEWITNESS_DB=jdbc:h2:file:%REPO:\=/%/data/local/tracewitness"

echo MOCK_MODE
call :use_java8
if errorlevel 1 goto fail

call :require_free 8080
if errorlevel 1 goto fail
call :require_free 8000
if errorlevel 1 goto fail

if not exist "ai-service\.venv\Scripts\python.exe" (
  py -3.13 -m venv ai-service\.venv
  if errorlevel 1 py -3 -m venv ai-service\.venv
  if errorlevel 1 python -m venv ai-service\.venv
  if errorlevel 1 goto fail
  ai-service\.venv\Scripts\python.exe -m pip install -i https://pypi.tuna.tsinghua.edu.cn/simple -r ai-service\requirements.txt
  if errorlevel 1 ai-service\.venv\Scripts\python.exe -m pip install -r ai-service\requirements.txt
  if errorlevel 1 goto fail
)

start "tracewitness-ai" /D "%REPO%\ai-service" cmd /c ".venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000"
call :wait_url http://127.0.0.1:8000/health
if errorlevel 1 goto fail

start "tracewitness-platform" /D "%REPO%\platform" cmd /c "set JAVA_HOME=%JAVA_HOME%&& set PATH=%JAVA_HOME%\bin;%%PATH%%&& gradlew.bat bootRun"
call :wait_url http://127.0.0.1:8080/api/health
if errorlevel 1 goto fail

echo [mock] Open http://127.0.0.1:8080/
echo [mock] done. This run does not count as an Agent metric.
exit /b 0

:use_java8
set "JAVA_HOME="
if defined JAVA_HOME_8 set "JAVA_HOME=%JAVA_HOME_8%"
if not defined JAVA_HOME if exist "C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot\bin\java.exe" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"
if not defined JAVA_HOME for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-8*") do if exist "%%D\bin\java.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME for /d %%D in ("C:\Program Files\Java\jdk1.8*") do if exist "%%D\bin\java.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME (
  echo [java] Java 8 was not found. Set JAVA_HOME_8 to a JDK 8 directory.
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /C:"1.8.0" >nul
if errorlevel 1 (
  echo [java] %JAVA_HOME% is not Java 8.
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
exit /b 0

:fail
echo [mock] failed
if not defined NO_PAUSE pause
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
