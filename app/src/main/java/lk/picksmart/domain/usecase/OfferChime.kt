package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ResultBand

/** The offer reader's sounds (CLAUDE.md section 9b), when the rider has turned them on. */
enum class OfferChime { PROFIT, LOSS }

/**
 * The chime for an offer's band: one for a profit (on target or no target set), another for a
 * loss, and none for below target or break even, so the two sounds always mean the same thing.
 */
fun offerChime(band: ResultBand): OfferChime? = when (band) {
    ResultBand.GOOD, ResultBand.PROFIT -> OfferChime.PROFIT
    ResultBand.LOSS -> OfferChime.LOSS
    ResultBand.BELOW_TARGET, ResultBand.BREAK_EVEN -> null
}
