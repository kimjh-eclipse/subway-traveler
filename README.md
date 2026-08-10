# 원데이 노선도 (travle)

Android app built from the Claude Design project
`서울 원데이 노선도.dc.html` — a one-day Seoul transit itinerary rendered as a
vertical timeline.

## Stack

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| AGP / Gradle | 9.3.1 / 9.5.0 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |

AGP 9 ships built-in Kotlin support, so the module applies only
`com.android.application` and the Compose compiler plugin.

Two pins worth knowing about:

- **compileSdk stays at 36.** The next AndroidX releases (core-ktx 1.19,
  lifecycle 2.11, activity 1.13, Compose BOM 2026.06.01) all refuse to compile
  against anything below 37. Bump `compileSdk` and those four versions together
  once the `android-37` platform is installed.
- **The launcher icon lives in `mipmap-anydpi-v26`.** Lint's `ObsoleteSdkInt`
  suggests dropping the `-v26` because minSdk is already 26, but doing so breaks
  `processReleaseResources` (`resource mipmap/ic_launcher not found`) while the
  debug variant still links. Leave the qualifier in place.

## Build & run

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home ./gradlew :app:assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` points at the local SDK and is git-ignored.

### Release signing

`app/build.gradle.kts` reads signing credentials from a git-ignored
`keystore.properties` at the repo root (see `keystore.properties.example`) —
same convention as the `19` project. `storeFile` resolves relative to the `app/`
module directory, so the keystore lives at `app/upload-keystore.keystore`.

If `keystore.properties` is missing, the release variant builds **unsigned**
rather than silently falling back to the debug key.

```bash
./gradlew :app:assembleRelease   # → app/build/outputs/apk/release/app-release.apk
```

`keystore.properties`, `*.keystore` and `*.jks` are all git-ignored. Back the
keystore up somewhere outside the repo — losing it means never being able to
ship an update to an app signed with it.

## Layout

```
app/src/main/java/com/actimedi/travle/
  MainActivity.kt              edge-to-edge host
  data/
    RouteModels.kt             ClockTime, RouteSegment, summary + timeline derivation
    RouteDraft.kt              the editor's stop model → segments, plus validation
    RouteStore.kt              routes.json in internal storage
    SeoulOneDayRoute.kt        the itinerary, transcribed from the design's RAW array
  ui/
    TravleApp.kt               tab shell (노선 / 기록 / 설정) + status-bar appearance
    TravleViewModel.kt         saved routes, selection, persistence
    theme/                     ActiMedi color + type tokens, line-colour lookup
    route/                     RouteScreen, TimelineRow
    history/HistoryScreen.kt   every saved route, newest first
    editor/RouteEditorScreen.kt  build a route from stops
    common/PlaceholderScreen.kt
```

## Creating routes

The 노선 tab opens on the **most recently created** route; 기록 lists them all and
tapping one switches the 노선 tab to it. 새 경로 opens the editor.

The editor works in **stops**, not segments, because that is how a trip is
planned. Each stop carries a station/place name, the line taken to reach it,
arrival and departure times, and a 체류/환승 switch:

- **체류** — becomes a card on the timeline, with the memo as its label.
- **환승** — leaves a gap between two consecutive rides, which the timeline reads
  back as a 환승 대기 chip.

`RouteDraft.toSegments()` performs that expansion: a ride runs from the previous
stop's departure to this stop's arrival, so the two stop kinds fall out of the
same rule. `RouteDraft.validate()` blocks saving on a blank name, a single stop,
a missing line, departing before arriving, or arriving before the previous stop
left — the last two are the mistakes that would otherwise produce a nonsensical
timeline.

Routes are stored as JSON in `filesDir/routes.json` (whole-file rewrite via a
temp file). Line colours are **not** stored — `lineColorFor()` derives them from
the line name, so a saved route only carries text. The design's route is seeded
on first launch so the app is never empty.

## Collapsing header

Scrolling the timeline down collapses the header to **1/4 of its expanded
height**, handing the recovered space to the list. `RouteScreen` drives this
with a `NestedScrollConnection` using `exitUntilCollapsed` semantics: a
downward scroll is consumed by the header before the list moves, and the header
only re-expands once the list is back at the top.

The expanded content (departure time, day, eyebrow, title, three stat chips)
fades out over the first ~half of the collapse and drifts upward slightly for
parallax. What remains is the route title on one line plus the **총 소요** total
in a pill — the two things worth keeping when space is scarce.

The natural expanded height is measured once via `onSizeChanged` on a child
using `wrapContentHeight(unbounded = true)`, so the content keeps its real
height while the clipping parent shrinks around it. The status-bar inset is
outside the collapsing box and is never reclaimed.

## Design fidelity notes

The screen is a one-for-one port of the mockup — gradient header, segmented
filter, timeline rails, expandable stay cards, transfer-wait chips, closing
summary card and bottom nav. Four deliberate deviations:

1. **Header totals are computed, not hardcoded.** The mockup's chips read
   `13:28 / 6:27 / 7:01`, which do not add up. Derived from the same route data
   they are `13:28 / 6:19 / 7:09`; 이동·환승 includes transfer waiting, so the
   three values reconcile exactly (`이동·환승 + 체류 = 총 소요`). The closing card's
   arrival time, transfer/leg/stay counts are likewise derived.
2. **The fake status bar became route metadata.** The mockup drew a phone chrome
   (`07:00`, a battery outline, `토요일`). The app has a real status bar, so that
   row now reads `07:00 출발` / `토요일`.
3. **Move rows are not tappable.** The mockup wired a toggle to them but had no
   expanded state to show, so tapping did nothing. Only stay cards expand.
4. **기록 / 설정 tabs are placeholders.** The design only covers the 노선 tab.

The `→` in the header title renders as a shaft-less chevron. That is SUITE's own
`U+2192` glyph, not a fallback — the browser mockup renders it identically.

## Third-party data & licences

- **Subway network** — `app/src/main/assets/subway_map.json` is derived from
  OpenStreetMap, © OpenStreetMap contributors, licensed **ODbL 1.0**. The
  attribution is shown on every screen that draws the map, as the licence
  requires. Two 공항철도 stops (디지털미디어시티, 홍대입구) missing from the
  upstream relation were added back; see the generator notes in the commit.
- **Fonts** — SUIT and SUITE are distributed under the **SIL Open Font License
  1.1**, which permits bundling them in an application.

## Fonts

`SUIT` (body) and `SUITE` (display) from the ActiMedi brand kit are bundled in
`app/src/main/res/font/`. Only SUITE Bold is distributed, so the display family
exposes a single weight; the mockup's 800-weight headings render as SUITE Bold.

## Data

`SeoulOneDayRoute` is seed data compiled into the app. There is no routing API,
no persistence and no live departure times behind it — the same static
itinerary the design carried.

## Tests

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home ./gradlew :app:testDebugUnitTest
```

18 tests across four classes:

- `RouteModelsTest` — derived totals, transfer-wait detection, filter
  partitioning, duration formatting.
- `RouteDraftTest` — stop→segment expansion, 체류 vs 환승 behaviour, and every
  validation rule.
- `RouteSerializationTest` — the JSON round trip, both segment kinds included.
- `LineColorTest` — the name→colour lookup reproduces every colour the mockup
  assigned, and 인천1호선 is not swallowed by the 1호선 rule.
