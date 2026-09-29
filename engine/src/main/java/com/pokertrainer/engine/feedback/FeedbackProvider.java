package com.pokertrainer.engine.feedback;

import com.pokertrainer.engine.game.Action;
import com.pokertrainer.engine.game.HandState;

/**
 * Grades the decision {@code taken} by the player to act in {@code before}. Called before the
 * action is applied, so {@code before} is exactly what the player saw.
 *
 * <p>Planned implementations, tried in order: a solver lookup for heads-up and single-raised
 * spots that are in the precomputed library, a live turn/river solve, then the heuristic.
 */
public interface FeedbackProvider {
    Feedback evaluate(HandState before, Action taken);
}
