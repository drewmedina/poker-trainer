package com.pokertrainer.engine.game;

import com.pokertrainer.engine.core.Card;
import com.pokertrainer.engine.core.Deck;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HandStateTest {

    private static int[] stacks(int... bb) {
        return Arrays.stream(bb).map(b -> b * 100).toArray();
    }

    @Test
    void headsUpButtonPostsSmallBlindAndActsFirst() {
        HandState h = HandState.deal(stacks(100, 100), 0, new Deck(new Random(1)));
        assertEquals(0, h.smallBlindSeat());
        assertEquals(1, h.bigBlindSeat());
        assertEquals(0, h.toAct());
        assertEquals("BTN", h.position(0));
        assertEquals("BB", h.position(1));

        List<LegalAction> legal = h.legalActions();
        assertEquals(ActionType.FOLD, legal.get(0).type());
        assertEquals(ActionType.CALL, legal.get(1).type());
        assertEquals(50, legal.get(1).amount());
        assertEquals(250, legal.get(2).amount());
        assertTrue(legal.get(legal.size() - 1).allIn());

        h.apply(Action.call());
        assertEquals(1, h.toAct(), "big blind gets the option");
        h.apply(Action.check());
        assertEquals(Street.FLOP, h.street());
        assertEquals(3, h.board().length);
        assertEquals(1, h.toAct(), "big blind acts first after the flop heads-up");
    }

    @Test
    void sixMaxBlindsAndFirstToAct() {
        HandState h = HandState.deal(stacks(100, 100, 100, 100, 100, 100), 2, new Deck(new Random(1)));
        assertEquals(3, h.smallBlindSeat());
        assertEquals(4, h.bigBlindSeat());
        assertEquals(5, h.toAct(), "UTG is left of the big blind");
        assertEquals("UTG", h.position(5));
        assertEquals("HJ", h.position(0));
        assertEquals("CO", h.position(1));
        assertEquals("BTN", h.position(2));
    }

    @Test
    void foldsToTheBigBlind() {
        HandState h = HandState.deal(stacks(100, 100, 100, 100, 100, 100), 0, new Deck(new Random(1)));
        while (!h.isTerminal()) h.apply(Action.fold());
        HandResult r = h.result();
        assertFalse(r.showdown());
        assertEquals(List.of(2), r.winners());
        assertEquals(50, r.net()[2]);
        assertEquals(-50, r.net()[1]);
    }

    @Test
    void postflopActionStartsLeftOfTheButton() {
        HandState h = HandState.deal(stacks(100, 100, 100), 0, new Deck(new Random(2)));
        h.apply(Action.call());  // BTN
        h.apply(Action.call());  // SB completes
        h.apply(Action.check()); // BB
        assertEquals(Street.FLOP, h.street());
        assertEquals(1, h.toAct());
        assertEquals(300, h.potTotal());
    }

    @Test
    void enforcesMinimumRaise() {
        HandState h = HandState.deal(stacks(100, 100), 0, new Deck(new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> h.apply(Action.raiseTo(150)));
        h.apply(Action.raiseTo(300));
        // Last raise was 200, so the next raise must reach at least 500.
        assertThrows(IllegalArgumentException.class, () -> h.apply(Action.raiseTo(400)));
        h.apply(Action.raiseTo(500));
    }

    @Test
    void cannotCheckFacingABetOrFoldForFree() {
        HandState h = HandState.deal(stacks(100, 100), 0, new Deck(new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> h.apply(Action.check()));
        h.apply(Action.call());
        assertThrows(IllegalArgumentException.class, () -> h.apply(Action.fold()));
    }

    @Test
    void sidePotGoesToTheBestHandAmongThoseWhoCoveredIt() {
        // Hole cards are dealt starting from the small blind (seat 1), two rounds.
        Deck deck = Deck.stacked(Card.parseMany("Kc Qc As Kd Qd Ad 2c 7d 9h Js 3h"));
        HandState h = HandState.deal(stacks(10, 50, 50), 0, deck); // seat 0: AA, seat 1: KK, seat 2: QQ
        h.apply(Action.raiseTo(1000)); // button shoves 10bb
        h.apply(Action.call());
        h.apply(Action.call());
        assertEquals(Street.FLOP, h.street());
        assertEquals(1, h.toAct());
        h.apply(Action.betTo(1000));
        h.apply(Action.call());
        while (!h.isTerminal()) h.apply(Action.check());

        HandResult r = h.result();
        assertTrue(r.showdown());
        assertEquals(3000, r.won()[0], "aces win the main pot");
        assertEquals(2000, r.won()[1], "kings win the side pot");
        assertEquals(0, r.won()[2]);
        assertEquals(0, Arrays.stream(r.net()).sum());
        assertEquals("Pair of aces", r.handNames()[0]);
    }

    @Test
    void uncalledChipsComeBack() {
        Deck deck = Deck.stacked(Card.parseMany("2c 7d 3h 8s"));
        HandState h = HandState.deal(stacks(200, 50), 0, deck);
        h.apply(Action.raiseTo(20000)); // shove 200bb into a 50bb stack
        h.apply(Action.call());
        assertTrue(h.isTerminal());
        HandResult r = h.result();
        assertEquals(0, Arrays.stream(r.net()).sum());
        assertTrue(Math.abs(r.net()[0]) <= 5000, "can never lose more than the covered stack");
    }

    @Test
    void randomPlayConservesChipsAtEveryTableSize() {
        Random rng = new Random(42);
        for (int hand = 0; hand < 3000; hand++) {
            int n = 2 + rng.nextInt(5);
            int[] st = new int[n];
            for (int i = 0; i < n; i++) st[i] = (1 + rng.nextInt(300)) * 100;
            HandState h = HandState.deal(st, rng.nextInt(n), new Deck(rng));
            int steps = 0;
            while (!h.isTerminal()) {
                List<LegalAction> legal = h.legalActions();
                assertFalse(legal.isEmpty());
                h.apply(legal.get(rng.nextInt(legal.size())).toAction());
                assertTrue(++steps < 500, "hand should end");
            }
            HandResult r = h.result();
            assertEquals(0, Arrays.stream(r.net()).sum(), "chips are conserved");
            assertEquals(Arrays.stream(st).sum(), Arrays.stream(r.won()).sum() + sumStacksMinusWon(h, r),
                "every chip is either won or still behind");
            for (int s = 0; s < n; s++) assertTrue(h.stack(s) >= 0);
        }
    }

    private static int sumStacksMinusWon(HandState h, HandResult r) {
        int sum = 0;
        for (int s = 0; s < h.players(); s++) sum += h.stack(s) - r.won()[s];
        return sum;
    }
}
