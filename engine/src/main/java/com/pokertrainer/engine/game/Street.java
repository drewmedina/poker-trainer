package com.pokertrainer.engine.game;

public enum Street {
    PREFLOP, FLOP, TURN, RIVER;

    public Street next() {
        return switch (this) {
            case PREFLOP -> FLOP;
            case FLOP -> TURN;
            case TURN -> RIVER;
            case RIVER -> throw new IllegalStateException("No street after the river");
        };
    }
}
