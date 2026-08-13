#!/usr/bin/env python3
"""서울교통공사 공식 노선도에서 역의 도식 좌표를 캐낸다.

서울 열린데이터광장 「서울교통공사_수도권 지하철 노선도 4종(국영중일)」은
**공공누리 제1유형**이라 변경과 상업적 이용이 모두 허용된다. 국·영·중·일 네 가지가
있어 관광객을 겨냥한 앱에 그대로 맞다.

PDF는 벡터이지만 역 이름이 글꼴이 아니라 외곽선으로 변환돼 있어 글자로는 여덟
낱말만 남아 있다. 그래서 두 갈래로 캔다.

  * **자리**는 벡터에서. 역 표시는 지름 6pt 안팎의 흰 원이고, 환승역은 더 크다.
  * **이름**은 그림에서. `tools/ocr_map.py`가 Vision으로 읽는다.

선도 캔다. 굵게 그은 색 스트로크가 노선이고 꺾임이 그 안에 그대로 들어 있어,
역과 역을 직선으로 잇는 것과는 아주 다른 그림이 나온다. 배경 그림을 깔지 않고
벡터로 그리므로 확대해도 뭉개지지 않고, 역 이름은 우리가 쓰는 말로 붙일 수 있다.

앞선 도구:
    curl -s -X POST 'https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do' \
         --data 'infId=OA-22535&infSeq=2&seq=2' -o seoul_ko.pdf
    pdftocairo -svg seoul_ko.pdf seoul_ko.svg
    pdftocairo -png -r 300 -singlefile seoul_ko.pdf seoul_hi
    swiftc -O -o tools/ocr_labels tools/ocr_labels.swift
    python3 tools/ocr_map.py seoul_hi.png

쓰기:
    python3 tools/seoul_schematic.py seoul_ko.svg seoul_hi.labels.json
"""
import argparse
import json
import math
import pathlib
import re
from collections import defaultdict
from difflib import SequenceMatcher

import numpy as np
from scipy.optimize import linear_sum_assignment

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"
OUT = ROOT / "app/src/main/assets/schematic_map.json"
CREDIT = "서울교통공사 수도권 지하철 노선도 · 공공누리 제1유형"

# 정차역은 지름 6pt 안팎의 흰 원이다. 이보다 작은 것은 글자 속의 구멍(ㅇ, ㅁ)이다.
STOP_MIN, STOP_MAX = 4.5, 8.0
# 환승역은 흰 테 안에 노선 색 점을 늘어놓은 모양이다. 나란히 선 곳은 두 점이면
# 16x26, 서울역처럼 다섯 점이면 20x73이다. 그런데 회기·청량리처럼 선이 대각으로
# 만나는 곳은 점이 비스듬히 놓여 짧은 쪽도 함께 길어진다 — 짧은 쪽을 좁게 잡으면
# 그런 역을 놓치고, 그 자리를 옆 역이 차지해 연쇄로 어긋난다.
TRANSFER_MIN, TRANSFER_MAX = 13.0, 84.0
# 이름 상자에서 역 표시까지 이보다 멀면 남남이다(px 기준, 300dpi).
# 상자 중심이 아니라 상자 테두리에서 재는 것이 중요하다 — OCR가 한글과 로마자를
# 한 덩어리로 읽으면 중심이 오른쪽으로 밀려 왕십리는 250px까지 벌어진다.
LABEL_REACH_PX = 260.0
# OCR가 틀리게 읽은 이름을 역명에 붙일 최소 닮음. 이보다 낮으면 붙이지 않는다.
FUZZY_FLOOR = 0.75
# 표시 모양이 노선 수와 어긋날 때 물리는 벌점. 사정거리보다 커서, 모양이 맞는
# 짝이 있으면 언제나 그쪽이 먼저 간다.
SHAPE_PENALTY = 400.0
# 왼쪽 위 범례가 놓인 자리(pt). 여기에도 노선 색 막대와 환승역 견본 원이 있어,
# 걸러 내지 않으면 노선도에 짧은 색 막대가 뜨고 견본 원이 역 자리를 훔친다.
# 이 안에는 실제 노선이 지나지 않는다.
LEGEND_BOX = (0.0, 0.0, 1650.0, 250.0)


# 곡선을 자를 잣대(pt). 제어점을 이은 길이를 이 값으로 나눠 토막 수를 정한다.
CURVE_STEP = 6.0
# 물의 색과, 물로 볼 최소 크기(pt). 같은 색의 작은 조각은 글자 장식이다.
WATER_COLOUR = "#d9f3fd"
WATER_MIN_SPAN = 100.0
# 역이 선에서 이보다 멀리 떨어져 있으면 선 위로 끌어다 놓는다(px). 공식 그림에도
# 작도 오류가 있다 — 동수는 원이 인천1호선에서 90px 떨어진 허공에 찍혀 있다.
# 나란한 두 노선 사이의 환승역이 27px까지 떨어지므로 그보다 넉넉히 잡는다.
SNAP_IF_FURTHER = 40.0


def in_legend(x, y):
    left, top, right, bottom = LEGEND_BOX
    return left <= x <= right and top <= y <= bottom


def closest_on(point, start, end):
    """선 토막 위에서 점에 가장 가까운 자리."""
    dx, dy = end[0] - start[0], end[1] - start[1]
    length = dx * dx + dy * dy
    along = 0.0 if length == 0 else max(
        0.0, min(1.0, ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / length)
    )
    return start[0] + along * dx, start[1] + along * dy


def numbers(text):
    return [float(v) for v in re.findall(r"-?\d+\.?\d*(?:e-?\d+)?", text)]


def normalize(name):
    return re.sub(r"[\s·・]", "", name).split("(")[0].removesuffix("역")


def marks_from(svg_text):
    """역 표시. 두 가지 모양이 있다.

    정차역은 검은 테를 두른 작은 흰 원이다. 환승역은 흰 캡슐 안에 노선 색 점을
    나란히 넣은 것이라 동그랗지 않다 — 서울역은 점 다섯 개가 들어가 길쭉하다.
    둘 다 흰색으로 채워져 있어, 모양으로만 가른다.
    """
    body = svg_text[svg_text.find("</defs>"):]
    found = []
    for match in re.finditer(r"<path([^>]*)>", body):
        attributes = match.group(1)
        fill = re.search(r'fill="rgb\(([^)]*)\)"', attributes)
        if not fill:
            continue
        channels = [float(v) for v in re.findall(r"([\d.]+)%", fill.group(1))]
        if len(channels) < 3 or min(channels) < 99.0:
            continue  # 흰색만
        drawing = re.search(r'\bd="([^"]*)"', attributes)
        if not drawing or " C " not in drawing.group(1):
            continue  # 곡선이 없으면 원도 캡슐도 아니다
        values = numbers(drawing.group(1))
        xs, ys = values[0::2], values[1::2]
        if not xs or not ys:
            continue
        width, height = max(xs) - min(xs), max(ys) - min(ys)
        short, long = min(width, height), max(width, height)
        stop = STOP_MIN < short and long < STOP_MAX
        transfer = TRANSFER_MIN < short and long < TRANSFER_MAX
        if not stop and not transfer:
            continue
        centre_x, centre_y = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
        if in_legend(centre_x, centre_y):
            continue
        found.append((centre_x, centre_y, long, stop))
    return found


def flatten(path_data, matrix):
    """`d`를 꺾은선의 이어짐으로 푼다.

    이 그림에 쓰인 명령은 M·L·C·Z 넷뿐이고 모두 절대 좌표다. C를 몇 토막으로
    잘라 근사하는데, 토막 수를 곡선 길이에 맞춘다 — 2호선 순환선은 크게 휘어
    네 토막으로는 모서리를 잘라먹고, 그러면 그 위의 역이 선에서 40px 넘게 떠 보인다.
    """
    a, b, c, d, e, f = matrix
    def apply(x, y):
        return (a * x + c * y + e, b * x + d * y + f)

    runs, run = [], []
    start = current = None
    tokens = re.findall(r"([MLCZmlcz])|(-?\d+\.?\d*(?:e-?\d+)?)", path_data)
    index, command = 0, None
    values = []
    steps = {"M": 2, "L": 2, "C": 6, "Z": 0}
    while index < len(tokens):
        letter, number = tokens[index]
        index += 1
        if letter:
            command = letter.upper()
            values = []
            if command == "Z":
                if start and run:
                    run.append(start)
                continue
            continue
        if command is None:
            continue
        values.append(float(number))
        if len(values) < steps[command]:
            continue
        if command == "M":
            if len(run) > 1:
                runs.append(run)
            current = start = apply(values[0], values[1])
            run = [current]
        elif command == "L":
            current = apply(values[0], values[1])
            run.append(current)
        else:  # C
            p0 = current
            p1 = apply(values[0], values[1])
            p2 = apply(values[2], values[3])
            p3 = apply(values[4], values[5])
            reach = (
                math.hypot(p1[0] - p0[0], p1[1] - p0[1])
                + math.hypot(p2[0] - p1[0], p2[1] - p1[1])
                + math.hypot(p3[0] - p2[0], p3[1] - p2[1])
            )
            pieces = max(4, min(64, int(reach / CURVE_STEP) + 1))
            for piece in range(1, pieces + 1):
                t = piece / pieces
                u = 1 - t
                run.append((
                    u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
                    u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1],
                ))
            current = p3
        values = []
    if len(run) > 1:
        runs.append(run)
    return runs


def segments_from(svg_text, min_width=3.0):
    """노선의 선. 굵게 그은 색 스트로크가 노선이고, 그 좌표가 곧 도식의 모양이다.

    선을 우리가 다시 이어 그리지 않고 원본의 꺾임을 그대로 옮기는 것이 요점이다 —
    역과 역을 직선으로 잇는 순간 도식이 아니라 그냥 이은 선이 된다.
    """
    body = svg_text[svg_text.find("</defs>"):]
    segments = []
    for match in re.finditer(r"<path([^>]*)>", body):
        attributes = match.group(1)
        stroke = re.search(r'stroke="rgb\(([^)]*)\)"', attributes)
        width = re.search(r'stroke-width="([\d.]+)"', attributes)
        drawing = re.search(r'\bd="([^"]*)"', attributes)
        if not stroke or not width or not drawing or float(width.group(1)) < min_width:
            continue
        channels = [float(v) for v in re.findall(r"([\d.]+)%", stroke.group(1))]
        if len(channels) < 3:
            continue
        colour = "#%02x%02x%02x" % tuple(round(c * 255 / 100) for c in channels[:3])
        transform = re.search(r'transform="matrix\(([^)]+)\)"', attributes)
        matrix = numbers(transform.group(1)) if transform else [1, 0, 0, 1, 0, 0]
        for run in flatten(drawing.group(1), matrix):
            for first, second in zip(run, run[1:]):
                if in_legend(*first) and in_legend(*second):
                    continue
                segments.append((first, second, colour, float(width.group(1))))
    return segments


def waters_from(svg_text):
    """강. 한강이 노선은 아니지만, 그려야 서울로 읽힌다.

    공식 그림에서 물은 #d9f3fd 로 채운 다각형이다. 큰 것 둘이 한강 본체와
    임진강 물줄기이고, 같은 색의 작은 조각들은 글자 장식이라 크기로 거른다.
    """
    body = svg_text[svg_text.find("</defs>"):]
    waters = []
    for match in re.finditer(r"<path([^>]*)>", body):
        attributes = match.group(1)
        fill = re.search(r'fill="rgb\(([^)]*)\)"', attributes)
        if not fill:
            continue
        channels = [float(v) for v in re.findall(r"([\d.]+)%", fill.group(1))]
        colour = "#%02x%02x%02x" % tuple(round(c * 255 / 100) for c in channels[:3])
        if colour != WATER_COLOUR:
            continue
        drawing = re.search(r'\bd="([^"]*)"', attributes)
        if not drawing:
            continue
        for ring in flatten(drawing.group(1), [1, 0, 0, 1, 0, 0]):
            xs = [x for x, _ in ring]
            ys = [y for _, y in ring]
            if max(max(xs) - min(xs), max(ys) - min(ys)) < WATER_MIN_SPAN:
                continue
            waters.append((ring, colour))
    return waters


def merge_rings(marks, gap=8.0):
    """표시는 테와 속을 겹쳐 그린다. 겹친 것은 큰 쪽으로 하나만 남긴다.

    큰 쪽부터 집으므로 환승역 덩어리 하나가 그 안의 작은 원들을 삼킨다 — 환승역이
    노선 수만큼의 역으로 갈라지지 않게 하는 것이 이 함수의 요점이다.
    """
    kept = []
    for x, y, size, stop in sorted(marks, key=lambda m: -m[2]):
        if any(math.hypot(x - kx, y - ky) < max(gap, size / 2) for kx, ky, _, _ in kept):
            continue
        kept.append((x, y, size, stop))
    return kept


def place(network, marks, labels):
    """역마다 자리를 하나씩 정한다. 이름이 가리키고 거리가 고른다."""
    stations = network["stations"]
    by_name = defaultdict(list)
    for index, station in enumerate(stations):
        by_name[normalize(station["n"])].append(index)
    interchange = [len(station.get("l", [])) > 1 for station in stations]

    pairs = []
    for name, lx, ly, half_width, half_height in labels:
        for index in by_name.get(name, ()):
            for mark, (mx, my, _, stop) in enumerate(marks):
                # 글자 상자 테두리까지의 거리. 상자 안이면 0이다.
                gap_x = max(0.0, abs(mx - lx) - half_width)
                gap_y = max(0.0, abs(my - ly) - half_height)
                distance = math.hypot(gap_x, gap_y)
                if distance > LABEL_REACH_PX:
                    continue
                # 노선이 둘 이상인 역은 환승 표시에, 하나인 역은 정차 원에 붙는다.
                # 왕십리가 옆 마장의 원을 차지해 연쇄로 어긋나는 일을 막는다.
                # 막지 않고 벌점만 물리는 것은 우리 노선망이 OpenStreetMap에서 와
                # 환승 여부가 도식과 갈리는 역이 있기 때문이다.
                if stop == interchange[index]:
                    distance += SHAPE_PENALTY
                pairs.append((index, mark, distance))
    if not pairs:
        return [None] * len(stations)

    rows = sorted({index for index, _, _ in pairs})
    columns = sorted({mark for _, mark, _ in pairs})
    row_at = {index: i for i, index in enumerate(rows)}
    column_at = {mark: j for j, mark in enumerate(columns)}
    far = (LABEL_REACH_PX + SHAPE_PENALTY) * 10
    cost = np.full((len(rows), len(columns)), far)
    for index, mark, distance in pairs:
        i, j = row_at[index], column_at[mark]
        cost[i, j] = min(cost[i, j], distance)

    placed = [None] * len(stations)
    taken = set()
    for i, j in zip(*linear_sum_assignment(cost)):
        if cost[i, j] >= far:
            continue
        x, y, _, _ = marks[columns[j]]
        placed[rows[i]] = [round(x, 1), round(y, 1)]
        taken.add(columns[j])
    # 임자를 못 찾은 표시. 이름을 못 읽어 비어 있는 역이 여기 어딘가에 있으므로,
    # 그 자리만 더 큰 배율로 다시 읽으면 된다 — tools/ocr_focus.py 가 그 일을 한다.
    spare = [(x, y) for mark, (x, y, _, _) in enumerate(marks) if mark not in taken]
    return placed, spare


def read_labels(path, wanted):
    """OCR가 읽은 덩어리에서 역 이름만 골라 낸다.

    공식 노선도는 한 줄에 한글, 그 아래 로마자를 쓴다. 한글만 남기고 로마자는
    버린다. 긴 이름은 두 줄로 꺾여 있어 위아래로 가까운 덩어리를 이어 본다.
    """
    chunks = []
    for item in json.loads(pathlib.Path(path).read_text(encoding="utf-8")):
        text = item["t"]
        x, y = item["x"], item["y"]
        width, height = item.get("w", 24.0), item.get("h", 24.0)
        korean = re.sub(r"[^가-힣0-9·\-]", "", text)
        if korean:
            chunks.append((korean, x, y, width / 2, height / 2))
        # 이웃한 역 이름이 한 줄로 붙어 읽히기도 한다 — 「오류동 개봉 구일」.
        # 통짜 덩어리로는 어느 역도 못 가리키므로, 공백으로 쪼개 낱말마다
        # 글자 수에 비례한 제 자리를 준다.
        words = [w for w in re.split(r"\s+", text.strip()) if re.search(r"[가-힣]", w)]
        if len(words) < 2:
            continue
        total = sum(len(w) for w in words) + len(words) - 1
        left = x - width / 2
        offset = 0
        for word in words:
            korean = re.sub(r"[^가-힣0-9·\-]", "", word)
            share = width * len(word) / total
            if korean:
                chunks.append((
                    korean, left + width * offset / total + share / 2, y,
                    share / 2, height / 2,
                ))
            offset += len(word) + 1

    labels = [
        (normalize(text), x, y, half_width, half_height)
        for text, x, y, half_width, half_height in chunks
        if normalize(text) in wanted
    ]
    # 작은 글자는 OCR가 한두 자를 틀린다 — 「쌍용」을 「쐉용」으로 읽는 식이다.
    # 그대로 맞는 이름이 없는 덩어리만 가장 닮은 역명에 붙인다. 닮음의 문턱을
    # 높게 두어, 애매하면 붙이지 않고 그 역을 비워 둔다.
    for text, x, y, half_width, half_height in chunks:
        name = normalize(text)
        if name in wanted or len(name) < 2:
            continue
        close = [
            (SequenceMatcher(None, name, candidate).ratio(), candidate)
            for candidate in wanted
            if abs(len(candidate) - len(name)) <= 2
        ]
        if not close:
            continue
        ratio, candidate = max(close)
        # 두 글자까지 다른 것도 받되 문턱을 올린다 — 「용인중앙시장」이 「용인중앙」으로
        # 잘려 읽히는 것은 받고(0.80), 「중앙」이 아무 데나 붙는 것은 막는다.
        floor = FUZZY_FLOOR if abs(len(candidate) - len(name)) <= 1 else 0.8
        if ratio >= floor:
            labels.append((candidate, x, y, half_width, half_height))
    # 두 줄로 꺾인 이름. 아래 줄을 위 줄에 붙여 본다.
    for text, x, y, half_width, half_height in chunks:
        for other, ox, oy, other_half_width, other_half_height in chunks:
            if abs(x - ox) > half_height * 4 or not 0 < oy - y < half_height * 4.4:
                continue
            joined = normalize(text + other)
            if joined in wanted:
                labels.append((
                    joined,
                    (x + ox) / 2,
                    (y + oy) / 2,
                    max(half_width, other_half_width),
                    (oy - y) / 2 + max(half_height, other_half_height),
                ))
    return labels


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("svg", help="PDF를 pdftocairo -svg 로 바꾼 것")
    parser.add_argument("labels", help="tools/ocr_map.py 가 내놓은 .labels.json")
    parser.add_argument("--dpi", type=float, default=300.0, help="OCR에 쓴 그림의 해상도")
    parser.add_argument(
        "--spare",
        help="임자를 못 찾은 표시의 자리를 적어 둘 곳. tools/ocr_focus.py 가 받는다.",
    )
    options = parser.parse_args()

    svg_text = pathlib.Path(options.svg).read_text(encoding="utf-8", errors="replace")
    box = numbers(re.search(r'viewBox="([^"]+)"', svg_text).group(1))
    # 벡터는 pt, OCR는 px. px로 맞춘다 — 배경 그림과 같은 자리라야 겹쳐 그릴 수 있다.
    scale = options.dpi / 72.0

    marks = merge_rings(marks_from(svg_text))
    marks = [(x * scale, y * scale, size * scale, stop) for x, y, size, stop in marks]
    segments = [
        ((a[0] * scale, a[1] * scale), (b[0] * scale, b[1] * scale), colour, width * scale)
        for a, b, colour, width in segments_from(svg_text)
    ]
    waters = [
        ([round(v * scale, 1) for point in ring for v in point], colour)
        for ring, colour in waters_from(svg_text)
    ]

    network = json.loads(NETWORK.read_text(encoding="utf-8"))
    wanted = {normalize(station["n"]) for station in network["stations"]}
    labels = read_labels(options.labels, wanted)
    placed, spare = place(network, marks, labels)

    # 선에서 떨어져 찍힌 역을 선 위로. 공식 그림에도 작도 오류가 있어 —
    # 동수의 원은 인천1호선에서 90px 떨어진 허공에 있다 — 원본 충실과 어긋나지만,
    # 노선도에서 역이 선 밖에 뜬 것은 그냥 틀린 그림이다.
    for index, point in enumerate(placed):
        if point is None:
            continue
        gap, (x, y) = min(
            (math.hypot(point[0] - cx, point[1] - cy), (cx, cy))
            for a, b, _, _ in segments
            for cx, cy in [closest_on(point, a, b)]
        )
        if gap > SNAP_IF_FURTHER:
            print(f"선 밖에 뜬 역을 끌어다 놓는다: {network['stations'][index]['n']} ({gap:.0f}px)")
            placed[index] = [round(x, 1), round(y, 1)]

    found = sum(1 for point in placed if point)
    print(f"역 표시 {len(marks)} · 이름 {len(labels)} · 선 토막 {len(segments)} · 물 {len(waters)} · 자리 찾은 역 {found}/{len(placed)}")
    missing = [s["n"] for s, p in zip(network["stations"], placed) if not p]
    if missing:
        print(f"못 찾은 역 {len(missing)}: {', '.join(missing)}")
    if options.spare:
        pathlib.Path(options.spare).write_text(
            json.dumps([[round(x, 1), round(y, 1)] for x, y in spare]), encoding="utf-8"
        )
        print(f"임자 없는 표시 {len(spare)} → {options.spare}")

    OUT.write_text(
        json.dumps(
            {
                "source": CREDIT,
                "w": round(box[2] * scale, 1),
                "h": round(box[3] * scale, 1),
                "p": placed,
                "g": [{"p": points, "c": colour} for points, colour in waters],
                "s": [
                    {
                        "a": [round(a[0], 1), round(a[1], 1)],
                        "b": [round(b[0], 1), round(b[1], 1)],
                        "c": colour,
                        "w": round(width, 1),
                    }
                    for a, b, colour, width in segments
                ],
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )
    print(f"{OUT.relative_to(ROOT)} · {OUT.stat().st_size // 1024}KB")


if __name__ == "__main__":
    main()
