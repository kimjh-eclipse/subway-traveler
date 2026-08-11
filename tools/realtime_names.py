#!/usr/bin/env python3
"""실시간 도착 API가 쓰는 역 이름을 subway_map.json에 채워 넣는다.

노선망은 OpenStreetMap에서 왔고 실시간 도착은 서울시 API라, 같은 역을 서로 다른
이름으로 부른다. 방향이 한쪽이 아니라 둘 다다.

    OSM 교대(법원·검찰청)   → API 교대
    OSM 군자               → API 군자(능동)

이름이 어긋나면 API는 오류 대신 `INFO-200`(자료 없음)을 돌려주므로, 화면에는
"들어올 열차가 없습니다"라고 뜬다. 고장인지 막차 이후인지 구분이 안 된다.
그래서 어긋나는 역에만 `r` 항목을 달아 둔다.

자료: 서울 열린데이터광장 '지하철 실시간 도착정보' 역정보 xlsx
      https://data.seoul.go.kr/dataList/OA-12764/A/1/datasetView.do

사용법:
    python3 tools/realtime_names.py ~/Downloads/실시간도착_역정보\\(20260804\\).xlsx
"""
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
ASSET = pathlib.Path(__file__).parent.parent / "app/src/main/assets/subway_map.json"


def official_names(xlsx):
    """xlsx의 STATN_NM 열. openpyxl 없이 읽으려고 시트를 직접 푼다."""
    book = zipfile.ZipFile(xlsx)
    shared = [
        "".join(t.text or "" for t in si.iter(NS + "t"))
        for si in ET.fromstring(book.read("xl/sharedStrings.xml"))
    ]
    rows = []
    for row in ET.fromstring(book.read("xl/worksheets/sheet1.xml")).iter(NS + "row"):
        cells = []
        for cell in row.iter(NS + "c"):
            value = cell.find(NS + "v")
            text = "" if value is None else value.text
            cells.append(shared[int(text)] if cell.get("t") == "s" and text else text)
        rows.append(cells)
    return sorted({r[2] for r in rows[1:] if len(r) > 2 and r[2]})


def key(name):
    """괄호 병기와 띄어쓰기, 끝의 '역'을 떼어 두 이름을 같은 자리에 놓는다."""
    stripped = re.sub(r"\s", "", name).split("(")[0]
    return stripped.removesuffix("역") or stripped


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)

    lookup = {}
    for name in official_names(sys.argv[1]):
        lookup.setdefault(key(name), name)

    data = json.loads(ASSET.read_text(encoding="utf-8"))
    changed = 0
    for station in data["stations"]:
        station.pop("r", None)
        api = lookup.get(key(station["n"]))
        # 실시간 API가 다루지 않는 노선(에버랜드선 등)은 짝이 없다. 그대로 둔다.
        if api and api != station["n"]:
            station["r"] = api
            changed += 1

    ASSET.write_text(
        json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    print(f"{changed}개 역에 실시간 이름을 달았다 (전체 {len(data['stations'])}개).")


if __name__ == "__main__":
    main()
