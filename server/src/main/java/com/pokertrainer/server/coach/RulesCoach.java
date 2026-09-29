package com.pokertrainer.server.coach;

import com.pokertrainer.engine.feedback.Verdict;

/**
 * Deterministic fallback coach. Used when no API key is set or a Claude call fails, so every
 * decision always gets a verdict and a short explanation. It works from the same background
 * numbers the AI coach sees: hand strength relative to a fair share, pot odds, the opening
 * chart, and the opponents' known styles.
 */
public final class RulesCoach implements Coach {

    private enum Strength { WEAK, MEDIUM, STRONG }

    @Override
    public CoachFeedback review(DecisionContext c) {
        String action = c.actionType();
        boolean allIn = c.actionLabel().startsWith("All-in");
        Strength strength = strength(c);
        DecisionContext.Reference ref = c.reference();

        // 1. Unopened pot preflop with a chart: follow the chart.
        if (c.unopenedPreflop() && ref.chartOpens() != null) {
            boolean opens = ref.chartOpens();
            String pos = c.heroPosition();
            if (opens) {
                return switch (action) {
                    case "RAISE", "BET" -> allIn && c.stackDepthBb() > 25
                        ? fb(Verdict.INACCURACY, "Too big an open", "This hand is a normal open from " + pos + ", but moving all-in risks your whole stack to win just the blinds.", "Open to a normal size and play the hand after the flop.")
                        : fb(Verdict.GOOD, "Standard open", "This hand is a normal opening hand from " + pos + ". Raising first in lets you take the lead and often win the blinds right away.", "When everyone folds to you, open with a raise or fold. Avoid limping.");
                    case "CALL" -> fb(Verdict.INACCURACY, "Raise instead of limping", "This hand is good enough to open from " + pos + ". Just calling the big blind invites more players in and gives up the lead.", "When you are first in, raise or fold. Avoid limping.");
                    default -> fb(strength == Strength.STRONG ? Verdict.MISTAKE : Verdict.INACCURACY, "Too tight a fold", "Most players open this hand from " + pos + ". Folding gives up a spot where you are first in and can take the initiative.", "The later your position, the wider you can open.");
                };
            }
            return switch (action) {
                case "FOLD" -> fb(Verdict.GOOD, "Good fold", "This hand is too weak to open from " + pos + ". Folding keeps you out of spots where you are often dominated.", "Early positions need tighter hands because more players act after you.");
                case "CALL" -> fb(Verdict.INACCURACY, "Avoid limping", "This hand is not strong enough to open from " + pos + ", and limping with it tends to lose small pots out of position.", "When you are first in, raise or fold. Avoid limping.");
                default -> fb(strength == Strength.WEAK ? Verdict.MISTAKE : Verdict.INACCURACY, "Loose open", "This hand is outside a normal opening range from " + pos + ". When it gets called or raised, it is often behind.", "Tighten up in early position and loosen up near the button.");
            };
        }

        // 2. Facing a bet and calling or folding: the pot-odds check decides.
        if (c.toCallBb() > 0 && (action.equals("CALL") || action.equals("FOLD")) && ref.mathVerdict() != null) {
            Verdict v = Verdict.valueOf(ref.mathVerdict());
            boolean call = action.equals("CALL");
            String field = c.playersInHand() > 2 ? "the players still in the hand" : "your opponent";
            if (call) {
                return switch (v) {
                    case GOOD -> fb(v, "Good call", "The price is right for how often your hand wins against " + field + ".", "Compare the price of a call with how often you expect to win.");
                    case INACCURACY -> fb(v, "Slightly loose call", "This call is close, but your hand does not win quite often enough against " + field + " to justify the price.", "Close calls add up. Fold more when the price is only borderline.");
                    default -> fb(v, "Too loose a call", "Your hand rarely wins here, so the price is too high. Bets usually mean a stronger range than a random hand.", "Respect bets, especially from players who rarely bluff.");
                };
            }
            return switch (v) {
                case GOOD -> fb(v, "Good fold", "Your hand does not win often enough to pay this price. Letting it go saves money.", "Folding weak hands to bets is where a lot of profit comes from.");
                case INACCURACY -> fb(v, "Slightly tight fold", "You were getting a decent price, and your hand wins often enough that continuing was a bit better.", "When the pot lays you a good price, lean toward continuing.");
                default -> fb(v, "Too tight a fold", "You were getting a great price and your hand wins often enough to call. Folding here gives up value.", "Small bets into big pots need to be called with a wide range.");
            };
        }

        // 3. Raising a bet.
        if (c.toCallBb() > 0 && (action.equals("RAISE") || action.equals("BET"))) {
            if (strength == Strength.STRONG) {
                return fb(Verdict.GOOD, "Good raise for value", "Your hand is strong enough to build the pot while you are likely ahead.", "Raise strong hands to get more money in while you are ahead.");
            }
            if (strength == Strength.MEDIUM) {
                return fb(Verdict.INACCURACY, "Risky raise", "A medium hand like this is usually called by better hands and folds out worse ones when you raise.", "Medium hands usually do better calling than raising.");
            }
            boolean headsUp = c.playersInHand() == 2;
            return headsUp
                ? fb(Verdict.INACCURACY, "Ambitious bluff raise", "Bluff raising with little equity only works against players who fold a lot, and it risks a big part of your stack.", "Pick bluffs that have some way to improve if called.")
                : fb(Verdict.MISTAKE, "Bluff into a crowd", "Raising with a weak hand against several players rarely works. Someone usually has enough to continue.", "Bluff less the more players are in the pot.");
        }

        // 4. Nobody has bet: checking or betting.
        boolean station = c.opponents().stream().anyMatch(o -> o.style() != null && o.style().toLowerCase().contains("calling station"));
        boolean preflop = "PREFLOP".equals(c.street());
        if (action.equals("CHECK")) {
            return switch (strength) {
                case STRONG -> fb(Verdict.INACCURACY, preflop ? "Raise your strong hands" : "Missed value", "Your hand is strong and worse hands can still pay you. Checking gives up money you could win.", "Bet your strong hands. Most of your profit comes from value bets.");
                case MEDIUM -> fb(Verdict.GOOD, "Good check", "A medium hand plays well as a check here. It keeps the pot manageable and still wins at showdown often.", "Medium hands like smaller pots.");
                default -> fb(Verdict.GOOD, "Fine check", "With a weak hand, taking a free card or a free showdown is usually best.", "Not every weak hand needs a bluff. Choose bluffs that can improve.");
            };
        }
        if (allIn && strength != Strength.STRONG && c.spr() > 3) {
            return fb(Verdict.MISTAKE, "Risky all-in", "Moving all-in puts your whole stack at risk with a hand that does not want a big pot.", "Save all-ins for strong hands or when stacks are already short.");
        }
        return switch (strength) {
            case STRONG -> fb(Verdict.GOOD, "Good value bet", "Your hand is strong and worse hands can call. Betting builds the pot while you are ahead.", "Bet your strong hands. Most of your profit comes from value bets.");
            case MEDIUM -> fb(Verdict.GOOD, "Reasonable bet", "Betting a medium hand can protect it against draws and get value from worse hands. Keep the size moderate.", "With medium hands, smaller bets usually work best.");
            default -> {
                if (c.playersInHand() > 2) {
                    yield fb(Verdict.INACCURACY, "Risky bluff", "Bluffing into several players rarely works. Someone usually has enough to call.", "Bluff less the more players are in the pot.");
                }
                if (station) {
                    yield fb(Verdict.INACCURACY, "Bluffing a calling station", "This opponent calls too often, so bluffs lose more than usual. Save bets for hands that can win at showdown.", "Bluff players who fold a lot. Value bet players who call a lot.");
                }
                yield fb(Verdict.GOOD, "Reasonable bluff", "Heads-up with nobody showing strength, a well-sized bluff can take the pot often enough.", "Good bluffs have some way to improve if called.");
            }
        };
    }

    /** Hand strength relative to a fair share of the pot, so multiway spots need more. */
    private static Strength strength(DecisionContext c) {
        int players = Math.max(2, c.playersInHand());
        double relative = c.reference().equity() * players;
        if (relative >= 1.4) return Strength.STRONG;
        if (relative >= 0.8) return Strength.MEDIUM;
        return Strength.WEAK;
    }

    private static CoachFeedback fb(Verdict v, String headline, String explanation, String tip) {
        return new CoachFeedback(v, headline, explanation, tip, "rules");
    }
}
