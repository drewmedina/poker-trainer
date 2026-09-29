package com.pokertrainer.server.session;

import com.pokertrainer.server.api.Dto;
import com.pokertrainer.server.coach.CoachService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TrainingSessionTest {

    private final SessionStore store = new SessionStore(CoachService.rulesOnly());

    private static Dto.ActionRequest randomLegal(Dto.HandView hand, Random rng) {
        List<Dto.ActionView> legal = hand.legalActions();
        Dto.ActionView a = legal.get(rng.nextInt(legal.size()));
        return new Dto.ActionRequest(a.type(), a.amount());
    }

    private void playMany(String format, int hands, long seed) {
        Dto.SessionView view = store.create(new Dto.CreateSessionRequest(format, "mixed", null, 100, seed));
        TrainingSession s = store.get(view.id());
        Random rng = new Random(seed);
        double net = 0;
        int decisions = 0;
        for (int h = 0; h < hands; h++) {
            Dto.HandView hand = view.hand();
            int inHand = 0;
            while (hand.result() == null) {
                assertEquals(0, hand.toAct(), "the server only stops when it is the hero's turn");
                Dto.ActionResult r = s.act(randomLegal(hand, rng));
                assertEquals(hand.handNumber(), r.decision().handNumber());
                assertEquals(inHand++, r.decision().index());
                Dto.CoachView coach = s.coach(r.decision().handNumber(), r.decision().index());
                assertNotEquals("INFO", coach.verdict(), "every decision gets a real verdict");
                assertFalse(coach.headline().isBlank());
                assertFalse(coach.explanation().isBlank());
                assertFalse(coach.explanation().contains("—"), "house style: no em dashes");
                view = r.session();
                hand = view.hand();
                decisions++;
            }
            Dto.ReviewView review = s.review(hand.handNumber());
            assertTrue(review.finished());
            assertEquals(inHand, review.decisions().size());
            assertNotNull(review.result());
            for (Dto.DecisionReview d : review.decisions()) {
                assertNotNull(d.coach());
                assertTrue(d.numbers().equity() >= 0 && d.numbers().equity() <= 1);
            }
            net += hand.result().heroNetBb();
            assertEquals(net, view.netBb(), 0.051);
            double stacks = hand.seats().stream().mapToDouble(Dto.SeatView::stackBb).sum();
            assertEquals(100.0 * hand.seats().size(), stacks, 1e-9);
            view = s.dealNext();
        }
        assertEquals(decisions, view.decisions());
    }

    @Test
    void sixMaxSessionPlaysManyHands() {
        playMany("SIX_MAX", 120, 1);
    }

    @Test
    void headsUpSessionPlaysManyHands() {
        playMany("HEADS_UP", 120, 2);
    }

    @Test
    void sixMaxUsesTheLineup() {
        Dto.SessionView v = store.create(new Dto.CreateSessionRequest("SIX_MAX", "tough", null, 100, 5L));
        assertEquals(6, v.hand().seats().size());
        assertEquals("vera", v.hand().seats().get(1).botId());
        assertEquals("You", v.hand().seats().get(0).name());
        assertNotNull(v.hand().seats().get(0).cards());
        assertNull(v.hand().seats().get(1).cards(), "bot cards stay hidden until showdown");
        assertFalse(v.aiCoach());
    }

    @Test
    void unopenedPreflopSpotsCarryTheOpeningChart() {
        // Find a hand where the hero is first in preflop (not in the big blind) and fold it.
        for (long seed = 1; seed < 200; seed++) {
            Dto.SessionView v = store.create(new Dto.CreateSessionRequest("SIX_MAX", "mixed", null, 100, seed));
            Dto.HandView h = v.hand();
            if (h.result() != null || !"PREFLOP".equals(h.street())) continue;
            boolean firstIn = h.log().stream().allMatch(l -> l.text().contains("posts") || l.text().contains("folds"));
            String pos = h.seats().get(0).position();
            if (!firstIn || pos.equals("BB")) continue;
            TrainingSession s = store.get(v.id());
            s.act(new Dto.ActionRequest("FOLD", 0));
            Dto.DecisionReview d = s.review(h.handNumber()).decisions().get(0);
            assertEquals(pos, d.numbers().chartPosition());
            assertNotNull(d.numbers().chartOpens());
            return;
        }
        fail("never found an unopened spot");
    }

    @Test
    void illegalActionsAreRejectedWithoutSideEffects() {
        Dto.SessionView v = store.create(new Dto.CreateSessionRequest("HEADS_UP", null, List.of("rex"), 100, 9L));
        TrainingSession s = store.get(v.id());
        while (v.hand().result() != null) v = s.dealNext();
        int before = v.decisions();
        assertThrows(IllegalArgumentException.class, () -> s.act(new Dto.ActionRequest("RAISE", 101)));
        assertThrows(IllegalArgumentException.class, () -> s.act(new Dto.ActionRequest("DANCE", 0)));
        assertEquals(before, s.view().decisions());
        assertThrows(IllegalStateException.class, s::dealNext, "cannot skip a live hand");
        int handNo = s.view().handNumber();
        assertThrows(java.util.NoSuchElementException.class, () -> s.coach(handNo, 5));
    }

    @Test
    void unknownBotsAndPresetsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> store.create(new Dto.CreateSessionRequest("SIX_MAX", "spicy", null, 100, 1L)));
        assertThrows(IllegalArgumentException.class,
            () -> store.create(new Dto.CreateSessionRequest("HEADS_UP", null, List.of("zed"), 100, 1L)));
    }
}
