package com.actimedi.travle.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color tokens ported from the ActiMedi · Kneefresh design system
 * (`_ds/.../tokens/colors.css`). The corporate palette is exactly six colors;
 * everything else here is one of the documented tints.
 */
object AmColor {
    // Brand — the canonical six
    val Blue = Color(0xFF1E5AF0)
    val Navy = Color(0xFF0C1E6B)
    val SkyBlue = Color(0xFF38D2FF)
    val Yellow = Color(0xFFFFCF2C)
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF101010)

    // Blue tints
    val Blue800 = Color(0xFF0A2DA8)
    val Blue100 = Color(0xFFE3EBFF)

    // Ink ramp
    val Ink900 = Color(0xFF101010)
    val Ink600 = Color(0xFF3A3F4B)
    val Ink500 = Color(0xFF555B68)
    val Ink400 = Color(0xFF7C8290)
    val Ink300 = Color(0xFFA8ADBA)
    val Ink200 = Color(0xFFC9CDD6)
    val Ink100 = Color(0xFFE5E7EC)
    val Ink50 = Color(0xFFF3F4F7)
    val Ink0 = Color(0xFFFFFFFF)

    // Semantic surfaces / lines
    val SurfacePage = White
    val SurfaceSunken = Ink50
    val Line = Ink100
}

/**
 * Screen-level colors that the mockup uses directly. Kept separate from the
 * brand tokens because these are composition choices of this screen, not
 * design-system tokens.
 */
object RouteColor {
    val HeaderTop = Color(0xFF1E5AF0)
    val HeaderMid = Color(0xFF123FBE)
    val HeaderBottom = Color(0xFF0C1E6B)

    val HeaderEyebrow = Color(0xFF9CC6FF)
    val HeaderStatLabel = Color(0xFFC8DBFF)
    val HeaderChipFill = Color(0x21FFFFFF) // rgba(255,255,255,.13)
    val HeaderChipLine = Color(0x33FFFFFF) // rgba(255,255,255,.20)

    val TabTrack = Color(0xFFF3F4F7)
    val TabInactiveText = Color(0xFF7A8296)

    val TimeStrong = Color(0xFF3A4256)
    val TimeWeak = Color(0xFFAFB5C4)

    val MoveRowFill = Color(0xFFF6F7FA)
    val MoveDuration = Color(0xFF8A90A0)

    val StayLabel = Color(0xFF5C6478)
    val StayBadgeFill = Color(0xFFEAF0FE)
    val DetailLabel = Color(0xFF9AA1B2)

    val WaitFill = Color(0xFFFFF6DA)
    val WaitLine = Color(0xFFFFE49A)
    val WaitDot = Color(0xFFE8A800)
    val WaitText = Color(0xFF8A6400)

    val DashRail = Color(0xFFC7CBD6)
    val NavInactive = Color(0xFFAFB5C4)
    val NavInactiveIcon = Color(0xFFC7CBD6)

    val CardShadow = Color(0xFF0C1E6B)
    val Destructive = Color(0xFFE5484D)
}

/**
 * Resolves a line badge colour from its name, so a saved route only has to
 * carry text. Order matters — 인천1호선 must be matched before 1호선.
 */
fun lineColorFor(line: String): Color {
    val name = line.replace(" ", "")
    return when {
        name.contains("신분당") -> LineColor.Sinbundang
        name.contains("수인분당") || name.contains("분당") -> LineColor.Suin
        name.contains("인천1") -> LineColor.Incheon1
        name.contains("공항철도") -> LineColor.Arex
        name.contains("GTX", ignoreCase = true) -> LineColor.Gtx
        name.contains("서해") -> LineColor.Seohae
        name.contains("1호선") -> LineColor.Line1
        name.contains("2호선") -> LineColor.Line2
        name.contains("3호선") -> LineColor.Line3
        name.contains("4호선") -> LineColor.Line4
        name.contains("5호선") -> LineColor.Line5
        name.contains("6호선") -> LineColor.Line6
        name.contains("7호선") -> LineColor.Line7
        name.contains("8호선") -> LineColor.Line8
        name.contains("9호선") -> LineColor.Line9
        name.contains("경의중앙") -> LineColor.Gyeongui
        name.contains("경춘") -> LineColor.Gyeongchun
        name.contains("버스") || name.contains("번") -> LineColor.Bus
        name.contains("도보") || name.contains("걷") -> LineColor.Walk
        else -> LineColor.Unknown
    }
}

/** Transit line colors, as defined in the mockup's `C` map. */
object LineColor {
    val Bus = Color(0xFF3D5BAB)
    val Sinbundang = Color(0xFFD4003B)
    val Line1 = Color(0xFF0052A4)
    val Line2 = Color(0xFF00A84D)
    val Line4 = Color(0xFF00A5DE)
    val Line5 = Color(0xFF996CAC)
    val Line6 = Color(0xFFCD7C2F)
    val Line7 = Color(0xFF747F00)
    val Suin = Color(0xFFF5A200)
    val Gtx = Color(0xFF9A6292)
    val Arex = Color(0xFF0090D2)
    val Incheon1 = Color(0xFF7CA8D5)
    val Seohae = Color(0xFF8BC53F)

    // Not in the mockup, but reachable once the user types their own line names.
    val Line3 = Color(0xFFEF7C1C)
    val Line8 = Color(0xFFE6186C)
    val Line9 = Color(0xFFBDB092)
    val Gyeongui = Color(0xFF77C4A3)
    val Gyeongchun = Color(0xFF178C72)
    val Walk = Color(0xFF7C8290)

    /** Fallback for a line name we cannot place. */
    val Unknown = Color(0xFF5C6478)
}
