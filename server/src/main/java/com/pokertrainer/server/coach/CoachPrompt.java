package com.pokertrainer.server.coach;

import java.util.Locale;

/** Builds the system prompt and the per-decision message sent to Claude. */
public final class CoachPrompt {
    private CoachPrompt() {}

    public static final String SYSTEM = """
        You are the coach inside a poker training app. The player is practicing no-limit hold'em \
        cash games against bots and just made one decision. Give short, consistent, generic \
        feedback on that single decision.

        How to judge:
        - Use sound, widely taught strategy: position, hand strength relative to the board, pot \
        odds, stack depth, the number of opponents, and the opponents' known tendencies.
        - Pick exactly one verdict. GOOD: a sound, standard play, including close spots where the \
        choice is defensible. INACCURACY: playable, but a clearly better option existed. MISTAKE: \
        gives up significant value or ignores an important principle.
        - Be consistent. Similar spots get similar verdicts. When a decision is close, prefer GOOD.
        - The reference section is background from the app. Equity there is measured against \
        random hands, which overstates the player's chances when opponents have bet or raised.
        - If the reference says a call loses money, do not grade that call GOOD.
        - If the reference gives an opening chart for an unopened pot, treat it as the standard play.

        How to write:
        - Talk to the player as "you". Plain words. Explain any poker term in a few words.
        - Do not quote numbers from the reference, solver frequencies, or EV figures. Explain in \
        principles. The exact numbers are shown to the player later in the hand review.
        - headline: 2 to 4 words, like "Good value bet" or "Too loose a call".
        - explanation: 1 or 2 sentences, at most 45 words, about why.
        - tip: one reusable rule of thumb for next time, at most 20 words.
        - Never use em dashes.
        """;

    public static String user(DecisionContext c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(c.format()).append(", ").append(c.stackDepthBb()).append("bb cash game.\n");
        sb.append("Street: ").append(c.street()).append(". Board: ")
            .append(c.board().isEmpty() ? "none yet" : String.join(" ", c.board())).append(".\n");
        sb.append("Hero: ").append(c.heroPosition()).append(", holding ").append(String.join(" ", c.heroCards()))
            .append(", stack ").append(bb(c.heroStackBb())).append(".\n");
        sb.append("Pot before this decision: ").append(bb(c.potBb())).append(". To call: ").append(bb(c.toCallBb()))
            .append(". Stack-to-pot ratio: ").append(String.format(Locale.ROOT, "%.1f", c.spr())).append(".\n");
        sb.append("Players still in the hand, including hero: ").append(c.playersInHand()).append(".\n");
        sb.append("Opponents still in the hand:\n");
        for (DecisionContext.Opponent o : c.opponents()) {
            sb.append("- ").append(o.name()).append(" (").append(o.position()).append(", ").append(o.style())
                .append(": ").append(o.description()).append(") stack ").append(bb(o.stackBb()));
            if (o.lastAction() != null) sb.append(", last action: ").append(o.lastAction());
            sb.append('\n');
        }
        sb.append("Action so far this hand:\n");
        for (String h : c.history()) sb.append("- ").append(h).append('\n');
        sb.append("Options hero had: ").append(String.join(", ", c.options())).append(".\n");
        sb.append("Hero chose: ").append(c.actionLabel()).append(".\n\n");

        DecisionContext.Reference r = c.reference();
        sb.append("Reference (background only, do not quote the numbers):\n");
        sb.append("- Hero equity against random hands of the players still in: ").append(pct(r.equity())).append('\n');
        if (r.potOdds() > 0) sb.append("- Equity needed to call: ").append(pct(r.potOdds())).append('\n');
        if (r.mathNote() != null) sb.append("- Pot-odds check: ").append(r.mathNote()).append('\n');
        if (r.chartOpens() != null) {
            sb.append("- Opening chart (unopened pot, ").append(r.chartPosition()).append("): the standard chart ")
                .append(r.chartOpens() ? "OPENS" : "FOLDS").append(" this hand.\n");
        }
        sb.append("\nCall give_feedback with your verdict.");
        return sb.toString();
    }

    private static String bb(double v) {
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v)) + "bb";
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
