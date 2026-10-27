package lk.picksmart.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import lk.picksmart.R

/**
 * Google Sans Flex (OFL), bundled as four static Latin weights so it works offline and stays
 * small; see `design/fonts/README.md`. Other scripts fall back to the system font.
 */
val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_bold, FontWeight.Bold),
)

private val Base = Typography()

private fun TextStyle.app() = copy(fontFamily = GoogleSansFlex)

/**
 * Bold, large numbers and clear titles, one step below Material's sizes (owner, 2026-10-27) so
 * longer Sinhala and Tamil labels fit on small phones. Body text stays at 14 sp or more.
 */
val PicksmartTypography = Typography(
    // Big money figures (net profit on a result card).
    displayLarge = Base.displayLarge.app().copy(fontSize = 52.sp, lineHeight = 58.sp),
    displayMedium = Base.displayMedium.app().copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.app().copy(fontSize = 31.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold),
    // Screen titles.
    headlineLarge = Base.headlineLarge.app().copy(fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.app().copy(fontSize = 25.sp, lineHeight = 31.sp),
    headlineSmall = Base.headlineSmall.app().copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    // Card values and section titles.
    titleLarge = Base.titleLarge.app().copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.app().copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.app().copy(fontSize = 13.sp, lineHeight = 19.sp),
    bodyLarge = Base.bodyLarge.app().copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = Base.bodyMedium.app().copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Base.bodySmall.app(),
    labelLarge = Base.labelLarge.app().copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.app().copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.app(),
)
