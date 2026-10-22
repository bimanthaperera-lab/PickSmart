package lk.picksmart.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Bold, large numbers and clear titles; body text stays at Material sizes for readability. */
val PicksmartTypography = Typography(
    // Big money figures (net profit on a result card).
    displayMedium = Base.displayMedium.copy(fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    // Screen titles.
    headlineLarge = Base.headlineLarge.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    // Card values and section titles.
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)
