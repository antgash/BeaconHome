# Horizon Home Kit

An ad-free home screen for Android TV / Google TV / projectors, built around **Nuvio**, with a full **Live TV guide**. It also includes my Nuvio setup, ad blocking and speed tweaks, and a runbook so **Claude sets it all up for you**.

Built on an XGIMI Horizon S Max (Android TV 11, 1.7 GB RAM). It should work on any Android TV 8+ box or projector.

![Home](screenshots/01-home.png)

https://github.com/jyotiprakash101/horizon-home-kit/raw/main/screenshots/horizon-demo.mp4

▶️ **[Watch the demo video](screenshots/horizon-demo.mp4)** (3 min: home, memory, NextDNS, Netflix/Prime pages, TV guide)

| | |
|---|---|
| ![Memory](screenshots/02-memory.png) Free memory + which app uses it | ![NextDNS](screenshots/03-nextdns.png) Ads & trackers blocked today |
| ![Netflix](screenshots/04-netflix-page.png) Netflix page, powered by Nuvio | ![Prime Video](screenshots/05-prime-page.png) Prime Video page |
| ![TV guide](screenshots/06-tv-guide.png) Live TV guide with live preview | ![Countries](screenshots/07-guide-countries.png) Pick a country |
| ![Categories](screenshots/08-guide-categories.png) Category filter | ![Languages](screenshots/09-guide-languages.png) Language filter |
| ![Overlay](screenshots/10-guide-overlay.png) See-through guide while watching | ![Nuvio home](screenshots/11-nuvio-home.png) Nuvio home with the kit's layout |
| ![Nuvio Discover](screenshots/12-nuvio-discover.png) Nuvio Discover collection | ![Nuvio streaming services](screenshots/13-nuvio-streaming-services.png) Nuvio Streaming Services collection |

## What's inside

**Horizon Home** (`launcher/`): a home screen app, about 160 MB in memory.
- **No ads or promoted rows.** It opens on what you were watching, with a big artwork backdrop.
- **Continue watching** from Nuvio, with time left and "done by 10:32 PM".
- **Streaming services** row (Netflix, Prime Video, Apple TV, Hulu, AMC+, Crave…). Each opens a Nuvio-powered page: big title art, then the service's Top 10 and its popular and new movies and series. Picking a title opens it in Nuvio.
- **Your apps** row (Nuvio, YouTube, Prime Video, JioHotstar…) plus All apps.
- **📺 Live TV guide**, TiviMate-style:
  - Big live preview at the top; filter by country, category, language and source.
  - Full screen with channel up/down and a see-through guide.
  - Favourites, and "Open in Nuvio".
  - Picks the sharpest version of each stream (up to 1080p).
- **Top bar:** free memory, with per-app memory and a "free up" button. Also ads blocked today (NextDNS), clock and weather.
- **Settings on the TV itself** (no PC needed): ad blocking, animation speed, Google TV recommendations, power button. Each change has an undo.
- **Switch back to the Google TV home any time** from Horizon's settings on the TV (or `use-original-home.bat` from the PC), and back to Horizon again.
- The remote's **Home button** always brings you to Horizon. The top bar has HDMI input and Wi-Fi shortcuts.

**Live TV addon** (`livetv-addon/`): a Nuvio/Stremio addon you host free on Cloudflare.
- About 15,500 channels in 180 countries; about 7,500 have a TV guide.
- Sources: iptv-org, a validated list, Free-TV, Samsung TV Plus, Pluto TV and Roku Channel.
- Every link is test-played every 3 hours; working and sharpest links come first.
- Rows: On now, Starting soon, Tonight. Each channel page shows its schedule.
- Also gives a playlist + guide link for TiviMate / OTT Navigator.

**Nuvio setup** (`nuvio/`): my addon list in order (no keys), and my Home layout as a template (5 collections, 58 folders: Discover, Streaming Services with Netflix/Prime/Apple TV/Hulu…, Genres, Anime, Formats). This gives Nuvio's own home the same streaming-service tiles as the screenshots.

**Windows scripts** (`windows/`): install, update, switch back, ad blocking on/off, speed tweaks, XGIMI power button. Every script backs up the original setting first and has an undo.

## Set it up with Claude (easiest)

1. Download this repo (Code → Download ZIP) and unzip it on your Windows PC.
2. Open the Claude desktop app, connect that folder, and say:
   > Set up Horizon Home Kit on my TV using SETUP-WITH-CLAUDE.md
3. Claude asks which parts you want and for the keys only you have:
   - a free TMDB key;
   - optionally a NextDNS profile;
   - a free Cloudflare account for your own Live TV server.

   It then builds and installs everything, step by step, checking the TV screen as it goes.

Your keys only go into `windows/config.bat` on your PC and into your own addon links. Nothing is uploaded anywhere else.

## Set it up yourself

1. TV: enable Developer options → **Network debugging**.
2. `windows/get-adb.bat`, then `find-projector.bat`, then `setup-config.bat` (asks for the address and optional keys, and writes `config.bat` for you).
   - **Android 11+ / Google TV** (Mi Box S 2nd gen, Chromecast with Google TV, newer TVs) have *Wireless debugging* instead of *Network debugging*: run `pair-device.bat` first, it pairs with the code shown on the TV and saves the address.
3. Live TV: follow `livetv-addon/README.md` (a 5-minute Cloudflare deploy). Add the link to Nuvio and to `config.bat`.
4. Download `HorizonHome.apk` from **Releases → latest** into `windows/`, then run `install-horizon.bat`.
5. Nuvio: `nuvio/addons.md` and `nuvio/README.md`.
6. Optional: `ad-block-on.bat`, `speed-up.bat`, `xgimi-power-full-off.bat`.

**Undo:** `use-original-home.bat` puts your old home screen back. `uninstall-horizon.bat` removes the app. Every tweak has an undo script. Keep the `backup` folder the scripts create.

## Building the APK

GitHub Actions builds it on every change (Releases → latest). Locally: `launcher/build.sh` (JDK 17, Android SDK build-tools + platform 30; no Gradle).

`launcher/kit.keystore` is a public signing key so everyone's builds can update each other. Only install APKs from this repo or ones you built yourself.

## Credits

- Channel data: [iptv-org](https://github.com/iptv-org), [Free-TV/IPTV](https://github.com/Free-TV/IPTV), [AdeelWajid/iptv_validated_list](https://github.com/AdeelWajid/iptv_validated_list), Samsung TV Plus / Pluto TV / Roku listings via [i.mjh.nz](https://github.com/matthuisman/i.mjh.nz).
- Guides: [epg.pw](https://epg.pw), epgshare01.
- [Nuvio](https://nuvio.tv), [Nuvio Personalized](https://personalized.virtualpavi.com).
- Font: Manrope (SIL Open Font License).

Not affiliated with XGIMI, Google, Nuvio or any streaming service. The kit hosts no video; it only links to publicly available streams.

## Troubleshooting

- **`'Projector' is not recognized…` / `syntax is incorrect` / `Can't reach the projector at .`** — your `config.bat` lost its formatting (usually from editing it in a chat or a word processor). Delete it and run `setup-config.bat`.
- **Mi Box S 2nd gen / Chromecast with Google TV / other Android 11+ devices** — turn on Developer options (Settings → System → About → click *Android TV OS build* 7 times), then Developer options → *Wireless debugging* → on, and run `pair-device.bat`. The port can change after a restart; just run it again.
- **"Unauthorized"** — accept the *Allow debugging* prompt on the TV (tick *Always allow*).
