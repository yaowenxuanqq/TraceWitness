@echo off
setlocal
cd /d "%~dp0"
set "REPO=%CD%"

call :use_java8
if errorlevel 1 goto fail

if not exist "%REPO%\build\eval" mkdir "%REPO%\build\eval"
if exist "%REPO%\platform\build\test-results" rmdir /s /q "%REPO%\platform\build\test-results"
del /q "%REPO%\build\eval\pytest.txt" "%REPO%\build\eval\detect-bench.txt" "%REPO%\build\eval\gradle-exit.txt" "%REPO%\build\eval\pytest-exit.txt" "%REPO%\build\eval\bench-exit.txt" 2>nul
> "%REPO%\build\eval\this-run.txt" echo start

echo [eval] platform tests
call "%REPO%\platform\gradlew.bat" -p "%REPO%\platform" clean test
set GRADLE_EXIT=%ERRORLEVEL%
> "%REPO%\build\eval\gradle-exit.txt" echo %GRADLE_EXIT%

set "PY=%REPO%\ai-service\.venv\Scripts\python.exe"
if not exist "%PY%" set "PY=python"

echo [eval] python tests
pushd "%REPO%\ai-service"
"%PY%" -m pytest tests -q > "%REPO%\build\eval\pytest.txt" 2>&1
set PYTEST_EXIT=%ERRORLEVEL%
> "%REPO%\build\eval\pytest-exit.txt" echo %PYTEST_EXIT%

echo [eval] detect bench
"%PY%" bench_detect.py > "%REPO%\build\eval\detect-bench.txt" 2>&1
set BENCH_EXIT=%ERRORLEVEL%
> "%REPO%\build\eval\bench-exit.txt" echo %BENCH_EXIT%
popd

echo [eval] report
"%PY%" "%REPO%\scripts\write_eval_report.py"
set REPORT_EXIT=%ERRORLEVEL%

echo [eval] gradle=%GRADLE_EXIT% pytest=%PYTEST_EXIT% bench=%BENCH_EXIT% report=%REPORT_EXIT%
echo [eval] see docs\eval-report.md
if not "%GRADLE_EXIT%"=="0" goto fail
if not "%PYTEST_EXIT%"=="0" goto fail
if not "%BENCH_EXIT%"=="0" goto fail
if not "%REPORT_EXIT%"=="0" goto fail
exit /b 0

:use_java8
set "JAVA_HOME="
if defined JAVA_HOME_8 set "JAVA_HOME=%JAVA_HOME_8%"
if not defined JAVA_HOME if exist "C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot\bin\java.exe" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"
if not defined JAVA_HOME for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-8*") do if exist "%%D\bin\java.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME for /d %%D in ("C:\Program Files\Java\jdk1.8*") do if exist "%%D\bin\java.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk8*") do if exist "%%D\bin\java.exe" set "JAVA_HOME=%%~fD"
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
echo [eval] failed
if not defined NO_PAUSE pause
exit /b 1
