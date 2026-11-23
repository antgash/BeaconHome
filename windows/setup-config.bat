@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
echo ============================================
echo   Mantle Home Kit - settings
echo   (saved only on this PC, in config.bat)
echo ============================================
echo.
set "IP="
if exist found-projector.txt type found-projector.txt
echo.
echo Device address, e.g. 192.168.1.20:5555
echo   (Android 11+/Google TV wireless debugging: use the address:port shown
echo    under "Wireless debugging" on the TV, after running pair-device.bat)
set /p IP=Address: 
if "!IP!"=="" ( echo No address entered. & pause & exit /b 1 )
echo !IP! | find ":" >nul || set "IP=!IP!:5555"
set /p NAME=Your first name for the greeting (optional, Enter to skip): 
set /p TMDB_KEY=TMDB API key (optional, Enter to skip): 
set /p LIVETV=Your Live TV addon link (optional, Enter to skip): 
set /p NEXTDNS_ID=NextDNS profile ID (optional, Enter to skip): 
set /p NEXTDNS_KEY=NextDNS API key (optional, Enter to skip): 
if defined LIVETV set "LIVETV=!LIVETV:/manifest.json=!"
(
  echo @echo off
  echo set "IP=!IP!"
  echo set "NAME=!NAME!"
  echo set "TMDB_KEY=!TMDB_KEY!"
  echo set "LIVETV=!LIVETV!"
  echo set "NEXTDNS_ID=!NEXTDNS_ID!"
  echo set "NEXTDNS_KEY=!NEXTDNS_KEY!"
) > config.bat
echo.
echo Saved to config.bat.
timeout /t 3 >nul
