package com.pokertrainer.engine.feedback;

public enum Verdict {
    GOOD, INACCURACY, MISTAKE,
    /** No grade, only context (equity, pot odds). Used where no model can judge the spot yet. */
    INFO;

    /** Thresholds in big blinds of EV lost. Tune once real solver numbers exist. */
    public static Verdict fromLossBb(double lossBb) {
        if (lossBb < 0.25) return GOOD;
        if (lossBb < 1.0) return INACCURACY;
        return MISTAKE;
    }
}
