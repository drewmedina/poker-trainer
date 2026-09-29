package com.pokertrainer.server.coach;

import com.pokertrainer.engine.feedback.Verdict;

/**
 * What the player sees after a decision: a verdict and a couple of plain sentences.
 * {@code source} is "ai" for Claude and "rules" for the built-in fallback.
 */
public record CoachFeedback(Verdict verdict, String headline, String explanation, String tip, String source) {}
