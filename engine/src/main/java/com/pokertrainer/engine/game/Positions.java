package com.pokertrainer.engine.game;

/** Position labels relative to the button, for tables of 2 to 6 players. */
public final class Positions {
    private static final String[][] BY_SIZE = {
        {},
        {},
        {"BTN", "BB"},
        {"BTN", "SB", "BB"},
        {"BTN", "SB", "BB", "CO"},
        {"BTN", "SB", "BB", "HJ", "CO"},
        {"BTN", "SB", "BB", "UTG", "HJ", "CO"},
    };

    private Positions() {}

    public static String label(int seat, int button, int players) {
        int offset = Math.floorMod(seat - button, players);
        return BY_SIZE[players][offset];
    }
}
