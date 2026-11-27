# Using `use-beacon-home.bat`

`use-beacon-home.bat` makes beacon your TV's home screen. Run it to switch back to beacon after using the original Google TV home.

**Where it lives:** the `windows` folder of this repo. Download the repo as a ZIP from GitHub and extract it. Run the script from that folder, because it looks for `platform-tools` and `config.bat` next to itself.

## Before the first run

1. Turn on **Developer options** on the TV, then **USB debugging** or **Network debugging**. To get Developer options, go to Settings → Device Preferences → About and click *Build* seven times.
2. Put the PC and the TV on the same Wi-Fi or network.
3. Install beacon on the TV first.

## Running it

1. Double-click `use-beacon-home.bat` in the `windows` folder.
2. If `platform-tools` is missing, it tells you to run `get-adb.bat` first. Do that, then run this script again.
3. If there's no `config.bat`, it runs `setup-config.bat` for you. That asks for the TV's IP address (Settings → Network on the TV) and saves it, so you only enter it once.
4. If it can't reach the device, it shows a message:
   - On Android 11+ or Google TV (such as the Chromecast), run `pair-device.bat` first.
   - Otherwise run `find-device.bat`, then `setup-config.bat`.
   - Allow the USB or network debugging prompt on the TV if it appears.
5. It then does four things on the TV:
   - sets beacon as the default home app;
   - turns on beacon's Home-button accessibility service;
   - sends a Home key press;
   - prints "beacon is the home screen again."

## Going the other way

`use-original-home.bat` switches back to the Google TV home.

You can also switch from the TV itself, with no PC, in beacon's menu under *System → Home screen*.
