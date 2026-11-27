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
"%ADB%" -s %IP% get-state >nul 2>&1 || ( echo Can't reach the device at %IP%. & echo - Is it on and on the same Wi-Fi? & echo - Android 11+ / Google TV ^(Mi Box S 2nd gen, Chromecast^): run pair-device.bat first. & echo - Otherwise run find-device.bat, then setup-config.bat. & pause & exit /b 1 )
rem -- undo install-beacon.bat: original launcher, Home button and helper --
"%ADB%" -s %IP% shell pm enable com.google.android.tvlauncher >nul 2>&1
"%ADB%" -s %IP% shell pm enable com.google.android.apps.tv.launcherx >nul 2>&1
set /p H=<"%B%\home.txt"
if defined H "%ADB%" -s %IP% shell cmd package set-home-activity %H%
set /p S=<"%B%\a11y-services.txt"
set /p E=<"%B%\a11y-enabled.txt"
if "%S%"=="null" ( "%ADB%" -s %IP% shell settings delete secure enabled_accessibility_services ) else ( "%ADB%" -s %IP% shell settings put secure enabled_accessibility_services "%S%" )
if "%E%"=="null" ( "%ADB%" -s %IP% shell settings delete secure accessibility_enabled ) else ( "%ADB%" -s %IP% shell settings put secure accessibility_enabled %E% )
"%ADB%" -s %IP% shell input keyevent KEYCODE_HOME
echo Back on the original home screen. beacon is still installed; run use-beacon-home.bat to switch back.
pause
