#!/usr/bin/env python3
"""역과 역 사이를, 도식도에 **그려진 선을 따라** 잇는 길을 구해 자산에 넣는다.

경로를 지도에 얹을 때 우리는 역과 역을 직선으로 이어 왔다. 도식도의 선은 역이
아닌 자리에서도 꺾이므로, 꺾이는 구간에서는 굵은 경로선이 그려진 선을 벗어나
엉뚱한 데를 가로지른다. 인접한 818쌍을 재어 보니 167쌍(20%)이 20단위(그림 폭
10630 기준) 넘게 벗어났고, 심한 것은 350단위였다 — GTX·급행처럼 역 사이가 먼
구간이 특히 그랬다.

## 어떻게

자산에 이미 들어 있는 것만 쓴다. 원본 SVG는 저장소에 없다(`seoul_schematic.py`가
PDF에서 변환한 파일을 인자로 받는다). 그래서 이 도구는 **이미 커밋된 자산을 읽어
되먹인다** — 선 토막(`s`)과 역 자리(`p`)면 충분하다.

1. 토막의 끝점을 용접해 마디로 만든다. 원본은 끝점이 정확히 맞물려 있다.
2. 역을 그 역이 앉은 토막 위에 끼워 넣는다. 654곳 중 650곳이 선 위에 있다.
3. **색마다 따로** 그래프를 둔다. 색을 넘나들며 길을 찾으면 다른 노선을 타고
   돌아가는 길이 나온다 — `수서 ↔ 구성`을 색 없이 풀었더니 34개 마디를 지나는
   길이 나왔다. 그건 GTX가 아니라 옆 노선이다.
4. 두 역이 함께 앉은 색으로만, 직선의 1.5배 안에서 최단 길을 찾는다.

167쌍 중 107쌍이 이 조건을 통과한다. 나머지 60쌍은 그대로 직선으로 둔다 —
못 그리는 것보다 나쁜 것은 잘못 그리는 것이다.

    python3 tools/schematic_runs.py
"""
import heapq
import json
import math
import pathlib
from collections import defaultdict

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"
SCHEMATIC = ROOT / "app/src/main/assets/schematic_map.json"

# 역이 선에서 이만큼 안에 있으면 그 선 위의 점으로 본다. 역 표시(흰 원)의 중심과
# 선의 중심이 살짝 어긋나 있어 0으로 둘 수는 없다.
SNAP = 25.0
# 끝점끼리 이만큼 가까우면 같은 마디. 원본은 대개 정확히 겹친다.
WELD = 1.5
# 직선이 그림에서 이만큼 벗어나면 고칠 값어치가 있다. 그림 폭은 10630이다.
STRAY = 8.0
# 따라간 길이 직선의 이 배를 넘으면 버린다. 돌아가는 길을 잡은 것이다.
DETOUR = 1.5


def point_on(point, a, b):
    """점에서 토막까지의 거리와, 토막 위 어디쯤인지(0~1)."""
    (px, py), (ax, ay), (bx, by) = point, a, b
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy
    if length == 0:
        return 0.0, math.dist(point, a)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length))
    return t, math.dist(point, (ax + t * dx, ay + t * dy))


def build(schematic):
    """색마다 하나씩, 역이 마디로 끼워진 그래프."""
    segments = [s for s in schematic["s"] if s.get("a") and s.get("b")]
    points = schematic["p"]

    # 역이 어느 토막 위에 앉았는가. 환승역은 여러 색 위에 동시에 앉는다.
    cuts = defaultdict(list)
    colours_at = defaultdict(set)
    for index, point in enumerate(points):
        if not point:
            continue
        for j, segment in enumerate(segments):
            _, distance = point_on(tuple(point), segment["a"], segment["b"])
            if distance <= SNAP:
                t, _ = point_on(tuple(point), segment["a"], segment["b"])
                cuts[j].append((t, index))
                colours_at[index].add(segment.get("c", ""))

    welded, seen = {}, []
    for segment in segments:
        for end in (tuple(segment["a"]), tuple(segment["b"])):
            if end in welded:
                continue
            near = next((s for s in seen if math.dist(end, s) <= WELD), None)
            if near is None:
                seen.append(end)
                welded[end] = end
            else:
                welded[end] = near

    graph = defaultdict(lambda: defaultdict(list))
    place = {}
    for j, segment in enumerate(segments):
        a, b = tuple(segment["a"]), tuple(segment["b"])
        colour = segment.get("c", "")
        chain = (
            [(0.0, ("pt", welded[a]))]
            + [(t, ("st", i)) for t, i in sorted(cuts[j])]
            + [(1.0, ("pt", welded[b]))]
        )
        for t, node in chain:
            place[(colour, node)] = (a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]))
        for (_, one), (_, two) in zip(chain, chain[1:]):
            if one == two:
                continue
            span = math.dist(place[(colour, one)], place[(colour, two)])
            graph[colour][one].append((two, span))
            graph[colour][two].append((one, span))
    return graph, place, colours_at, segments


def trace(graph, place, colour, u, v, limit):
    """한 색 안에서의 최단 길. 없으면 None."""
    lanes = graph[colour]
    start, goal = ("st", u), ("st", v)
    if start not in lanes or goal not in lanes:
        return None
    best = {start: 0.0}
    came = {}
    queue = [(0.0, start)]
    while queue:
        cost, node = heapq.heappop(queue)
        if node == goal:
            break
        if cost > best.get(node, math.inf) or cost > limit:
            continue
        for neighbour, span in lanes[node]:
            step = cost + span
            if step < best.get(neighbour, math.inf) - 1e-9 and step <= limit:
                best[neighbour] = step
                came[neighbour] = node
                heapq.heappush(queue, (step, neighbour))
    if goal not in best:
        return None
    path = [goal]
    while path[-1] != start:
        path.append(came[path[-1]])
    return [place[(colour, n)] for n in reversed(path)]


def main():
    schematic = json.loads(SCHEMATIC.read_text(encoding="utf-8"))
    network = json.loads(NETWORK.read_text(encoding="utf-8"))
    points = schematic["p"]
    graph, place, colours_at, segments = build(schematic)

    def stray_of(a, b):
        """직선이 그림의 선에서 가장 많이 벗어나는 거리."""
        worst = 0.0
        for t in (0.2, 0.35, 0.5, 0.65, 0.8):
            probe = (a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]))
            near = min(point_on(probe, s["a"], s["b"])[1] for s in segments)
            worst = max(worst, near)
        return worst

    pairs = set()
    for line in network["lines"]:
        for path in line["p"]:
            for u, v in zip(path, path[1:]):
                pairs.add((min(u, v), max(u, v)))

    runs, left = [], 0
    for u, v in sorted(pairs):
        if not points[u] or not points[v]:
            continue
        if stray_of(points[u], points[v]) <= STRAY:
            continue
        chord = math.dist(points[u], points[v])
        best = None
        for colour in colours_at[u] & colours_at[v]:
            found = trace(graph, place, colour, u, v, chord * DETOUR + 30)
            if found and (best is None or len(found) < len(best)):
                best = found
        if best is None or len(best) <= 2:
            left += 1
            continue
        flat = [round(c, 1) for point in best for c in point]
        runs.append({"u": u, "v": v, "p": flat})

    schematic["r"] = runs
    SCHEMATIC.write_text(
        json.dumps(schematic, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    print(f"벗어난 구간을 {len(runs)}개 이었다 · 그대로 둔 것 {left}개")
    print(f"  {SCHEMATIC.relative_to(ROOT)} · {SCHEMATIC.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
