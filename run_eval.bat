@echo off
setlocal
cd /d "%~dp0"

echo [eval] Scores start in week 6. Week 2 only has the mock incident run-w02-mock-incident-20261110.
echo master_seed=20261110
echo clock_mode=EVAL
echo epoch_start=2026-01-01T00:00:00.000Z
echo tick_ms=1000
echo run_id=run-w01-tool-select-20261110
exit /b 0
