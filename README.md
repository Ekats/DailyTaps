# DailyTaps

An Android app for tables of buttons on your home screen, plus graphs of everything you tap.

## What it does

- **Boards**: a board is a table of 1–7 rows × 1–7 columns. Every slot is a button.
- **Home screen widget**: place any number of widgets and pick a board for each. Tapping a slot
  on the home screen presses it; the app doesn't open. Widgets resize freely, and several widgets
  can show the same board.
- **Slot types**
  - **On/off** (default): each tap toggles between two looks. Add more states to make a cycle
    (up to 8).
  - **Counter**: each tap adds a step. The color fades from the "at zero" look to the "at target"
    look as the count approaches the target.
  - **Value**: a tap opens a small number dialog (weight, water, mood…). Accepts `,` or `.` as
    the decimal separator.
- **Looks**: every state has its own fill (solid color or a gradient with 5 directions,
  transparency supported) and text color. The board has its own background (solid or gradient),
  corner radius, spacing and text size.
- **Text**: optional board title; each slot has an optional label (with a board-wide or per-slot
  size) and can show its state name, count or last value under it. Boards can have column headers
  (weekdays starting Monday, or custom) and row headers.
- **Daily reset** (per board): at local midnight every slot goes back to its first state. History
  is kept.
- **Graphs** per board, per slot or across all boards, for 7 days, 30 days, 90 days or a year:
  - taps per day
  - calendar heatmap of activity, or of days a slot was "done"
  - time of day
  - taps per slot or per board
  - counted per day (counter slots)
  - a line chart of logged values (value slots)
  - summary tiles: streaks, averages, done rate, min/avg/max

  Tap any bar, day or point to read its value. Every chart can also be shown as a table.
- **CSV export** of every tap and value.

Everything is stored locally with Room. There is no network access and no tracking.

## Using it

1. Create a board in the app and style it: tap a slot in the board editor's preview to edit that
   slot.
2. Long-press the home screen → Widgets → DailyTaps, drag it out and pick the board. Or use
   **Add to home screen** in the board's menu.
3. Long-press a placed widget to switch it to another board (Android 12+).

In the app, tapping a slot presses it just like the widget does. Long-press a slot for
corrections (set a state, adjust a counter, clear a value); corrections don't count as taps in the
graphs. Wrong taps can be deleted from the board's recent activity; the slot then goes back to
whatever its remaining history says (last recorded state, last logged value, counter total without
the deleted amount).

## Building

Requires JDK 17+ and the Android SDK (compileSdk 37).

```sh
./gradlew :app:testDebugUnitTest   # unit tests for the tap logic, stats and CSV
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Every push is built by GitHub Actions (`.github/workflows/build.yml`). Pushes to `main` and
`claude/**` also publish a signed test APK under the
[`test-build` pre-release](../../releases/tag/test-build). It is signed with the public key in
`.github/test-signing`, so each build installs as an update over the previous one. Release signing
reads `ANDROID_SIGNING_*` environment variables.

## Code map

| Path | What |
|---|---|
| `data/` | Room entities, DAOs, repository, CSV export |
| `domain/SlotLogic.kt` | What a tap does, daily reset, how a slot looks (shared by widget and app) |
| `domain/Stats.kt` | Everything the graphs show, computed from the event log |
| `widget/` | Glance widget, tap action, board picker, value dialog, midnight refresh, pinning |
| `ui/components/` | Board grid, color and gradient pickers, Canvas charts |
| `ui/screens/` | Home, board, board editor, slot editor, graphs, settings |
