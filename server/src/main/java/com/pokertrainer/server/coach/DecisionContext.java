package com.pokertrainer.server.coach;

import java.util.List;

/**
 * Everything the coach needs to judge one decision, captured the moment before the hero acted.
 * Amounts are in big blinds. Built by TrainingSession; read by both coaches and the review view.
 */
public record DecisionContext(
    String format,              // "6-max" or "heads-up"
    int stackDepthBb,
    int playersInHand,
    String street,              // PREFLOP, FLOP, TURN, RIVER
    String heroPosition,
    List<String> heroCards,
    List<String> board,
    double potBb,               // everything in the middle, before the hero's action
    double toCallBb,
    double heroStackBb,
    double spr,                 // effective stack / pot
    boolean unopenedPreflop,    // everyone before the hero folded, only blinds are in
    List<Opponent> opponents,   // still in the hand
    List<String> history,       // "Preflop: Rex (UTG) raises to 2.5"
    List<String> options,       // button labels the hero could press
    String actionType,          // FOLD, CHECK, CALL, BET, RAISE
    String actionLabel,         // "Call 2.7"
    Reference reference) {

    public record Opponent(String name, String position, String style, String description, double stackBb, String lastAction) {}

    /**
     * Background numbers. Never shown in the live feedback sheet; shown in the hand review and
     * given to the coach as context.
     *
     * @param equity        hero equity against random hands of every player still in the hand
     * @param potOdds       share of the final pot a call costs, 0 when nothing to call
     * @param chartPosition position the opening chart was looked up for, or null
     * @param chartOpens    whether the opening chart opens this hand, or null when no chart applies
     * @param chartShare    share of hands the chart opens from that position
     * @param mathVerdict   GOOD / INACCURACY / MISTAKE for call-or-fold spots from pot odds, else null
     * @param mathEvLossBb  EV the pot-odds check says was given up (call/fold only)
     * @param mathNote      one line explaining the math check, or null
     */
    public record Reference(
        double equity,
        double potOdds,
        String chartPosition,
        Boolean chartOpens,
        double chartShare,
        String mathVerdict,
        double mathEvLossBb,
        String mathNote) {}
}
