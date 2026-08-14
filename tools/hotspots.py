#!/usr/bin/env python3
"""역마다 '내려서 뭘 할까'를 담은 자산을 만든다.

머무는 정거장 카드에 붙일 거리다. 지금은 지도 앱을 열어 주기만 해서, 처음 오는
사람은 무엇을 검색해야 하는지조차 모른다.

## 왜 가게가 아니라 동네인가

가게는 문을 닫는다. 앱에 구워 넣은 맛집 목록은 반년이면 절반이 틀리고, 관광객을
없어진 가게 앞으로 보낸다. 그것이 아무것도 안 알려주는 것보다 나쁘다.

동네와 명소는 안 없어진다. 성수동 카페거리도 광장시장도 경복궁도 내년 그 자리에
있다. 그래서 **어디로 갈지는 자산이 알려주고, 지금 뭐가 좋은지는 지도 앱에
넘긴다** — 검색어까지 쥐여 주면 그 다음은 최신 자료가 답한다.

## 만드는 방법

씨앗 목록은 손으로 적는다(아래 SEED). 사실만 적는다 — 이름·역·갈래·검색어.
설명 문장은 남의 큐레이션을 옮기지 않고 직접 쓴다.

좌표와 다국어 이름은 **OpenStreetMap에서 확인해 채운다**. 노선망과 같은
출처(ODbL)라 라이선스가 일관되고, 무엇보다 내가 지어낸 좌표를 자산에 남기지
않는다. OSM에서 못 찾는 자리(`망리단길` 같은 별명 골목)는 역 좌표에 앉히고
`area`로 표시한다 — 점이 아니라 동네라는 뜻이다.

기준 날짜를 자산에 박는다. 이 자료는 늙는다는 것을 화면에서도 알 수 있어야 한다.

    python3 tools/hotspots.py

나중에 한국관광공사 TourAPI로 갈아탈 자리다. 그때는 이 자산이 씨앗으로 남는다.
"""
import json
import math
import pathlib
import re
import time
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"
OUT = ROOT / "app/src/main/assets/hotspots.json"
CACHE = ROOT / "tools/.osm_places.json"

OVERPASS = "https://overpass-api.de/api/interpreter"
AGENT = {"User-Agent": "subway-traveler/1.0 (tourist spots)"}
AS_OF = "2026-08"

# 걷는 속도(m/분). 지하철 출구를 찾아 나가는 시간까지 치면 이 정도가 현실적이다.
WALK_METRES_PER_MINUTE = 70.0

# 갈래. 화면에서 칩으로 쓴다.
AREA, LANDMARK, MARKET, PARK, PALACE, SHOPPING = "area", "landmark", "market", "park", "palace", "shopping"

# 씨앗. `역: [(한국어 이름, 갈래, 지도 검색어, 영문 이름, OSM 이름)]`
#
# 영문 이름은 OSM 에 없을 때만 쓰는 대비책이다. OSM 에 있으면 그쪽이 이긴다 —
# 관광 안내판과 표기를 맞추는 편이 낫다.
#
# 다섯째 칸은 OSM 이 다르게 부를 때만 적는다(`서울로7017` → `서울로 7017`).
# 비워 두면 한국어 이름 그대로 찾는다. 찾을 때는 **완전 일치**라 `북촌`이
# `북촌손만두`에 붙을 일은 없다 — 못 찾으면 그냥 역 앞으로 둔다.
SEED = {
    "명동": [
        ("명동거리", AREA, "명동 쇼핑", "Myeongdong Street"),
        ("명동성당", LANDMARK, "명동성당", "Myeongdong Cathedral"),
    ],
    "회현": [("남대문시장", MARKET, "남대문시장", "Namdaemun Market")],
    "홍대입구": [
        ("홍대 걷고싶은거리", AREA, "홍대 거리", "Hongdae Street"),
        ("연남동 경의선숲길", PARK, "연남동", "Yeonnam-dong Gyeongui Line Forest Park", "경의선숲길공원"),
    ],
    "상수": [("상수동 카페거리", AREA, "상수동 카페", "Sangsu Cafe Street")],
    "망원": [("망원시장", MARKET, "망원시장", "Mangwon Market")],
    "성수": [("성수동 카페거리", AREA, "성수동 카페", "Seongsu Cafe Street")],
    "뚝섬": [("서울숲", PARK, "서울숲", "Seoul Forest")],
    "건대입구": [("커먼그라운드", SHOPPING, "커먼그라운드", "Common Ground")],
    "강남": [("강남역 지하상가", SHOPPING, "강남 쇼핑", "Gangnam Station Underground Shopping")],
    "신사": [("가로수길", AREA, "가로수길", "Garosu-gil")],
    "압구정로데오": [("압구정 로데오거리", AREA, "압구정 로데오", "Apgujeong Rodeo Street", "압구정로데오거리")],
    "삼성": [("코엑스", SHOPPING, "코엑스", "COEX")],
    "봉은사": [("봉은사", LANDMARK, "봉은사", "Bongeunsa Temple")],
    "잠실": [
        ("롯데월드", LANDMARK, "롯데월드", "Lotte World"),
        ("석촌호수", PARK, "석촌호수", "Seokchon Lake"),
    ],
    "경복궁": [("경복궁", PALACE, "경복궁", "Gyeongbokgung Palace")],
    "안국": [
        ("북촌한옥마을", AREA, "북촌한옥마을", "Bukchon Hanok Village", "북촌마을안내소"),
        ("인사동", AREA, "인사동", "Insa-dong"),
    ],
    "종로3가": [("익선동 한옥거리", AREA, "익선동", "Ikseon-dong Hanok Street", "익선동 한옥마을 Ikseondong")],
    "종로5가": [("광장시장", MARKET, "광장시장", "Gwangjang Market")],
    "을지로3가": [("을지로 골목", AREA, "을지로 노포", "Euljiro Alleys")],
    "동대문역사문화공원": [("동대문디자인플라자", LANDMARK, "DDP", "Dongdaemun Design Plaza")],
    "시청": [("덕수궁", PALACE, "덕수궁", "Deoksugung Palace")],
    "광화문": [("광화문광장", LANDMARK, "광화문광장", "Gwanghwamun Square")],
    "이태원": [("이태원 거리", AREA, "이태원", "Itaewon Street")],
    "충무로": [("남산골한옥마을", AREA, "남산골한옥마을", "Namsangol Hanok Village")],
    "혜화": [("대학로", AREA, "대학로", "Daehak-ro")],
    "신촌": [("신촌 연세로", AREA, "신촌 거리", "Sinchon Yonsei-ro", "연세로")],
    "여의도": [("여의도한강공원", PARK, "여의도한강공원", "Yeouido Hangang Park")],
    "노량진": [("노량진수산시장", MARKET, "노량진수산시장", "Noryangjin Fish Market")],
    "고속터미널": [("반포한강공원", PARK, "반포한강공원", "Banpo Hangang Park")],
    "서울역": [("서울로7017", PARK, "서울로7017", "Seoullo 7017", "서울로 7017")],
}


def normalise(name):
    return re.sub(r"\s", "", name).split("(")[0].removesuffix("역")


def haversine(lat1, lon1, lat2, lon2):
    r = 6_371_000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def fetch_places(names):
    """이름으로 서울 안을 뒤진다. 한 번 받아 캐시에 둔다."""
    if CACHE.exists():
        return json.loads(CACHE.read_text(encoding="utf-8"))

    escaped = "|".join(re.escape(n) for n in sorted(names))
    query = f"""
    [out:json][timeout:180];
    (
      node["name"~"^({escaped})$"](37.40,126.75,37.72,127.20);
      way["name"~"^({escaped})$"](37.40,126.75,37.72,127.20);
      relation["name"~"^({escaped})$"](37.40,126.75,37.72,127.20);
    );
    out tags center;
    """
    request = urllib.request.Request(
        OVERPASS, data=urllib.parse.urlencode({"data": query}).encode(), headers=AGENT
    )
    elements = json.load(urllib.request.urlopen(request, timeout=200))["elements"]
    CACHE.write_text(json.dumps(elements, ensure_ascii=False), encoding="utf-8")
    return elements


def main():
    stations = json.loads(NETWORK.read_text(encoding="utf-8"))["stations"]
    by_station = {normalise(s["n"]): s for s in stations}

    # 다섯째 칸이 있으면 그 이름으로, 없으면 한국어 이름 그대로 찾는다.
    osm_name = {
        entry[0]: (entry[4] if len(entry) > 4 else entry[0])
        for entries in SEED.values()
        for entry in entries
    }
    wanted = set(osm_name.values())
    elements = fetch_places(wanted)

    # 이름이 같은 것이 여럿이면 역에서 가까운 쪽을 고른다.
    found = {}
    for element in elements:
        tags = element.get("tags", {})
        name = tags.get("name")
        if name not in wanted:
            continue
        centre = element.get("center") or element
        lat, lon = centre.get("lat"), centre.get("lon")
        if lat is None or lon is None:
            continue
        found.setdefault(name, []).append((lat, lon, tags))

    spots, missing = [], []
    for station_name, entries in SEED.items():
        station = by_station.get(normalise(station_name))
        if station is None:
            missing.append(f"{station_name}(역을 못 찾음)")
            continue

        for entry in entries:
            name, kind, query, english = entry[:4]
            candidates = found.get(osm_name[name], [])
            best = min(
                candidates,
                key=lambda c: haversine(station["y"], station["x"], c[0], c[1]),
                default=None,
            )
            spot = {"n": name, "st": station["n"], "c": kind, "q": query}

            if best is None:
                # 자리를 못 박은 것. 역 앞으로 둔다 — 갈래는 그대로다. 좌표를 모르는
                # 것과 그것이 시장인지 공원인지는 다른 이야기다.
                spot |= {"y": station["y"], "x": station["x"], "w": 0}
                spot["e"] = english
                missing.append(f"{name} → OSM `{osm_name[name]}` 없음 · 역 앞으로")
            else:
                lat, lon, tags = best
                metres = haversine(station["y"], station["x"], lat, lon)
                if metres > 1_500:
                    missing.append(f"{name}({int(metres)}m · 너무 멀어 뺌)")
                    continue
                spot |= {
                    "y": round(lat, 5),
                    "x": round(lon, 5),
                    "w": max(1, round(metres / WALK_METRES_PER_MINUTE)),
                }
                for key, tag in (("e", "name:en"), ("j", "name:ja"), ("s", "name:zh")):
                    if tags.get(tag):
                        spot[key] = tags[tag]
                spot.setdefault("e", english)
            spots.append(spot)

    OUT.write_text(
        json.dumps(
            {
                "asOf": AS_OF,
                "source": "© OpenStreetMap contributors (ODbL 1.0)",
                "spots": spots,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )
    print(f"{len(spots)}곳 · 역 {len({s['st'] for s in spots})}개 · {OUT.stat().st_size // 1024} KB")
    if missing:
        print("확인이 필요한 것:")
        for m in missing:
            print("   ", m)


if __name__ == "__main__":
    main()
