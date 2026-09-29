package com.pokertrainer.engine.game;

/**
 * One button the UI can show. For BET and RAISE, {@code amount} is the "to" amount in chips;
 * for CALL it is the chips the call adds. {@code label} is short button text ("Bet 75%").
 */
public record LegalAction(ActionType type, int amount, String label, boolean allIn) {
    public Action toAction() {
        return new Action(type, amount);
    }
}
