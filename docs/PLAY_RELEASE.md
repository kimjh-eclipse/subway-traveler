# 구글 플레이 배포 준비

`6b847ec` 기준으로 점검한 것과, 사람이 해야 하는 것, 그리고 **배포 전에 결정해야
하는 것**을 나눠 적는다.

---

## 1. 갖춰진 것 — 확인 완료

| 항목 | 상태 |
|---|---|
| 업로드 키 | `app/upload-keystore.keystore`, 별칭 `upload`, RSA 2048, **2094-01-04까지 유효** (플레이 요건은 2033-10-22 이후) |
| 비밀 관리 | `keystore.properties`·`.env`·`*.keystore` 모두 `.gitignore`. 없으면 release는 서명 없이 빌드된다 — 디버그 키로 조용히 대체하지 않는다 |
| 서명 검증 | `apksigner verify` → `Verifies` (APK Signature Scheme v2) |
| AAB | `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` (4.7 MB) |
| **축소 빌드 실제 구동** | R8 + `shrinkResources` 켠 릴리스를 기기에 올려 확인. 경로·도식 노선도·역 이름표·노선 칩 모두 정상. `kotlinx.serialization`이 자산을 읽는 경로가 살아 있다 |
| SDK | `minSdk 26` / `targetSdk 36` / `compileSdk 36` — 플레이의 타깃 SDK 요건 충족 |
| 버전 | `versionCode 1` / `versionName "1.0"` |
| 권한 | `INTERNET`, `ACCESS_NETWORK_STATE` 둘뿐. 위치·저장소·연락처 없음 |
| 평문 통신 | `swopenapi.seoul.go.kr` 한 호스트만 예외. 나머지는 HTTPS 강제 |
| 키 유출 | 인증키를 로그로 찍는 코드 없음 (`grep` 확인) |
| 런처 아이콘 | adaptive(`anydpi-v26`) + `monochrome` (테마 아이콘 지원). `minSdk 26`이라 레거시 PNG 불필요 |
| 스토어 아이콘 | 512×512 32비트 PNG 생성 — `~/Downloads/play-지하철여행자/icon-512.png` |
| 스크린샷 | 1080×2400 5장 — `~/Downloads/play-지하철여행자/screenshots-ko/` |

---

## 2. 배포 전에 결정해야 하는 것

### 2.1 실시간 도착 API 하루 1,000회 — 공개 배포를 막는 수준 🚨

서울 열린데이터광장 실시간 도착 API는 **키 하나당 하루 1,000회**다. 앱에 키를
하나 박아 배포하면 **모든 사용자가 그 1,000회를 나눠 쓴다**. 역 하나를 열 때마다
한 번씩 쓰므로, 하루 활성 사용자 수십 명이면 오후에 고갈되고 그 뒤에 앱을 연
사람에게는 도착 정보가 **아무 설명 없이 안 나온다**(`ERROR-337`).

실제로 개발 중에 내가 이 쿼터를 하루치 다 써 본 적이 있다.

고를 수 있는 길:

1. **서버를 하나 둔다** — 우리 서버가 키를 들고 캐시해서 앱에 내려준다. 같은 역을
   여러 사람이 봐도 호출은 한 번이다. 쿼터 문제와 2.2의 키 노출 문제가 함께 풀린다.
2. **쿼터 증량을 신청한다** — 열린데이터광장에 사유를 적어 신청.
3. **실시간 기능을 끄고 시간표만으로 낸다** — 시간표 API는 하루 10,000회이고, 애초에
   '계획을 짜는 앱'이라 실시간이 없어도 앱은 성립한다. 여행 중 모드만 빠진다.
4. **사용자가 자기 키를 넣게 한다** — 관광객에게는 사실상 불가능.

지금 상태로 올리면 첫 며칠은 되고 그 뒤로는 안 되는 기능이 된다.

### 2.2 인증키가 APK 안에 평문으로 들어간다

`buildConfigField`로 넣으므로 APK를 뜯으면 나온다. 게다가 실시간 API는 HTTPS가
없어 **키가 URL에 담겨 평문으로 오간다**. 저장소에 안 올리는 것과 배포물에 안
들어가는 것은 다른 이야기다. 2.1의 1번을 고르면 함께 해결된다.

### 2.3 응암순환은 단선(한 방향)인데 우리 자료에는 방향이 없다

6호선 응암순환은 `응암 → 역촌 → 불광 → 독바위 → 연신내 → 구산 → 응암` 한
방향으로만 간다. 우리 노선망의 갈래는 방향이 없어서, 길찾기가 `연신내 → 독바위 →
불광`을 6호선 2정거장으로 안내할 수 있다 — **실제로는 갈 수 없다**(정답은 3호선
한 정거장).

관광객에게 없는 열차를 타라고 하는 것이라 배포 전에 고치는 편이 좋다. 노선 갈래에
'한 방향' 표시를 더하고 `stationsBetween`·`RouteSearch`가 그것을 지키게 하면 된다.
단선 순환은 수도권에서 이 한 곳이다.

### 2.4 자산 라이선스 — 명시했고, API 약관만 남았다

- `subway_map.json`, `station_names.json`, `hotspots.json`은 **OpenStreetMap 파생**이다.
  ODbL은 파생 데이터베이스를 배포할 때 **같은 라이선스로 공개**할 것을 요구한다.
  저장소는 Apache-2.0이고 자산은 ODbL이라 서로 다르므로, **`app/src/main/assets/LICENSE`에
  자산별 출처와 라이선스를 적어 두었다**(앱에도 함께 실린다).
- `schematic_map.json`은 서울교통공사 노선도(공공누리 1유형) 파생 — 출처 표시로 충분하고
  변경·상업적 이용이 허용된다. 앱의 `설정` 화면과 지도 하단에 표기되어 있다.
- **남은 것**: 열린데이터광장·공공데이터포털 API의 이용약관에서 재배포·상업적 이용
  조건을 한 번 확인할 것. 이건 약관을 읽고 판단할 일이라 내가 대신 결론 내리지 않았다.

---

## 3. 사람이 해야 하는 것

- **Play Console 개발자 등록** (일회 $25) 및 앱 만들기
- **업로드 키 백업** — `app/upload-keystore.keystore`와 비밀번호. 잃으면 Play에
  키 재설정을 요청해야 하고 그동안 업데이트를 못 낸다. 저장소에는 절대 올리지 않는다
- **개인정보처리방침 호스팅** — 초안은 `docs/privacy-policy.md`. 플레이는 URL을 요구한다
  (GitHub Pages로 그 파일을 그대로 띄우면 된다)
- **Data safety 폼** 제출 (아래 4.3의 답안 사용)
- **콘텐츠 등급 설문** — 폭력·성·도박·약물 없음 → 전체 이용가
- **광고 없음**, **인앱결제 없음**, **정부 앱 아님**으로 신고
- 스토어 자산 업로드 (아이콘·스크린샷·기능 그래픽)

---

## 4. 스토어 등록 정보

### 4.1 기본

- 앱 이름: **지하철 여행자** / Subway Traveler
- 패키지: `com.actimedi.travle`
- 카테고리: 여행 및 지역정보 (Travel & Local)
- 태그: 지하철, 노선도, 여행 계획, 서울
- 지원 언어: 한국어, English, 日本語, 简体中文, 繁體中文

### 4.2 설명

**한국어 — 짧은 설명 (80자)**

> 수도권 지하철로 하루를 짜고, 노선도 위에서 확인하는 여행 계획 앱.

**한국어 — 전체 설명**

> 지하철로 하루를 도는 일정을 짭니다.
>
> • 출발·경유·머무름·종착을 지도에서 눌러 담습니다. 역 이름을 몰라도 됩니다.
> • 실제 열차 시각표에 맞춰 일정을 다시 계산합니다.
> • 이 일정으로 막차를 놓치는지 미리 봅니다.
> • 머무는 역마다 걸어서 다녀올 수 있는 곳을 알려줍니다.
> • 654개 역의 노선도를 역 이름과 함께 봅니다. 역과 노선을 누르면 그 역의 하루치
>   시간표가 나옵니다.
> • 한국어·영어·일본어·중국어(간체·번체)로 역 이름과 노선 이름까지 번역됩니다.
>
> 노선 자료: © OpenStreetMap 기여자 (ODbL 1.0)
> 도식 노선도: 서울교통공사 (공공누리 제1유형)

**English — short (80)**

> Plan a day on the Seoul subway and see it drawn on the map.

**English — full**

> Plan a day of riding the Seoul metropolitan subway.
>
> • Build the route by tapping the map — start, change, stay, or last stop. You do
>   not need to know the Korean station names.
> • Recalculate the plan against real train timetables.
> • Check whether the plan misses the last train.
> • For every stop where you linger, see places within walking distance.
> • Browse all 654 stations with their names. Tap a station and a line to read that
>   station's timetable for the day.
> • Station and line names in Korean, English, Japanese and Chinese.
>
> Network data: © OpenStreetMap contributors (ODbL 1.0)
> Diagram map: Seoul Metro (KOGL Type 1)

**日本語 — 短い説明**

> 首都圏の地下鉄で一日の行程を組み、路線図で確かめる旅の計画アプリ。

**简体中文 — 简短说明**

> 规划首尔地铁一日行程，并在线路图上查看。

**繁體中文 — 簡短說明**

> 規劃首爾地鐵一日行程，並在線路圖上查看。

### 4.3 Data safety 답안

- **수집하는 데이터: 없음.** 계정도 없고 로그인도 없다.
- **공유하는 데이터: 없음.** 분석·광고 SDK를 넣지 않았다(의존성 목록으로 확인 가능).
- **기기에 저장하는 것**: 사용자가 만든 경로(`files/routes.json`)와 시간표 캐시.
  앱을 지우면 사라진다. `allowBackup="true"`이므로 구글 백업으로 기기 이전 시 함께 옮겨진다.
- **네트워크로 나가는 것**: 역 이름과 노선 이름(공개 정보)을 서울시·공공데이터포털
  API에 보낸다. 사용자를 식별하는 값은 보내지 않는다.
- **위치**: 권한을 요구하지 않는다. 지도 앱을 열 때 검색어만 넘긴다.
- **전송 중 암호화**: 시간표 API는 HTTPS. 실시간 도착 API는 서울시가 HTTPS를 제공하지
  않아 평문이다 — 공개 교통정보이고 사용자 데이터는 담기지 않는다.

---

## 5. 릴리스 절차

```bash
# 1. 자산이 도구와 어긋나지 않는지
python3 tools/audit_order.py            # 되짚는 곳 0군데여야 한다

# 2. 시험
./gradlew :app:testDebugUnitTest

# 3. AAB (플레이에 올리는 것)
./gradlew :app:bundleRelease
#   → app/build/outputs/bundle/release/app-release.aab

# 4. 서명 확인
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk
```

버전을 올릴 때는 `app/build.gradle.kts`의 `versionCode`를 **반드시** 1 증가시킨다.
플레이는 같은 `versionCode`를 두 번 받지 않는다.

---

## 6. 넘겨 둔 자산

`~/Downloads/play-지하철여행자/`

- `icon-512.png` — 스토어 아이콘 512×512, 32비트 PNG
- `feature-graphic-1024x500.png` — 기능 그래픽
- `screenshots-ko/` — 1080×2400 다섯 장 (경로 · 노선도 · 시간표 · 가 볼 만한 곳 · 경로 지도)
- `app-release.aab` — 플레이에 올리는 것

## 7. 아직 만들지 않은 것

- **영어·일본어·중국어 스크린샷** — 한국어만 담았다. 로케일별로 올리는 편이 낫다.
  앱 안에서 언어를 바꿔(`adb shell cmd locale set-app-locales`) 같은 화면을 다시 담으면 된다.
- **7인치/10인치 태블릿 스크린샷** — 태블릿을 지원 기기로 둘 경우 필요.
