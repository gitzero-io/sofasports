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
