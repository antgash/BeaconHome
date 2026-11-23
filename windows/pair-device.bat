@echo off
setlocal
cd /d "%~dp0"
set "ADB=%~dp0platform-tools\adb.exe"
if not exist "%ADB%" ( echo ADB is missing. Run get-adb.bat first. & pause & exit /b 1 )
echo For Android 11+ and Google TV (Mi Box S 2nd gen, Chromecast with Google TV, newer TVs):
echo   On the TV: Settings ^> System ^> About ^> click "Android TV OS build" 7 times
echo   Then: Settings ^> System ^> Developer options ^> Wireless debugging ^> ON
echo   Choose "Pair device with pairing code". Keep that screen open.
echo.
set /p PAIR=Pairing address shown on the TV (IP:port under the code): 
set /p CODE=6-digit pairing code: 
"%ADB%" pair %PAIR% %CODE%
echo.
echo Now go back one screen on the TV. Under "Wireless debugging" it shows
echo "IP address & Port" (a different port from the pairing one).
set /p CONN=That IP address and port: 
"%ADB%" connect %CONN%
"%ADB%" -s %CONN% get-state >nul 2>&1 && (
  echo Connected. Saving the address...
  if exist config.bat ( powershell -NoProfile -Command "(Get-Content config.bat) -replace '^set \"IP=.*\"', 'set \"IP=%CONN%\"' | Set-Content config.bat" ) else ( (echo @echo off& echo set "IP=%CONN%") > config.bat )
  echo Done. Note: Google TV may change this port after a restart - run pair-device.bat again if scripts can't connect.
) || echo Couldn't connect. Check the address and try again.
pause
