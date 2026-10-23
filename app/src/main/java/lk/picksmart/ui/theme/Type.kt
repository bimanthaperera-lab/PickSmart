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

/** Bold, large numbers and clear titles; body text stays at Material sizes for readability. */
val PicksmartTypography = Typography(
    // Big money figures (net profit on a result card).
    displayLarge = Base.displayLarge.app(),
    displayMedium = Base.displayMedium.app().copy(fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.app().copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    // Screen titles.
    headlineLarge = Base.headlineLarge.app().copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.app(),
    headlineSmall = Base.headlineSmall.app().copy(fontWeight = FontWeight.SemiBold),
    // Card values and section titles.
    titleLarge = Base.titleLarge.app().copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.app().copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.app(),
    bodyLarge = Base.bodyLarge.app(),
    bodyMedium = Base.bodyMedium.app().copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = Base.bodySmall.app(),
    labelLarge = Base.labelLarge.app().copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.app().copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.app(),
)
