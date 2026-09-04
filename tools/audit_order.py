#!/usr/bin/env python3
"""노선의 역 순서와 도식 자리가 서로 어긋나는 곳을 찾고, 알려진 것을 고친다.

경로를 지도에 얹으면 굵은 선이 역과 역을 차례로 잇는다. 그 차례가 실제와 다르면
선이 **갔다가 되돌아온다** — 그런데 되돌아온 자리에는 역 표시가 없다. 경로가
서지 않는 역이기 때문이다. 그래서 화면에는 이유 없이 겹친 선만 남는다.

찾는 방법은 간단하다. 이웃한 세 역을 도식 자리에 놓고 안쪽 각을 본다. 150도
넘게 꺾이면 왔던 길을 되짚은 것이다. 노선이 그렇게 꺾이는 일은 없다.

    python3 tools/audit_order.py            # 찾기만
    python3 tools/audit_order.py --fix      # 아래 표에 적힌 것을 고친다

고친 뒤에는 `schematic_runs.py`를 다시 돌려야 한다. 이은 길이 역 자리에서 나온다.
"""
import json
import math
import pathlib
from collections import Counter, defaultdict
import sys

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"
SCHEMATIC = ROOT / "app/src/main/assets/schematic_map.json"

# 안쪽 각이 이보다 더 꺾이면 되짚은 것으로 본다(cos -0.86 ≈ 150도).
REVERSAL = -0.86

# 노선망에 역 차례가 뒤바뀐 곳. `노선: [(앞, 뒤)]` — 이웃한 둘의 자리를 맞바꾼다.
#
#   공항철도 — 자산은 `마곡나루 → 홍대입구 → 디지털미디어시티 → 공덕`인데 실제는
#   `마곡나루 → 디지털미디어시티 → 홍대입구 → 공덕`이다. 위경도로도 그렇고
#   (디지털미디어시티 126.9009 < 홍대입구 126.9235), 도식 자리로도 그렇다.
ORDER_SWAPS = {
    "공항철도": [("홍대입구", "디지털미디어시티")],
}

# 도식 자리가 서로 바뀐 역. 이름이 같은 두 역을 노선으로 가른다.
#
#   양평 — 5호선 양평(서울 영등포)이 지도 오른쪽 끝에, 경의중앙선 양평(경기 양평)이
#   왼쪽에 놓여 있었다. 이름으로 자리를 맞추다 둘이 뒤바뀐 것이다.
POINT_SWAPS = [
    ("양평", "5호선", "경의중앙선"),
]

# 노선이 하나뿐인 역이 그 노선 선에서 이만큼 떨어져 있으면 선 위로 끌어다 놓는다.
#
# 환승역은 손대지 않는다. 역 표시는 하나여야 하고, 그 하나가 여러 노선의 선
# 사이에 있는 것은 그림이 그렇게 그려졌기 때문이다 — 서울역은 다섯 노선이
# 지나므로 어느 한 점도 다섯 선 위에 동시에 있을 수 없다. 옮겨 보면 121이
# 120이 될 뿐이고, 대신 역 표시가 그림의 역 자리에서 벗어난다.
#
# 노선이 하나인 역은 이야기가 다르다. 자기 선에서 떨어져 있으면 그냥 자리가
# 틀린 것이다. 지금은 가좌(75)와 보문(25) 둘뿐이다.
PULL_ONTO_LINE = 25.0


def line_colours(network, schematic, segments):
    """노선마다 색 하나. `schematic_runs.py`와 같은 방식으로 뽑는다."""
    points = schematic["p"]
    votes = defaultdict(Counter)
    for line in network["lines"]:
        for path in line["p"]:
            for index in path:
                if not points[index]:
                    continue
                point = tuple(points[index])
                nearest = {}
                for segment in segments:
                    distance = point_to(point, segment)[0]
                    colour = segment.get("c", "")
                    if colour not in nearest or distance < nearest[colour]:
                        nearest[colour] = distance
                for colour, distance in nearest.items():
                    if distance <= 12.0:
                        votes[line["n"]][colour] += 1
    return {n: c.most_common(1)[0][0] for n, c in votes.items() if c}


def point_to(point, segment):
    """점에서 토막까지의 거리와 그 발점."""
    (px, py), (ax, ay), (bx, by) = point, segment["a"], segment["b"]
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy
    if length == 0:
        return math.dist(point, (ax, ay)), (ax, ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length))
    foot = (ax + t * dx, ay + t * dy)
    return math.dist(point, foot), foot


def pull_singles(network, schematic):
    """노선이 하나인 역을 그 노선 선 위로 끌어다 놓는다."""
    segments = [s for s in schematic["s"] if s.get("a") and s.get("b")]
    colours = line_colours(network, schematic, segments)
    by_colour = defaultdict(list)
    for segment in segments:
        by_colour[segment.get("c", "")].append(segment)

    serves = defaultdict(set)
    for line in network["lines"]:
        colour = colours.get(line["n"])
        if not colour:
            continue
        for path in line["p"]:
            for index in path:
                if schematic["p"][index]:
                    serves[index].add((line["n"], colour))

    moved = []
    twins = Counter(s["n"] for s in network["stations"])
    for index, lines in serves.items():
        if len(lines) != 1:
            continue
        # 이름이 같은 역이 둘 이상이면 손대지 않는다. 자리가 뒤바뀐 것인지
        # 자리가 틀린 것인지 여기서는 가릴 수 없다 — 양평을 1000이나 옮겼다.
        if twins[network["stations"][index]["n"]] > 1:
            continue
        (name, colour), = lines
        point = tuple(schematic["p"][index])
        distance, foot = min(
            (point_to(point, s) for s in by_colour[colour]), key=lambda x: x[0]
        )
        if distance <= PULL_ONTO_LINE:
            continue
        schematic["p"][index] = [round(foot[0], 1), round(foot[1], 1)]
        moved.append((network["stations"][index]["n"], name, distance))
    return moved


def reversals(network, schematic):
    """되짚는 곳을 모두 찾는다."""
    points = schematic["p"]
    names = [s["n"] for s in network["stations"]]
    found = []
    for line in network["lines"]:
        for path in line["p"]:
            for a, b, c in zip(path, path[1:], path[2:]):
                if not (points[a] and points[b] and points[c]):
                    continue
                first = (points[b][0] - points[a][0], points[b][1] - points[a][1])
                second = (points[c][0] - points[b][0], points[c][1] - points[b][1])
                one = math.hypot(*first)
                two = math.hypot(*second)
                if one < 1 or two < 1:
                    continue
                cos = (first[0] * second[0] + first[1] * second[1]) / (one * two)
                if cos < REVERSAL:
                    found.append((line["n"], names[a], names[b], names[c], cos))
    return found


def main():
    network = json.loads(NETWORK.read_text(encoding="utf-8"))
    schematic = json.loads(SCHEMATIC.read_text(encoding="utf-8"))
    names = [s["n"] for s in network["stations"]]

    before = reversals(network, schematic)
    print(f"되짚는 곳 {len(before)}군데")
    for line, a, b, c, cos in before:
        print(f"   {line:<12} {a} → {b} → {c}   cos {cos:.2f}")

    if "--fix" not in sys.argv:
        if before:
            print("\n고치려면 --fix 를 붙인다.")
        return

    # 맞바꿈은 **되짚음이 줄어들 때만** 한다. 조건 없이 바꾸면 도구를 두 번
    # 돌리는 순간 고친 것이 되돌아간다 — 실제로 그렇게 자산을 망가뜨렸다.
    for line in network["lines"]:
        for first, second in ORDER_SWAPS.get(line["n"], []):
            for path in line["p"]:
                spots = {names[i]: k for k, i in enumerate(path)}
                if first not in spots or second not in spots:
                    continue
                one, two = spots[first], spots[second]
                before = len(reversals(network, schematic))
                path[one], path[two] = path[two], path[one]
                if len(reversals(network, schematic)) < before:
                    print(f"차례를 바꿨다 · {line['n']}: {first} ↔ {second}")
                else:
                    path[one], path[two] = path[two], path[one]

    for name, one_line, other_line in POINT_SWAPS:
        pair = [i for i, n in enumerate(names) if n == name]
        one = next((i for i in pair if one_line in network["stations"][i].get("l", [])), None)
        other = next((i for i in pair if other_line in network["stations"][i].get("l", [])), None)
        if one is None or other is None:
            print(f"자리를 못 바꿨다 · {name}: 두 역을 못 가렸다")
            continue
        points = schematic["p"]
        before = len(reversals(network, schematic))
        points[one], points[other] = points[other], points[one]
        if len(reversals(network, schematic)) < before:
            print(f"도식 자리를 바꿨다 · {name}: {one_line} ↔ {other_line}")
        else:
            points[one], points[other] = points[other], points[one]

    for station, line, distance in pull_singles(network, schematic):
        print(f"선 위로 옮겼다 · {station}({line}): {distance:.0f} 떨어져 있었다")

    NETWORK.write_text(
        json.dumps(network, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    SCHEMATIC.write_text(
        json.dumps(schematic, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )

    after = reversals(network, schematic)
    print(f"\n고친 뒤 되짚는 곳 {len(after)}군데")
    for line, a, b, c, cos in after:
        print(f"   {line:<12} {a} → {b} → {c}   cos {cos:.2f}")


if __name__ == "__main__":
    main()
