package com.pokertrainer.server.api;

import java.util.List;
import java.util.Map;

/**
 * Everything the web app sends and receives. Amounts the UI shows are in big blinds (doubles);
 * amounts the UI sends back for an action are in chips (ints), copied from a LegalAction, so the
 * server never has to round a user-supplied float.
 */
public final class Dto {
    private Dto() {}

    // ---- requests ----

    /** format: "SIX_MAX" or "HEADS_UP". Give either a preset ("mixed", "soft", "tough") or explicit bot ids. */
    public record CreateSessionRequest(String format, String preset, List<String> lineup, Integer stackBb, Long seed) {}

    /** type: FOLD, CHECK, CALL, BET or RAISE. amount: chips, the "to" amount for BET and RAISE. */
    public record ActionRequest(String type, int amount) {}

    // ---- responses ----

    public record BotView(String id, String name, String style, String description) {}

    public record LobbyView(List<BotView> bots, Map<String, List<String>> presets, boolean aiCoach) {}

    public record SessionView(
        String id,
        String format,
        int handNumber,
        double netBb,
        int decisions,
        int goodDecisions,
        int mistakes,
        boolean aiCoach,
        HandView hand) {}

    public record HandView(
        int handNumber,
        String street,
        double potBb,
        double potBeforeStreetBb,
        List<String> board,
        List<SeatView> seats,
        int heroSeat,
        int toAct,
        List<ActionView> legalActions,
        List<LogView> log,
        ResultView result) {}

    public record SeatView(
        int seat,
        String name,
        String botId,
        String style,
        String position,
        double stackBb,
        double committedBb,
        boolean folded,
        boolean allIn,
        boolean button,
        boolean hero,
        List<String> cards,
        String lastAction,
        String handName) {}

    public record ActionView(String type, int amount, String label, double amountBb, boolean allIn) {}

    public record LogView(int seat, String name, String street, String text) {}

    public record ResultView(boolean showdown, List<Integer> winners, double heroNetBb, String summary) {}

    /** Returned right after an action. Fetch the coach feedback for it separately. */
    public record DecisionView(int handNumber, int index, String street, String actionLabel) {}

    public record ActionResult(SessionView session, DecisionView decision) {}

    /** verdict: GOOD, INACCURACY or MISTAKE. source: "ai" or "rules". */
    public record CoachView(String verdict, String headline, String explanation, String tip, String source) {}

    /** The background numbers, only shown in the hand review. */
    public record NumbersView(
        double equity,
        double potOdds,
        double spr,
        String chartPosition,
        Boolean chartOpens,
        double chartShare,
        String mathVerdict,
        double mathEvLossBb,
        String mathNote) {}

    public record DecisionReview(
        int index,
        String street,
        String position,
        List<String> board,
        double potBb,
        double toCallBb,
        int playersInHand,
        String actionLabel,
        List<String> options,
        CoachView coach,
        NumbersView numbers) {}

    public record ReviewView(
        int handNumber,
        boolean finished,
        List<String> heroCards,
        List<String> board,
        ResultView result,
        List<DecisionReview> decisions) {}

    public record ErrorView(String error) {}
}
