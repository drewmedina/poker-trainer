package com.pokertrainer.engine.core;

import java.util.Random;

/** A shuffled 52-card deck that deals from the top. */
public final class Deck {
    private final int[] cards = new int[52];
    private int next;

    public Deck(Random rng) {
        for (int i = 0; i < 52; i++) {
            cards[i] = i;
        }
        for (int i = 51; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = cards[i];
            cards[i] = cards[j];
            cards[j] = t;
        }
    }

    /** A deck in a fixed order, for tests that need to control the deal. */
    public static Deck stacked(int... topCards) {
        Deck d = new Deck(new Random(0));
        long used = Card.mask(topCards);
        int i = 0;
        for (int c : topCards) {
            d.cards[i++] = c;
        }
        for (int c = 0; c < 52; c++) {
            if ((used & (1L << c)) == 0) {
                d.cards[i++] = c;
            }
        }
        return d;
    }

    public int draw() {
        if (next >= 52) {
            throw new IllegalStateException("Deck is empty");
        }
        return cards[next++];
    }
}
