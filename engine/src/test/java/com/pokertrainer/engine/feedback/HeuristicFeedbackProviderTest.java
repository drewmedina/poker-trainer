package com.pokertrainer.engine.feedback;

import com.pokertrainer.engine.core.Card;
import com.pokertrainer.engine.core.Deck;
import com.pokertrainer.engine.game.Action;
import com.pokertrainer.engine.game.HandState;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicFeedbackProviderTest {

    private final HeuristicFeedbackProvider provider = new HeuristicFeedbackProvider(new Random(9), 4000);

    /** Heads-up, hero on the button (seat 0) acts first with the given hand. */
    private static HandState headsUpWith(String heroHand) {
        int[] hero = Card.parseMany(heroHand);
        int[] villain = Card.parseMany("2c7d");
        Deck deck = Deck.stacked(hero[0], villain[0], hero[1], villain[1]);
        return HandState.deal(new int[] {10_000, 10_000}, 0, deck);
    }

    @Test
    void foldingAcesIsAMistake() {
        Feedback f = provider.evaluate(headsUpWith("AsAd"), Action.fold());
        assertEquals(Verdict.MISTAKE, f.verdict());
        assertTrue(f.evLossBb() > 1.0);
        assertTrue(f.estimate());
        assertFalse(f.multiway());
    }

    @Test
    void callingWithAcesIsGood() {
        Feedback f = provider.evaluate(headsUpWith("AsAd"), Action.call());
        assertEquals(Verdict.GOOD, f.verdict());
        assertEquals(0.0, f.evLossBb(), 1e-9);
    }

    @Test
    void raisesGetContextButNoGrade() {
        Feedback f = provider.evaluate(headsUpWith("AsAd"), Action.raiseTo(250));
        assertEquals(Verdict.INFO, f.verdict());
        assertTrue(f.equity() > 0.8);
    }

    @Test
    void multiwaySpotsAreFlagged() {
        HandState h = HandState.deal(new int[] {10_000, 10_000, 10_000, 10_000, 10_000, 10_000}, 0, new Deck(new Random(4)));
        Feedback f = provider.evaluate(h, Action.fold());
        assertTrue(f.multiway());
    }
}
