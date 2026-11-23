@echo off
setlocal
cd /d "%~dp0"
set "ADB=%~dp0platform-tools\adb.exe"
if not exist "%ADB%" ( echo ADB is missing. Run get-adb.bat first. & pause & exit /b 1 )
if not exist config.bat ( echo No config yet - running setup-config.bat first. & call setup-config.bat )
set "IP="
call config.bat >nul 2>&1
if "%IP%"=="" ( echo config.bat has no device address. Run setup-config.bat to fix it. & pause & exit /b 1 )
set "B=%~dp0backup"
if not exist "%B%" mkdir "%B%"
"%ADB%" connect %IP% >nul 2>&1
"%ADB%" -s %IP% get-state >nul 2>&1 || ( echo Can't reach the device at %IP%. & echo - Is it on and on the same Wi-Fi? & echo - Android 11+ / Google TV ^(Mi Box S 2nd gen, Chromecast^): run pair-device.bat first. & echo - Otherwise run find-projector.bat, then setup-config.bat. & pause & exit /b 1 )
if "%NEXTDNS_ID%"=="" ( echo Set NEXTDNS_ID in config.bat first. & pause & exit /b 1 )
if not exist "%B%\dns-mode.txt" "%ADB%" -s %IP% shell settings get global private_dns_mode > "%B%\dns-mode.txt"
if not exist "%B%\dns-host.txt" "%ADB%" -s %IP% shell settings get global private_dns_specifier > "%B%\dns-host.txt"
"%ADB%" -s %IP% shell settings put global private_dns_specifier %NEXTDNS_ID%.dns.nextdns.io
"%ADB%" -s %IP% shell settings put global private_dns_mode hostname
echo Ad and tracker blocking is on (NextDNS, encrypted DNS). Undo: ad-block-off.bat
pause
