package com.beacon.home;

/**
 * beacon's major revisions, newest first, for the changelog in Menu > Updates & downloads. Each has a one-line
 * summary (shown on the row) and its main changes (shown when the row is opened), with an optional picture from
 * assets/changelog/. Add new revisions at the top.
 */
final class Changelog {
    static final class Entry {
        final String title, date, summary, image;
        final int[] icons;
        final String[] changes;

        Entry(String title, String date, String summary, String image, Object... iconTextPairs) {
            this.title = title;
            this.date = date;
            this.summary = summary;
            this.image = image;
            icons = new int[iconTextPairs.length / 2];
            changes = new String[iconTextPairs.length / 2];
            for (int i = 0; i < icons.length; i++) {
                icons[i] = (Integer) iconTextPairs[i * 2];
                changes[i] = (String) iconTextPairs[i * 2 + 1];
            }
        }
    }

    static final Entry[] ENTRIES = {
            new Entry("Streaming pages", "Nov 27", "Simpler service pages with genres", null,
                    Icon.PLAY, "Every service shows Latest, Top and Popular, for movies and for series",
                    Icon.G_SCIFI, "A row of genre tiles under them, from Action to Mystery",
                    Icon.GRID4, "Each genre opens a page of that genre\u2019s movies and series on the service",
                    Icon.SLIDERS, "Movie and series rows are paired: Latest movies, then Latest series, and so on"),
            new Entry("New look", "Nov 27", "A lighthouse icon and banner", "asset:changelog/beacon-lighthouse.jpg",
                    Icon.LIGHTHOUSE, "New lighthouse icon and app banner, also on the screensaver",
                    Icon.GEAR, "Menu descriptions now list what each page contains"),
            new Entry("Nuvio Beacon", "Nov 26", "Switch Nuvio profiles from Home", "asset:changelog/nuvio-beacon.jpg",
                    Icon.NUVIO, "Nuvio Beacon: a build of Nuvio made for beacon, downloadable from this page",
                    Icon.EYE, "Profile button at the top right shows who's watching; select it to switch profiles",
                    Icon.PLAY, "Continue watching is named after the profile, like “Anthony’s continue watching”",
                    Icon.NEXT, "Switching profiles updates Continue watching straight away",
                    Icon.CHECK, "Titles open in Nuvio Beacon without asking for a profile again"),
            new Entry("Surfaces", "Nov 26", "Menus match your surface style", null,
                    Icon.SLIDERS, "Menus are solid and take their colours from Frosted glass, Smoke or Slate",
                    Icon.ORBIT, "Changing the surface restyles the open menu instantly",
                    Icon.APPS, "Larger round icons for your apps"),
            new Entry("beacon", "Nov 25", "A new name, icon and app of its own", "asset:changelog/beacon.jpg",
                    Icon.BEACON, "Renamed to beacon, with the pulse-rings icon and banner",
                    Icon.APPS, "Installs next to Horizon instead of replacing it",
                    Icon.CHECK, "Built-in updater: checks GitHub, downloads and installs new builds",
                    Icon.GEAR, "Every setting lives in the beacon menu"),
            new Entry("Sound & screensaver", "Nov", "Navigation sounds and an ambient screensaver", null,
                    Icon.SPEAKER, "Soft navigation sounds with an on/off switch and 8-step volume",
                    Icon.MOON, "Artwork screensaver for Home and for Android TV, with clock and weather",
                    Icon.PLAY, "Select a title on the screensaver to open it in Nuvio"),
            new Entry("Home screen", "Nov", "Card styles, title art and focus glow", null,
                    Icon.GRID4, "Continue watching as pills, progress rings or poster cards",
                    Icon.EYE, "Title artwork replaces plain-text titles when it can be found",
                    Icon.ORBIT, "Focus glow in your theme colour",
                    Icon.PIN, "Pin, rearrange and hide apps and streaming tiles",
                    Icon.NUVIO, "Choose which Nuvio app beacon uses"),
            new Entry("Live TV", "Nov", "A TiviMate-style guide", null,
                    Icon.WALL, "Live preview, with filters by country, category, language and source",
                    Icon.PLAY, "Full screen with channel up/down and a see-through guide",
                    Icon.CHECK, "Favourites, and the sharpest version of each stream"),
            new Entry("Horizon roots", "Nov 22", "Where beacon started", null,
                    Icon.WALL, "Started from Horizon Home Kit (MIT): an ad-free Android TV home screen",
                    Icon.SHIELD_OFF, "Ad blocking and the memory pill removed",
                    Icon.SUN, "Centred clock with the weather beside it"),
    };

    private Changelog() {}
}
