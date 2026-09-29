package com.pokertrainer.engine.game;

import java.util.List;

/**
 * How a hand ended. {@code net} and {@code won} are per seat, in chips. {@code handNames} is
 * filled only for seats that reached showdown, null elsewhere.
 */
public record HandResult(int[] net, int[] won, List<Integer> winners, boolean showdown, String[] handNames) {}
