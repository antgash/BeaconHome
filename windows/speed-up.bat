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
rem -- shorter animations and no Google TV recommendation service (backed up first) --
if not exist "%B%\anim-window.txt" "%ADB%" -s %IP% shell settings get global window_animation_scale > "%B%\anim-window.txt"
if not exist "%B%\anim-transition.txt" "%ADB%" -s %IP% shell settings get global transition_animation_scale > "%B%\anim-transition.txt"
if not exist "%B%\anim-animator.txt" "%ADB%" -s %IP% shell settings get global animator_duration_scale > "%B%\anim-animator.txt"
"%ADB%" -s %IP% shell settings put global window_animation_scale 0.5
"%ADB%" -s %IP% shell settings put global transition_animation_scale 0.5
"%ADB%" -s %IP% shell settings put global animator_duration_scale 0.5
"%ADB%" -s %IP% shell pm disable-user --user 0 com.google.android.tvrecommendations >nul 2>&1
echo Faster. Undo: undo-speed-up.bat
pause
