#!/usr/bin/env python3
"""이름을 못 읽은 자리만 더 큰 배율로 다시 읽는다.

`tools/ocr_map.py`는 노선도 전체를 300dpi로 읽는다. 그것으로 656개 역명 가운데
585개가 잡히는데, 남는 것은 하나같이 작게 쓰인 이름이다 — 노선 끝의 천안·직산·
쌍용, 의정부경전철의 흥선·새말 같은 것들. 지도를 통째로 더 크게 렌더하면
10,000px가 20,000px가 되어 메모리만 잡아먹으므로, **임자를 못 찾은 표시 주변만**
오려서 두 배 배율로 읽는다.

임자 없는 표시가 곧 이름을 못 읽은 역의 자리다. 그 자리는
`tools/seoul_schematic.py --spare` 가 적어 준다.

쓰기:
    python3 tools/seoul_schematic.py 노선도.svg 노선도.labels.json --spare spare.json
    python3 tools/ocr_focus.py 노선도.pdf spare.json 노선도.labels.json
    python3 tools/seoul_schematic.py 노선도.svg 노선도.labels.json   # 다시
"""
import argparse
import json
import math
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).parent.parent
# 표시 하나를 둘러싸고 오릴 크기(원래 배율의 px). 이름이 표시에서 260px까지
# 떨어져 있을 수 있어 넉넉히 잡는다.
WINDOW = 700
# 이만큼 안에 든 표시들은 한 조각에 함께 담는다. 노선 끝에서는 못 읽은 역이
# 줄줄이 붙어 있어, 하나씩 오리면 같은 자리를 여러 번 읽는다.
CLUSTER = 500


def cluster(points):
    """가까운 자리끼리 묶어 오릴 상자를 만든다."""
    remaining = list(points)
    boxes = []
    while remaining:
        group = [remaining.pop()]
        changed = True
        while changed:
            changed = False
            for point in list(remaining):
                if any(math.dist(point, member) <= CLUSTER for member in group):
                    group.append(point)
                    remaining.remove(point)
                    changed = True
        xs = [x for x, _ in group]
        ys = [y for _, y in group]
        boxes.append((min(xs), min(ys), max(xs), max(ys)))
    return boxes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("pdf", help="공식 노선도 PDF")
    parser.add_argument("spare", help="임자 없는 표시의 자리 (--spare 가 적은 것)")
    parser.add_argument("labels", help="여기에 읽은 것을 더한다")
    parser.add_argument("--dpi", type=float, default=300.0, help="labels 의 기준 해상도")
    parser.add_argument("--zoom", type=float, default=2.0, help="몇 배로 키워 읽을지")
    parser.add_argument("--binary", default=None)
    options = parser.parse_args()

    binary = options.binary or (ROOT / "tools" / "ocr_labels")
    if not pathlib.Path(binary).exists():
        raise SystemExit(
            f"{binary} 가 없습니다. 먼저 컴파일하세요:\n"
            f"  swiftc -O -o tools/ocr_labels tools/ocr_labels.swift"
        )

    points = [tuple(p) for p in json.loads(pathlib.Path(options.spare).read_text())]
    boxes = cluster(points)
    print(f"임자 없는 표시 {len(points)} → 오릴 조각 {len(boxes)}개 ({options.zoom:g}배)")

    labels = json.loads(pathlib.Path(options.labels).read_text(encoding="utf-8"))
    before = len(labels)
    high_dpi = options.dpi * options.zoom

    with tempfile.TemporaryDirectory() as workspace:
        stem = pathlib.Path(workspace) / "crop"
        for number, (left, top, right, bottom) in enumerate(boxes, 1):
            # 오릴 상자를 큰 배율의 px로 옮긴다.
            x = max(0, int((left - WINDOW / 2) * options.zoom))
            y = max(0, int((top - WINDOW / 2) * options.zoom))
            width = int((right - left + WINDOW) * options.zoom)
            height = int((bottom - top + WINDOW) * options.zoom)
            subprocess.run(
                [
                    "pdftocairo", "-png", "-r", str(high_dpi), "-singlefile",
                    "-x", str(x), "-y", str(y), "-W", str(width), "-H", str(height),
                    options.pdf, str(stem),
                ],
                check=True,
                capture_output=True,
            )
            found = json.loads(
                subprocess.run(
                    [str(binary), f"{stem}.png"], capture_output=True, text=True, check=True
                ).stdout
                or "[]"
            )
            for item in found:
                # 조각 안의 자리를 원래 배율의 자리로 되돌린다.
                labels.append({
                    "t": item["t"],
                    "x": (x + item["x"]) / options.zoom,
                    "y": (y + item["y"]) / options.zoom,
                    "w": item["w"] / options.zoom,
                    "h": item["h"] / options.zoom,
                    "c": item["c"],
                })
            if number % 5 == 0 or number == len(boxes):
                print(f"   {number}/{len(boxes)} · 누적 {len(labels) - before}")

    pathlib.Path(options.labels).write_text(
        json.dumps(labels, ensure_ascii=False), encoding="utf-8"
    )
    print(f"더 읽은 글자 덩어리 {len(labels) - before} → {options.labels}")


if __name__ == "__main__":
    main()
