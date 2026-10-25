# Sofa Sports v0.1: handoff

All 16 steps are done and committed (on `main`; pushed to https://github.com/gitzero-io/sofasports).
`./gradlew assembleDebug` and `./gradlew test` pass: **91 unit tests, 0 failures**. Lint: 0 errors.
Screenshots from the Android TV emulator are in `/screenshots`.

## ⚠️ Read first: SPEC.md was missing

There was no `SPEC.md` in the project or anywhere nearby on disk. I built v0.1 from the 16-step brief alone.
That means the **Red vs Blue tokens and fonts are my stand-ins**, not your spec:

- Colours: `app/src/main/java/com/sofasports/tv/ui/theme/Color.kt` (every colour in the app comes from here)
- Fonts/sizes: `ui/theme/Type.kt` (Barlow Condensed for scores/headings, Inter for body, both OFL, bundled)
- The rule is implemented everywhere: blue = me / my favorite / my fantasy team (home team when neither side is mine), red = opponent. Live/close badges are amber so red always means "opponent".

Swapping in the real tokens should be a one-file change. Please also check the v0.1 scope against the spec, since I couldn't.

## 1. What works, step by step

1. **Setup.** Single `:app`, package `com.sofasports.tv` (debug: `com.sofasports.tv.debug`), minSdk 26, Kotlin 2.4.21, Compose for TV, Hilt, Ktor, kotlinx.serialization, DataStore, Coil. Leanback launcher + banner. Dark Red vs Blue theme, white 3dp D-pad focus ring.
2. **Fixtures.** Real ESPN JSON (week 6 finals, week 7 schedule, HOU@JAX and LAC@KC summaries) and Sleeper JSON (Sleeper's own documented example league) in `app/src/test/resources/fixtures`. Parsers and tests run against them. Debug builds include them, and **Settings → Data source → Fixtures** runs every screen offline: two week-6 games replay play-by-play as if live (HOU@JAX starts late in Q3 so the close-game badge appears), and week 7 is shifted so the first kickoff is 3 minutes away.
3. **Providers.** `ScoresProvider`, `GameStatsProvider`, `FantasyProvider` with ESPN, Sleeper and fixture implementations behind a debug switch. Release builds are always live.
4. **Remote config.** `config.json` is fetched hourly and cached. Every field has a safe default, bad values are clamped, broken JSON is ignored, there's a kill switch (optionally for old versions only), feature flags, and ESPN/Sleeper base URLs can be overridden. Sample: `release-assets/config.json`.
5. **Overlay.** A foreground service draws a corner card over other apps. On/off and corner are in Settings, with on-screen adb grant steps when permission is missing. It hides while Sofa Sports itself is open and comes back after the app restarts. Verified over the Google TV launcher on the emulator.
6. **Favorites picker.** On first run, all 32 teams by division, up to 4, in pick order. You can skip it and edit it later in Settings.
7. **Scores.** Favorites first, then Live, Upcoming, Final. Close-game badge (live, Q4 or later, within 8 points; OT always), possession dot, last play. Polls every 30 s while anything is live, otherwise sleeps until the next kickoff (max 5 min). Keeps the last good data when offline.
8. **Game Center.** Header with big scores. Summary tab: win-probability label (leader's %, capped 1–99% while live) and a chart (blue above 50% = me), last play, scoring plays, venue. Box score tab: team stats and passing/rushing/receiving. Play-by-play tab: drives newest first. Polls while live. Has a "Pin to overlay" button.
9. **Sleeper.** Link by username on Home or in Settings (no password). Shows all of the user's NFL leagues for the season with a switcher, and a live matchup card (me blue vs opponent red, starters with points). Refreshes every 30 s while NFL games are live.
10. **Overlay wiring.** The overlay shows the selected league's matchup plus the pinned game. If nothing is pinned, it falls back to your top favorite's game when it's live, today, or just finished.
11. **Countdown and pulse.** "in 5h 07m" / "in 42:05" on cards, the Game Center header and the overlay (ticks every second in the last hour). Score changes pulse (scale + flash in the scoring side's colour) on cards, the header, matchup totals and the overlay. The pulse was caught live in `screenshots/step-11-score-pulse-in-overlay.png`.
12. **Settings.** Sleeper link/unlink, favorites edit, overlay on/off + corner + unpin, usage-events opt-out, feedback QR, version, not-affiliated notice, and the debug data-source toggle.
13. **Update check.** Fetches `version.json` (sample in `release-assets/`) at most every 12 h, plus a manual button in Settings. A newer build shows a Home banner with a QR code and the APK URL. "Required" when your build is below `minSupportedVersionCode`. It never downloads or installs anything.
14. **Sentry.** Only initialised when `sentry.dsn` is set. With no DSN the app runs normally (verified on the emulator: logcat says "crash reporting off").
15. **Usage events.** A closed set of anonymous events (no usernames or ids possible), a `UsageEvents` interface and a logcat sink (`adb logcat -s SofaUsage`). Respects the opt-out and the remote flag.
16. **Release build.** R8 enabled with keep rules. Unsigned by default; signing is opt-in through local.properties. I checked that an R8 release copy (signed in a scratch folder with the existing debug keystore, then uninstalled) runs live ESPN and Sleeper on the emulator. Debug APK: `release-assets/sofasports-0.1.0-debug.apk` (git-ignored).

Every judgment call is in `DECISIONS.md`; per-step status is in `PROGRESS.md`.

## Added after v0.1 (your requests)

- **Sleeper is optional**: "Skip for now" on Home; link any time in Settings. Text boxes only open the keyboard when you press OK, so the remote can't get stuck in them.
- **ESPN Fantasy** (Settings → ESPN Fantasy → Add, or from Home): public leagues need only the league ID; private leagues also need the `espn_s2` and `SWID` cookies (instructions on screen; paste them with the Google TV phone app's keyboard). Then pick your team. ESPN and Sleeper leagues share one switcher and both feed the overlay. Uses ESPN's unofficial fantasy API.
- **DraftKings lines**: spread / total / moneyline from DraftKings (ESPN's odds provider) on game cards, in Game Center (with live "covering / already over" status) and in the overlay. Toggle: Settings → DraftKings lines.
- **Picks tab (PrizePicks + DraftKings)**: add picks on the TV (book → bet type → team → player → stat → line → over/under). Props, spreads, totals and moneylines are tracked live from ESPN box scores on the Picks tab, on Home and in the overlay ("PICKS 2/3 HITTING").
- **Not built: logging in to DraftKings or PrizePicks.** Neither has a public API for your bets/entries; importing them would mean storing your sportsbook password and calling their private endpoints (against their terms, and DraftKings holds real money). Picks are entered by hand instead.
- **News ticker**: a crawl along the bottom of every app screen with ESPN headlines, scoring plays (detected between 30 s score polls, e.g. "TD HOU · Stroud pass … · HOU 24–14 JAX") and live scores. Anything about your favorite teams, your fantasy starters or your picks goes first, marked ★. Over video, Settings → News ticker → Over video: Off / Corner card line / Bottom bar / Both (default: Corner card line; needs the overlay on). Headlines only, no article text; refreshed every 5 min.
- Remote kill switches for the new pieces: `features.odds`, `features.picks`, `features.ticker` in config.json.

Test it in fixture mode (Settings → Data source → Fixtures): add a PrizePicks prop on a HOU/JAX/LAC/KC player and a DraftKings spread on HOU; they move as the replay runs. Switching data mode clears fantasy links, picks and the pinned game.

## 2. Blocked or unfinished, and why

Nothing is hard-blocked. Not done or not verified:

- **Theme and scope vs SPEC.md**: not verified, because the file was missing (see top).
- **Live Sleeper with your account**: not tested. The brief's test username was the literal placeholder `[YOUR SLEEPER USERNAME]`. I verified the live Sleeper API paths with the public `sleeper` bot account (user found, no leagues) and an unknown user (shows "not found"). Matchup building is tested against real Sleeper JSON and in fixture mode.
- **Live in-game ESPN data**: not seen yet, because no NFL game was live while I worked (Saturday). The live fields (`situation`, `period`, `displayClock`) are parsed defensively. Sunday is the real test.
- **Real hardware**: everything ran on the `Television_1080p` Google TV emulator, not the Onn box. Overlay behaviour can differ between TV builds.
- **No boot receiver**: after a reboot, open the app once to bring the overlay back.
- **Fixture box score**: in fixture mode the box score shows full-game totals even mid-replay. Debug only.
- **Sentry mapping upload**: no Sentry Gradle plugin, so release stack traces stay obfuscated until it's added.

## 3. What to do in the morning

### Install on the Onn box
1. On the Onn: Settings → System → About → click **Android TV OS build** 7 times. Then Settings → System → Developer options → turn on **USB debugging** (and **Wireless debugging** if that's what your build offers).
2. Find the box's IP: Settings → Network & Internet → your network.
3. From this folder on your Mac:
   ```bash
   adb connect <ONN_IP>:5555
   # If your build only offers "Wireless debugging": use "Pair device with pairing code" on the TV, then
   #   adb pair <ONN_IP>:<PAIR_PORT>   (enter the code)   and   adb connect <ONN_IP>:<PORT shown on TV>
   adb install -r release-assets/sofasports-0.1.0-debug.apk
   ```
   To rebuild first: `./gradlew assembleDebug`. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### Allow the overlay (one time)
```bash
adb shell appops set com.sofasports.tv.debug SYSTEM_ALERT_WINDOW allow
```
(For a future release build the package is `com.sofasports.tv`.) Then go to Settings → Score overlay → **On** in the app.

### local.properties (all optional; the app runs with none of these)
```properties
sentry.dsn=https://<key>@<org>.ingest.sentry.io/<project>
sofa.configUrl=https://raw.githubusercontent.com/<you>/<repo>/main/config.json
sofa.versionUrl=https://raw.githubusercontent.com/<you>/<repo>/main/version.json
sofa.feedbackUrl=https://<your feedback form>
# Only once you've created a keystore yourself:
release.storeFile=/absolute/path/to/release.jks
release.storePassword=...
release.keyAlias=...
release.keyPassword=...
```
These are read at build time, so rebuild after editing (`./gradlew assembleDebug`).

### What to test during Sunday's games
- [ ] First run: pick your teams; they appear first on Scores and Home, in blue.
- [ ] Home → link your Sleeper username. All your leagues appear; switching changes the matchup; points move during games.
- [ ] Scores: LIVE badges, clock and possession update about every 30 s; the CLOSE badge appears in one-score 4th-quarter/OT games; countdowns on early-afternoon games tick.
- [ ] Open a live game: win % and chart move, play-by-play grows, the box score fills in. Press "Pin to overlay".
- [ ] Start a streaming app (YouTube TV / Fox / CBS): the overlay is visible in the chosen corner, the remote still controls the video, and scores and fantasy points pulse when they change. Try each corner.
- [ ] Leave it running a full window: watch for battery/heat, stale scores, overlay disappearing (`adb logcat -s SofaOverlay`) or crashes (`adb logcat -b crash`).
- [ ] Note anything about ESPN live fields that looks wrong (down & distance text, clock). Save a live JSON for a fixture: `curl -s "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard" > live.json`.
- [ ] Usage events show in `adb logcat -s SofaUsage`; flipping Settings → "Don't share" stops them.

## 4. Placeholders to replace

| What | Where | Current value |
|---|---|---|
| Remote config URL | local.properties `sofa.configUrl` (default in `app/build.gradle.kts`) | `https://raw.githubusercontent.com/gitzero-io/sofasports/main/release-assets/config.json` |
| Update check URL | local.properties `sofa.versionUrl` (default in `app/build.gradle.kts`) | `https://raw.githubusercontent.com/gitzero-io/sofasports/main/release-assets/version.json` |
| APK download URL | `release-assets/version.json` → `apkUrl` | `https://github.com/gitzero-io/sofasports/releases/download/v0.1.0/sofasports-0.1.0.apk` |
| Feedback QR URL | local.properties `sofa.feedbackUrl` (default in `app/build.gradle.kts`) | `https://example.com/sofasports-feedback` |
| Sentry DSN | local.properties `sentry.dsn` | empty (crash reporting off) |
| Sleeper username | Enter it on the box (Home or Settings) | the brief had `[YOUR SLEEPER USERNAME]` |
| Theme tokens / fonts | `ui/theme/Color.kt`, `ui/theme/Type.kt` | stand-ins (SPEC.md missing) |
| Release signing | local.properties `release.*` | none; release APK is unsigned |

Until the config and version URLs point at real files, the app quietly uses built-in defaults, and "Check for updates" reports "Couldn't check: HTTP 404". That's expected.
