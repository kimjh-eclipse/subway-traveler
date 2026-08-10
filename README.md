# subway-traveler · 지하철 여행기

수도권 지하철로 하루를 도는 일정을 짜고, 노선도 위에서 확인하는 안드로이드 앱.

Claude Design 프로젝트 `서울 원데이 노선도.dc.html` 목업에서 출발해, 경로 작성과
전체 노선도 뷰까지 확장했다.

| | |
|---|---|
| 언어 | Kotlin |
| UI | Jetpack Compose (Material 3) |
| AGP / Gradle | 9.3.1 / 9.5.0 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |

## 화면

**노선** — 가장 최근에 만든 경로를 세로 타임라인으로 보여준다. 아래로 스크롤하면
헤더가 펼친 높이의 1/4까지 접히고, 남는 공간은 목록이 가져간다. 접혔을 때는 노선명과
총 소요만 남는다. 체류 카드를 누르면 도착·출발·누적 시간과 함께 구글맵·네이버지도로
주변 맛집을 찾는 버튼이 나온다.

**기록** — 저장된 경로가 최신순으로 쌓인다. 누르면 노선 탭이 그 경로로 바뀐다.

**노선도** — 수도권 676개 역·24개 노선 위에 이 경로를 겹쳐 그린다. 핀치 줌과 드래그로
움직인다.

## 경로 만들기

편집기는 **시각을 거의 입력하지 않는다.** 출발 시각 하나만 정하면 나머지는 앞 구간에서
계산된다.

| 정거장 | 입력하는 것 | 계산되는 것 |
|---|---|---|
| 출발지 | 역·장소 | — |
| 체류 | 머무는 시간 | 도착·출발 시각 |
| 환승 | 환승 대기 (기본 4분) | 도착·출발 시각 |

- **체류**는 타임라인에 카드가 되고, 메모가 설명으로 들어간다.
- **환승**은 두 이동 사이에 틈을 남기고, 타임라인이 그것을 `환승 대기 N분`으로 읽는다.
  입력에서 출발 시각을 빼되 계산에는 남겨, 하루 통계에서 대기 시간이 사라지지 않게 했다.
- 마지막 정거장에는 체류·대기를 붙이지 않는다. 도착했으므로 없는 시간을 만들지 않는다.

역 이름은 **초성으로도 검색**된다 (`ㄱㄴㄱㅊ` → 강남구청). 노선도에서 직접 눌러 고를
수도 있고, 그때 그 역을 지나는 노선이 칩으로 뜬다. **타고 온 노선은 앞뒤 역에서 자동으로
도출**하며, 직접 고르면 그 뒤로는 건드리지 않는다.

### 소요시간은 추정치다

번들 데이터에 시각표가 없다. OpenStreetMap은 기하 정보를 담지 노선 시각표를 담지 않는다.
그래서 이동 시간은 모델로 뽑는다 — 노선 위 정거장 수 × 2분(서울 지하철 평균, 정차 포함),
노선을 특정할 수 없으면 직선거리 ÷ 32km/h에 굴곡 보정 1.25를 곱한다. 화면에는 `예상`
배지를 달고, 값이 틀리면 그 구간만 직접 덮어쓸 수 있다.

## 데이터

경로는 `filesDir/routes.json`에 저장한다 (임시 파일 경유 전체 재작성). 노선 색은 저장하지
않고 노선 이름에서 파생하므로, 저장 데이터는 텍스트만 갖는다. 첫 실행 시 목업의 서울 경로가
시드로 들어가 앱이 비어 있지 않다.

## 구조

```
app/src/main/java/com/actimedi/travle/
  MainActivity.kt                edge-to-edge 호스트
  data/
    RouteModels.kt               ClockTime, RouteSegment, 합계·타임라인 파생
    RouteDraft.kt                편집기의 정거장 모델 → 구간, 일정 계산, 검증
    TravelEstimate.kt            소요시간 추정 모델
    SubwayMap.kt                 수도권 노선망, 역 검색(초성), 노선 도출
    RouteStore.kt                routes.json 영속화
    SeoulOneDayRoute.kt          목업 RAW 배열을 그대로 옮긴 시드 경로
  ui/
    TravleApp.kt                 탭 셸(노선 / 기록 / 설정) + 상태바 외형
    TravleViewModel.kt           저장된 경로, 선택, 영속화
    theme/                       ActiMedi 색·타이포 토큰, 노선색 조회
    route/                       RouteScreen, TimelineRow, 지도앱 연동
    history/HistoryScreen.kt     저장된 경로 목록
    editor/RouteEditorScreen.kt  경로 편집기
    map/                         노선도 뷰, 역 선택, 경로 투영
```

## 빌드

```bash
./gradlew :app:assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`가 로컬 SDK 경로를 가리키며 git에서 제외된다.

### 릴리즈 서명

`app/build.gradle.kts`가 저장소 루트의 `keystore.properties`(git 제외)에서 서명 정보를
읽는다. `keystore.properties.example`을 복사해 채우면 된다. `storeFile`은 `app/` 모듈
기준 상대 경로로 해석되므로 키스토어는 `app/`에 둔다.

파일이 없으면 release는 **서명 없이** 빌드된다. 디버그 키로 조용히 대체하지 않는다.

## 테스트

```bash
./gradlew :app:testDebugUnitTest
```

20개 테스트:

- `RouteModelsTest` — 파생 합계, 환승 대기 판정, 필터 분할, 시간 포맷
- `RouteDraftTest` — 출발 시각 하나에서 전개되는 일정, 체류/환승 동작, 추정값 덮어쓰기,
  노선 자동 도출, 검증 규칙
- `RouteSerializationTest` — JSON 왕복
- `LineColorTest` — 이름→색 조회가 목업의 색을 그대로 재현하는지, 인천1호선이 1호선
  규칙에 먹히지 않는지

## 알려진 제약

- **compileSdk는 36에 고정.** 다음 AndroidX 릴리즈(core-ktx 1.19, lifecycle 2.11,
  activity 1.13, Compose BOM 2026.06.01)가 모두 37 이상을 요구한다. `android-37`
  플랫폼을 설치한 뒤 함께 올려야 한다.
- **런처 아이콘은 `mipmap-anydpi-v26`에 둔다.** lint의 `ObsoleteSdkInt`는 minSdk가
  26이니 `-v26`을 떼라고 하지만, 그러면 `processReleaseResources`가 깨진다.
- 설정 탭은 아직 비어 있다.

## 목업과 다르게 한 것

1. **헤더 합계를 계산한다.** 목업의 `13:28 / 6:27 / 7:01`은 서로 맞지 않는다. 같은 데이터에서
   계산하면 `13:28 / 6:19 / 7:09`이고, 이동·환승에 환승 대기를 포함시켜
   `이동·환승 + 체류 = 총 소요`가 정확히 맞는다.
2. **가짜 상태바를 노선 메타 정보로 바꿨다.** 목업이 그린 폰 크롬 자리에 실제 상태바가 있다.
3. **이동 행은 탭되지 않는다.** 목업은 토글을 걸어놨지만 펼칠 내용이 없었다.

헤더 제목의 `→`가 화살대 없는 꺾쇠로 보이는 것은 SUITE 자체의 `U+2192` 글리프다. 브라우저
목업도 동일하게 렌더링한다.

## 서드파티 데이터·라이선스

- **노선망** — `app/src/main/assets/subway_map.json`은 OpenStreetMap에서 파생했다.
  © OpenStreetMap contributors, **ODbL 1.0**. 라이선스 요구대로 지도를 그리는 모든 화면에
  출처를 표시한다. 상류 관계에서 빠져 있던 공항철도의 디지털미디어시티·홍대입구는
  소속만 보정해 되살렸다.
- **폰트** — SUIT, SUITE는 **SIL Open Font License 1.1**로 배포되어 앱에 동봉할 수 있다.
