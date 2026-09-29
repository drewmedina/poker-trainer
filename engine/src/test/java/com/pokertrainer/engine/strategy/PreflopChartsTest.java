package com.pokertrainer.engine.strategy;

import com.pokertrainer.engine.core.Card;
import com.pokertrainer.engine.core.Range;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreflopChartsTest {

    private static boolean opens(String pos, int players, String hand) {
        int[] c = Card.parseMany(hand);
        Range r = PreflopCharts.openingRange(pos, players).orElseThrow();
        return PreflopCharts.opens(r, c[0], c[1]);
    }

    @Test
    void rangesWidenTowardsTheButton() {
        double utg = PreflopCharts.share(PreflopCharts.openingRange("UTG", 6).orElseThrow());
        double hj = PreflopCharts.share(PreflopCharts.openingRange("HJ", 6).orElseThrow());
        double co = PreflopCharts.share(PreflopCharts.openingRange("CO", 6).orElseThrow());
        double btn = PreflopCharts.share(PreflopCharts.openingRange("BTN", 6).orElseThrow());
        assertTrue(utg < hj && hj < co && co < btn);
        assertTrue(utg > 0.10 && utg < 0.22, "UTG opens roughly 15%, got " + utg);
        assertTrue(btn > 0.35 && btn < 0.55, "BTN opens roughly 45%, got " + btn);
    }

    @Test
    void sampleHands() {
        assertTrue(opens("UTG", 6, "AsKd"));
        assertFalse(opens("UTG", 6, "7s2d"));
        assertFalse(opens("UTG", 6, "Kh9d"));
        assertTrue(opens("BTN", 6, "Kh9d"));
        assertTrue(opens("BTN", 2, "Qh5d"), "heads-up buttons open very wide");
    }

    @Test
    void bigBlindAndOddTableSizesHaveNoChart() {
        assertTrue(PreflopCharts.openingRange("BB", 6).isEmpty());
        assertTrue(PreflopCharts.openingRange("BB", 2).isEmpty());
        assertTrue(PreflopCharts.openingRange("CO", 4).isEmpty());
    }
}
