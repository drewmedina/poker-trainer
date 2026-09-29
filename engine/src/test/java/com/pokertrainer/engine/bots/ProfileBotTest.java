package com.pokertrainer.engine.bots;

import com.pokertrainer.engine.core.Deck;
import com.pokertrainer.engine.game.HandState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ProfileBotTest {

    @Test
    void botsOnlyMakeLegalMovesAndHandsFinish() {
        Random rng = new Random(11);
        BotProfile[] profiles = BotProfile.values();
        int showdowns = 0;
        for (int hand = 0; hand < 150; hand++) {
            int n = hand % 2 == 0 ? 6 : 2;
            int[] stacks = new int[n];
            Arrays.fill(stacks, 10_000);
            HandState h = HandState.deal(stacks, hand % n, new Deck(rng));
            while (!h.isTerminal()) {
                ProfileBot bot = new ProfileBot(profiles[h.toAct() % profiles.length]);
                h.apply(bot.decide(h, rng)); // apply() throws on anything illegal
            }
            assertEquals(0, Arrays.stream(h.result().net()).sum());
            if (h.result().showdown()) showdowns++;
        }
        assertTrue(showdowns > 0, "some hands should reach showdown");
    }

    @Test
    void profilesAreFoundById() {
        assertEquals(BotProfile.REX, BotProfile.byId("rex").orElseThrow());
        assertTrue(BotProfile.byId("nobody").isEmpty());
    }
}
