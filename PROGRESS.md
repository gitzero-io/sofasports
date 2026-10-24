# Progress

## Done
- [x] 1. Project setup, theme, fonts, nav shell, D-pad focus ring

- [x] 2. Fixtures (ESPN wk6 finals, wk7 schedule, 2 summaries, Sleeper example league) + ESPN/Sleeper parsers + tests

- [x] 3. ScoresProvider / GameStatsProvider / FantasyProvider + ESPN, Sleeper, fixture-replay impls, switched by a debug data-mode toggle

- [x] 4. Remote config: cached in filesDir, safe defaults, clamped values, kill switch screen, hourly refresh; sample at release-assets/config.json

- [x] 5. Overlay: foreground service + TYPE_APPLICATION_OVERLAY ComposeView, corner choice, start/stop in Settings, adb grant steps on screen; verified drawing over the Google TV launcher on the emulator

- [x] 6. First-run favorites picker (32 teams by division, up to 4, pick order kept, skippable)

- [x] 7. Scores: favorites first then live/upcoming/final, close-game badge (rule from remote config), 30 s polling while live (smart idle/kickoff wake-up), countdown on upcoming cards, last-good data kept when offline

- [x] 8. Game Center: header, Summary (win-prob label + blue/red chart, last play, scoring plays, venue), Box score (team + player stats), Play-by-play (drives newest first); polls while live; "Pin to overlay"

- [x] 9. Sleeper: username entry on Home (no password), all of the user's NFL leagues for the current season with a switcher, live matchup card (me blue / opponent red, starters + points), refreshes at 30 s while NFL games are live

- [x] 10. Overlay shows the selected Sleeper matchup + the pinned game (falls back to my favorite's game today); hides while Sofa Sports itself is on screen; stops on remote `features.overlay=false` or kill switch

- [x] 11. Kickoff countdown (score cards, Game Center header, overlay; ticks every second in the last hour) + score-change pulse (scale + side-colour flash on game scores, fantasy totals, overlay). Pulse rule unit-tested; the animation itself was not captured on the emulator.

- [x] 12. Settings: Sleeper link/unlink, favorites (edit screen reuses the picker), overlay on/off + corner + unpin + adb steps, usage-events opt-out, feedback QR (placeholder URL), version, not-affiliated notice, debug data-source toggle

- [x] 13. Update check: version.json (sample in release-assets) checked hourly-loop/at most every 12 h + manual button in Settings; Home banner with QR + URL when newer; "required" when below minSupportedVersionCode

- [x] 14. Sentry: manual init only with a valid `sentry.dsn` in local.properties; verified the app launches with no DSN ("crash reporting off" in logcat)

- [x] 15. Usage events: closed `UsageEvent` set (no user data), `UsageEvents` interface, logcat sink (`adb logcat -s SofaUsage`), gated by Settings opt-out + remote flag, events held until the opt-out is loaded

- [x] 16. Release: R8 on, keep rules for kotlinx.serialization/Ktor, unsigned by default, opt-in signing via local.properties (no key created). Verified a throwaway debug-key-signed copy of the R8 release runs live ESPN + Sleeper on the emulator. Debug APK copied to release-assets/sofasports-0.1.0-debug.apk (git-ignored). Lint: 0 errors, 12 cosmetic warnings.

## In progress
- (none)

## Blocked
- (none)
