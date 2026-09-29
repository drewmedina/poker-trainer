package com.pokertrainer.engine.core;

import java.util.Random;

/**
 * A weighted range over all 1326 specific two-card combos.
 *
 * <p>Storing combos rather than the 169 hand classes means card removal is handled for free:
 * when the board or hero's cards block a combo, sampling simply skips it. The 13x13 grid in the
 * UI is just a view on top of this.
 *
 * <p>Text format: comma separated tokens such as {@code "QQ+, AKs, ATs+, KQo, 76s, 22-55"},
 * each optionally followed by {@code :weight} (for example {@code "A5s:0.5"}).
 */
public final class Range {
    public static final int COMBOS = 1326;
    private static final int[] FIRST = new int[COMBOS];
    private static final int[] SECOND = new int[COMBOS];

    static {
        for (int b = 1; b < 52; b++) {
            for (int a = 0; a < b; a++) {
                int i = index(a, b);
                FIRST[i] = a;
                SECOND[i] = b;
            }
        }
    }

    private final double[] weights = new double[COMBOS];

    public static int index(int a, int b) {
        if (a == b) {
            throw new IllegalArgumentException("Same card twice");
        }
        if (a > b) {
            int t = a;
            a = b;
            b = t;
        }
        return b * (b - 1) / 2 + a;
    }

    public static int first(int index) {
        return FIRST[index];
    }

    public static int second(int index) {
        return SECOND[index];
    }

    public static Range empty() {
        return new Range();
    }

    public static Range full() {
        Range r = new Range();
        java.util.Arrays.fill(r.weights, 1.0);
        return r;
    }

    public static Range parse(String text) {
        Range r = new Range();
        for (String raw : text.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            double weight = 1.0;
            int colon = token.indexOf(':');
            if (colon >= 0) {
                weight = Double.parseDouble(token.substring(colon + 1).trim());
                token = token.substring(0, colon).trim();
            }
            r.addToken(token, weight);
        }
        return r;
    }

    private void addToken(String token, double weight) {
        if (token.contains("-")) {
            String[] ends = token.split("-");
            HandClass a = HandClass.parse(ends[0]);
            HandClass b = HandClass.parse(ends[1]);
            if (a.pair() && b.pair()) {
                for (int r = Math.min(a.hi, b.hi); r <= Math.max(a.hi, b.hi); r++) {
                    setClass(r, r, 'p', weight);
                }
            } else if (a.hi == b.hi && a.kind == b.kind) {
                for (int k = Math.min(a.lo, b.lo); k <= Math.max(a.lo, b.lo); k++) {
                    setClass(a.hi, k, a.kind, weight);
                }
            } else {
                throw new IllegalArgumentException("Unsupported range: " + token);
            }
            return;
        }
        boolean plus = token.endsWith("+");
        HandClass h = HandClass.parse(plus ? token.substring(0, token.length() - 1) : token);
        if (h.pair()) {
            for (int r = h.hi; r <= (plus ? 12 : h.hi); r++) {
                setClass(r, r, 'p', weight);
            }
        } else {
            for (int k = h.lo; k <= (plus ? h.hi - 1 : h.lo); k++) {
                setClass(h.hi, k, h.kind, weight);
            }
        }
    }

    /** Sets every combo of a hand class. kind is 'p' (pair), 's' (suited), 'o' (offsuit) or 'a' (any). */
    public void setClass(int hi, int lo, char kind, double weight) {
        for (int s1 = 0; s1 < 4; s1++) {
            for (int s2 = 0; s2 < 4; s2++) {
                int a = Card.of(hi, s1);
                int b = Card.of(lo, s2);
                if (a == b) continue;
                boolean suited = s1 == s2;
                if (kind == 'p' && (hi != lo || s1 >= s2)) continue;
                if (kind == 's' && !suited) continue;
                if (kind == 'o' && suited) continue;
                weights[index(a, b)] = weight;
            }
        }
    }

    public double weight(int a, int b) {
        return weights[index(a, b)];
    }

    public void setWeight(int a, int b, double w) {
        weights[index(a, b)] = w;
    }

    /** Total weight, i.e. the number of combos when every weight is 1. */
    public double comboCount() {
        double sum = 0;
        for (double w : weights) sum += w;
        return sum;
    }

    /**
     * Samples a combo index by weight, skipping combos that use a dead card.
     * Returns -1 when nothing in the range is still possible.
     */
    public int sample(Random rng, long dead) {
        double total = 0;
        for (int i = 0; i < COMBOS; i++) {
            if (weights[i] > 0 && !blocked(i, dead)) total += weights[i];
        }
        if (total <= 0) return -1;
        double pick = rng.nextDouble() * total;
        for (int i = 0; i < COMBOS; i++) {
            if (weights[i] > 0 && !blocked(i, dead)) {
                pick -= weights[i];
                if (pick <= 0) return i;
            }
        }
        return -1;
    }

    private static boolean blocked(int combo, long dead) {
        return ((dead >> FIRST[combo]) & 1L) != 0 || ((dead >> SECOND[combo]) & 1L) != 0;
    }

    private record HandClass(int hi, int lo, char kind) {
        boolean pair() {
            return hi == lo;
        }

        static HandClass parse(String t) {
            if (t.length() < 2 || t.length() > 3) {
                throw new IllegalArgumentException("Bad hand class: " + t);
            }
            int r1 = Card.RANKS.indexOf(Character.toUpperCase(t.charAt(0)));
            int r2 = Card.RANKS.indexOf(Character.toUpperCase(t.charAt(1)));
            if (r1 < 0 || r2 < 0) {
                throw new IllegalArgumentException("Bad hand class: " + t);
            }
            char kind = t.length() == 3 ? Character.toLowerCase(t.charAt(2)) : 'a';
            if (kind != 's' && kind != 'o' && kind != 'a') {
                throw new IllegalArgumentException("Bad hand class: " + t);
            }
            if (r1 == r2) {
                return new HandClass(r1, r1, 'p');
            }
            return new HandClass(Math.max(r1, r2), Math.min(r1, r2), kind);
        }
    }
}
