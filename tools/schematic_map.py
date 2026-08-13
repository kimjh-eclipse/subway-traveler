#!/usr/bin/env python3
"""도식 노선도 좌표를 SVG에서 뽑아 자산으로 굽는다.

지리 좌표로 그린 노선도는 사실이지만 읽기 어렵다. 선이 제멋대로 꺾이고 도심에서는
역이 뭉친다. 사람들이 아는 지하철 노선도는 옥토리니어 도식도 — 모든 선이 0·45·90도로
꺾이고 역 간격이 고른 그림이다. 그 배치는 지리 좌표에서 자동으로 나오지 않는다.

MIT 라이선스로 공개된 도식 노선도 SVG에서 역 좌표를 캐낸다. SVG의 원에는 이름이
없어 라벨과 거리로 짝지어야 하는데, 가까운 것부터 무작정 붙이면 틀린다. 밀집한
환승역 구역에서 엉뚱한 원을 물고, `서울역 (경의중앙선)` 같은 병기 라벨의 `중앙`이
안산선 중앙역을 낚아챈다 — 실제로 그랬다.

그래서 세 단계로 푼다.

1. 두 줄로 쪼개진 라벨을 붙인다. 둘째 줄은 첫 줄에서 정확히 (+3.3, +3.5)만큼
   어긋나 있어 규칙이 뚜렷하다. `가산`+`디지털단지`, `압구정`+`로데오`.
2. 가까운 짝부터 확정하되 원 하나는 한 역만 갖는다. 겹침이 33건에서 1건으로 준다.
3. 이웃 무리에서 벗어난 역을 찾아 다시 붙인다. 노선에서 이웃한 역은 도식에서도
   가까워야 한다 — 이 성질이 짝짓기가 맞는지 가려 주는 유일한 잣대다.

좌표는 역 이름이 아니라 **노선망의 순번**으로 낸다. 이름으로 키를 잡으면 5호선
양평과 경의중앙선 양평이 한 자리로 뭉개진다 — 실제로 그렇게 만들었다가 둘 다
엉뚱한 데 놓였다. 같은 이름이 20쌍이나 있어 그냥 넘길 문제가 아니다.

자료: https://github.com/Sinseiki/opensource-seoul-subway-map (MIT)

사용법:
    python3 tools/schematic_map.py mapimage.svg
"""
import html
import json
import math
import pathlib
import re
import sys
from collections import defaultdict

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"
ASSET = ROOT / "app/src/main/assets/schematic_map.json"
SOURCE = "Sinseiki/opensource-seoul-subway-map · MIT"

# 둘째 줄 라벨이 첫 줄에서 어긋난 만큼.
LINE_BREAK = (3.3, 3.5)
LINE_BREAK_SLACK = 1.2

# 라벨에서 이만큼 떨어진 원까지만 후보로 본다.
MAX_LABEL_DISTANCE = 40.0

# 이웃 무리의 한가운데에서 이만큼 벗어나면 잘못 붙은 것으로 보고 다시 붙인다.
STRAY_LIMIT = 80.0


def normalize(name):
    return re.sub(r"\s", "", name).split("(")[0].removesuffix("역")


def read_segments(svg):
    """도식도의 선 그 자체.

    여태 역과 역을 직선으로 이어 그렸더니 도식처럼 보이지 않았다. 진짜 도식도는
    역이 아닌 자리에서도 꺾이고, 그 꺾임이 그림의 성격을 만든다. SVG에 그 선이
    `<line>` 408개로 들어 있다 — 405개가 정확히 0·45·90도다.

    굵은 하늘색 한 줄기는 한강이다. 노선이 아니지만 그려야 서울로 읽힌다.
    """
    rules = {}
    style = re.search(r"<style>(.*?)</style>", svg, re.S)
    for selector, body in re.findall(r"([^{}]+)\{([^{}]*)\}", style.group(1) if style else ""):
        props = dict(re.findall(r"([a-z-]+)\s*:\s*([^;]+)", body))
        for name in re.findall(r"\.(cls-\d+)", selector):
            rules.setdefault(name, {}).update(props)

    out = []
    for cls, x1, y1, x2, y2 in re.findall(
        r'<line[^>]*class="(cls-\d+)"[^>]*x1="([-\d.]+)"[^>]*y1="([-\d.]+)"'
        r'[^>]*x2="([-\d.]+)"[^>]*y2="([-\d.]+)"',
        svg,
    ):
        style_of = rules.get(cls, {})
        stroke = style_of.get("stroke", "").strip()
        if not stroke.startswith("#"):
            continue
        width = float(re.sub(r"[^\d.]", "", style_of.get("stroke-width", "3")) or 3)
        out.append(
            {
                "a": [round(float(x1), 2), round(float(y1), 2)],
                "b": [round(float(x2), 2), round(float(y2), 2)],
                "c": stroke,
                "w": width,
            }
        )
    return out


def read_svg(path):
    """원의 자리와, 두 줄짜리를 합친 한글 라벨."""
    svg = pathlib.Path(path).read_text(encoding="utf-8", errors="replace")
    circles = [
        (float(x), float(y))
        for x, y in re.findall(r'<circle[^>]*cx="([-\d.]+)"[^>]*cy="([-\d.]+)"', svg)
    ]

    texts = []
    for match in re.finditer(
        r'<text[^>]*transform="translate\(([-\d.]+)[ ,]+([-\d.]+)\)[^"]*"[^>]*>(.*?)</text>',
        svg,
        re.S,
    ):
        pieces = [
            html.unescape(re.sub(r"<[^>]+>", "", piece)).strip()
            for piece in re.findall(r"<tspan[^>]*>(.*?)</tspan>", match.group(3), re.S)
        ]
        label = "".join(pieces)
        if label and re.search(r"[가-힣]", label):
            texts.append((float(match.group(1)), float(match.group(2)), label))

    merged, taken = [], set()
    for i, (x, y, label) in enumerate(texts):
        if i in taken:
            continue
        second = next(
            (
                j
                for j, (x2, y2, _) in enumerate(texts)
                if j != i
                and j not in taken
                and abs((x2 - x) - LINE_BREAK[0]) < LINE_BREAK_SLACK
                and abs((y2 - y) - LINE_BREAK[1]) < LINE_BREAK_SLACK
            ),
            None,
        )
        if second is None:
            merged.append((x, y, label))
        else:
            taken.add(second)
            merged.append((x, y, label + texts[second][2]))
    return circles, merged, read_segments(svg)


def neighbours_of(network):
    """순번끼리의 이웃 관계. 이름으로 잡으면 같은 이름의 두 역이 엉킨다."""
    adjacency = defaultdict(set)
    for line in network["lines"]:
        for path in line["p"]:
            for a, b in zip(path, path[1:]):
                adjacency[a].add(b)
                adjacency[b].add(a)
    return adjacency


def centroid(points):
    return (
        sum(p[0] for p in points) / len(points),
        sum(p[1] for p in points) / len(points),
    )


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)

    circles, labels, segments = read_svg(sys.argv[1])
    network = json.loads(NETWORK.read_text(encoding="utf-8"))
    names = [s["n"] for s in network["stations"]]
    neighbour_index = neighbours_of(network)

    # 이름이 같은 역을 모두 후보로 둔다 — 노선망이 한 역을 둘로 쪼개 둔 곳이 있다.
    by_normalized = defaultdict(list)
    for i, name in enumerate(names):
        by_normalized[normalize(name)].append(i)

    # 역마다 '자기 라벨이 가리키는 원'만 후보다. 이 울타리가 없으면 재배치가
    # 라벨과 무관한 원으로 역을 옮겨 버린다 — 오목교가 그렇게 목동에서 멀어졌다.
    allowed = defaultdict(set)
    candidates = []
    for x, y, label in labels:
        for station in by_normalized.get(normalize(label), ()):
            for index, (cx, cy) in enumerate(circles):
                distance = math.hypot(cx - x, cy - y)
                if distance <= MAX_LABEL_DISTANCE:
                    candidates.append((distance, station, index))
                    allowed[station].add(index)
    candidates.sort()

    placed, used_circles = {}, set()
    for _, station, index in candidates:
        if station in placed or index in used_circles:
            continue
        placed[station] = index
        used_circles.add(index)

    # 2차 — 이웃 무리에서 벗어난 역을 다시 붙인다.
    def stray(station):
        near = [circles[placed[n]] for n in neighbour_index[station] if n in placed]
        if not near:
            return 0.0
        mx, my = centroid(near)
        cx, cy = circles[placed[station]]
        return math.hypot(cx - mx, cy - my)

    moved = []
    for station in sorted(placed, key=stray, reverse=True):
        if stray(station) <= STRAY_LIMIT:
            break
        near = [circles[placed[n]] for n in neighbour_index[station] if n in placed]
        if not near:
            continue
        mx, my = centroid(near)
        # 자기 라벨이 가리키는 원 중에서만 고른다.
        free = [i for i in allowed[station] if i not in used_circles or i == placed[station]]
        if not free:
            continue
        best = min(free, key=lambda i: math.hypot(circles[i][0] - mx, circles[i][1] - my))
        if best != placed[station]:
            used_circles.discard(placed[station])
            used_circles.add(best)
            moved.append(station)
            placed[station] = best

    # 3차 — 서로 상대의 원을 물었으면 맞바꾼다.
    #
    # 재배치만으로는 못 푼다. 이미 남이 차지한 원은 후보에서 빠지기 때문이다.
    # 5호선 양평과 경의중앙선 양평이 정확히 이 꼴이었다 — 이름이 같아 둘 다
    # 상대편 라벨 옆 원에 붙었고, 둘 다 이웃에서 500 넘게 떨어져 있었다.
    swapped = []
    for _ in range(3):
        strays = {i: stray(i) for i in placed}
        far = [i for i, d in strays.items() if d > STRAY_LIMIT]
        done = True
        for a in far:
            for b in placed:
                if a == b or placed[b] not in allowed[a] or placed[a] not in allowed[b]:
                    continue
                before = strays[a] + stray(b)
                placed[a], placed[b] = placed[b], placed[a]
                after = stray(a) + stray(b)
                if after < before:
                    swapped.append((names[a], names[b]))
                    strays[a], strays[b] = stray(a), stray(b)
                    done = False
                    break
                placed[a], placed[b] = placed[b], placed[a]
        if done:
            break

    # 노선망이 한 역을 둘로 쪼갠 곳은 같은 자리를 준다 — 같은 역이니 당연하다.
    for group in by_normalized.values():
        if len(group) < 2:
            continue
        anchored = [i for i in group if i in placed]
        if len(anchored) == 1:
            for i in group:
                placed.setdefault(i, placed[anchored[0]])

    coords = {i: circles[j] for i, j in placed.items()}

    # 라벨이 없어 자리를 못 찾은 역은 노선을 따라 양옆의 자리 사이에 끼워 넣는다.
    #
    # 도식도에 원은 있는데 이름표가 없거나(약수·금호), 이 SVG가 만들어진 뒤 생긴
    # 역(운정중앙·신검단중앙)이 그렇다. 빈칸으로 두면 노선이 끊겨 보이는데,
    # 어차피 두 이웃 사이에 있는 역이니 그 사이에 놓는 편이 사실에 가깝다.
    filled = []
    for line in network["lines"]:
        for path in line["p"]:
            for k, station in enumerate(path):
                if station in coords:
                    continue
                before = next(((d, path[k - d]) for d in range(1, k + 1) if path[k - d] in coords), None)
                after = next(
                    ((d, path[k + d]) for d in range(1, len(path) - k) if path[k + d] in coords), None
                )
                if not before or not after:
                    continue
                (da, a), (db, b) = before, after
                t = da / (da + db)
                coords[station] = (
                    coords[a][0] + (coords[b][0] - coords[a][0]) * t,
                    coords[a][1] + (coords[b][1] - coords[a][1]) * t,
                )
                filled.append(names[station])

    # 순번 그대로 늘어놓는다. 끝내 자리를 못 찾은 역은 null이다.
    points = [
        [round(coords[i][0], 2), round(coords[i][1], 2)] if i in coords else None
        for i in range(len(names))
    ]
    viewbox = [1150.36, 1074.59]
    ASSET.write_text(
        json.dumps(
            {
                "source": SOURCE,
                "w": viewbox[0],
                "h": viewbox[1],
                "p": points,
                "s": segments,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )

    pairs = [
        (math.hypot(points[a][0] - points[b][0], points[a][1] - points[b][1]), line["n"], names[a], names[b])
        for line in network["lines"]
        for path in line["p"]
        for a, b in zip(path, path[1:])
        if points[a] and points[b]
    ]
    pairs.sort()
    gaps = [p[0] for p in pairs]
    found = sum(1 for p in points if p)
    print(f"좌표 {found}역 / 노선망 {len(names)}역 · 다시 붙인 역 {len(moved)} {[names[i] for i in moved]}")
    print(f"맞바꾼 쌍 {len(swapped)} {swapped}")
    print(f"이웃 사이에 끼워 넣은 역 {len(filled)} {filled}")
    print(
        f"인접 간격 중앙값 {gaps[len(gaps) // 2]:.1f} · 90% {gaps[int(len(gaps) * 0.9)]:.1f}"
        f" · 최대 {gaps[-1]:.1f} · 겹침 {sum(1 for d in gaps if d < 0.5)}"
    )
    print("가장 먼 이웃 6쌍:")
    for d, line, a, b in pairs[-6:]:
        print(f"   {d:7.1f}  {line:8} {a} ↔ {b}")
    print(f"선 {len(segments)}개")
    print(f"{ASSET.name} {ASSET.stat().st_size // 1024} KB")
    missing = sorted(names[i] for i, p in enumerate(points) if not p)
    print(f"좌표 없는 역 {len(missing)}: {missing[:15]}")


if __name__ == "__main__":
    main()
