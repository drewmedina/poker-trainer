package com.pokertrainer.engine.game;

public enum ActionType {
    FOLD, CHECK, CALL, BET, RAISE,
    /** Only appears in the hand log, for blinds. Never a legal player action. */
    POST
}
