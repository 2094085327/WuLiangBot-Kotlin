@echo off

REM 检查参数数量
if "%~1"=="" (
    echo Usage: %0 ^<jar_file^> [log_file]
    exit /b 1
)

set JAR_FILE=%~1
set LOG_FILE=%~2

echo Stopping Java process...
taskkill /F /IM java.exe

echo Waiting for the process to terminate...
timeout /T 5 /NOBREAK > nul

echo Starting Java process...
if "%LOG_FILE%"=="" (
    start java -jar "%JAR_FILE%"
) else (
    start java -jar "%JAR_FILE%" >> "%LOG_FILE%" 2>&1
)
