# Decisions

One line per judgment call, with the reason.

- SPEC.md was not in the project (or anywhere nearby on disk) → built v0.1 from the 16-step brief alone; flagged in HANDOFF.md.
- Red vs Blue tokens/fonts invented as stand-ins (SPEC.md missing) and kept in one file (`ui/theme/Color.kt`, `Type.kt`) → swapping in the real spec values is a one-file change.
- Fonts: Barlow Condensed (display/scores) + Inter variable (body), both OFL, bundled in `res/font` → no Google Play Services font provider on cheap TV boxes; licences in `/licenses`.
- Live/close-game status colour is amber, not red → red is reserved for "opponent", so a red LIVE badge would be misread.
- Template files under `com.example.sofasports` were moved (not deleted) into `com.sofasports.tv` → required package; obeys "don't delete files you didn't create".
- `/screenshots` and `/release-assets` interpreted as project-relative folders → "stay inside this project folder".
- Git commits use `git -c user.name/user.email` per command → no global git config exists and the brief forbids changing it.
- minSdk 26, compile/target SDK 37 (the SDK installed by the template) → brief says minSdk 26; target left at template value.
- Kotlin bumped 2.2.10 → 2.4.21 → Ktor 3.6 / kotlinx.serialization 1.12 are built with newer Kotlin; avoids metadata-version errors.
- Debug build uses applicationId suffix `.debug` → debug and a future release build can sit side by side on the Onn box.
- Started the local `Television_1080p` AVD headless for screenshots → no physical device was connected; it's a local emulator, nothing external.
- Sleeper fixtures use Sleeper's own documented example league (289646328504385536, 2018 season) → the brief's Sleeper username was left as the literal placeholder `[YOUR SLEEPER USERNAME]`, and I won't pull a stranger's league into the repo.
- Sleeper `players/nfl` is 14 MB → fixture is trimmed to the 189 players in the fixture matchups; at runtime the app caches a trimmed copy and refreshes at most once a day (Sleeper's guidance).
- Fixture JSON lives once, in `app/src/test/resources/fixtures`, and is added to debug assets via sourceSets → one copy for tests and the debug "Fixtures" data mode; release APK excludes it.
- ESPN `broadcasts` differ between scoreboard (`names[]`) and summary (`media.shortName`) → DTO accepts both; found by testing against real fixtures.
- Sentry auto-init disabled in the manifest → with no DSN it crashed on launch (caught on the emulator); SofaApp inits it by hand only when a DSN is set.
- Emulator: dismissed the Google TV setup prompt and set user_setup_complete/device_provisioned on the local AVD → otherwise the setup wizard sits on top of every app.
- Fixture mode replays last week's HOU@JAX (from late Q3) and LAC@KC (from kickoff) play-by-play, 1 play / 4 s, and shifts week 7's schedule to start 3 min after launch → lets live-only features (close badge, pulse, polling, countdown) be tested offline.
- Fixture Sleeper: any username maps to roster 1's owner in the example league; a 2nd league is synthesised from the same data as roster 5's owner → exercises the league switcher; points scale with replay progress so the overlay pulse fires.
- Data-mode toggle is ignored in release builds (`BuildConfig.DEBUG`) → release can never accidentally show fake data.
- Ktor `expectSuccess = false`; providers parse the body and let parse errors surface → one error path (repository catches and keeps last good data).
- Remote config URL is a build-time value (`sofa.configUrl` in local.properties, placeholder GitHub raw URL otherwise) → no backend; a static file on GitHub is enough for v0.1.
- Bad config never replaces good: unparseable/wrong-typed JSON is ignored, poll intervals clamped (live ≥15 s), endpoint overrides must be https → a typo in config.json can't brick or DDoS anything.
- Kill switch supports `maxVersionCode` → can kill one broken build without killing a fixed one.
- Unit test reads `/release-assets/config.json` directly → the shipped sample can't drift from the parser.
- Overlay is a foreground service (type `specialUse`) → needed to survive in the background on API 26+; the notification is IMPORTANCE_MIN so it doesn't nag.
- Overlay window is NOT_FOCUSABLE but deliberately not NOT_TOUCHABLE → Android caps NOT_TOUCHABLE overlays at 0.8 alpha; TVs have no touch, so full opacity costs nothing.
- Overlay can't be tested over system Settings (it hides third-party overlays by design) → verified over the Google TV launcher instead.
- Overlay auto-restarts on app process start if it was left on → it's a persistent user setting. No BOOT_COMPLETED receiver in v0.1 (open the app once after a reboot).
- Briefly disabled then re-enabled the emulator's Google TV launcher while testing → its setup popups kept covering the app; state restored.
- Max 4 favorite teams, pick order = priority → "favorites first" stays meaningful and the first favorite decides which side is blue when two favorites meet.
- Team logos load from ESPN's public CDN via Coil → no bundled NFL marks in the APK.
- Close game = live, 4th quarter or later, within 8 points (one score); overtime always close; both numbers in remote config → standard "one-score game late" definition, tunable without a release.
- Neutral games (no favorite playing): home = blue, away = red → brief says "Blue = me/home/favorite".
- Two favorites meeting: the one picked first is blue.
- Win-probability label shows the leader's chance (mine when level), capped at 1–99% while live and 100% only for the final winner → avoids a premature "100%" on TV.
- Polling: 30 s while any game is live or a fetch failed; otherwise sleep until the next kickoff (min 30 s, max 5 min) → near-zero traffic on weekdays, instant pickup at kickoff.
- One app-wide scoreboard poller (shared StateFlow, stops 5 s after the last screen/overlay unsubscribes) → screens and overlay never double-poll ESPN.
- Countdown: hidden beyond 24 h (date shown), "in 5h 07m" under 24 h, "in 42:05" in the last hour, "Kickoff soon" if ESPN hasn't flipped to live yet.
- Game Center tabs are buttons, and every read-only row is focusable (`focusableRow`) → TV lazy lists only scroll by moving focus; plain text would be unreachable.
- Win-prob chart: my side's chance, filled blue above 50% and red below, no axes → readable from the couch; disabled with `features.winProbability`.
- "Pin to overlay" lives in Game Center (one pinned game at a time; pinning another replaces it).
- Fixture box score shows the full-game totals even mid-replay → ESPN only gives end-of-game box scores in the fixture; acceptable for a debug mode.
- `features.gameCenter=false` makes score cards non-clickable rather than removing the screen → simplest remote kill for that feature.
- Sleeper "login" is username only → Sleeper's public read API needs no auth; nothing secret is stored.
- First league is auto-selected and the choice persisted; leagues refetched hourly, matchup every 30 s while any NFL game is live (5 min otherwise).
- Matchup week = Sleeper `state/nfl` `display_week` → matches what the Sleeper app shows.
- Testing Sleeper username in the brief was the unfilled placeholder `[YOUR SLEEPER USERNAME]` → live Sleeper verified only via unit tests against real response shapes and fixture mode; you need to enter your username on the box.
- Overlay game = pinned game if it's in this week's scoreboard, else my top favorite's game if live / kicking off within 12 h / finished within 6 h, else none → overlay is useful even before anything is pinned, and stays small otherwise.
- Overlay hides while Sofa Sports is in the foreground (activity onStart/onStop) → it otherwise covered the app's own header and Pin button.
- Overlay card is fully opaque → any translucency let bright UI behind it bleed through on the emulator.
- Score pulse: two quick scale+flash beats (~1.4 s) in the scoring side's colour; game scores pulse only upward (downward = stat correction), fantasy points pulse both ways; never on first load.
- Feedback QR rendered locally with ZXing core (no network, no Play Services); URL is `sofa.feedbackUrl` in local.properties, placeholder `https://example.com/sofasports-feedback`.
- Not-affiliated notice also says logos are shown for identification only and scores may be delayed → covers the ESPN-logo and unofficial-API realities.
- Usage events default to ON with a clear opt-out in Settings → brief asks for an opt-out (not opt-in); nothing is sent anywhere in v0.1 anyway (logging only).
- Update check never downloads or installs → no REQUEST_INSTALL_PACKAGES permission; shows the APK URL + QR for the Downloader app or adb. Safer and enough for a sideloaded v0.1.
  - Superseded in 0.2.1: an "Install update" button downloads the APK and installs it with a PackageInstaller session, so Android shows its own "Update this app?" prompt. Only on a press, never in the background. Android rejects an APK signed with another key, the archive's package name and versionCode are checked first, and version.json may carry `apkSha256`. The first press sends the user to "Install unknown apps" (Google TV opens the full list, not our entry). version.json points at the tag's own download URL so its hash never refers to a newer "latest" file. The QR stays as a fallback.
- version.json URL is `sofa.versionUrl` in local.properties, placeholder GitHub raw URL otherwise; apkUrl must be https.
- Sentry: no PII, no performance tracing (sample rate 0), release tag `sofasports@<versionName>+<code>`; crash reports are not tied to the usage-events opt-out → crashes are needed to keep the app working, and nothing personal is sent. Revisit if you disagree.
- No Sentry Gradle plugin (no mapping upload) → it needs an auth token and network at build time; release stack traces will be obfuscated until that's added.
- Usage events are a sealed class, not `track(String, Map)` → impossible to accidentally send a username, league id or free text.
- Events fired before the opt-out preference loads are held (max 20) and then sent or dropped → found on the emulator that `app_open` was being lost.
- Release signing is opt-in through four `release.*` keys in local.properties → brief says don't create a key; without them assembleRelease gives an unsigned APK.
- To check R8 didn't break serialization/Hilt, a scratch copy of the release APK was signed with the existing ~/.android/debug.keystore, run on the emulator, then uninstalled → no new key, nothing kept.
- APKs are git-ignored; the debug APK is copied to release-assets/ for convenience only.
- Lint's 12 warnings (Compose modifier-param order, newer Gradle/BOM available, unused template colors) left as is → cosmetic; upgrading dependencies overnight is risk without benefit.
- Home Sleeper prompt has "Skip for now"; skipping hides the Fantasy section on Home until an account is linked (Settings → Sleeper always has the link form) → user feedback: linking must be optional.
- Username box is a button until OK is pressed, then becomes an editable field → on a remote, a focused text field popped the keyboard and trapped D-pad focus just from scrolling Home.
- Pressing Link with an empty box says "Type your Sleeper username first" instead of looking up "".
- DraftKings "overlay" = DraftKings lines (spread, total, moneyline) that ESPN already publishes from DraftKings, shown on cards, Game Center (with live cover / over-under status) and the overlay. No DraftKings API or account involved. Toggle in Settings; remote kill via `features.odds`.
- Spread is parsed from ESPN's text ("PIT -3") rather than its numeric `spread`, whose sign differs between scoreboard and summary.
- ESPN Fantasy uses ESPN's unofficial `lm-api-reads.fantasy.espn.com` API (base URL overridable in remote config). Public leagues: league ID only. Private: espn_s2 + SWID cookies typed or pasted (Google TV phone app keyboard). If SWID is given, the user's team is pre-selected.
- ESPN cookies live in `files/espn_leagues.json`, excluded from Android backup and device transfer, and are only sent to ESPN.
- ESPN fixtures come from a real public 2026 league with team names, owner names and account ids scrubbed; NFL player names kept (public).
- ESPN weekly points: `totalPointsLive` when > 0, else `totalPoints` (ESPN leaves totalPoints at 0 until the week is scored). Starters = lineup slots other than bench (20) and IR (21).
- Matchup period = league's `currentMatchupPeriod`, also used as scoringPeriodId → correct for standard weekly leagues; multi-week playoff matchups will show only the current week.
- Switching the debug data mode clears Sleeper/ESPN links and the pinned game → the fixture Sleeper owner id is a real Sleeper account and leaked into live mode during testing.
- Sleeper and ESPN leagues share one switcher, labelled "Sleeper · …" / "ESPN · …".
- PrizePicks / DraftKings: manual pick tracker instead of account connection → no public APIs for user entries; connecting would require storing sportsbook credentials and using private endpoints (ToS, real-money accounts). Picks live in `files/picks.json` on the TV only.
- Picks are matched to "this week's game for the pick's team" and props to ESPN athlete ids (roster ids == box-score ids, verified 34/34) → no game ids stored, so picks keep working across scoreboard refreshes.
- Monotonic prop stats (TDs, receptions, completions, attempts, INTs, rush attempts) settle early once an over is cleared (or an under is broken); yardage can go down, so it stays "hitting" until final. A live game total over is won once past the number.
- Box scores are fetched only for games that have prop picks: every 30 s while live, once after the final.
- Player list = ESPN team roster, offense QB/RB/WR/TE only, ESPN's order (backups can appear before starters).
- Fixture mode scales end-of-game player stats by replay progress → props visibly move in a test; live mode uses ESPN's real live box score.
- Book badge colours: DraftKings green, PrizePicks violet → never red/blue, which stay reserved for me/opponent.
- News ticker source: ESPN's public NFL news feed (`/news`), headlines only, refreshed every 5 min; "Headlines: ESPN" shown in the crawl. Articles are tagged with team abbreviations and ESPN athlete ids, so "mine" matches favorites/pick players by id and fantasy starters by name.
- Scoring plays for the ticker are inferred by diffing consecutive scoreboard polls (6–8 pts = TD, 3 = FG, 2 = 2 PTS, 1 = XP) with ESPN's last-play text trimmed to one sentence; they expire after 15 min.
- Over-video ticker is a setting with Off / Corner card line / Bottom bar / Both; default Corner card line (adds no screen area beyond the existing card). The bottom bar is a second NOT_FOCUSABLE overlay window; a bottom-corner card is lifted above it.
- Ticker bars keep their text inside the TV overscan-safe area (48dp sides, extra bottom inset in-app); the background runs edge to edge.
- The ticker's "NFL" label is neutral grey, not blue → blue stays reserved for "me".

## Overlay controls and customization (after v0.1.0)
- Remote control uses an AccessibilityService that only filters key events (`flagRequestFilterKeyEvents`, `canRetrieveWindowContent=false`) → it's the only way for a sideloaded app to react to remote buttons while another app (YouTube etc.) has focus. Every key other than the 1–2 the user picked is passed straight through.
- Buttons are learned ("press the button you want") instead of hard-coded → works on any remote, not just the onn 4K Pro. D-pad, OK, Back, Home, volume, power and play/pause can't be bound.
- Show/hide button: press = show/hide, double = next page, hold = open Sofa Sports. Optional next-page button: press = next, double = previous, hold = show/hide. A single press waits 350 ms to rule out a double press.
- A press while the overlay is off turns it on (if the overlay permission is granted).
- Hidden = the card's window is removed → on the emulator a window that shrank to 0 px kept its last frame on screen.
- Pages: My matchup, My game, Around the league, Picks, Minimized pill. Pages with nothing to show are skipped; at least one page must stay enabled. Auto-rotate never goes to Minimized.
- Smart automatic mode is one switch (default off) that gates everything the overlay does by itself: pop on big moments, minimize when quiet, live games only, quiet hours → per user request, pop only runs in smart mode. Pop is on by default once smart mode is on.
- Big moments are found by diffing consecutive overlay states (score in my game, a starter gaining ≥ 4 fantasy points between polls, fantasy lead flips, a pick settling, a game turning close). Each pops once for 8 s, max 3 queued; quiet hours drop them.
- A show/hide press overrides smart mode's rules until those rules change their answer (e.g. games start or end), so it never sticks forever.
- Size is a density multiplier (0.85 / 1 / 1.25) so text and spacing scale together; opacity applies to the background only.
- `adb shell input keyevent` bypasses Android's accessibility key filter, so remote-button tests on the emulator use a `uinput` virtual remote (real key events).

## Bet and parlay tracker (0.2.1)
- Manual by design: sportsbooks and PrizePicks have no public API for a user's bets, so this works with no account and never breaks on a sync.
- Books: PrizePicks (props only), DraftKings, FanDuel, BetMGM, Other. Badges stay off red/blue.
- Bet types: player props, spread, game total, moneyline (game winner), team total. Props added: anytime TD (yes/no), rushing and receiving TDs, longest rush/reception, targets, pass+rush yards, field goals made and kicking points (kickers come from the roster's special-teams group; the kicking box score table is now parsed and shows in Game Center).
- A parlay is a `BetTicket` plus legs that point to it (`TrackedPick.ticketId`), stored in tickets.json beside picks.json so existing saved picks load unchanged. One lost leg loses it, pushes drop out, all pushes push.
- No stake, odds or payout: the user said financial details are irrelevant, so the tracker only answers "is it hitting".

## Watch games (launcher)
- "Watch on …" opens the user's own app; no account or provider sign-in happens in Sofa Sports.
- No provider offers a public link that opens a specific game or channel, so we open the app (the user picks the channel) or ask Google TV search for "Away vs Home", whose results list where it's live.
- Routing: games on TV networks (CBS, Fox, NBC, ABC, ESPN, NFL Net) open the live TV app the user picked (Settings → Watch games, chosen from installed TV apps); streaming exclusives (Prime Video, Peacock, Netflix, YouTube, Disney+, Paramount+, NFL+) open that app when installed. ESPN's full broadcasts list is kept as `Game.networks`.
- Package visibility: the manifest `<queries>` asks only for home-screen (LEANBACK_LAUNCHER) apps and the global search action, not all packages.

## Ticker: bettor/fantasy news only
- Headlines must be ESPN "HeadlineNews" (not Story/Media), tag at least one player, and read as a status change (out, questionable, IR, activated, starting, returning, traded, injury terms). Everything else (rankings, betting guides, uniforms, features, team off-field news) is dropped. Scoring plays and live scores are unchanged.
- The feed request went from 25 to 50 items because roughly a third survive the filter.

## Fantasy page: projections, win probability, photos
- New Fantasy tab with the ESPN-style matchup: totals with live projections, a win-probability bar between the teams, then each lineup slot side by side (photo, injury tag, game and clock, points, projection). Home keeps a compact card that opens it.
- Projections: Sleeper's public weekly projections (api.sleeper.app/projections, undocumented) in the league's scoring (PPR/half/standard from scoring_settings.rec); ESPN's from the league box score's projected stat, falling back to ESPN's public default (PPR) projections by player id.
- Live projection per player = points + projection × share of his game left (from period and clock); finished players count their points. Win probability = normal model with each remaining projection's spread at 50% of itself; settled when no game time is left.
- Photos: ESPN headshots by athlete id, Sleeper thumbnails by player id; defenses show the team logo.
- Sleeper's trimmed player cache gained injury status (file renamed to _v2 so old caches refresh).

## Multi-sport groundwork (2026-10-26, branch `multi-sport`)
- Leagues are one enum (`domain/Leagues.kt`): NFL, NCAAF, NBA, NCAAM, MLB, NHL, EPL, MLS, Champions League, LaLiga, Serie A, Bundesliga, Ligue 1, Liga MX, UFC, Boxing, F1, NASCAR Cup, IndyCar. A league's `id` is stored in settings, favorites and pins, so it must never change.
- Three event shapes, not one: team games stay `Game` (home/away + score); fights reuse `Game` (two athletes, no score, `winner`, grouped into a card by `eventId`); races are a separate `Race` (a field in finishing order, one per weekend with its sessions). Forcing races into home/away would have broken every screen.
- Data: ESPN's site API for every league (same shapes as NFL). Boxing isn't on ESPN at all, so it comes from TheSportsDB's free test key ("123"), which returns only the next and the last fight; base URL is remote-configurable for a paid key later.
- College asks for the whole division (`groups=80` FBS, `groups=50` D-I) because ESPN's default college scoreboard is a featured handful, which would hide most favorites.
- Favorites are league-scoped keys, `"nba:LAL"` (`TeamKey`): ESPN reuses abbreviations across sports (DAL is the Cowboys, Mavericks and Stars). Bare keys from NFL-only builds read back as NFL, so upgrades keep their teams with no migration write. Up to 4 per league, 12 overall.
- Pins and Game Center routes carry a `GameRef` key, `"nba:401909861"`: ESPN ids are only unique within a sport. A bare pinned id is NFL.
- Followed leagues are a setting (default NFL only, so existing users see no change until they opt in). Each followed league polls on its own with the same PollPolicy; NFL is also polled when Sleeper is linked or a pick is tracked, since fantasy and picks are NFL-only.
- A PRE event whose start passed more than 3 h ago no longer forces 30 s polling (a boxing card with no result yet would otherwise poll all night).
- Close-game rules per sport, counted from the end of regulation: football keeps the remote rule; basketball last period within 6; hockey 3rd period within 1; baseball 8th inning on within 2; soccer 2nd half within 1. Overtime/extras/extra time are always close. Fights and races are never "close".
- Basketball scoring isn't news: no ticker score events, no overlay pops and no "scoring plays" list (every basket would be one). Football, soccer, hockey and baseball keep them ("GOAL", "2 RUNS").
- Game Center works for every ESPN team sport: flat play-by-play where there are no drives, soccer key events with a running score, baseball "Top 9th", and box-score columns trimmed to what fits a TV row per sport. Fights and races open a lighter detail page from the scoreboard instead (ESPN's MMA summary endpoint returns an error).
- The team picker loads teams from ESPN's `/teams` per league (NFL stays built in, grouped by division). Lists of 60+ teams (college) get an A–Z filter.
- Remote config gained `disabledLeagues` (kill one league's feed without a release) and `espnSportsBase` / `sportsDbBase`.
- Fixtures: a real scoreboard per league saved 2026-10-25/26, plus NBA, NHL, EPL and MLB summaries. Fixture mode replays LAL @ UTAH's fourth quarter so non-football live paths can be seen offline.
- Fantasy, the picks tracker and ESPN news-to-fantasy matching remain NFL-only.
- MLB and NHL send a competitor's `record` as a plain string, and soccer sends `"odds":[null]`; both made whole scoreboards fail to parse until the DTOs accepted them. Caught by parsing every saved scoreboard in a test.

## Multi-sport, round 2 (2026-10-26, branch `multi-sport-2`)
- Fighters and drivers can be favorites. Their key is the full name ("ufc:Alexander Volkanovski", "f1:Lando Norris"), not an id: TheSportsDB boxing has no athlete ids, and names read well in Settings. Keys compare case-insensitively; commas are stripped because favorites are stored comma-joined.
- Fighter list = everyone on UFC cards from 150 days back to 90 ahead (one ESPN date-range request, ~380 fighters), or the boxers TheSportsDB returns. Driver list = the current race weekend's entry list. Both sort by last name with accents ignored, so "Álvarez" files under A.
- A favorite fighter's bout is the one shown on their card's tile and counts as "your game" on Home and in the overlay. A favorite driver gets a blue line with their position on race cards and pages (even outside the top three).
- Races can be pinned. With nothing pinned and no favorite game on, the overlay's "my game" page shows a favorite driver's race while a session is live or starts within 12 h.
- Fixture mode replays one game each in the NHL, MLB and Premier League as well, and runs the UFC main event and the F1 race live for the first ten minutes.
- Not done: fantasy for other sports (Sleeper has NBA leagues). It touches the player cache, projections, the live-projection model and the matchup card, all NFL-shaped today, and there's no NBA league to test against.
