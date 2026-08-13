#!/usr/bin/env python3
"""여객 운행이 없는 역을 노선망에서 뺀다.

노선망은 OpenStreetMap에서 왔고, OSM은 선로가 닿는 역을 다 갖고 있다. 그중에는
지하철이라 부를 수 없는 곳이 있다 — 도라산은 민통선 안이라 매달 둘째 금요일에
예약제 관광열차 한 편이 들어갈 뿐이다(2022년 2월 코로나로 끊겼다가 2024년 8월에
그 꼴로만 재개). 남겨 두면 길찾기가 보통 역처럼 취급해서, 목적지로 잡으면
존재하지 않는 열차를 타는 계획이 나온다. 서울교통공사 공식 노선도도 이 역을
그리지 않는다.

임진강은 다르다 — 평일 2회·주말 4회로 드물지만 정기 운행이라 남긴다.

순번이 밀리므로 노선 경로와 역간 소요시간도 함께 고쳐 쓴다. 도식 좌표는
순번으로 담겨 있어 이 뒤에 tools/seoul_schematic.py 를 다시 돌려야 한다.

사용법:
    python3 tools/prune_stations.py
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).parent.parent
ASSET = ROOT / "app/src/main/assets/subway_map.json"

# 뺄 역과 그 까닭. 까닭 없이 이름만 늘어놓지 않는다 — 지울 때는 왜 지웠는지가
# 나중에 이 역이 되살아나야 할 때(정기 운행 재개) 판단 기준이 된다.
REMOVE = {
    "도라산": "정기 여객 운행 없음 — 월 1회 예약제 관광열차뿐. 공식 노선도에도 없다.",
}


def main():
    network = json.loads(ASSET.read_text(encoding="utf-8"))
    stations = network["stations"]

    doomed = {i for i, station in enumerate(stations) if station["n"] in REMOVE}
    if not doomed:
        print("뺄 것이 없다.")
        return

    survivors = [i for i in range(len(stations)) if i not in doomed]
    renumber = {old: new for new, old in enumerate(survivors)}

    for line in network["lines"]:
        rebuilt = []
        for path in line["p"]:
            # 빠진 역이 경로 한가운데면 길이 끊긴다. 도라산은 종점이라 걸리지
            # 않지만, 나중에 누가 중간 역을 지우려 들면 여기서 막혀야 한다.
            for k, index in enumerate(path):
                if index in doomed and 0 < k < len(path) - 1:
                    raise SystemExit(
                        f"{stations[index]['n']} 은 {line['n']} 경로의 한가운데다. "
                        "빼면 노선이 끊긴다 — 이 역은 지울 수 없다."
                    )
            run = [renumber[i] for i in path if i not in doomed]
            if len(run) > 1:
                rebuilt.append(run)
        line["p"] = rebuilt

    network["times"] = [
        [renumber[a], renumber[b], seconds]
        for a, b, seconds in network.get("times", [])
        if a not in doomed and b not in doomed
    ]
    network["stations"] = [stations[i] for i in survivors]

    ASSET.write_text(
        json.dumps(network, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    for index in sorted(doomed):
        print(f"뺐다: {stations[index]['n']} — {REMOVE[stations[index]['n']]}")
    print(f"역 {len(stations)} → {len(survivors)}")
    print("\n도식 좌표는 순번으로 담겨 있다. tools/seoul_schematic.py 를 다시 돌릴 것.")


if __name__ == "__main__":
    main()
