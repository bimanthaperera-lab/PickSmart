package lk.picksmart.util

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Every date in the app is a Sri Lankan calendar date (CLAUDE.md section 5). */
val ColomboZone: ZoneId = ZoneId.of("Asia/Colombo")

/** Today's date in Colombo. Pass a fixed [clock] in tests. */
fun todayInColombo(clock: Clock = Clock.system(ColomboZone)): LocalDate = LocalDate.now(clock.withZone(ColomboZone))
