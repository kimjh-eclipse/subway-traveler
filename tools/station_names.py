#!/usr/bin/env python3
"""역 이름의 영어·일본어·중국어 표기를 모아 자산으로 만든다.

노선망은 역 이름을 한국어로만 들고 있다. 화면을 영어나 일본어로 봐도 `강남`,
`군자`가 그대로 나오는데, 그러면 다국어를 지원해도 정작 여행에 필요한 이름은
읽을 수 없다. 그래서 이름표를 따로 만든다.

출처는 셋이고, 앞의 것부터 쓴다.

1. OpenStreetMap — 노선도와 같은 출처(ODbL)라 라이선스가 일관된다.
   `name:en`, `name:ja`, `name:zh`(및 Hans/Hant)를 그대로 읽는다.
2. Wikidata — 중국어가 비어 있으면 `wikidata` 태그를 따라가 라벨을 가져온다.
3. 일본어 표기의 괄호 한자 — 마지막 수단이다. OSM의 한국 역은 `カンナム(江南)`
   꼴이라 괄호에서 한자를 건질 수 있지만, 고유어 역명은 괄호 안이 한자가 아니라
   **일본어 번역**이다. `オリニデゴンウォン(子供大公園)`의 `子供`는 중국어가
   아니므로, 위키데이터가 있으면 그쪽을 먼저 쓴다.

중국어는 한 벌만 구해서 [zhconv]로 간체·번체를 각각 만든다. 출처마다 간체와
번체가 섞여 있어(母岳斋 / 鶴灘) 그대로 두면 한 화면에 두 서체가 나온다.

    python3 -m venv .venv && .venv/bin/pip install zhconv
    .venv/bin/python tools/station_names.py

결과는 `app/src/main/assets/station_names.json`. 노선망 자산은 건드리지 않는다 —
이름표는 한국어로 볼 때 읽을 필요가 없으므로 따로 두고 필요할 때만 읽는다.
"""
import json
import math
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from cjk import tidy_chinese, tidy_japanese

try:
    import zhconv
except ImportError:
    sys.exit("zhconv 가 필요하다: pip install zhconv")

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"
OUT = ROOT / "app/src/main/assets/station_names.json"
CACHE = ROOT / "tools/.osm_stations.json"

OVERPASS = "https://overpass-api.de/api/interpreter"
WIKIDATA = "https://www.wikidata.org/w/api.php"
AGENT = {"User-Agent": "subway-traveler/1.0 (station name table)"}

# 수도권 전체 — 남으로 신창, 북으로 연천, 동으로 춘천·여주, 서로 인천공항.
BBOX = "36.55,126.20,38.25,127.95"

HANJA_IN_PARENS = re.compile(r"[（(]([一-鿿・･\s]+)[)）]")


# OSM이 틀린 자리. 위키데이터의 영문 라벨과 전수 대조해 찾았고, 표기 방식 차이가
# 아니라 **다른 역의 이름**이 붙은 것만 골랐다.
#
#   용인중앙시장 — `name:en` 이 이웃한 운동장·송담대의 영문으로 잘못 달려 있다.
#   일원·답십리 — 국어의 로마자 표기법에서 어긋난다(Irwon·Dapsimni가 맞다).
#
# 고치면 여기서 지운다. 규칙으로 풀 수 없어 손으로 적어 두는 자리다.
ENGLISH_OVERRIDES = {
    # 위와 같은 자리. OSM은 `City hall.Yongin Univ.` 로 적어 두었다.
    "시청·용인대": "City Hall·Yongin Univ.",
    "용인중앙시장": "Yongin Jungang Market",
    "일원": "Irwon",
    "답십리": "Dapsimni",
}

# 출처로는 메울 수 없는 자리. 규칙이 아니라 사실이라 손으로 적는다.
#
#   뚝섬유원지 — OSM 에 역 노드가 없다(`뚝섬`만 있다). 실시간 도착에는 나온다.
#   자양       — OSM 노드에 달린 wikidata 가 이웃 뚝섬유원지를 가리켜, 중국어가
#                `纛岛游园地`(뚝섬유원지)로 들어왔다.
# 위키데이터가 **뜻옮김**을 들고 있는 자리. 이름은 옮기는 것이 아니라 부르는 것이다.
#
#   보라매병원 — `獵鷹醫院`(사냥매 병원)으로 온다. `보라매`는 고유어라 한자가 없고,
#   서울교통공사 안내와 위키데이터의 옆 역(`보라매공원 → 波拉美公园`)은 음차를 쓴다.
#   같은 `보라매`가 한 화면에서 두 가지로 불리면 안 된다.
CHINESE_OVERRIDES = {
    "보라매병원": "波拉美醫院",
    # OSM의 `시청.용인대` 노드에 **삼가역의** `wikidata`(Q702622)가 달려 있어
    # 중국어가 `三街`(삼가)로 들어왔다. 제 항목은 Q702636이다.
    "시청·용인대": "市廳·龍仁大",
    # 위키데이터가 **틀린** 자리. 좌표는 맞는 신림선 서원역(Q97339831)인데 중국어
    # 라벨이 `炭岭站`(탄령)으로 붙어 있다. 역 이름은 관악구 서원동에서 왔고
    # 그 한자는 書院洞이다.
    "서원": "書院",
}

MANUAL = {
    "뚝섬유원지": {"e": "Ttukseom Resort", "j": "トゥクソムユウォンジ(纛島遊園地)", "zh": "纛島遊園地"},
    "자양": {"e": "Jayang", "j": "チャヤン(紫陽)", "zh": "紫陽"},
}
# `シンチョン(新村)[国鉄駅]` 처럼 뒤에 붙는 구분용 꼬리표.
DISAMBIGUATION = re.compile(r"[\[［][^\]］]*[\]］]")


def normalise(name):
    """`총신대입구 (이수)` 와 `총신대입구(이수)` 를 같은 자리에 놓는다.

    가운뎃점과 마침표도 같게 본다. 노선망은 `시청·용인대`, OSM은 `시청.용인대`라
    서로 못 찾아, 그 역만 영어·일본어·중국어가 통째로 비어 있었다.
    """
    plain = re.sub(r"[\s·・.]", "", name)
    return plain.split("(")[0].removesuffix("역")


def fetch_osm():
    if CACHE.exists():
        return json.loads(CACHE.read_text(encoding="utf-8"))

    query = f"""
    [out:json][timeout:300];
    (
      node["railway"~"^(station|halt)$"]({BBOX});
      way["railway"~"^(station|halt)$"]({BBOX});
    );
    out tags center;
    """
    request = urllib.request.Request(
        OVERPASS, data=urllib.parse.urlencode({"data": query}).encode(), headers=AGENT
    )
    elements = json.load(urllib.request.urlopen(request, timeout=320))["elements"]
    CACHE.write_text(json.dumps(elements, ensure_ascii=False), encoding="utf-8")
    return elements


def index_by_name(elements):
    """이름이 겹치면 다국어가 더 채워진 쪽을 남긴다."""
    best = {}
    for element in elements:
        tags = element.get("tags", {})
        name = tags.get("name") or tags.get("name:ko")
        if not name:
            continue
        filled = sum(
            1
            for key in ("name:en", "name:ja", "name:zh", "name:zh-Hans", "name:zh-Hant", "wikidata")
            if tags.get(key)
        )
        key = normalise(name)
        if key not in best or filled > best[key][0]:
            best[key] = (filled, tags)
    return {k: v[1] for k, v in best.items()}


def strip_tag(text):
    """같은 이름의 역을 가르려고 붙인 꼬리표는 화면에 쓸 것이 아니다.

    `シンチョン(新村)[国鉄駅]` → `シンチョン(新村)`. 괄호는 남긴다 — 중국어를
    못 구했을 때 그 안의 한자가 마지막 수단이기 때문이다.
    """
    if not text:
        return None
    return DISAMBIGUATION.sub("", text).strip() or None


def hanja_from_japanese(japanese):
    """`カンナム(江南)` → `江南`."""
    found = HANJA_IN_PARENS.search(japanese or "")
    return re.sub(r"[\s・･]", "", found.group(1)) if found else None


# 간체에서 한 글자로 합쳐진 것을 번체로 되돌릴 때 엉뚱한 쪽이 나온다.
#
#   里 → 裏  `왕십리`가 `往十裏`가 됐다. 裏는 '속'이고, 지명의 리는 마을 里다.
#            한국 지명의 리는 언제나 里라 통째로 되돌린다.
#   钟 → 鍾  종로·종각의 종은 **종(鐘)**이다. 鍾은 술잔·모으다다. 다만 만종(萬鍾)은
#            鍾이 맞아, 낱말로 짚어 고친다.
#   谷 → 穀  화곡의 곡은 골짜기 谷이다. 穀은 곡식이다.
TRADITIONAL_FIXES = {
    "鍾路": "鐘路",
    "鍾閣": "鐘閣",
    "禾穀": "禾谷",
}


def fix_traditional(text):
    if not text:
        return text
    text = text.replace("裏", "里")
    for wrong, right in TRADITIONAL_FIXES.items():
        text = text.replace(wrong, right)
    return text


def haversine(lat1, lon1, lat2, lon2):
    """두 자리 사이 거리(km)."""
    radius = 6371.0
    one, two = math.radians(lat1), math.radians(lat2)
    dlat, dlon = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(one) * math.cos(two) * math.sin(dlon / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(a))


def get_json(url):
    """위키데이터는 몰아치면 429를 준다. 물러섰다 다시 묻는다."""
    for attempt in range(5):
        try:
            request = urllib.request.Request(url, headers=AGENT)
            return json.load(urllib.request.urlopen(request, timeout=60))
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 4:
                raise
            time.sleep(3 * (2 ** attempt))
    raise RuntimeError("unreachable")


def wikidata_by_name(korean, lat, lon):
    """이름으로 찾아 **좌표로 거른다**.

    `wikidata` 태그가 없는 역이 남는다 — 경춘선 시골 역과 새 노선이 그렇다.
    이름 검색은 동명이인을 물어 오므로, 찾아온 항목이 그 역에서 2km 안에 있어야
    받아들인다. 못 찾으면 비워 둔다. 지어내는 것보다 한국어로 남는 편이 낫다.
    """
    query = urllib.parse.quote(f"{korean}역")
    found = get_json(
        f"{WIKIDATA}?action=wbsearchentities&format=json&type=item"
        f"&language=ko&uselang=ko&limit=3&search={query}"
    ).get("search") or []
    if not found:
        return None
    ids = "|".join(item["id"] for item in found)
    entities = get_json(
        f"{WIKIDATA}?action=wbgetentities&format=json&props=labels|claims"
        f"&languages=zh|zh-hans|zh-hant|zh-cn|zh-tw&ids={ids}"
    ).get("entities") or {}
    for entity in entities.values():
        claims = (entity.get("claims") or {}).get("P625") or []
        place = claims[0]["mainsnak"].get("datavalue", {}).get("value") if claims else None
        if not place:
            continue
        # 3km 안이면 같은 역으로 본다. 위경도 차로 자르면 위도에 따라 기준이
        # 달라지고, 옮겨 지은 역에서 아슬아슬하게 걸린다 — 경춘선 백양리역이
        # 2010년에 옮겨 위키데이터와 2.1km 떨어져 있다.
        if haversine(lat, lon, place["latitude"], place["longitude"]) > 3.0:
            continue
        labels = entity.get("labels") or {}
        for code in ("zh-hans", "zh-cn", "zh", "zh-hant", "zh-tw"):
            if code in labels:
                return labels[code]["value"]
    return None


def wikidata_labels(ids):
    """중국어를 못 구한 역만 따라간다. 45개씩 끊어 부른다."""
    out = {}
    for start in range(0, len(ids), 45):
        chunk = "|".join(ids[start : start + 45])
        url = (
            f"{WIKIDATA}?action=wbgetentities&format=json&props=labels"
            f"&languages=zh|zh-hans|zh-hant|zh-cn|zh-tw|zh-hk&ids={chunk}"
        )
        entities = get_json(url).get("entities") or {}
        for qid, entity in entities.items():
            labels = entity.get("labels") or {}
            for code in ("zh-hans", "zh-cn", "zh", "zh-hant", "zh-tw", "zh-hk"):
                if code in labels:
                    out[qid] = labels[code]["value"]
                    break
        time.sleep(0.4)
    return out


def main():
    osm = index_by_name(fetch_osm())
    stations = json.loads(NETWORK.read_text(encoding="utf-8"))["stations"]

    # 노선망에 있는 역 + 실시간 API가 언급하는 역. 뚝섬유원지처럼 우리 노선망에는
    # 없는데 도착 정보에는 나오는 역이 있다 — 그 이름이 한국어로 남으면, 화면을
    # 영어로 보다가 거기서만 읽을 수 없게 된다.
    wanted = [station["n"] for station in stations]
    wanted += [name for name in osm if name not in {normalise(w) for w in wanted}]

    rows, pending = {}, []
    for name in wanted:
        tags = osm.get(normalise(name))
        if not tags:
            continue
        station = {"n": name}
        # 괄호를 뗀 것은 화면용이고, 한자를 건지는 것은 괄호가 붙은 원본에서 한다.
        raw = strip_tag(tags.get("name:ja"))
        rows[station["n"]] = {
            "e": ENGLISH_OVERRIDES.get(station["n"], tags.get("name:en")),
            "j": tidy_japanese(raw),
            "zh": tidy_chinese(
                tags.get("name:zh-Hans") or tags.get("name:zh") or tags.get("name:zh-Hant")
            ),
            "hanja": tidy_chinese(hanja_from_japanese(raw)),
        }
        if not rows[station["n"]]["zh"] and tags.get("wikidata"):
            pending.append((station["n"], tags["wikidata"]))

    for name, fixed in MANUAL.items():
        rows[name] = dict(fixed, j=tidy_japanese(fixed["j"]), hanja=None)
        pending = [p for p in pending if p[0] != name]

    print(f"위키데이터로 메울 역 {len(pending)}개…")
    labels = wikidata_labels([qid for _, qid in pending])
    for name, qid in pending:
        rows[name]["zh"] = tidy_chinese(labels.get(qid))

    # 아직 중국어가 없는 역은 이름으로 찾는다. `wikidata` 태그가 없는 역이 남는데,
    # 경춘선 시골 역(가평·강촌·백양리)과 새 노선(다산·동구릉·보라매병원)이 그렇다.
    placed = {s["n"]: (s["y"], s["x"]) for s in stations}
    filled = []
    for name, row in rows.items():
        if row["zh"] or name not in placed:
            continue
        lat, lon = placed[name]
        try:
            label = tidy_chinese(wikidata_by_name(name, lat, lon))
        except Exception as error:  # 망이 끊겨도 나머지는 만든다
            print(f"  {name} 이름 검색 실패: {error}")
            continue
        if label:
            row["zh"] = label
            filled.append(f"{name}→{label}")
        time.sleep(1.2)
    if filled:
        print(f"이름으로 찾아 채운 역 {len(filled)}개: {', '.join(filled)}")

    # 손으로 바로잡은 것은 **맨 마지막에** 얹는다. 출처가 이미 값을 들고 있어도
    # 덮어야 한다 — 시청·용인대는 OSM 태그가 삼가를 가리켜 三街가 먼저 들어왔고,
    # 비어 있을 때만 얹던 때에는 그 위를 덮지 못했다.
    for name, fixed in CHINESE_OVERRIDES.items():
        if name in rows:
            rows[name]["zh"] = fixed

    # 괄호 한자는 마지막에만 쓴다 — 고유어 역명에서는 일본어 번역이 섞여 나온다.
    for row in rows.values():
        row["zh"] = row["zh"] or row.pop("hanja", None)
        row.pop("hanja", None)

    # 출처마다 서체가 섞여 있어 한 벌에서 둘을 만든다. 같으면 번체는 접는다.
    table = {}
    for name, row in rows.items():
        entry = {}
        if row["e"]:
            entry["e"] = row["e"]
        if row["j"]:
            entry["j"] = row["j"]
        if row["zh"]:
            simplified = zhconv.convert(row["zh"], "zh-hans")
            traditional = fix_traditional(zhconv.convert(row["zh"], "zh-hant"))
            entry["s"] = simplified
            if traditional != simplified:
                entry["t"] = traditional
        if entry:
            table[name] = entry

    OUT.write_text(
        json.dumps(
            {
                "source": "© OpenStreetMap contributors (ODbL 1.0) · Wikidata (CC0)",
                "names": table,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )

    total = len(stations)
    have = lambda key: sum(1 for v in table.values() if v.get(key))
    print(f"역 {total}개 중 이름표 {len(table)}개")
    print(f"  영어 {have('e')} · 일본어 {have('j')} · 중국어 {have('s')}")
    print(f"  {OUT.relative_to(ROOT)} · {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
