package com.pokertrainer.engine.feedback;

import com.pokertrainer.engine.core.Equity;
import com.pokertrainer.engine.game.Action;
import com.pokertrainer.engine.game.ActionType;
import com.pokertrainer.engine.game.HandState;

import java.util.Map;
import java.util.Random;

/**
 * Fallback feedback from equity and pot odds. It only grades call-or-fold decisions, where the
 * math is honest: calling wins {@code equity * (pot + call)} and costs {@code call}, compared
 * with folding for zero. It ignores future betting, so it is always marked as an estimate.
 *
 * <p>Bets, checks and raises get INFO feedback with the numbers but no grade, until solver data
 * can say what the right frequency is.
 *
 * <p>TODO: replace "random hands" with each opponent's estimated range given their actions.
 */
public final class HeuristicFeedbackProvider implements FeedbackProvider {
    private final Random rng;
    private final int trials;

    public HeuristicFeedbackProvider(Random rng, int trials) {
        this.rng = rng;
        this.trials = trials;
    }

    @Override
    public Feedback evaluate(HandState before, Action taken) {
        int seat = before.toAct();
        int opponents = before.playersInHand() - 1;
        boolean multiway = opponents >= 2;
        double equity = Equity.vsRandom(before.holeCards(seat), before.board(), opponents, trials, rng);
        int toCall = before.toCall(seat);
        double potOdds = toCall == 0 ? 0 : toCall / (double) (before.potTotal() + toCall);

        boolean callOrFold = toCall > 0 && (taken.type() == ActionType.CALL || taken.type() == ActionType.FOLD);
        if (!callOrFold) {
            return new Feedback(Verdict.INFO, 0, "No grade yet",
                String.format("Your hand has about %d%% equity against %s. Bets, checks and raises get "
                    + "graded once solver data is in.", pct(equity), who(opponents)),
                equity, potOdds, Map.of(), multiway, true, "heuristic");
        }

        double callEvChips = equity * (before.potTotal() + toCall) - toCall;
        double callEvBb = callEvChips / HandState.BIG_BLIND;
        boolean folded = taken.type() == ActionType.FOLD;
        double loss = folded ? Math.max(0, callEvBb) : Math.max(0, -callEvBb);
        Verdict verdict = Verdict.fromLossBb(loss);

        String headline = switch (verdict) {
            case GOOD -> folded ? "Good fold" : "Good call";
            case INACCURACY -> folded ? "Slightly tight fold" : "Slightly loose call";
            default -> folded ? "This fold gives up value" : "This call loses money";
        };
        String detail = String.format(
            "You needed %d%% equity to call and had about %d%% against %s.",
            pct(potOdds), pct(equity), who(opponents));

        return new Feedback(verdict, loss, headline, detail, equity, potOdds, Map.of(), multiway, true, "heuristic");
    }

    private static int pct(double x) {
        return (int) Math.round(x * 100);
    }

    private static String who(int opponents) {
        return opponents == 1 ? "a random hand" : opponents + " random hands";
    }
}
