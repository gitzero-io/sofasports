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
