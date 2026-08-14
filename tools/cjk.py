#!/usr/bin/env python3
"""긁어 온 일본어·중국어 이름을 화면에 낼 만하게 다듬는다.

`station_names.py`와 `hotspots.py`가 같은 자국을 밟아서 한곳에 모았다. 규칙이
두 벌로 갈라지면 역 이름은 `江南`인데 명소 이름은 `カンナム(江南)`이 되어, 같은
화면에 두 표기가 나란히 선다.
"""
import re

# 음차. 가나·로마자와 그 사이에 끼는 구분 기호까지.
READING = r"[぀-ヿｦ-ﾟA-Za-z\s・･·‧.\-]+"
# 한자. 괄호 안이 이것뿐이면 읽는 법이 아니라 이름이다.
IDEOGRAPHS = r"[㐀-鿿\s・･]+"

WHOLE_READING = re.compile(rf"({READING})[（(](.+)[)）]")
READING_THEN_IDEOGRAPHS = re.compile(rf"{READING}[（(]({IDEOGRAPHS})[)）]")
READING_IN_PARENS = re.compile(rf"[（(]{READING}[)）]")


def tidy_japanese(text):
    """화면에 낼 일본어.

    OSM과 위키데이터의 한국 지명은 음차와 한자를 나란히 적는다. 화면은 좁고,
    일본어를 읽는 사람에게는 한자 쪽이 짧고 알아보기 쉽다. 괄호 안이 읽는
    법인지 이름인지는 **글자로 가린다** — 지명 목록을 손으로 훑어 정한 규칙이다.

        カンナム(江南)                  → 江南
        ソウルスプ(ソウルの森)          → ソウルの森
        ナマンサン(南漢山)城入口        → 南漢山城入口
        光化門(クァンファムン)広場      → 光化門広場
        南洞産業団地 (ナムドン·インダスパーク) → 南洞産業団地

    앞이 통째로 음차면 괄호 안이 이름이고, 음차 뒤에 붙은 한자 괄호는 그 음차를
    읽은 것이며, 괄호 안이 음차뿐이면 그것은 읽는 법이라 뺀다.

    `市民公園(文化創作地帯)`처럼 앞이 한자면 괄호는 읽는 법이 아니라 구분이므로
    건드리지 않는다.
    """
    if not text:
        return None
    text = text.strip()
    whole = WHOLE_READING.fullmatch(text)
    if whole:
        return whole.group(2).strip() or None
    text = READING_THEN_IDEOGRAPHS.sub(lambda m: re.sub(r"[\s・･]", "", m.group(1)), text)
    return READING_IN_PARENS.sub("", text).strip() or None


def tidy_chinese(text):
    """화면에 낼 중국어.

    출처에서 **지하철역**의 이름이 새어 든다. 위키데이터 라벨은 `三松站`이고,
    같은 이름의 OSM 요소를 합치면 봉은사(절)에 봉은사역의 `奉恩寺站`이 붙는다.
    우리가 가리키는 것은 역이 아니다.
    """
    if not text:
        return None
    text = text.strip()
    if len(text) > 1 and text.endswith(("站", "驛", "驿")):
        text = text[:-1]
    return text or None
