package com.actimedi.travle.data

private fun move(line: String, destination: String, start: String, end: String, minutes: Int) =
    RouteSegment.Move(line, destination, ClockTime.parse(start), ClockTime.parse(end), minutes)

private fun stay(place: String, label: String, start: String, end: String, minutes: Int) =
    RouteSegment.Stay(place, label, ClockTime.parse(start), ClockTime.parse(end), minutes)

const val SEED_ROUTE_ID = "seed-seoul-one-day"

/**
 * The route from the design file, transcribed one-for-one from its `RAW` array.
 * Seeded on first launch so the app is never empty.
 */
val SeoulOneDayRoute = Route(
    id = SEED_ROUTE_ID,
    title = "교동마을 → 서울 한 바퀴\n→ 교동마을",
    dayOfWeek = "토요일",
    createdAt = 0L,
    segments = listOf(
        stay("교동마을 LG자이", "정류장 도착 · 26-2번 대기", "07:00", "07:08", 8),
        move("26-2번", "미금역", "07:08", "07:23", 15),
        move("신분당선", "강남역", "07:33", "07:49", 16),
        stay("강남", "강남역 구경 30분", "07:49", "08:27", 38),
        move("2호선", "선릉역", "08:31", "08:36", 5),
        stay("선릉", "아침 식사 + PC방 1시간", "08:36", "09:46", 70),
        move("수인분당선", "강남구청역", "09:54", "09:58", 4),
        move("7호선", "어린이대공원역", "10:04", "10:13", 9),
        stay("어린이대공원", "세종대 + 어린이대공원", "10:13", "11:00", 47),
        move("7호선", "군자역", "11:04", "11:06", 2),
        move("5호선", "청구역", "11:13", "11:23", 10),
        move("6호선", "신당역", "11:30", "11:32", 2),
        stay("신당", "점심 — 신당동 떡볶이", "11:32", "12:42", 70),
        move("2호선", "동대문역사문화공원역", "12:46", "12:48", 2),
        stay("DDP", "동대문디자인플라자 구경 + 디저트", "12:48", "13:56", 68),
        move("4호선", "명동역", "14:00", "14:04", 4),
        stay("명동", "명동 거리 구경", "14:04", "15:12", 68),
        move("4호선", "서울역", "15:16", "15:20", 4),
        move("GTX-A", "연신내역", "15:40", "15:47", 7),
        move("6호선", "디지털미디어시티역", "16:00", "16:11", 11),
        move("공항철도", "계양역", "16:22", "16:41", 19),
        move("인천1호선", "부평역", "16:48", "17:03", 15),
        stay("부평", "저녁 — 규카츠 (문화의거리)", "17:03", "18:03", 60),
        move("1호선", "소사역", "18:08", "18:18", 10),
        move("서해선", "초지역", "18:31", "18:49", 18),
        move("수인분당선", "구성역", "19:02", "20:07", 65),
        move("26 / 26-2번", "교동마을 LG자이", "20:20", "20:28", 8),
    ),
)
