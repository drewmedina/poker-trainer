package com.pokertrainer.engine.strategy;

import com.pokertrainer.engine.core.Range;

import java.util.Map;
import java.util.Optional;

/**
 * APPROXIMATE raise-first-in (opening) ranges for 100bb cash games, by position. They are close to
 * common solver-based charts but hand-simplified, so treat them as a reference, not ground truth.
 * Milestone 2 replaces these with real solver output and adds 3-bet and defense charts.
 *
 * <p>Only unopened pots are covered: everyone before you folded, and you are deciding whether
 * to open.
 */
public final class PreflopCharts {
    private static final Map<String, Range> SIX_MAX_RFI = Map.of(
        "UTG", Range.parse("66+, A3s+, K9s+, QTs+, JTs, T9s, 98s, 87s, AJo+, KQo"),
        "HJ", Range.parse("55+, A2s+, K8s+, Q9s+, J9s+, T9s, 98s, 87s, 76s, ATo+, KJo+, QJo"),
        "CO", Range.parse("33+, A2s+, K5s+, Q8s+, J8s+, T8s+, 97s+, 86s+, 75s+, 65s, 54s, A8o+, KTo+, QTo+, JTo"),
        "BTN", Range.parse("22+, A2s+, K2s+, Q4s+, J6s+, T6s+, 96s+, 85s+, 74s+, 64s+, 53s+, 43s, A2o+, K8o+, Q9o+, J9o+, T8o+, 98o"),
        "SB", Range.parse("22+, A2s+, K3s+, Q6s+, J7s+, T7s+, 97s+, 86s+, 75s+, 65s, 54s, A4o+, K9o+, QTo+, JTo")
    );

    /** Heads-up, the button opens very wide. */
    private static final Range HEADS_UP_BUTTON = Range.parse(
        "22+, A2s+, K2s+, Q2s+, J2s+, T4s+, 95s+, 85s+, 74s+, 63s+, 53s+, 43s, A2o+, K2o+, Q5o+, J7o+, T7o+, 97o+, 87o");

    private PreflopCharts() {}

    /**
     * The opening range for {@code position} at a table of {@code players}, or empty when there is
     * no chart (the big blind never opens, and 3-5 handed tables are not covered yet).
     */
    public static Optional<Range> openingRange(String position, int players) {
        if (players == 2) {
            return "BTN".equals(position) ? Optional.of(HEADS_UP_BUTTON) : Optional.empty();
        }
        if (players == 6) {
            return Optional.ofNullable(SIX_MAX_RFI.get(position));
        }
        return Optional.empty();
    }

    /** True when the chart opens this exact hand. */
    public static boolean opens(Range range, int card1, int card2) {
        return range.weight(card1, card2) > 0;
    }

    /** Share of all starting hands the range opens, for display ("opens about 27%"). */
    public static double share(Range range) {
        return range.comboCount() / Range.COMBOS;
    }
}
