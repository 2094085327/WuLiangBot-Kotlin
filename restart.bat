@echo off

echo Stopping Java process...
taskkill /F /IM java.exe

echo Waiting for the process to terminate...
timeout /T 5 /NOBREAK > nul

echo Starting Java process...
start java -jar Tencent-Bot-Kotlin-0.0.1-SNAPSHOT.jar
