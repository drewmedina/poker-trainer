package com.pokertrainer.server.session;

import com.pokertrainer.server.api.Dto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TrainingSessionTest {

    private final SessionStore store = new SessionStore();

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
        for (int h = 0; h < hands; h++) {
            Dto.HandView hand = view.hand();
            while (hand.result() == null) {
                assertEquals(0, hand.toAct(), "the server only stops when it is the hero's turn");
                assertFalse(hand.legalActions().isEmpty());
                Dto.ActionResult r = s.act(randomLegal(hand, rng));
                assertNotNull(r.feedback());
                view = r.session();
                hand = view.hand();
            }
            net += hand.result().heroNetBb();
            assertEquals(net, view.netBb(), 0.051);
            double stacks = hand.seats().stream().mapToDouble(Dto.SeatView::stackBb).sum();
            assertEquals(100.0 * hand.seats().size(), stacks, 1e-9);
            view = s.dealNext();
        }
    }

    @Test
    void sixMaxSessionPlaysManyHands() {
        playMany("SIX_MAX", 150, 1);
    }

    @Test
    void headsUpSessionPlaysManyHands() {
        playMany("HEADS_UP", 150, 2);
    }

    @Test
    void sixMaxUsesTheLineup() {
        Dto.SessionView v = store.create(new Dto.CreateSessionRequest("SIX_MAX", "tough", null, 100, 5L));
        assertEquals(6, v.hand().seats().size());
        assertEquals("vera", v.hand().seats().get(1).botId());
        assertEquals("You", v.hand().seats().get(0).name());
        assertNotNull(v.hand().seats().get(0).cards());
        assertNull(v.hand().seats().get(1).cards(), "bot cards stay hidden until showdown");
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
    }

    @Test
    void unknownBotsAndPresetsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> store.create(new Dto.CreateSessionRequest("SIX_MAX", "spicy", null, 100, 1L)));
        assertThrows(IllegalArgumentException.class,
            () -> store.create(new Dto.CreateSessionRequest("HEADS_UP", null, List.of("zed"), 100, 1L)));
    }
}
