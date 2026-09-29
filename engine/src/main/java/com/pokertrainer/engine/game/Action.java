package com.pokertrainer.engine.game;

/**
 * A player decision. For BET and RAISE, {@code amount} is the player's total commitment on this
 * street after the action ("raise to"), in chips. For every other type it is ignored.
 */
public record Action(ActionType type, int amount) {
    public static Action fold() {
        return new Action(ActionType.FOLD, 0);
    }

    public static Action check() {
        return new Action(ActionType.CHECK, 0);
    }

    public static Action call() {
        return new Action(ActionType.CALL, 0);
    }

    public static Action betTo(int chips) {
        return new Action(ActionType.BET, chips);
    }

    public static Action raiseTo(int chips) {
        return new Action(ActionType.RAISE, chips);
    }
}
