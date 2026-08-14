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

좌표와 다국어 이름은 **OpenStreetMap에서 확인해 채운다**. OSM이 명소에
`name:ja`·`name:zh`를 잘 달지 않아, 비면 그 자리에 붙은 `wikidata` 태그를 따라가
라벨을 가져온다(역 이름과 같은 방식). 중국어는 한 벌만 구해 [zhconv]로 간체·번체를
각각 만든다 — 출처마다 어느 쪽인지 제각각이라 그대로 두면 번체 사용자에게 간체가 간다.

    python3 -m venv .venv && .venv/bin/pip install zhconv
    .venv/bin/python3 tools/hotspots.py

 노선망과 같은
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
import sys
import urllib.error
import urllib.parse
import urllib.request

try:
    import zhconv
except ImportError:  # pragma: no cover
    sys.exit("zhconv 가 필요하다: python3 -m venv .venv && .venv/bin/pip install zhconv")

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"
OUT = ROOT / "app/src/main/assets/hotspots.json"
CACHE = ROOT / "tools/.osm_places.json"
WD_CACHE = ROOT / "tools/.wikidata_spots.json"

OVERPASS = "https://overpass-api.de/api/interpreter"
WIKIDATA = "https://www.wikidata.org/w/api.php"
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
        ("연남동 경의선숲길", PARK, "연남동", "Gyeongui Line Forest Park", "경의선숲길공원"),
    ],
    "상수": [("상수동 카페거리", AREA, "상수동 카페", "Sangsu Cafe Street")],
    "망원": [("망원시장", MARKET, "망원시장", "Mangwon Market")],
    "성수": [("성수동 카페거리", AREA, "성수동 카페", "Seongsu Cafe Street")],
    "뚝섬": [("서울숲", PARK, "서울숲", "Seoul Forest")],
    "건대입구": [("커먼그라운드", SHOPPING, "커먼그라운드", "Common Ground")],
    "강남": [("강남역 지하상가", SHOPPING, "강남 쇼핑", "Gangnam Underground Mall")],
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


# 괄호 안이 가나·로마자뿐이면 그것은 읽는 법이지 이름이 아니다.
READING_IN_PARENS = re.compile(r"[（(][\u3040-\u30ff\uff66-\uff9fA-Za-z\s・･-]+[)）]")


def tidy_japanese(text):
    """`キョンボックン(景福宮)` → `景福宮`, `光化門(クァンファムン)広場` → `光化門広場`.

    음차와 한자를 나란히 적는 표기가 흔하다. 화면은 좁고, 한자 쪽이 짧고 읽기 쉽다.
    """
    if not text:
        return None
    text = text.strip()
    # 앞이 통째로 음차면 뒤가 이름이다 — `ソウルスプ(ソウルの森)`.
    whole = re.fullmatch(r"([^()（）]+)[（(](.+)[)）]", text)
    if whole and re.fullmatch(r"[\u30a0-\u30ff\uff66-\uff9fA-Za-z\s・･-]+", whole.group(1)):
        return whole.group(2).strip() or None
    return READING_IN_PARENS.sub("", text).strip() or None


def tidy_chinese(text):
    """`奉恩寺站` → `奉恩寺`.

    같은 이름의 요소를 합치다 보면 **지하철역**이 섞여 든다. 봉은사역의 중국어는
    `奉恩寺站`인데, 우리가 가리키는 것은 절이지 역이 아니다.
    """
    if not text:
        return None
    text = text.strip()
    if len(text) > 1 and text.endswith(("站", "驛", "驿")):
        text = text[:-1]
    return text or None


def merge_tags(candidates):
    """같은 이름의 요소가 여럿이면 이름표를 합친다.

    좌표는 역에서 가까운 것을 써야 하지만 이름은 그렇지 않다. 경복궁에서 역에
    가장 가까웠던 것은 `{"name": "경복궁"}`뿐인 맨 노드였고, `名:ja`·`name:zh`를
    들고 있는 요소는 조금 더 멀리 있었다. 가까운 쪽을 먼저 쓰되 빈 칸은 뒤엣것이
    메운다 — 같은 역 반경 안에서 이름까지 똑같으면 같은 곳으로 본다.
    """
    merged = {}
    for _, _, tags in candidates:
        for key, value in tags.items():
            merged.setdefault(key, value)
    return merged


def chinese_from_tags(tags):
    """어느 칸에 들어 있든 한 벌만 꺼낸다. 간체·번체는 뒤에서 만든다."""
    return tags.get("name:zh-Hans") or tags.get("name:zh") or tags.get("name:zh-Hant")


def wikidata_labels(ids):
    """`wikidata` 태그를 따라가 일본어·중국어 라벨을 가져온다. 45개씩 끊어 부른다."""
    out = {}
    for start in range(0, len(ids), 45):
        chunk = "|".join(ids[start : start + 45])
        url = (
            f"{WIKIDATA}?action=wbgetentities&format=json&props=labels"
            f"&languages=ja|zh|zh-hans|zh-cn|zh-hant|zh-tw|zh-hk&ids={chunk}"
        )
        entities = get_json(url).get("entities") or {}
        for qid, entity in entities.items():
            labels = entity.get("labels") or {}
            row = {}
            if "ja" in labels:
                row["j"] = labels["ja"]["value"]
            for code in ("zh-hans", "zh-cn", "zh", "zh-hant", "zh-tw", "zh-hk"):
                if code in labels:
                    row["zh"] = labels[code]["value"]
                    break
            if row:
                out[qid] = row
        time.sleep(0.4)
    return out


def get_json(url):
    """위키데이터는 몰아치면 429를 준다. 물러섰다 다시 묻는다."""
    for attempt in range(5):
        try:
            return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=AGENT), timeout=60))
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 4:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError("unreachable")


def wikidata_search(term):
    """한국어 이름으로 위키데이터를 찾는다. 후보만 돌려주고 고르지는 않는다."""
    url = (
        f"{WIKIDATA}?action=wbsearchentities&format=json&type=item"
        f"&language=ko&uselang=ko&limit=5&search={urllib.parse.quote(term)}"
    )
    return [item["id"] for item in (get_json(url).get("search") or [])]


def wikidata_placed(ids):
    """라벨과 함께 좌표(P625)를 가져온다. 좌표가 없는 항목은 버린다."""
    out = {}
    for start in range(0, len(ids), 45):
        chunk = "|".join(ids[start : start + 45])
        url = (
            f"{WIKIDATA}?action=wbgetentities&format=json&props=labels|claims"
            f"&languages=ja|zh|zh-hans|zh-cn|zh-hant|zh-tw|zh-hk&ids={chunk}"
        )
        entities = get_json(url).get("entities") or {}
        for qid, entity in entities.items():
            claims = ((entity.get("claims") or {}).get("P625") or [])
            value = claims[0]["mainsnak"].get("datavalue", {}).get("value") if claims else None
            if not value:
                continue
            labels = entity.get("labels") or {}
            row = {"y": value["latitude"], "x": value["longitude"]}
            if "ja" in labels:
                row["j"] = labels["ja"]["value"]
            for code in ("zh-hans", "zh-cn", "zh", "zh-hant", "zh-tw", "zh-hk"):
                if code in labels:
                    row["zh"] = labels[code]["value"]
                    break
            out[qid] = row
        time.sleep(0.4)
    return out


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

    spots, missing, pending, weak = [], [], [], {}
    for station_name, entries in SEED.items():
        station = by_station.get(normalise(station_name))
        if station is None:
            missing.append(f"{station_name}(역을 못 찾음)")
            continue

        for entry in entries:
            name, kind, query, english = entry[:4]
            candidates = sorted(
                found.get(osm_name[name], []),
                key=lambda c: haversine(station["y"], station["x"], c[0], c[1]),
            )
            best = candidates[0] if candidates else None
            spot = {"n": name, "st": station["n"], "c": kind, "q": query}

            if best is None:
                # 자리를 못 박은 것. 역 앞으로 둔다 — 갈래는 그대로다. 좌표를 모르는
                # 것과 그것이 시장인지 공원인지는 다른 이야기다.
                spot |= {"y": station["y"], "x": station["x"], "w": 0}
                spot["e"] = english
                missing.append(f"{name} → OSM `{osm_name[name]}` 없음 · 역 앞으로")
            else:
                lat, lon, _ = best
                tags = merge_tags(candidates)
                metres = haversine(station["y"], station["x"], lat, lon)
                if metres > 1_500:
                    missing.append(f"{name}({int(metres)}m · 너무 멀어 뺌)")
                    continue
                spot |= {
                    "y": round(lat, 5),
                    "x": round(lon, 5),
                    "w": max(1, round(metres / WALK_METRES_PER_MINUTE)),
                }
                # 씨앗이 OSM 이름을 따로 적었다면 그 요소는 **자리를 잡아 주는 대리**라
                # 이름을 그대로 믿을 수 없다 — 북촌한옥마을의 자리를 잡아 준
                # `북촌마을안내소`의 `name:ja`는 `…韓屋村の案内所`였다. 안내소에 가라고
                # 한 적이 없다. 그렇다고 버리면 `연세로`의 `延世路`까지 함께 잃는다.
                # 대리의 이름은 **약한 후보**로 미뤄 두었다가, 위키데이터가 아무것도
                # 못 가져왔을 때만 쓴다.
                names = {
                    "j": tidy_japanese(tags.get("name:ja")),
                    "zh": tidy_chinese(chinese_from_tags(tags)),
                }
                names = {k: v for k, v in names.items() if v}
                if len(entry) > 4:
                    weak[len(spots)] = names
                else:
                    spot |= names
                # OSM이 비워 둔 것은 위키데이터에 물어본다. 이 자리에 붙은 QID라
                # 이름으로 검색해 엉뚱한 동명이인을 물어 올 위험이 없다.
                if tags.get("wikidata") and not (spot.get("j") and spot.get("zh")):
                    pending.append((len(spots), tags["wikidata"]))
                # 영문은 SEED가 이긴다. OSM에서 찾은 것은 '그 자리에 있는 물체'의
                # 이름이라 우리가 가리키려는 곳과 다를 때가 있다 — 북촌한옥마을의
                # 자리를 잡아 준 것은 `북촌마을안내소`였고, 그 `name:en`은
                # `Bukchon Hanok Village Information Center`였다. 안내소에 가라고
                # 한 적이 없다. OSM 이름은 SEED가 비었을 때만 쓴다.
                spot["e"] = english or tags.get("name:en")
            spots.append(spot)

    # 위키데이터는 비어 있는 칸만 메운다. OSM 표기가 있으면 그쪽이 이긴다 —
    # 현지 안내판과 맞을 확률이 높다.
    labels = wikidata_labels(sorted({qid for _, qid in pending}))
    for index, qid in pending:
        row = labels.get(qid) or {}
        if row.get("j"):
            spots[index].setdefault("j", tidy_japanese(row["j"]))
        if row.get("zh"):
            spots[index].setdefault("zh", tidy_chinese(row["zh"]))

    # 아직 빈 칸은 이름으로 찾아본다. 이름 검색은 동명이인을 물어 오므로 **좌표로
    # 거른다** — 찾아온 항목이 그 자리에서 1.5km 안에 있어야 받아들인다. `명동거리`
    # 처럼 OSM에 없는 골목은 위키데이터에도 그 이름이 없어서 동네(`명동`)가 걸리는데,
    # 같은 곳을 가리키므로 그대로 쓴다.
    hits = json.loads(WD_CACHE.read_text(encoding="utf-8")) if WD_CACHE.exists() else {}
    searched = []
    for index, spot in enumerate(spots):
        if spot.get("j") and spot.get("zh"):
            continue
        if spot["n"] not in hits:
            hits[spot["n"]] = wikidata_search(spot["n"])
            time.sleep(1.0)
        searched += [(index, qid) for qid in hits[spot["n"]]]
    WD_CACHE.write_text(json.dumps(hits, ensure_ascii=False), encoding="utf-8")

    placed = wikidata_placed(sorted({qid for _, qid in searched}))
    taken = set()
    for index, qid in searched:
        spot, row = spots[index], placed.get(qid)
        if not row or index in taken:
            continue
        if haversine(spot["y"], spot["x"], row["y"], row["x"]) > 1_500:
            continue
        if not (row.get("j") or row.get("zh")):
            continue
        taken.add(index)
        if row.get("j"):
            spot.setdefault("j", tidy_japanese(row["j"]))
        if row.get("zh"):
            spot.setdefault("zh", tidy_chinese(row["zh"]))

    # 위키데이터가 못 채운 자리를 대리의 이름으로 메운다.
    for index, names in weak.items():
        for key, value in names.items():
            spots[index].setdefault(key, value)

    # 중국어는 여기서 한 벌을 간체·번체로 나눈다.
    for spot in spots:
        chinese = spot.pop("zh", None)
        if chinese:
            spot["s"] = zhconv.convert(chinese, "zh-hans")
            spot["t"] = zhconv.convert(chinese, "zh-hant")

    for spot in spots:
        holes = [tag for tag, key in (("일", "j"), ("중", "s")) if not spot.get(key)]
        if holes:
            missing.append(f"{spot['n']} → {'·'.join(holes)} 없음 · 한국어로 보인다")

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
