@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0configuration\Start-CartIqBackend.ps1" -ChangeQuickCommerceKey
if errorlevel 1 (
    pause
    exit /b 1
)
pause
