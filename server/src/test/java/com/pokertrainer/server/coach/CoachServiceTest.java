package com.pokertrainer.server.coach;

import com.pokertrainer.engine.feedback.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoachServiceTest {

    private static final DecisionContext SPOT = RulesCoachTest.spot("FLOP", 2, 0, false, "CHECK", "Check",
        RulesCoachTest.ref(0.5, null, null), "Tight");

    @Test
    void usesThePrimaryCoachWhenItAnswers() throws Exception {
        Coach ai = c -> new CoachFeedback(Verdict.GOOD, "Nice", "Because.", "Tip.", "ai");
        try (CoachService s = new CoachService(ai, new RulesCoach(), 1, 5)) {
            assertEquals("ai", s.submit(SPOT).get().source());
            assertTrue(s.aiEnabled());
        }
    }

    @Test
    void fallsBackToRulesWhenThePrimaryFails() throws Exception {
        Coach broken = c -> { throw new java.io.IOException("boom"); };
        try (CoachService s = new CoachService(broken, new RulesCoach(), 1, 5)) {
            assertEquals("rules", s.submit(SPOT).get().source());
        }
    }

    @Test
    void fallsBackToRulesWhenThePrimaryIsTooSlow() throws Exception {
        Coach slow = c -> { Thread.sleep(3000); return null; };
        try (CoachService s = new CoachService(slow, new RulesCoach(), 1, 1)) {
            assertEquals("rules", s.submit(SPOT).get().source());
        }
    }

    @Test
    void rulesOnlyAnswersImmediately() throws Exception {
        try (CoachService s = CoachService.rulesOnly()) {
            assertTrue(s.submit(SPOT).isDone());
            assertFalse(s.aiEnabled());
        }
    }
}
