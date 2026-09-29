package com.pokertrainer.engine.core;

import org.junit.jupiter.api.Test;

import static com.pokertrainer.engine.core.HandEvaluator.*;
import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorTest {

    private static int eval(String cards) {
        return HandEvaluator.evaluate(Card.parseMany(cards));
    }

    @Test
    void recognisesEveryCategory() {
        assertEquals(STRAIGHT_FLUSH, category(eval("Ah Kh Qh Jh Th 2c 3d")));
        assertEquals(QUADS, category(eval("9c 9d 9h 9s Ah 2c 3d")));
        assertEquals(FULL_HOUSE, category(eval("9c 9d 9h As Ah 2c 3d")));
        assertEquals(FLUSH, category(eval("2h 7h 9h Jh Kh 2c 3d")));
        assertEquals(STRAIGHT, category(eval("5c 6d 7h 8s 9h 2c 2d")));
        assertEquals(TRIPS, category(eval("7c 7d 7h As Kh 2c 3d")));
        assertEquals(TWO_PAIR, category(eval("7c 7d Kh Ks Ah 2c 3d")));
        assertEquals(PAIR, category(eval("7c 7d Kh Qs Ah 2c 3d")));
        assertEquals(HIGH_CARD, category(eval("7c 9d Kh Qs Ah 2c 3d")));
    }

    @Test
    void wheelIsTheLowestStraight() {
        int wheel = eval("Ac 2d 3h 4s 5h Kc Kd");
        int sixHigh = eval("2d 3h 4s 5h 6c Kc Qd");
        assertEquals(STRAIGHT, category(wheel));
        assertTrue(sixHigh > wheel);
        assertEquals("Five-high straight", describe(wheel));
    }

    @Test
    void steelWheelIsAStraightFlush() {
        assertEquals(STRAIGHT_FLUSH, category(eval("Ah 2h 3h 4h 5h Kc Kd")));
    }

    @Test
    void higherCategoriesAlwaysWin() {
        int flush = eval("2h 4h 6h 8h Th");
        int straight = eval("Tc Jd Qh Ks Ah");
        int fullHouse = eval("2c 2d 2h 3s 3h");
        assertTrue(flush > straight);
        assertTrue(fullHouse > flush);
    }

    @Test
    void kickersBreakTies() {
        int akKicker = eval("Kc Kd Ah 9s 4h 3c 2d");
        int kqKicker = eval("Kh Ks Qh 9c 4d 3s 2c");
        assertTrue(akKicker > kqKicker);
    }

    @Test
    void identicalHandsTie() {
        // Both players play the board: a broadway straight.
        int a = eval("Tc Jd Qh Ks Ah 2c 3d");
        int b = eval("Tc Jd Qh Ks Ah 4c 5d");
        assertEquals(a, b);
    }

    @Test
    void twoPairUsesBestKickerIncludingAThirdPair() {
        int s = eval("Ac Ad Kc Kd Qh Qs 2c");
        assertEquals(TWO_PAIR, category(s));
        assertEquals("Two pair, aces and kings", describe(s));
        assertTrue(s > eval("Ac Ad Kc Kd Jh Js 2c"));
    }

    @Test
    void fullHouseFromTwoTrips() {
        int s = eval("9c 9d 9h 5s 5h 5c 2d");
        assertEquals(FULL_HOUSE, category(s));
        assertEquals("Nines full of fives", describe(s));
    }

    @Test
    void describesCommonHands() {
        assertEquals("Pair of kings", describe(eval("Kc Kd Ah 9s 4h 3c 2d")));
        assertEquals("Royal flush", describe(eval("Ah Kh Qh Jh Th")));
    }
}
