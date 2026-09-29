package com.pokertrainer.engine.core;

import java.util.List;
import java.util.Random;

/**
 * Equity calculations. Equity is hero's expected share of the pot at showdown, with ties split
 * between the tied players, so it always lands in [0, 1].
 */
public final class Equity {
    private Equity() {}

    /**
     * Hero vs one known hand. Enumerates every runout exactly when two or fewer board cards are
     * missing (the turn and river are cheap), otherwise runs {@code trials} Monte Carlo samples.
     */
    public static double headsUp(int[] hero, int[] villain, int[] board, int trials, Random rng) {
        long dead = Card.mask(hero) | Card.mask(villain) | Card.mask(board);
        int[] live = liveCards(dead);
        int missing = 5 - board.length;
        int[] h = new int[7];
        int[] v = new int[7];
        h[0] = hero[0];
        h[1] = hero[1];
        v[0] = villain[0];
        v[1] = villain[1];
        for (int i = 0; i < board.length; i++) {
            h[2 + i] = board[i];
            v[2 + i] = board[i];
        }

        double share = 0;
        long samples = 0;
        if (missing == 0) {
            return showdownShare(h, new int[][] {v});
        } else if (missing == 1) {
            for (int a : live) {
                h[6] = v[6] = a;
                share += showdownShare(h, new int[][] {v});
                samples++;
            }
        } else if (missing == 2) {
            for (int i = 0; i < live.length; i++) {
                for (int j = i + 1; j < live.length; j++) {
                    h[5] = v[5] = live[i];
                    h[6] = v[6] = live[j];
                    share += showdownShare(h, new int[][] {v});
                    samples++;
                }
            }
        } else {
            for (int t = 0; t < trials; t++) {
                partialShuffle(live, missing, rng);
                for (int k = 0; k < missing; k++) {
                    h[2 + board.length + k] = v[2 + board.length + k] = live[k];
                }
                share += showdownShare(h, new int[][] {v});
                samples++;
            }
        }
        return share / samples;
    }

    /** Hero vs {@code opponents} unknown random hands. Monte Carlo. */
    public static double vsRandom(int[] hero, int[] board, int opponents, int trials, Random rng) {
        return vsRanges(hero, board, java.util.Collections.nCopies(opponents, (Range) null), trials, rng);
    }

    /**
     * Hero vs one range per opponent. A null range means a random hand. Monte Carlo.
     * Opponent hands are sampled in order, so each respects the cards dealt before it.
     */
    public static double vsRanges(int[] hero, int[] board, List<Range> ranges, int trials, Random rng) {
        int n = ranges.size();
        if (n == 0) return 1.0;
        long baseDead = Card.mask(hero) | Card.mask(board);
        int[] h = new int[7];
        int[][] opp = new int[n][7];
        h[0] = hero[0];
        h[1] = hero[1];
        for (int i = 0; i < board.length; i++) {
            h[2 + i] = board[i];
            for (int o = 0; o < n; o++) opp[o][2 + i] = board[i];
        }
        int missing = 5 - board.length;

        double share = 0;
        int samples = 0;
        for (int t = 0; t < trials; t++) {
            long dead = baseDead;
            boolean ok = true;
            for (int o = 0; o < n && ok; o++) {
                Range r = ranges.get(o);
                int a, b;
                if (r != null) {
                    int combo = r.sample(rng, dead);
                    if (combo < 0) {
                        ok = false;
                        break;
                    }
                    a = Range.first(combo);
                    b = Range.second(combo);
                } else {
                    a = randomLive(dead, rng);
                    dead |= 1L << a;
                    b = randomLive(dead, rng);
                }
                dead |= (1L << a) | (1L << b);
                opp[o][0] = a;
                opp[o][1] = b;
            }
            if (!ok) continue;
            for (int k = 0; k < missing; k++) {
                int c = randomLive(dead, rng);
                dead |= 1L << c;
                h[2 + board.length + k] = c;
                for (int o = 0; o < n; o++) opp[o][2 + board.length + k] = c;
            }
            share += showdownShare(h, opp);
            samples++;
        }
        return samples == 0 ? 0.0 : share / samples;
    }

    /** Hero's share of the pot when all hands are complete 7-card hands. */
    static double showdownShare(int[] hero, int[][] opponents) {
        int hs = HandEvaluator.evaluate(hero, 7);
        int tied = 1;
        for (int[] o : opponents) {
            int os = HandEvaluator.evaluate(o, 7);
            if (os > hs) return 0.0;
            if (os == hs) tied++;
        }
        return 1.0 / tied;
    }

    private static int[] liveCards(long dead) {
        int[] out = new int[52 - Long.bitCount(dead)];
        int i = 0;
        for (int c = 0; c < 52; c++) {
            if ((dead & (1L << c)) == 0) out[i++] = c;
        }
        return out;
    }

    private static void partialShuffle(int[] a, int k, Random rng) {
        for (int i = 0; i < k; i++) {
            int j = i + rng.nextInt(a.length - i);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private static int randomLive(long dead, Random rng) {
        while (true) {
            int c = rng.nextInt(52);
            if ((dead & (1L << c)) == 0) return c;
        }
    }
}
