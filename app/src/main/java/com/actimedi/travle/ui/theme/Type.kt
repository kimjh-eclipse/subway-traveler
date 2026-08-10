package com.actimedi.travle.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.actimedi.travle.R

/**
 * SUITE for display/headings, SUIT for body — per the ActiMedi typography tokens.
 *
 * Only SUITE Bold ships with the brand kit, so the display family exposes a
 * single weight; the mockup's 800 headings render as SUITE Bold rather than a
 * synthesised heavier cut.
 */
val SuiteFamily = FontFamily(
    Font(R.font.suite_bold, FontWeight.Bold),
)

val SuitFamily = FontFamily(
    Font(R.font.suit_regular, FontWeight.Normal),
    Font(R.font.suit_medium, FontWeight.Medium),
    Font(R.font.suit_semibold, FontWeight.SemiBold),
    Font(R.font.suit_bold, FontWeight.Bold),
    Font(R.font.suit_extrabold, FontWeight.ExtraBold),
)
