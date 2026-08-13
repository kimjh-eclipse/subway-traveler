# 지하철 여행자 — 핸드오프 문서

서울 지하철로 하루를 짜는 안드로이드 앱. 정거장을 순서대로 넣으면 시각·환승·요금을
셈해 주고, 실제 열차 시간표에 맞추고, 막차를 놓치는지 검사한다. 외국인 관광객까지
겨냥해 한국어·영어·일본어·중국어(간체/번체)를 지원한다.

- 저장소: https://github.com/kimjh-eclipse/subway-traveler (**공개**)
- Kotlin + Jetpack Compose(Material 3), AGP 내장 Kotlin, minSdk 26 / targetSdk 36
- 시험: `./gradlew :app:testDebugUnitTest` — 현재 100개, 전부 통과 상태로 유지할 것
- 빌드: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

## 지켜야 할 것 (보안)

- `.env`에 API 키가 있고 **절대 커밋하지 않는다** (`.env.example`이 안내서다).
  `keystore.properties`, `*.jks`, `local.properties`도 마찬가지. 전부 gitignore에 있다.
- 저장소가 공개라 **푸시하는 순간 공개**다. 키 값은 채팅·로그에도 옮겨 적지 않는다
  (길이나 해시로만 말한다).
- `network_security_config.xml`: 평문 HTTP는 `swopenapi.seoul.go.kr` 한 곳만 허용.

## 외부 API 둘

| 용도 | 끝점 | 키(.env) | 한도 | 주의 |
|---|---|---|---|---|
| 실시간 도착 | `swopenapi.seoul.go.kr` | `SEOUL_SUBWAY_API_KEY` (지하철인증키) | **1,000/일** | 일반인증키를 넣으면 ERROR-338. 한도를 이미 한 번 소진한 적 있다 — 전수 조사는 금물 |
| 열차 시간표 | `apis.data.go.kr/B553766/schedule` | `SEOUL_SUBWAY2_API_KEY` (인코딩 키 그대로) | 10,000/일 | 디코딩 키·이중 인코딩이면 코드 30 거부. `강남역`처럼 틀린 이름이면 **오류 없이 0건** |

두 API 모두 실패가 조용하다(0건·INFO-200). "오류가 안 났으니 맞다"고 믿으면 안 된다.

## 자료 자산 (app/src/main/assets/)

전부 도구로 만든다. **손으로 고치지 말고 도구를 고쳐 다시 돌릴 것.**

| 자산 | 내용 | 출처·라이선스 | 만드는 도구 |
|---|---|---|---|
| `subway_map.json` | 노선망: 654역, 24노선, 경로, 실측 역간시간 | OpenStreetMap (ODbL) + 서울교통공사 역간시간 (공공누리 1유형) | 최초 생성 도구는 저장소에 없음. 보수는 `dedupe_stations.py` → `rename_stations.py` → `prune_stations.py` |
| `schematic_map.json` | 도식 좌표 654/654, 선 1,135토막, 강 2덩이 | **서울교통공사 「수도권 지하철 노선도」 (공공누리 1유형)** — 변경·상업이용 허용 | `seoul_schematic.py` |
| `station_names.json` | 역 이름 영·일·중 표기 | OSM + Wikidata (CC0) | `station_names.py` |
| `transfer_points.json` | 환승 하차·승차 위치, 소요시간 (방향별) | 서울교통공사 환승 안내 | `transfer_points.py` |

### 도식 좌표 재생성 절차 (노선망이 바뀔 때마다)

```bash
# 1) 원자료 받기 — 서울 열린데이터광장 OA-22535, 2025-09-29판
curl -s -X POST 'https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do' \
     --data 'infId=OA-22535&infSeq=2&seq=2' -o seoul_ko.pdf
pdftocairo -svg seoul_ko.pdf seoul_ko.svg
pdftocairo -png -r 300 -singlefile seoul_ko.pdf seoul_hi

# 2) 역 이름 OCR (macOS Vision — 큰 그림은 줄여 읽으므로 조각으로 자른다)
swiftc -O -o tools/ocr_labels tools/ocr_labels.swift
python3 tools/ocr_map.py seoul_hi.png              # → seoul_hi.labels.json

# 3) 캐내기 (자리·선·강). 임자 없는 표시가 남으면 그 자리만 크게 다시 읽는다
python3 tools/seoul_schematic.py seoul_ko.svg seoul_hi.labels.json --spare spare.json
python3 tools/ocr_focus.py seoul_ko.pdf spare.json seoul_hi.labels.json
python3 tools/seoul_schematic.py seoul_ko.svg seoul_hi.labels.json
```

PDF는 벡터지만 역 이름이 외곽선으로 변환돼 있어 글자로는 여덟 낱말만 남는다.
그래서 자리는 벡터에서, 이름은 OCR로 읽어 **이름×노선×표시 모양**으로 전역 최적
배정(헝가리안)한다. 좌표는 이름이 아니라 **순번**으로 담는다 — 같은 이름의 역이
있어(양평×2) 이름으로는 안 된다. 그래서 노선망 순번이 바뀌면 반드시 다시 돌린다.

### 노선망 보수 도구 (순서 중요)

1. `dedupe_stations.py` — 갈라진 같은 역 합치기. 600m 안 + 같은 이름(별칭 포함).
   별칭: 이수→총신대입구(한 역의 노선별 이름), 서구청→서해구청(개칭).
2. `rename_stations.py` — 지금 쓰이는 이름 못박기. 어떤 출처가 옛 이름을 가져와도
   이 목록이 마지막에 이긴다. (서구청→서해구청, 2026-07-01 인천시 고시)
3. `prune_stations.py` — 여객 운행 없는 역 빼기. 도라산(월 1회 예약 관광열차뿐).
   중간 역을 빼려 하면 노선이 끊기므로 도구가 거부한다.
4. `audit_names.py` — 역명 전수 대조. **이름이 아니라 좌표로** 오늘 자 OSM과 맞춰
   "같은 자리, 다른 이름"(개칭)을 잡는다. 자료를 고치지 않고 판단 자료만 내놓는다.

## 도식 노선도의 내력 (왜 이 출처인가)

1. 처음엔 MIT SVG(Sinseiki)를 썼다 — 역 자리만 있고 선이 토막이라 도식처럼 안 보였다.
2. 위키미디어 CC BY-SA 4.0 자료를 검토했다 — 동일조건 의무가 무거워 보류.
3. **서울 열린데이터광장 OA-22535가 정답이었다**: 사용자가 원하던 바로 그 공식
   그림이면서 공공누리 **1유형**(변경·상업이용 허용). 서울시 홈페이지의 같은 그림은
   4유형(변경금지)이니 혼동하지 말 것.

앱은 배경 그림을 깔지 않고 캐낸 벡터를 직접 그린다 — 확대해도 안 뭉개지고,
역 이름을 우리 다국어 자료로 붙일 수 있다.

## 조심해야 했던 함정들 (재발 주의)

- **출처는 낡는다.** 공식 노선도(2025-09판)를 믿고 OSM의 서해구청을 유령으로 몰아
  거꾸로 병합했던 일이 있다. 개칭(2026-07-01)은 OSM이 먼저 반영한다. 의심되면
  `audit_names.py` + 웹 확인.
- **공식 그림에도 작도 오류가 있다.** 동수역 원이 선에서 90px 떨어져 있었다.
  `seoul_schematic.py`가 40px 넘게 뜬 역을 선 위로 끌어다 놓는다.
- **자산 시험이 기하를 지킨다.** `SchematicMapAssetTest`의 "역은 자기 노선 위에
  앉아 있다"가 자리 밀림(왕십리가 마장 자리를 차지하는 류)을 이름 대조 없이 잡는다.
  이 시험이 동수 오류도 찾아냈다.
- **화면 검증은 확대까지.** 축소 렌더만 보다가 반투명 토막 겹침 얼룩을 놓쳤다.
  도식 선은 토막이 아니라 **한 획(Path)** 으로 그려야 한다(`PlacedStroke`).
- **시간표·환승표는 괄호 병기 이름을 안다.** 총신대입구(이수)는 4호선에선
  총신대입구, 7호선에선 이수다. `SeoulTimetable.variants()`와
  `TransferTable.keysOf()`가 괄호 안 이름도 후보로 쓴다.

## 화면 구조 (지도 관련)

- `RouteMapScreen` — 경로 화면 「지도」: 전체 도식 위에 내 경로를 굵게. 칩: 이 경로 /
  전체 노선도 / 지리↔도식.
- `StationPickerScreen` — 편집기 「노선도」: 눌러서 역 고르기. 여는 위치는
  ① 그 정거장의 이름 → ② 앞 정거장 → ③ 없으면 도심 4배(`FirstOpenZoomFactor`).
  역 근처는 8배(`PickerZoomFactor`). 라벨은 절대 배율 1.4부터, 점은 2.2부터.
- `SubwayMapView` — 공용 캔버스. 그리는 순서: 강(`PlacedWater`) → 도식 선
  (`PlacedStroke`, 한 획) → 역 점 → 경로 → 라벨.

## 하다 만 것·다음 후보

- **전체 보기에서 역 이름이 안 보인다** — 확대해야 나온다. 참고 그림(공식 노선도)은
  항상 이름이 보인다. 축소 단계에서 주요 역만이라도 라벨을 얹는 작업이 다음 후보.
- **역번호·다국어 병기** — 관광객은 "223" 같은 역번호로 길을 찾는다. 자료에 없음.
- **신촌 문제(미결)** — OSM이 2호선 신촌과 경의중앙선 신촌(450m, 환승 아님)을
  한 노드로 뭉쳐 놨을 가능성을 이전에 확인했다. 길찾기가 없는 환승을 만들 수 있다.
- **교통패스 손익 계산** — 기후동행카드 외국인 사용 가능 여부 조사가 중간에 끊겼다.
- 에뮬레이터 검증 흐름: `adb install -r` → 화면 조작 → `exec-out screencap`.
  기존 설치가 디버그 서명이면 지우고 설치해야 한다.

## 일하는 방식 (이 프로젝트에서 합의된 것)

- **"됐습니다" 대신 그림을 먼저.** 렌더나 스크린샷을 보여 주고 판단은 사용자가 한다.
- 자산을 만들면 원본 위에 겹쳐 그려 눈으로 검증하고, 기하 시험으로 못박는다.
- 지울 때는 까닭을 코드에 남긴다 — 그것이 나중에 되살릴 판단 기준이다.
- 커밋 메시지는 한국어로, 무엇보다 **왜**를 적는다. 틀렸던 것을 고칠 때는 틀린
  까닭도 적는다.
- APK는 `~/Downloads/지하철여행자-<커밋해시>.apk`로 내놓고 옛것은 지운다.
