package com.pokertrainer.engine.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RangeTest {

    @Test
    void comboCounts() {
        assertEquals(6, Range.parse("AA").comboCount());
        assertEquals(4, Range.parse("AKs").comboCount());
        assertEquals(12, Range.parse("AKo").comboCount());
        assertEquals(16, Range.parse("AK").comboCount());
        assertEquals(18, Range.parse("QQ+").comboCount());
        assertEquals(16, Range.parse("ATs+").comboCount());
        assertEquals(24, Range.parse("22-55").comboCount());
        assertEquals(16, Range.parse("A2s-A5s").comboCount());
        assertEquals(1326, Range.full().comboCount());
    }

    @Test
    void weightsAndMultipleTokens() {
        assertEquals(2.0, Range.parse("A5s:0.5").comboCount(), 1e-9);
        assertEquals(6 + 4 + 12, Range.parse("KK, AKs, AKo").comboCount());
    }

    @Test
    void indexRoundTrips() {
        for (int b = 1; b < 52; b++) {
            for (int a = 0; a < b; a++) {
                int i = Range.index(a, b);
                assertEquals(a, Range.first(i));
                assertEquals(b, Range.second(i));
                assertEquals(i, Range.index(b, a));
            }
        }
    }

    @Test
    void samplingSkipsDeadCards() {
        Range kings = Range.parse("KK");
        long dead = Card.mask(Card.parseMany("KsKh"));
        Random rng = new Random(5);
        for (int i = 0; i < 200; i++) {
            int combo = kings.sample(rng, dead);
            // Only KcKd survives.
            assertEquals(Range.index(Card.parse("Kc"), Card.parse("Kd")), combo);
        }
        assertEquals(-1, kings.sample(rng, Card.mask(Card.parseMany("KsKhKc"))));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Range.parse("AX"));
        assertThrows(IllegalArgumentException.class, () -> Range.parse("AKx"));
    }
}
