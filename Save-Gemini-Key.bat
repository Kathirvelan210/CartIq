@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0configuration\Start-CartIqGemini.ps1" -SaveKeyOnly
if errorlevel 1 (
    pause
    exit /b 1
)
