package com.pokertrainer.engine.bots;

import java.util.Arrays;
import java.util.Optional;

/**
 * Personality knobs for the placeholder bots. These are the leaks each bot is meant to have.
 * Once solver strategies exist, a profile becomes a set of node-locked adjustments to a solver
 * strategy instead of these rough numbers, but the ids and descriptions stay.
 *
 * <ul>
 *   <li>{@code callSlack}: subtracted from the equity a call needs. Positive calls too much.</li>
 *   <li>{@code valueThreshold}: equity needed to bet or raise for value.</li>
 *   <li>{@code aggression}: how often it bets or raises when it has value.</li>
 *   <li>{@code bluffRate}: how often it bets with weak hands when checked to.</li>
 *   <li>{@code preflopTop}: roughly the share of hands it plays preflop.</li>
 * </ul>
 */
public enum BotProfile {
    VERA("vera", "Vera", "Balanced", "Close to solver strategy. Few leaks.", 0.00, 0.62, 0.60, 0.18, 0.26),
    OTIS("otis", "Otis", "Calling station", "Calls too much, rarely bluffs.", 0.14, 0.72, 0.30, 0.04, 0.48),
    NELL("nell", "Nell", "Tight", "Folds too much. Big bets mean it.", -0.08, 0.70, 0.45, 0.04, 0.16),
    REX("rex", "Rex", "Aggressive", "Over-bluffs turns and rivers.", 0.02, 0.58, 0.80, 0.34, 0.34),
    SOL("sol", "Sol", "Solid regular", "Tight-aggressive with small leaks.", -0.02, 0.62, 0.65, 0.14, 0.22);

    public final String id;
    public final String displayName;
    public final String style;
    public final String description;
    public final double callSlack;
    public final double valueThreshold;
    public final double aggression;
    public final double bluffRate;
    public final double preflopTop;

    BotProfile(String id, String displayName, String style, String description,
               double callSlack, double valueThreshold, double aggression, double bluffRate, double preflopTop) {
        this.id = id;
        this.displayName = displayName;
        this.style = style;
        this.description = description;
        this.callSlack = callSlack;
        this.valueThreshold = valueThreshold;
        this.aggression = aggression;
        this.bluffRate = bluffRate;
        this.preflopTop = preflopTop;
    }

    public static Optional<BotProfile> byId(String id) {
        return Arrays.stream(values()).filter(p -> p.id.equalsIgnoreCase(id)).findFirst();
    }
}
