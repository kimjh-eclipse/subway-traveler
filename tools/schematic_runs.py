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
3. **노선마다 색을 하나 정한다.** 그 노선 역들 곁에 가장 자주 있는 색을 뽑는다
   (`GTX-A → #9b5091`). 그리고 그 색 위에서만 길을 찾는다.

   색을 안 가리고 풀면 다른 노선을 타고 도는 길이 나온다 — `수서 ↔ 구성`을 두
   역이 함께 앉은 색(`#ea9418`, 수인분당선)으로 풀었더니 직선의 1.8배짜리 길이
   나왔다. 그건 GTX가 아니라 옆 노선이다. 노선의 색을 못박아 두면 그럴 수 없다.

4. 색을 못박았으므로 역을 선에 붙이는 거리를 넉넉히 잡을 수 있다. 큰 환승역에서는
   GTX 선이 역 표시에서 55만큼 비껴 지난다 — 25로 재던 때는 `서울역`·`수서`가
   GTX 위에 아예 안 앉아, 가장 크게 어긋나던 두 구간을 고칠 수 없었다.

5. 직선의 1.5배 안에서 최단 길을 찾는다. 넘으면 버린다 — 돌아가는 길이다.

못 잇는 구간은 그대로 직선으로 둔다. 못 그리는 것보다 나쁜 것은 잘못 그리는 것이다.

    python3 tools/schematic_runs.py
"""
import heapq
import json
import math
import pathlib
from collections import Counter, defaultdict

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"
SCHEMATIC = ROOT / "app/src/main/assets/schematic_map.json"

# 역이 선에서 이만큼 안에 있으면 그 선 위의 점으로 본다. 큰 환승역에서는 선이 역
# 표시를 비껴 지나므로 넉넉해야 한다 — GTX가 `서울역`을 55만큼 비껴간다. 색을
# 못박아 두어 다른 노선으로 샐 일이 없으니 이만큼 잡아도 된다.
SNAP = 80.0
# 노선의 색을 뽑을 때, 역 곁에 이만큼 안에 있는 색만 한 표로 친다.
VOTE = 12.0
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


def line_colours(network, schematic, segments):
    """노선마다 색 하나. 그 노선 역들 곁에 가장 자주 있는 색을 뽑는다."""
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
                    _, distance = point_on(point, segment["a"], segment["b"])
                    colour = segment.get("c", "")
                    if colour not in nearest or distance < nearest[colour]:
                        nearest[colour] = distance
                for colour, distance in nearest.items():
                    if distance <= VOTE:
                        votes[line["n"]][colour] += 1
    return {
        name: counter.most_common(1)[0][0]
        for name, counter in votes.items()
        if counter
    }


def build(schematic, segments):
    """색마다 하나씩, 역이 마디로 끼워진 그래프."""
    points = schematic["p"]

    cuts = defaultdict(list)
    colours_at = defaultdict(set)
    for index, point in enumerate(points):
        if not point:
            continue
        for j, segment in enumerate(segments):
            t, distance = point_on(tuple(point), segment["a"], segment["b"])
            if distance <= SNAP:
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
    return graph, place, colours_at


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
    segments = [s for s in schematic["s"] if s.get("a") and s.get("b")]

    colours = line_colours(network, schematic, segments)
    missing = [line["n"] for line in network["lines"] if line["n"] not in colours]
    graph, place, _ = build(schematic, segments)

    def stray_of(a, b):
        """직선이 그림의 선에서 가장 많이 벗어나는 거리."""
        worst = 0.0
        for t in (0.2, 0.35, 0.5, 0.65, 0.8):
            probe = (a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]))
            worst = max(worst, min(point_on(probe, s["a"], s["b"])[1] for s in segments))
        return worst

    # 같은 두 역이 여러 노선에 함께 있으면(2호선과 지선 등) 노선마다 따로 본다.
    pairs = {}
    for line in network["lines"]:
        for path in line["p"]:
            for u, v in zip(path, path[1:]):
                pairs.setdefault((min(u, v), max(u, v)), set()).add(line["n"])

    runs, left, straight = [], [], 0
    for (u, v), lines in sorted(pairs.items()):
        if not points[u] or not points[v]:
            continue
        if stray_of(points[u], points[v]) <= STRAY:
            straight += 1
            continue
        chord = math.dist(points[u], points[v])
        best = None
        for line in lines:
            colour = colours.get(line)
            if colour is None:
                continue
            found = trace(graph, place, colour, u, v, chord * DETOUR + 30)
            if found and len(found) > 2 and (best is None or len(found) < len(best)):
                best = found
        if best is None:
            left.append((stray_of(points[u], points[v]), u, v))
            continue
        # 붙이는 거리를 넉넉히 잡은 탓에 길의 양 끝이 역에서 조금 떨어져 있다.
        # 그대로 두면 역 앞에서 선이 꺾여 보인다 — 끝은 역 자리로 되돌린다.
        best[0] = tuple(points[u])
        best[-1] = tuple(points[v])
        runs.append({"u": u, "v": v, "p": [round(c, 1) for pt in best for c in pt]})

    schematic["r"] = runs
    SCHEMATIC.write_text(
        json.dumps(schematic, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    print(f"노선 {len(colours)}개의 색을 잡았다" + (f" · 못 잡은 노선 {missing}" if missing else ""))
    print(f"벗어난 구간 {len(runs) + len(left)}개 중 {len(runs)}개를 이었다 · 원래 곧은 구간 {straight}개")
    if left:
        left.sort(reverse=True)
        print("아직 직선으로 두는 것 중 심한 순:")
        for stray, u, v in left[:8]:
            print(f"   {stray:6.0f}  {network['stations'][u]['n']} ↔ {network['stations'][v]['n']}")


if __name__ == "__main__":
    main()
