package com.pokertrainer.engine.feedback;

import java.util.Map;

/**
 * Feedback on one decision. Every number here comes from a model (solver or heuristic), never
 * from the language model. The coach layer only turns this into prose.
 *
 * @param evLossBb     EV given up versus the best option, in big blinds (0 when the pick was best)
 * @param frequencies  action label to frequency, when a strategy is known; empty otherwise
 * @param estimate     true when this is not solver output (heuristics, multiway spots)
 * @param source       "solver", "heuristic", ...
 */
public record Feedback(
    Verdict verdict,
    double evLossBb,
    String headline,
    String detail,
    double equity,
    double potOdds,
    Map<String, Double> frequencies,
    boolean multiway,
    boolean estimate,
    String source) {}
