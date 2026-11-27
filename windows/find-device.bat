@echo off
cd /d "%~dp0"
echo Looking for Android TV devices with network debugging on (port 5555)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$found=@(); foreach ($a in (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' })) { $p=$a.IPAddress.Split('.'); $base=$p[0]+'.'+$p[1]+'.'+$p[2]+'.'; $t=@{}; 1..254 | ForEach-Object { $c=New-Object System.Net.Sockets.TcpClient; $t[$base+$_]=@($c,$c.ConnectAsync($base+$_,5555)) }; Start-Sleep -Milliseconds 1500; foreach ($k in $t.Keys) { if ($t[$k][1].Status -eq 'RanToCompletion' -and $t[$k][0].Connected) { $found+=$k }; $t[$k][0].Close() } }; if ($found) { 'Found: ' + ($found -join ', ') + '   -> now run setup-config.bat and enter <address>:5555' } else { 'Nothing found. Turn on network debugging on the device (Settings > Device Preferences > About > tap Build 7 times, then Developer options > Network debugging). Android 11+ / Google TV (Mi Box S 2nd gen, Chromecast) has Wireless debugging instead - run pair-device.bat.' }" > found-device.txt
type found-device.txt
pause
