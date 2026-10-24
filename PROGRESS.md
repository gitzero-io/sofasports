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

## In progress
- [ ] 9. Sleeper

## Blocked
- (none)
