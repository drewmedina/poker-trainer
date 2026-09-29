package com.pokertrainer.engine.bots;

import com.pokertrainer.engine.core.Equity;
import com.pokertrainer.engine.game.Action;
import com.pokertrainer.engine.game.ActionType;
import com.pokertrainer.engine.game.HandState;
import com.pokertrainer.engine.game.LegalAction;
import com.pokertrainer.engine.game.Street;

import java.util.List;
import java.util.Random;

/**
 * PLACEHOLDER bot. Decides from raw equity against random hands plus its profile's leaks.
 * It is good enough to make hands playable end to end, and nothing more. Milestone 3 replaces
 * it with solver strategies (heads-up and single-raised pots) and node-locked adjustments.
 */
public final class ProfileBot implements BotPolicy {
    private static final int TRIALS = 350;

    private final BotProfile profile;

    public ProfileBot(BotProfile profile) {
        this.profile = profile;
    }

    public BotProfile profile() {
        return profile;
    }

    @Override
    public Action decide(HandState state, Random rng) {
        int seat = state.toAct();
        List<LegalAction> legal = state.legalActions();
        int opponents = state.playersInHand() - 1;
        double equity = Equity.vsRandom(state.holeCards(seat), state.board(), opponents, TRIALS, rng);
        // Equity against random hands overstates marginal hands multiway; scale the bar up.
        double bar = profile.valueThreshold + 0.06 * (opponents - 1);

        int toCall = state.toCall(seat);
        if (state.street() == Street.PREFLOP) {
            return preflop(state, legal, equity, toCall, rng);
        }
        if (toCall > 0) {
            double potOdds = toCall / (double) (state.potTotal() + toCall);
            if (equity >= bar + 0.1 && rng.nextDouble() < profile.aggression * 0.4) {
                return pickRaise(legal, rng).orElse(Action.call());
            }
            if (equity + profile.callSlack >= potOdds) {
                return Action.call();
            }
            return Action.fold();
        }
        if (equity >= bar && rng.nextDouble() < profile.aggression) {
            return pickRaise(legal, rng).orElse(Action.check());
        }
        if (equity < 0.35 && rng.nextDouble() < profile.bluffRate) {
            return pickRaise(legal, rng).orElse(Action.check());
        }
        return Action.check();
    }

    private Action preflop(HandState state, List<LegalAction> legal, double equity, int toCall, Random rng) {
        int opponents = state.players() - 1;
        // Heads-up ranges are far wider than 6-max ranges, so stretch the profile's 6-max number.
        double top = opponents == 1 ? Math.min(0.9, profile.preflopTop * 2.4) : profile.preflopTop;
        // Rough map from "top X% of hands" to equity vs random hands at this table size.
        double playBar = 1.0 / (opponents + 1) + (0.5 - top) * 0.35;
        double raiseBar = playBar + 0.08;
        boolean facingRaise = state.currentBet() > HandState.BIG_BLIND;

        if (equity >= raiseBar + (facingRaise ? 0.08 : 0) && rng.nextDouble() < 0.5 + profile.aggression / 2) {
            var raise = pickRaise(legal, rng);
            if (raise.isPresent()) return raise.get();
        }
        if (toCall == 0) return Action.check();
        if (equity + profile.callSlack * 0.5 >= playBar + (facingRaise ? 0.05 : 0)) return Action.call();
        return Action.fold();
    }

    /** Picks a non-all-in bet or raise, favoring the middle size. All-in only if it is the only option. */
    private static java.util.Optional<Action> pickRaise(List<LegalAction> legal, Random rng) {
        List<LegalAction> sized = legal.stream()
            .filter(a -> (a.type() == ActionType.BET || a.type() == ActionType.RAISE) && !a.allIn())
            .toList();
        if (!sized.isEmpty()) {
            int i = sized.size() >= 2 && rng.nextDouble() < 0.6 ? 1 : 0;
            return java.util.Optional.of(sized.get(Math.min(i, sized.size() - 1)).toAction());
        }
        return legal.stream()
            .filter(a -> a.type() == ActionType.BET || a.type() == ActionType.RAISE)
            .findFirst()
            .map(LegalAction::toAction);
    }
}
