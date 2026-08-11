#!/usr/bin/env python3
"""실시간 도착 API가 쓰는 역 이름을 subway_map.json에 채워 넣는다.

노선망은 OpenStreetMap에서, 실시간 도착은 서울시에서 온다. 같은 역을 서로 다르게
부르고, 방향도 한쪽이 아니다.

    OSM 군자              → API 군자(능동)
    OSM 교대(법원·검찰청)   → API 교대
    OSM 이수              → API 총신대입구(이수)
    OSM 평택지제           → API 지제

이름이 어긋나면 API는 오류가 아니라 `INFO-200`(자료 없음)을 준다. 조용히 틀리므로
화면만 봐서는 고장인지 막차가 끊긴 건지 알 수 없다.

이름이 하나로 안 되는 역도 있다. 올림픽공원은 API가 노선별로 쪼개 두어
`올림픽공원`(9호선)과 `올림픽공원(한국체대)`(5호선)이 따로 있다. 그래서 `r`은
목록이다.

짝을 짓는 방법은 세 단계다.

1. 엄격하게 맞춘다 — 이름이 같거나, 괄호와 '역'을 뗀 알맹이가 같거나, 한쪽의
   괄호 안이 다른 쪽의 알맹이인 경우(`이수` ↔ `총신대입구(이수)`). 여기서 맞은
   공식 이름은 그 역이 **가져간 것**으로 표시한다.
2. 남은 역만 느슨하게 맞춘다 — 글자가 순서대로 들어 있으면 짝으로 본다
   (`지제` ⊂ `평택지제`, `세종왕릉` ⊂ `세종대왕릉`). 대신 1단계에서 아무도
   가져가지 않은 이름만 쓴다. 이 울타리가 없으면 `신림`이 `신도림`에, `잠실나루`가
   `잠실`에 붙는다 — 실제로 그렇게 됐었다.
3. 고른 짝을 실제로 API에 물어 확인한다. 자료가 오지 않으면 버린다. 추측을
   자료에 남기지 않기 위해서다.

노선이 겹치는 후보만 본다는 조건이 1·2단계 내내 걸려 있다.

자료: 서울 열린데이터광장 '지하철 실시간 도착정보' 역정보 xlsx
      https://data.seoul.go.kr/dataList/OA-12764/A/1/datasetView.do

사용법:
    python3 tools/realtime_names.py ~/Downloads/실시간도착_역정보\\(20260804\\).xlsx
    # 인증키는 저장소 루트의 .env에서 읽는다 (SEOUL_SUBWAY_API_KEY).
"""
import json
import pathlib
import re
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
ROOT = pathlib.Path(__file__).parent.parent
ASSET = ROOT / "app/src/main/assets/subway_map.json"
HOST = "http://swopenapi.seoul.go.kr/api/subway"


def api_key():
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        if line.startswith("SEOUL_SUBWAY_API_KEY="):
            return line.split("=", 1)[1].strip()
    sys.exit(".env에 SEOUL_SUBWAY_API_KEY가 없다.")


def official_lines(xlsx):
    """공식 역명 → 그 이름이 담당하는 노선들. openpyxl 없이 시트를 직접 푼다."""
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

    lines = defaultdict(set)
    for row in rows[1:]:
        if len(row) > 3 and row[2]:
            lines[row[2]].add(row[3])
    return lines


def bare(name):
    """괄호 병기와 띄어쓰기, 끝의 '역'을 뗀 알맹이."""
    stripped = re.sub(r"\s", "", name).split("(")[0]
    return stripped.removesuffix("역") or stripped


def inside(name):
    """괄호 안의 병기 이름. `총신대입구(이수)` → `이수`."""
    found = re.search(r"\(([^)]*)\)", re.sub(r"\s", "", name))
    return found.group(1) if found else ""


def subsequence(short, long):
    """`세종왕릉`이 `세종대왕릉` 안에 순서대로 들어 있는가."""
    it = iter(long)
    return all(ch in it for ch in short)


def strictly_alike(ours, theirs):
    """이름이 같다고 단언할 수 있는 경우만."""
    a, b = bare(ours), bare(theirs)
    return a == b or inside(ours) == b or inside(theirs) == a


def loosely_alike(ours, theirs):
    """글자가 순서대로 들어 있는가. 아무도 안 가져간 이름에만 쓴다."""
    a, b = bare(ours), bare(theirs)
    short, long = (a, b) if len(a) <= len(b) else (b, a)
    return bool(short) and subsequence(short, long)


def probe(key, name):
    """이 이름으로 실제 도착 정보가 오는가. 오는 노선까지 돌려준다."""
    url = f"{HOST}/{key}/xml/realtimeStationArrival/0/15/{urllib.parse.quote(name)}"
    for _ in range(3):
        try:
            body = urllib.request.urlopen(url, timeout=25).read().decode("utf-8", "replace")
        except Exception:
            continue
        return name, set(re.findall(r"<subwayId>(.*?)</subwayId>", body))
    return name, set()


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)

    key = api_key()
    lines_of = official_lines(sys.argv[1])
    data = json.loads(ASSET.read_text(encoding="utf-8"))

    # 1단계 — 엄격하게. 노선이 겹치는 공식 이름만 본다.
    wanted = {}
    claimed = set()
    for station in data["stations"]:
        ours = set(station.get("l", []))
        picks = sorted(
            name
            for name, serves in lines_of.items()
            if serves & ours and strictly_alike(station["n"], name)
        )
        wanted[station["n"]] = picks
        claimed.update(picks)

    # 2단계 — 아직 짝이 없는 역만, 아무도 안 가져간 이름을 상대로.
    for station in data["stations"]:
        if wanted[station["n"]]:
            continue
        ours = set(station.get("l", []))
        wanted[station["n"]] = sorted(
            name
            for name, serves in lines_of.items()
            if name not in claimed and serves & ours and loosely_alike(station["n"], name)
        )

    # 이름이 이미 맞으면 굳이 확인하지 않는다 — 호출 한도가 아깝다.
    for name, picks in wanted.items():
        if picks == [name]:
            wanted[name] = []

    todo = sorted({name for picks in wanted.values() for name in picks})
    print(f"확인할 이름 {len(todo)}개…")
    with ThreadPoolExecutor(6) as pool:
        verified = {name: ids for name, ids in pool.map(lambda n: probe(key, n), todo)}

    changed = 0
    for station in data["stations"]:
        station.pop("r", None)
        # 자료가 실제로 오는 이름만 남긴다.
        names = [n for n in wanted[station["n"]] if verified.get(n)]
        if names and names != [station["n"]]:
            station["r"] = names
            changed += 1

    ASSET.write_text(
        json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    print(f"{changed}개 역에 실시간 이름을 달았다 (전체 {len(data['stations'])}개).")


if __name__ == "__main__":
    main()
