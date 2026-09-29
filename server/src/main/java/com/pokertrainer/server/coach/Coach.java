package com.pokertrainer.server.coach;

/** Judges one decision. Implementations may block (network); CoachService runs them off-thread. */
public interface Coach {
    CoachFeedback review(DecisionContext context) throws Exception;
}
