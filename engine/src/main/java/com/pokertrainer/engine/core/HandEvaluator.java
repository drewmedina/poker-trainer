package com.pokertrainer.engine.core;

/**
 * Scores the best five-card hand out of 5 to 7 cards.
 *
 * <p>A score packs the category into bits 20 to 23 and up to five tiebreak ranks into the
 * nibbles below it, so a higher int is always a stronger hand. This is a straightforward
 * bitmask evaluator, fast enough for Monte Carlo equity in a web request. If profiling ever
 * shows it as the bottleneck, swap in a lookup-table evaluator behind the same method.
 */
public final class HandEvaluator {
    public static final int HIGH_CARD = 0;
    public static final int PAIR = 1;
    public static final int TWO_PAIR = 2;
    public static final int TRIPS = 3;
    public static final int STRAIGHT = 4;
    public static final int FLUSH = 5;
    public static final int FULL_HOUSE = 6;
    public static final int QUADS = 7;
    public static final int STRAIGHT_FLUSH = 8;

    private static final String[] RANK_NAMES = {
        "two", "three", "four", "five", "six", "seven", "eight",
        "nine", "ten", "jack", "queen", "king", "ace"
    };
    private static final String[] RANK_PLURALS = {
        "twos", "threes", "fours", "fives", "sixes", "sevens", "eights",
        "nines", "tens", "jacks", "queens", "kings", "aces"
    };

    private HandEvaluator() {}

    public static int evaluate(int... cards) {
        return evaluate(cards, cards.length);
    }

    public static int evaluate(int[] cards, int n) {
        if (n < 5 || n > 7) {
            throw new IllegalArgumentException("Need 5 to 7 cards, got " + n);
        }
        int[] suitMask = new int[4];
        int[] count = new int[13];
        int all = 0;
        for (int i = 0; i < n; i++) {
            int r = Card.rank(cards[i]);
            suitMask[Card.suit(cards[i])] |= 1 << r;
            count[r]++;
            all |= 1 << r;
        }

        int flush = -1;
        for (int s = 0; s < 4; s++) {
            if (Integer.bitCount(suitMask[s]) >= 5) {
                flush = suitMask[s];
                break;
            }
        }
        if (flush >= 0) {
            int sf = straightHigh(flush);
            if (sf >= 0) {
                return score(STRAIGHT_FLUSH, sf);
            }
        }

        int quad = -1, trip1 = -1, trip2 = -1, pair1 = -1, pair2 = -1;
        for (int r = 12; r >= 0; r--) {
            switch (count[r]) {
                case 4 -> quad = r;
                case 3 -> {
                    if (trip1 < 0) trip1 = r; else if (trip2 < 0) trip2 = r;
                }
                case 2 -> {
                    if (pair1 < 0) pair1 = r; else if (pair2 < 0) pair2 = r;
                }
                default -> { }
            }
        }

        if (quad >= 0) {
            return score(QUADS, quad, highest(all & ~(1 << quad)));
        }
        if (trip1 >= 0 && (trip2 >= 0 || pair1 >= 0)) {
            return score(FULL_HOUSE, trip1, Math.max(trip2, pair1));
        }
        if (flush >= 0) {
            return score(FLUSH, top(flush, 5));
        }
        int st = straightHigh(all);
        if (st >= 0) {
            return score(STRAIGHT, st);
        }
        if (trip1 >= 0) {
            int[] k = top(all & ~(1 << trip1), 2);
            return score(TRIPS, trip1, k[0], k[1]);
        }
        if (pair2 >= 0) {
            return score(TWO_PAIR, pair1, pair2, highest(all & ~(1 << pair1) & ~(1 << pair2)));
        }
        if (pair1 >= 0) {
            int[] k = top(all & ~(1 << pair1), 3);
            return score(PAIR, pair1, k[0], k[1], k[2]);
        }
        return score(HIGH_CARD, top(all, 5));
    }

    public static int category(int score) {
        return score >>> 20;
    }

    /** A short human description, such as "Pair of kings" or "Queen-high flush". */
    public static String describe(int score) {
        int r1 = (score >> 16) & 0xF;
        int r2 = (score >> 12) & 0xF;
        return switch (category(score)) {
            case STRAIGHT_FLUSH -> r1 == 12 ? "Royal flush" : cap(RANK_NAMES[r1]) + "-high straight flush";
            case QUADS -> "Four " + RANK_PLURALS[r1];
            case FULL_HOUSE -> cap(RANK_PLURALS[r1]) + " full of " + RANK_PLURALS[r2];
            case FLUSH -> cap(RANK_NAMES[r1]) + "-high flush";
            case STRAIGHT -> cap(RANK_NAMES[r1]) + "-high straight";
            case TRIPS -> "Three " + RANK_PLURALS[r1];
            case TWO_PAIR -> "Two pair, " + RANK_PLURALS[r1] + " and " + RANK_PLURALS[r2];
            case PAIR -> "Pair of " + RANK_PLURALS[r1];
            default -> cap(RANK_NAMES[r1]) + " high";
        };
    }

    /** Highest rank of a five-card straight in the rank mask, or -1. The wheel returns 3 (a five). */
    static int straightHigh(int mask) {
        int ext = (mask << 1) | ((mask >> 12) & 1); // bit 0 is a low ace
        for (int top = 13; top >= 4; top--) {
            if (((ext >> (top - 4)) & 0x1F) == 0x1F) {
                return top - 1;
            }
        }
        return -1;
    }

    private static int highest(int mask) {
        return 31 - Integer.numberOfLeadingZeros(mask);
    }

    private static int[] top(int mask, int k) {
        int[] out = new int[k];
        for (int i = 0; i < k; i++) {
            int h = highest(mask);
            out[i] = h;
            mask &= ~(1 << h);
        }
        return out;
    }

    private static int score(int category, int... ranks) {
        int s = category;
        for (int i = 0; i < 5; i++) {
            s = (s << 4) | (i < ranks.length ? ranks[i] : 0);
        }
        return s;
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
