package com.pokertrainer.engine.game;

/** One entry in the hand history. {@code added} is chips put in by this action. */
public record LoggedAction(int seat, Street street, ActionType type, int added, int streetTotal, boolean allIn) {}
