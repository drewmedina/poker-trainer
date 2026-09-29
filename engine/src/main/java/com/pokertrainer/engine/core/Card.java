package com.pokertrainer.engine.core;

/**
 * Cards are plain ints in [0, 52): {@code rank * 4 + suit}.
 * Rank 0 is a deuce and 12 is an ace. Suits are c, d, h, s in that order.
 * Keeping cards as ints keeps the evaluator and equity loops allocation-free.
 */
public final class Card {
    public static final String RANKS = "23456789TJQKA";
    public static final String SUITS = "cdhs";

    private Card() {}

    public static int of(int rank, int suit) {
        return rank * 4 + suit;
    }

    public static int rank(int card) {
        return card >> 2;
    }

    public static int suit(int card) {
        return card & 3;
    }

    /** Parses a two-character card such as "Ah" or "td". */
    public static int parse(String text) {
        if (text == null || text.length() != 2) {
            throw new IllegalArgumentException("Bad card: " + text);
        }
        int rank = RANKS.indexOf(Character.toUpperCase(text.charAt(0)));
        int suit = SUITS.indexOf(Character.toLowerCase(text.charAt(1)));
        if (rank < 0 || suit < 0) {
            throw new IllegalArgumentException("Bad card: " + text);
        }
        return of(rank, suit);
    }

    /** Parses a run of cards such as "AhKd", "Ah Kd" or "Ah,Kd,2c". */
    public static int[] parseMany(String text) {
        String compact = text.replaceAll("[\\s,]", "");
        if (compact.length() % 2 != 0) {
            throw new IllegalArgumentException("Bad card list: " + text);
        }
        int[] out = new int[compact.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = parse(compact.substring(2 * i, 2 * i + 2));
        }
        return out;
    }

    public static String toString(int card) {
        return "" + RANKS.charAt(rank(card)) + SUITS.charAt(suit(card));
    }

    public static long mask(int... cards) {
        long m = 0L;
        for (int c : cards) {
            m |= 1L << c;
        }
        return m;
    }
}
