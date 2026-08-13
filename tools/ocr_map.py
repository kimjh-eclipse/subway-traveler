#!/usr/bin/env python3
"""공식 노선도 그림에서 역 이름과 그 자리를 읽는다.

서울교통공사가 내놓은 노선도는 벡터이지만 역 이름이 외곽선으로 변환돼 있어
글자로는 여덟 낱말만 남아 있다. 자리는 벡터에서 캐낼 수 있어도 그 자리가 어느
역인지는 이름을 읽어야 안다.

Vision은 그림이 크면 줄여서 읽는다. 10,630px 한 장을 그대로 주면 범례만 잡히고
역 이름은 뭉개진다. 그래서 겹쳐 자른 조각마다 읽고 자리를 되돌려 합친다.
겹쳐 자르는 것은 조각 경계에 걸린 이름을 잃지 않기 위한 것이고, 겹친 만큼
같은 이름이 두 번 읽히므로 가까운 것끼리 하나로 줄인다.

쓰기:
    python3 tools/ocr_map.py 노선도.png [--tile 1700] [--overlap 300]
    → 노선도.labels.json
"""
import argparse
import json
import pathlib
import subprocess
import tempfile

from PIL import Image

ROOT = pathlib.Path(__file__).parent.parent
# 같은 글자가 이만큼 안에서 두 번 읽히면 한 번으로 본다. 겹친 자리에서 생긴
# 그림자이지, 이름이 같은 다른 역이 이렇게 붙어 있지는 않다.
SAME_LABEL_PIXELS = 40.0


def run_ocr(binary, path):
    output = subprocess.run(
        [str(binary), str(path)], capture_output=True, text=True, check=True
    ).stdout
    return json.loads(output) if output.strip() else []


def dedupe(labels):
    kept = []
    for label in sorted(labels, key=lambda l: -l["c"]):
        twin = next(
            (
                k
                for k in kept
                if k["t"] == label["t"]
                and abs(k["x"] - label["x"]) < SAME_LABEL_PIXELS
                and abs(k["y"] - label["y"]) < SAME_LABEL_PIXELS
            ),
            None,
        )
        if twin is None:
            kept.append(label)
    return kept


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image")
    parser.add_argument("--tile", type=int, default=1700, help="조각 한 변의 픽셀")
    parser.add_argument("--overlap", type=int, default=300, help="조각끼리 겹치는 픽셀")
    parser.add_argument("--binary", default=None, help="ocr_labels 실행파일")
    options = parser.parse_args()

    binary = options.binary or (ROOT / "tools" / "ocr_labels")
    if not pathlib.Path(binary).exists():
        raise SystemExit(
            f"{binary} 가 없습니다. 먼저 컴파일하세요:\n"
            f"  swiftc -O -o tools/ocr_labels tools/ocr_labels.swift"
        )

    image = Image.open(options.image)
    width, height = image.size
    step = options.tile - options.overlap
    boxes = [
        (x, y)
        for y in range(0, height, step)
        for x in range(0, width, step)
    ]
    print(f"그림 {width}x{height} · 조각 {len(boxes)}개 ({options.tile}px, 겹침 {options.overlap}px)")

    labels = []
    with tempfile.TemporaryDirectory() as workspace:
        tile_path = pathlib.Path(workspace) / "tile.png"
        for number, (x, y) in enumerate(boxes, 1):
            right = min(x + options.tile, width)
            bottom = min(y + options.tile, height)
            if right - x < 40 or bottom - y < 40:
                continue
            image.crop((x, y, right, bottom)).save(tile_path)
            for label in run_ocr(binary, tile_path):
                label["x"] += x
                label["y"] += y
                labels.append(label)
            if number % 10 == 0 or number == len(boxes):
                print(f"   {number}/{len(boxes)} · 누적 {len(labels)}")

    labels = dedupe(labels)
    out = pathlib.Path(options.image).with_suffix(".labels.json")
    out.write_text(json.dumps(labels, ensure_ascii=False), encoding="utf-8")
    print(f"읽은 글자 덩어리 {len(labels)} → {out}")


if __name__ == "__main__":
    main()
