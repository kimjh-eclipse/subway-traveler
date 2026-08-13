#!/usr/bin/env python3
"""역 이름을 전수 대조한다.

역 이름은 바뀐다 — 2026년 7월 1일 서구청이 서해구청이 된 것처럼. 우리 자산의
이름이 지금 쓰이는 이름인지, 654역 전부를 좌표로 맞춰 본다. 이름끼리 비교하면
개칭된 역은 "없는 역"으로만 보이지만, **자리**로 비교하면 "같은 자리, 다른
이름"으로 드러나 무엇이 무엇으로 바뀌었는지까지 나온다.

대조 상대는 OpenStreetMap이다. 서해구청 개칭이 하루 만에 반영될 만큼 빠르고,
`old_name` 태그에 옛 이름이 남아 있어 단순 개칭인지까지 가려 준다.

내놓는 것은 판단 자료다. 여기서 바로 고치지 않는다 — OSM도 틀릴 수 있으므로
(유령 서구청 노드가 그랬다), 어긋난 것은 하나씩 확인한 뒤 tools/rename_stations.py
에 적는 것이 맞다.

사용법:
    python3 tools/audit_names.py [--refresh]   # --refresh 는 OSM 캐시를 새로 받는다
"""
import argparse
import json
import math
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"

sys.path.insert(0, str(ROOT / "tools"))
import station_names  # noqa: E402  (OSM 내려받기와 캐시를 그대로 쓴다)

# 이 안에 OSM 역 노드가 없으면 "짝 없음"으로 본다. 역 하나의 출입구들이
# 수백 미터 벌어지므로 넉넉히 잡되, 이웃 역(가장 가까워도 600m 이상)은 안 닿게.
NEARBY_METRES = 450.0


def normalize(name):
    return re.sub(r"[\s·・()（）]", "", name).removesuffix("역")


def metres(a_lat, a_lon, b_lat, b_lon):
    return math.hypot((a_lat - b_lat) * 111_000, (a_lon - b_lon) * 88_000)


def names_of(tags):
    """OSM 요소가 이 역을 부르는 모든 한국어 이름."""
    keys = ("name", "name:ko", "official_name", "alt_name", "alt_name:ko",
            "old_name", "loc_name", "short_name")
    found = []
    for key in keys:
        value = tags.get(key, "")
        # alt_name 은 `;` 로 여러 개를 담기도 한다.
        found.extend(v.strip() for v in value.split(";") if v.strip())
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--refresh", action="store_true", help="OSM 캐시를 지우고 새로 받는다")
    options = parser.parse_args()
    if options.refresh and station_names.CACHE.exists():
        station_names.CACHE.unlink()

    elements = [
        e for e in station_names.fetch_osm()
        if "tags" in e and (e.get("lat") or e.get("center"))
    ]
    for element in elements:
        centre = element.get("center") or element
        element["_lat"], element["_lon"] = centre["lat"], centre["lon"]

    network = json.loads(NETWORK.read_text(encoding="utf-8"))
    matched = renamed = unmatched = 0
    for station in network["stations"]:
        ours = station["n"]
        nearby = sorted(
            (
                (metres(station["y"], station["x"], e["_lat"], e["_lon"]), e)
                for e in elements
            ),
            key=lambda pair: pair[0],
        )
        close = [(d, e) for d, e in nearby if d <= NEARBY_METRES]
        if not close:
            unmatched += 1
            d, e = nearby[0]
            print(f"짝 없음   {ours}  (가장 가까운 OSM: {e['tags'].get('name','?')} {d:.0f}m)")
            continue

        # 이름이 맞는 노드가 하나라도 가까이 있으면 그 역은 현행이다.
        if any(
            normalize(ours) in {normalize(n) for n in names_of(e["tags"])}
            for _, e in close
        ):
            matched += 1
            continue

        renamed += 1
        d, e = close[0]
        tags = e["tags"]
        was = tags.get("old_name", "")
        hint = f"  (old_name: {was})" if was else ""
        print(f"이름 다름 {ours}  ↔  OSM {tags.get('name','?')} {d:.0f}m{hint}")

    print(f"\n일치 {matched} · 이름 다름 {renamed} · 짝 없음 {unmatched}  / {len(network['stations'])}")


if __name__ == "__main__":
    main()
