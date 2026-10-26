# Sofa Sports

Live scores, NFL fantasy and a score overlay for Android TV / Google TV.

- **Sports**: NFL, college football, NBA, men's college basketball, MLB, NHL, soccer (Premier League, MLS, Champions League, LaLiga, Serie A, Bundesliga, Ligue 1, Liga MX), UFC, boxing, F1, NASCAR and IndyCar. Pick the leagues you follow; NFL is the default.
- **Scores**: your favorite teams first, then live, upcoming and final games, one league at a time or all together, updated every 30 s while games are live. Close games are flagged. Fight cards and race weekends get their own pages.
- **Game Center**: win probability, box score, play-by-play and DraftKings lines, for every team sport.
- **Fantasy** (NFL): your Sleeper (username only) and ESPN Fantasy leagues, with a live matchup card.
- **Picks** (NFL): track PrizePicks and DraftKings picks you enter on the TV, live from ESPN box scores.
- **Overlay**: a small card over whatever you're watching, with pages you flip through (your matchup, your game, around the league, picks, or a one-line pill), plus an optional news ticker. Size, opacity and position are adjustable, and smart mode pops it up on big moments.
- **Remote buttons**: pick a spare button on your remote to show, hide and flip the overlay without leaving your video (uses an accessibility service that only listens for that button).

Blue is always you (your team, your fantasy side) and red is the opponent.

![Scores](screenshots/step-07a-scores-live.png)

## Install

1. Download the latest APK: https://github.com/gitzero-io/sofasports/releases/latest/download/sofasports.apk
2. On the TV, turn on Developer options and **Network debugging** (Settings → System → About → tap *Android TV OS build* 7 times).
3. From a computer with [adb](https://developer.android.com/tools/releases/platform-tools):

   ```sh
   adb connect <TV_IP>:5555
   adb install -r sofasports.apk
   # allow the score overlay (TVs have no settings screen for this)
   adb shell appops set com.sofasports.tv SYSTEM_ALERT_WINDOW allow
   ```

The app checks for new versions itself and shows a QR code with the download link when one is out.

## Build

Open in Android Studio, or:

```sh
./gradlew assembleDebug test
```

Optional settings go in `local.properties` (never committed): `sentry.dsn`, `sofa.configUrl`, `sofa.versionUrl`, `sofa.feedbackUrl`, and the `release.*` signing keys. The app runs fine without any of them. Debug builds include offline fixtures (Settings → Data source → Fixtures).

`release-assets/config.json` (remote config and kill switch) and `release-assets/version.json` (update check and the in-app "Install update" button, from 0.2.1) are read by the app straight from this repo's `main` branch.

## Notes

Not affiliated with or endorsed by the NFL, NBA, MLB, NHL, NCAA, UFC, Formula 1, NASCAR, IndyCar, any soccer league, ESPN, TheSportsDB, Sleeper, DraftKings or PrizePicks. Data comes from public ESPN, TheSportsDB and Sleeper endpoints, some of them unofficial, and can break without notice. Fonts: Barlow Condensed and Inter (SIL OFL, see `licenses/`).
