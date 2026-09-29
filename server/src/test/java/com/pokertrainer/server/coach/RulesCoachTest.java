package com.pokertrainer.server.coach;

import com.pokertrainer.engine.feedback.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulesCoachTest {

    private final RulesCoach coach = new RulesCoach();

    static DecisionContext spot(String street, int players, double toCall, boolean unopened, String action,
                                String label, DecisionContext.Reference ref, String opponentStyle) {
        List<DecisionContext.Opponent> opps = new java.util.ArrayList<>();
        for (int i = 1; i < players; i++) {
            opps.add(new DecisionContext.Opponent("Bot" + i, "BB", opponentStyle, "desc", 98, null));
        }
        return new DecisionContext("6-max", 100, players, street, "CO", List.of("As", "Kd"), List.of(),
            3.5, toCall, 99, 20, unopened, opps, List.of(), List.of("Fold", "Call 1"), action, label, ref);
    }

    static DecisionContext.Reference ref(double equity, Boolean chartOpens, String mathVerdict) {
        return new DecisionContext.Reference(equity, 0.3, chartOpens == null ? null : "CO", chartOpens, 0.27,
            mathVerdict, 0, null);
    }

    @Test
    void followsTheOpeningChart() {
        assertEquals(Verdict.GOOD, coach.review(spot("PREFLOP", 6, 1, true, "RAISE", "Raise to 2.5", ref(0.3, true, null), "Tight")).verdict());
        assertEquals(Verdict.GOOD, coach.review(spot("PREFLOP", 6, 1, true, "FOLD", "Fold", ref(0.1, false, "GOOD"), "Tight")).verdict());
        assertNotEquals(Verdict.GOOD, coach.review(spot("PREFLOP", 6, 1, true, "FOLD", "Fold", ref(0.3, true, "GOOD"), "Tight")).verdict());
        assertNotEquals(Verdict.GOOD, coach.review(spot("PREFLOP", 6, 1, true, "CALL", "Call 1", ref(0.3, true, "GOOD"), "Tight")).verdict());
        assertNotEquals(Verdict.GOOD, coach.review(spot("PREFLOP", 6, 1, true, "RAISE", "Raise to 2.5", ref(0.08, false, null), "Tight")).verdict());
    }

    @Test
    void callFoldSpotsFollowThePotOddsCheck() {
        assertEquals(Verdict.MISTAKE, coach.review(spot("RIVER", 2, 5, false, "CALL", "Call 5", ref(0.1, null, "MISTAKE"), "Tight")).verdict());
        assertEquals(Verdict.GOOD, coach.review(spot("RIVER", 2, 5, false, "FOLD", "Fold", ref(0.1, null, "GOOD"), "Tight")).verdict());
    }

    @Test
    void checkingAMonsterMissesValue() {
        assertEquals(Verdict.INACCURACY, coach.review(spot("FLOP", 2, 0, false, "CHECK", "Check", ref(0.9, null, null), "Tight")).verdict());
        assertEquals(Verdict.GOOD, coach.review(spot("FLOP", 2, 0, false, "BET", "Bet 75% (3)", ref(0.9, null, null), "Tight")).verdict());
    }

    @Test
    void bluffsDependOnOpponents() {
        assertEquals(Verdict.GOOD, coach.review(spot("TURN", 2, 0, false, "BET", "Bet 75% (3)", ref(0.2, null, null), "Tight")).verdict());
        assertEquals(Verdict.INACCURACY, coach.review(spot("TURN", 2, 0, false, "BET", "Bet 75% (3)", ref(0.2, null, null), "Calling station")).verdict());
        assertEquals(Verdict.INACCURACY, coach.review(spot("TURN", 4, 0, false, "BET", "Bet 75% (3)", ref(0.1, null, null), "Tight")).verdict());
    }

    @Test
    void alwaysAnswersWithoutEmDashes() {
        String[] actions = {"FOLD", "CHECK", "CALL", "BET", "RAISE"};
        for (String a : actions) {
            for (double eq : new double[] {0.05, 0.4, 0.95}) {
                for (int players : new int[] {2, 3, 6}) {
                    CoachFeedback f = coach.review(spot("FLOP", players, a.equals("CHECK") || a.equals("BET") ? 0 : 2,
                        false, a, a.equals("BET") ? "All-in 97" : a, ref(eq, null, a.equals("CALL") || a.equals("FOLD") ? "GOOD" : null), "Tight"));
                    assertNotNull(f.verdict());
                    assertFalse(f.headline().isBlank());
                    assertFalse((f.headline() + f.explanation() + f.tip()).contains("—"));
                    assertEquals("rules", f.source());
                }
            }
        }
    }
}
