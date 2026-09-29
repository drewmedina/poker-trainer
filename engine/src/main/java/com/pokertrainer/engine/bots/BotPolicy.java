package com.pokertrainer.engine.bots;

import com.pokertrainer.engine.game.Action;
import com.pokertrainer.engine.game.HandState;

import java.util.Random;

/** Decides an action for whoever is to act in {@code state}. Must return a legal action. */
public interface BotPolicy {
    Action decide(HandState state, Random rng);
}
