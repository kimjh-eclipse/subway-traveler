#!/usr/bin/env python3
"""한 역이 노드 둘로 갈라져 있는 것을 합친다.

노선망이 OpenStreetMap에서 오는데, 거기서는 한 역이 승강장마다 따로 찍혀 있는 곳이
있다. `교대`와 `교대(법원·검찰청)`, `대림`과 `대림(구로구청)` 같은 것들이다.

**둘은 서로 이어져 있지 않다.** 그래서 길찾기가 별개의 장소로 본다 — 2호선 교대에
내려 3호선으로 갈아타는 길을 찾지 못하고, 도식 노선도에는 `교대`가 두 번 찍힌다.
지리 노선도에서는 두 점이 겹쳐 보이지 않아 여태 드러나지 않았다.

이름이 같고 [SAME_STATION_METRES] 안에 있으면 같은 역으로 본다. 이 잣대가 `양평`
하나를 정확히 갈라낸다 — 5호선 양평과 경의중앙선 양평은 53km 떨어진 서로 다른
역이라 합치면 안 된다. 나머지 19쌍은 모두 458m 안에 있다.

남길 쪽은 노선이 많은 노드다. 노선 수가 같으면 이웃이 많은 쪽, 그것도 같으면 이름이
짧은 쪽을 남긴다.

순번이 밀리므로 노선 경로와 역간 소요시간도 함께 고쳐 쓴다. 도식 좌표는 순번으로
담겨 있어 이 뒤에 tools/seoul_schematic.py를 다시 돌려야 한다.

사용법:
    python3 tools/dedupe_stations.py
"""
import json
import math
import pathlib
import re
from collections import defaultdict

ROOT = pathlib.Path(__file__).parent.parent
ASSET = ROOT / "app/src/main/assets/subway_map.json"

# 이 안에 있으면 같은 역. 실제 자료는 458m와 53km로 뚜렷이 갈린다.
SAME_STATION_METRES = 600.0

# 이름이 달라도 같은 역인 것들. 이름이 같아야 후보가 되는 규칙으로는 못 잡는다.
#   * 이수: 7호선 역명. 4호선 쪽은 총신대입구다 — 한 역이 노선마다 다른 이름을 쓴다.
#     갈라져 있으면 4↔7호선 환승 경로를 못 찾는다.
#   * 서구청: 인천2호선의 옛 역명. 2026년 7월 1일 서구가 서해구로 개칭되면서
#     서해구청역이 되었다(인천시 고시, 2026-06-12). OSM에 두 이름의 노드가 100m
#     간격으로 남아 있어, 새 이름 쪽으로 합친다.
ALIASES = {"이수": "총신대입구", "서구청": "서해구청"}


def normalize(name):
    bare = re.sub(r"\s", "", name).split("(")[0].removesuffix("역")
    return ALIASES.get(bare, bare)


def metres_between(a, b):
    """위경도 차이를 대충 미터로. 몇백 미터를 가리는 데는 이 정도면 넉넉하다."""
    return math.hypot((a["y"] - b["y"]) * 111_000, (a["x"] - b["x"]) * 88_000)


def main():
    network = json.loads(ASSET.read_text(encoding="utf-8"))
    stations = network["stations"]

    neighbours = defaultdict(set)
    for line in network["lines"]:
        for path in line["p"]:
            for a, b in zip(path, path[1:]):
                neighbours[a].add(b)
                neighbours[b].add(a)

    groups = defaultdict(list)
    for index, station in enumerate(stations):
        groups[normalize(station["n"])].append(index)

    # 합칠 짝 고르기.
    merge_into = {}
    merged_pairs = []
    for members in groups.values():
        if len(members) < 2:
            continue
        close = [
            (a, b)
            for i, a in enumerate(members)
            for b in members[i + 1:]
            if metres_between(stations[a], stations[b]) <= SAME_STATION_METRES
        ]
        if not close:
            continue
        cluster = sorted({i for pair in close for i in pair})
        keep = max(
            cluster,
            key=lambda i: (
                # 별칭으로 불려 온 쪽은 본명에게 진다 — 이수가 아니라 총신대입구가 남는다.
                re.sub(r"\s", "", stations[i]["n"]).split("(")[0].removesuffix("역") not in ALIASES,
                len(stations[i].get("l", [])),
                len(neighbours[i]),
                -len(stations[i]["n"]),
            ),
        )
        for index in cluster:
            if index != keep:
                merge_into[index] = keep
                merged_pairs.append((stations[index]["n"], stations[keep]["n"]))

    if not merge_into:
        print("합칠 것이 없다.")
        return

    # 남는 쪽에 노선과 실시간 이름을 몰아 준다.
    for gone, keep in merge_into.items():
        lines = stations[keep].get("l", []) + stations[gone].get("l", [])
        stations[keep]["l"] = sorted(set(lines), key=lines.index)
        realtime = stations[keep].get("r", []) + stations[gone].get("r", [])
        if realtime:
            stations[keep]["r"] = sorted(set(realtime), key=realtime.index)

    survivors = [i for i in range(len(stations)) if i not in merge_into]
    renumber = {old: new for new, old in enumerate(survivors)}

    def moved(index):
        return renumber[merge_into.get(index, index)]

    for line in network["lines"]:
        rebuilt = []
        for path in line["p"]:
            # 합쳐서 같은 역이 잇달아 나오면 한 번만 남긴다.
            run = []
            for index in (moved(i) for i in path):
                if not run or run[-1] != index:
                    run.append(index)
            if len(run) > 1:
                rebuilt.append(run)
        line["p"] = rebuilt

    times, seen = [], set()
    for a, b, seconds in network.get("times", []):
        x, y = moved(a), moved(b)
        key = (min(x, y), max(x, y))
        # 합쳐진 두 노드 사이의 구간은 사라진다 — 이제 같은 역이다.
        if x == y or key in seen:
            continue
        seen.add(key)
        times.append([x, y, seconds])
    network["times"] = times
    network["stations"] = [stations[i] for i in survivors]

    ASSET.write_text(
        json.dumps(network, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    print(f"합친 노드 {len(merge_into)}개 · 역 {len(stations)} → {len(survivors)}")
    for gone, keep in merged_pairs:
        print(f"   {gone}  →  {keep}")
    print("\n도식 좌표는 순번으로 담겨 있다. tools/seoul_schematic.py를 다시 돌릴 것.")


if __name__ == "__main__":
    main()
