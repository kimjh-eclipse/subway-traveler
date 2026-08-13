#!/usr/bin/env python3
"""역 이름을 지금 쓰이는 이름으로 고친다.

역 이름은 바뀐다. 우리가 가진 출처들은 시점이 제각각이라 — OpenStreetMap은
바뀐 다음 날, 서울교통공사 공식 노선도는 해마다 한 번 — 어느 하나를 그대로
믿으면 옛 이름이 남는다. 실제로 그랬다. 2026년 7월 1일 인천 서구가 서해구로
개칭되며 서구청역이 서해구청역이 됐는데, 공식 노선도(2025-09-29판)를 기준으로
삼아 새 이름 쪽 노드를 지워 버린 일이 있다.

여기 적힌 이름이 최종이다. 자산을 다시 만들 때 어떤 출처가 옛 이름을 가져와도
이 도구가 마지막에 덮어쓴다.

사용법:
    python3 tools/rename_stations.py
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).parent.parent
ASSET = ROOT / "app/src/main/assets/subway_map.json"

# 옛 이름 → 지금 이름. 바뀐 날짜와 근거를 함께 적는다.
RENAMES = {
    # 인천시 고시(2026-06-12), 2026-07-01 시행. 서구가 서해구로 개칭되면서.
    "서구청": "서해구청",
}


def main():
    network = json.loads(ASSET.read_text(encoding="utf-8"))
    changed = 0
    for station in network["stations"]:
        new = RENAMES.get(station["n"])
        if new is None:
            continue
        print(f"고침: {station['n']} → {new}")
        station["n"] = new
        changed += 1
    if not changed:
        print("고칠 것이 없다.")
        return
    ASSET.write_text(
        json.dumps(network, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    print(f"역 이름 {changed}개를 고쳤다. 이름을 쓰는 자산(station_names 등)도 다시 만들 것.")


if __name__ == "__main__":
    main()
