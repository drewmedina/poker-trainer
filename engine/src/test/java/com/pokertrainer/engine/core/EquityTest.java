package com.pokertrainer.engine.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EquityTest {

    @Test
    void acesAgainstKingsPreflop() {
        double eq = Equity.headsUp(Card.parseMany("AsAd"), Card.parseMany("KsKd"), new int[0], 40_000, new Random(1));
        assertEquals(0.82, eq, 0.015);
    }

    @Test
    void turnIsEnumeratedExactly() {
        // Kings need the last king (which makes quads). Everything else holds for aces: 43 of 44.
        double eq = Equity.headsUp(Card.parseMany("AsAd"), Card.parseMany("KsKd"),
            Card.parseMany("Ac Kc 2h 3h"), 0, new Random(1));
        assertEquals(43.0 / 44.0, eq, 1e-9);
    }

    @Test
    void completeBoardIsDeterministic() {
        double eq = Equity.headsUp(Card.parseMany("AhKh"), Card.parseMany("QsQd"),
            Card.parseMany("Kc 7d 2s 9h 4c"), 0, new Random(1));
        assertEquals(1.0, eq, 1e-9);
    }

    @Test
    void chopsSplitThePot() {
        double eq = Equity.headsUp(Card.parseMany("2c3c"), Card.parseMany("2d3d"),
            Card.parseMany("Tc Jd Qh Ks Ah"), 0, new Random(1));
        assertEquals(0.5, eq, 1e-9);
    }

    @Test
    void acesAgainstFiveRandomHands() {
        double eq = Equity.vsRandom(Card.parseMany("AsAd"), new int[0], 5, 30_000, new Random(7));
        assertEquals(0.49, eq, 0.03);
    }

    @Test
    void rangesRespectCardRemoval() {
        // Villain's range is only AA, and hero holds two aces, so villain holds exactly AcAh.
        double eq = Equity.vsRanges(Card.parseMany("AsAd"), Card.parseMany("Kc 7d 2s 9h 4c"),
            List.of(Range.parse("AA")), 200, new Random(3));
        assertEquals(0.5, eq, 1e-9);
    }
}
