# 개인정보처리방침 · Privacy Policy

**지하철 여행자 (Subway Traveler)** · `com.actimedi.travle`

최종 수정: 2026-09-09

---

## 한국어

### 수집하는 개인정보

**없습니다.** 이 앱은 계정을 만들지 않고 로그인도 요구하지 않습니다. 이름, 전화번호,
이메일, 광고 식별자, 위치 정보를 수집하거나 전송하지 않습니다.

### 기기에 저장되는 것

- 사용자가 만든 경로(역 이름, 시각, 메모)
- 조회한 열차 시간표의 임시 사본

둘 다 기기 안에만 있고, 앱을 삭제하면 함께 사라집니다. 안드로이드 백업이 켜져 있으면
기기를 바꿀 때 구글 계정을 통해 함께 옮겨질 수 있습니다. 이 자료는 개발자에게
전송되지 않습니다.

### 외부로 나가는 통신

열차 시각과 실시간 도착 정보를 받기 위해 다음에 요청을 보냅니다.

| 대상 | 보내는 것 |
|---|---|
| 서울특별시 열린데이터광장 (`swopenapi.seoul.go.kr`) | 역 이름 |
| 공공데이터포털 (`apis.data.go.kr`) | 역 이름, 노선 이름, 요일 |

역 이름과 노선 이름은 공개 정보이며, 사용자를 식별할 수 있는 값은 함께 보내지
않습니다. 실시간 도착 API는 제공 기관이 HTTPS를 지원하지 않아 평문으로 통신합니다.

지도 앱(구글 지도·네이버 지도)을 열 때는 검색어와 좌표만 넘깁니다. 그 뒤의 처리는
해당 앱의 개인정보처리방침을 따릅니다.

### 권한

- **인터넷 / 네트워크 상태** — 위의 통신에만 씁니다.

위치, 저장소, 연락처, 카메라 권한은 요구하지 않습니다.

### 광고·분석

광고를 넣지 않았고, 분석·추적 도구를 넣지 않았습니다.

### 아동

만 14세 미만 아동을 대상으로 하지 않으며, 아동의 개인정보를 알면서 수집하지 않습니다.

### 문의

jh.kim@actimedi.com

---

## English

### Personal data we collect

**None.** The app has no accounts and no sign-in. It does not collect or transmit
names, phone numbers, email addresses, advertising identifiers, or location.

### What is stored on your device

- The routes you create (station names, times, notes)
- A temporary copy of timetables you have looked up

Both stay on the device and are removed when you uninstall the app. If Android
Backup is enabled, they may be carried to a new device through your Google account.
This data is never sent to the developer.

### Network requests

To fetch train times and live arrivals, the app sends requests to:

| Endpoint | What is sent |
|---|---|
| Seoul Open Data Plaza (`swopenapi.seoul.go.kr`) | Station name |
| Korea Public Data Portal (`apis.data.go.kr`) | Station name, line name, day type |

Station and line names are public information; no identifier for you is sent with
them. The live-arrivals API is served over plain HTTP because the provider does not
offer HTTPS.

Opening a map app (Google Maps, Naver Map) passes only a search term and
coordinates. What happens after that is governed by that app's privacy policy.

### Permissions

- **Internet / network state** — used only for the requests above.

The app does not request location, storage, contacts, or camera access.

### Ads and analytics

The app contains no advertising and no analytics or tracking SDKs.

### Children

The app is not directed at children under 14 and does not knowingly collect
personal data from them.

### Contact

jh.kim@actimedi.com
