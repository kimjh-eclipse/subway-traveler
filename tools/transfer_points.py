#!/usr/bin/env python3
"""환승역에서 어느 칸에 내려 어디로 갈아타는지를 자산으로 굽는다.

서울교통공사가 환승역마다 **최단 환승 경로**를 공개한다 — 몇 호차 몇 번 문에서
내려 어느 문으로 타면 되는지, 그리고 얼마나 걸리는지. 낯선 역에서 캐리어를 끌고
반대편 끝까지 걷는 일이 이 한 줄로 사라진다.

중요한 것은 이 자료가 **방향별**이라는 점이다. 같은 서울역이라도 시청 방면에서
왔으면 1-1, 남영 방면에서 왔으면 1-2에서 내린다. 방향을 무시하고 아무 줄이나
쓰면 엉뚱한 칸에 서 있게 된다 — 조용히 틀리는 종류라 더 나쁘다.

이름은 노선망(OSM)과 어긋난다. 실시간 도착 때만큼 심하지는 않아 규칙 일곱 개로
끝난다.

    총신대입구 → 총신대입구 (이수)      경의선       → 경의중앙선
    남부터미널 → 남부터미널(예술의전당)   용인경전철    → 용인 경전철
    1          → 1호선                우이신설경전철 → 우이신설선
                                     인천1        → 인천1호선

자료: 서울 열린데이터광장 「서울교통공사_수도권 도시철도 환승 데이터」
      https://data.seoul.go.kr/dataList/OA-22521/F/1/datasetView.do
      공공누리 1유형 (출처표시, 상업적 이용·변경 가능)

사용법:
    python3 tools/transfer_points.py ~/Downloads/서울교통공사_수도권*.csv
"""
import csv
import io
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).parent.parent
NETWORK = ROOT / "app/src/main/assets/subway_map.json"
ASSET = ROOT / "app/src/main/assets/transfer_points.json"
SOURCE = "서울교통공사 「수도권 도시철도 환승 데이터」 · 공공누리 1유형"

# 자료 쪽 표기 → 노선망 표기. 숫자만 있는 호선은 규칙으로 풀리므로 여기 두지 않는다.
LINE_ALIASES = {
    "경의선": "경의중앙선",
    "용인경전철": "용인 경전철",
    "우이신설경전철": "우이신설선",
    "인천1": "인천1호선",
    "인천2": "인천2호선",
}


def read_csv(path):
    """서울시 공개 자료는 대개 CP949다. UTF-8로 바뀌어도 읽히게 둘 다 시도한다."""
    raw = pathlib.Path(path).read_bytes()
    for encoding in ("utf-8-sig", "cp949", "euc-kr"):
        try:
            return list(csv.DictReader(io.StringIO(raw.decode(encoding))))
        except UnicodeDecodeError:
            continue
    sys.exit("CSV 인코딩을 알아내지 못했다.")


def station_resolver(names):
    """자료의 역 이름을 노선망 이름으로. 못 찾으면 None."""
    known = set(names)

    def resolve(raw):
        name = raw.strip()
        if not name:
            return None
        if name in known:
            return name
        if name.endswith("역") and name[:-1] in known:
            return name[:-1]
        if name + "역" in known:
            return name + "역"
        # 노선망에만 괄호 병기가 붙은 경우 — 총신대입구 (이수), 남부터미널(예술의전당).
        bare = re.sub(r"\s", "", name)
        for candidate in known:
            if re.sub(r"\s", "", candidate).split("(")[0] == bare:
                return candidate
        return None

    return resolve


def resolve_line(raw, known):
    name = raw.strip()
    if name.isdigit():
        name = f"{name}호선"
    name = LINE_ALIASES.get(name, name)
    return name if name in known else None


def seconds_of(text):
    """`10:00` → 600. 분:초다."""
    parts = text.strip().split(":")
    if len(parts) != 2:
        return None
    try:
        return int(parts[0]) * 60 + int(parts[1])
    except ValueError:
        return None


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)

    network = json.loads(NETWORK.read_text(encoding="utf-8"))
    station = station_resolver(s["n"] for s in network["stations"])
    lines = {line["n"] for line in network["lines"]}

    kept, dropped = [], []
    for row in read_csv(sys.argv[1]):
        at = station(row["환승시작역"])
        from_line = resolve_line(row["환승시작 호선"], lines)
        to_line = resolve_line(row["환승종료 호선"], lines)
        # `시청 방면`에서 역 이름만 남긴다.
        from_towards = station(row["하차 열차 방면"].replace("방면", ""))
        to_towards = station(row["환승 열차 방면"].replace("방면", ""))
        seconds = seconds_of(row["소요시간"])
        off = f"{row['하차위치(호차)'].strip()}-{row['하차위치(문)'].strip()}"
        on = f"{row['환승 승차위치(호차)'].strip()}-{row['환승 승차위치(문)'].strip()}"

        # 소요시간은 없어도 남긴다. 중랑·상봉·망우의 경의중앙선 갈아타기가 그런데,
        # 정작 알고 싶은 '어느 칸'은 멀쩡히 있다. 시간 하나 없다고 버릴 이유가 없다.
        if not all([at, from_line, to_line, from_towards, to_towards]) or off == "-" or on == "-":
            dropped.append((row["고유번호"], row["환승시작역"], row["환승시작 호선"], row["환승종료 호선"]))
            continue
        # 초는 문자열로 낸다 — 한 줄 안에서 자료형이 섞이지 않아야 읽는 쪽이 단순하다.
        kept.append([at, from_line, from_towards, to_line, to_towards, off, on,
                     None if seconds is None else str(seconds)])

    ASSET.write_text(
        json.dumps({"source": SOURCE, "t": kept}, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    stations = {row[0] for row in kept}
    untimed = sum(1 for row in kept if row[7] is None)
    print(f"{len(kept)}행 · 환승역 {len(stations)}곳 · 소요시간 없는 행 {untimed}")
    # 버린 것은 반드시 밝힌다 — 조용히 사라지면 덮인 줄 알게 된다.
    for item in dropped:
        print(f"  버림: {item}")
    print(f"{ASSET.name} {ASSET.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
