package com.actimedi.travle.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 실제 이름표 자산을 읽어 본다.
 *
 * `tools/cjk.py`가 정한 규칙이 654개 역에 실제로 걸렸는지 보는 자리다. 도구를
 * 고치고 자산을 다시 만들지 않으면 여기서 걸린다 — 도구만 고쳐 놓고 자산은
 * 옛것이 남은 적이 있다.
 */
class StationNamesAssetTest {

    private val table: StationNameTable = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/station_names.json").readText())

    /** 괄호 안이 가나·로마자뿐이면 그것은 읽는 법이지 이름이 아니다. */
    private val reading = Regex("""[（(][぀-ヿｦ-ﾟA-Za-z\s・･·‧.\-]+[)）]""")

    @Test
    fun `자산이 읽힌다`() {
        assertTrue("이름표가 없다", table.names.size > 600)
    }

    @Test
    fun `일본어에 읽는 법이 괄호로 남지 않았다`() {
        val left = table.names.entries
            .mapNotNull { entry -> entry.value.japanese?.let { entry.key to it } }
            .filter { reading.containsMatchIn(it.second) }
        assertTrue("읽는 법이 남았다: $left", left.isEmpty())
    }

    /** 위키데이터 라벨은 `三松站`처럼 역을 뜻하는 글자를 달고 온다. */
    @Test
    fun `중국어에 역 글자가 붙어 있지 않다`() {
        val left = table.names.entries.filter {
            val name = it.value.simplified ?: it.value.traditional
            name != null && name.length > 1 && (name.endsWith("站") || name.endsWith("驛") || name.endsWith("驿"))
        }.map { it.key }
        assertTrue("역 글자가 남았다: $left", left.isEmpty())
    }

    /** 음차보다 한자가 짧고 읽기 쉽다. 규칙이 실제로 그렇게 바꿨는지 본다. */
    @Test
    fun `음차 대신 한자를 쓴다`() {
        assertEquals("江南", table.localized("강남", java.util.Locale.JAPANESE))
        assertEquals("明洞", table.localized("명동", java.util.Locale.JAPANESE))
        assertEquals("南漢山城入口", table.localized("남한산성입구", java.util.Locale.JAPANESE))
    }
}
