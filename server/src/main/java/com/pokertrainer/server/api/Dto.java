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

    public record LobbyView(List<BotView> bots, Map<String, List<String>> presets) {}

    public record SessionView(
        String id,
        String format,
        int handNumber,
        double netBb,
        double evLostBb,
        int decisions,
        int goodDecisions,
        int mistakes,
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

    public record FeedbackView(
        String verdict,
        double evLossBb,
        String headline,
        String detail,
        double equity,
        double potOdds,
        Map<String, Double> frequencies,
        boolean multiway,
        boolean estimate,
        String source,
        String actionLabel) {}

    public record ActionResult(SessionView session, FeedbackView feedback) {}

    public record ErrorView(String error) {}
}
