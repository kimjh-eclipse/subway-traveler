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
import pathlib
import re
import sys
import time
import urllib.parse
import urllib.request

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


def normalise(name):
    """`총신대입구 (이수)` 와 `총신대입구(이수)` 를 같은 자리에 놓는다."""
    return re.sub(r"\s", "", name).split("(")[0].removesuffix("역")


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


def hanja_from_japanese(japanese):
    """`カンナム(江南)` → `江南`."""
    found = HANJA_IN_PARENS.search(japanese or "")
    return re.sub(r"[\s・･]", "", found.group(1)) if found else None


def tidy_chinese(text):
    """위키데이터 라벨은 `三松站`처럼 역을 뜻하는 글자를 달고 온다."""
    if not text:
        return None
    text = text.strip()
    if len(text) > 1 and text.endswith(("站", "驛", "驿")):
        text = text[:-1]
    return text or None


def wikidata_labels(ids):
    """중국어를 못 구한 역만 따라간다. 45개씩 끊어 부른다."""
    out = {}
    for start in range(0, len(ids), 45):
        chunk = "|".join(ids[start : start + 45])
        url = (
            f"{WIKIDATA}?action=wbgetentities&format=json&props=labels"
            f"&languages=zh|zh-hans|zh-hant|zh-cn|zh-tw|zh-hk&ids={chunk}"
        )
        request = urllib.request.Request(url, headers=AGENT)
        entities = json.load(urllib.request.urlopen(request, timeout=60)).get("entities") or {}
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

    rows, pending = {}, []
    for station in stations:
        tags = osm.get(normalise(station["n"]))
        if not tags:
            continue
        chinese = tidy_chinese(
            tags.get("name:zh-Hans")
            or tags.get("name:zh")
            or tags.get("name:zh-Hant")
            or hanja_from_japanese(tags.get("name:ja"))
        )
        rows[station["n"]] = {
            "e": tags.get("name:en"),
            "j": tags.get("name:ja"),
            "zh": chinese,
        }
        if not chinese and tags.get("wikidata"):
            pending.append((station["n"], tags["wikidata"]))

    print(f"위키데이터로 메울 역 {len(pending)}개…")
    labels = wikidata_labels([qid for _, qid in pending])
    for name, qid in pending:
        rows[name]["zh"] = tidy_chinese(labels.get(qid))

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
            traditional = zhconv.convert(row["zh"], "zh-hant")
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
