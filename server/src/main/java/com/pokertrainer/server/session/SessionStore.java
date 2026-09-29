package com.pokertrainer.server.session;

import com.pokertrainer.engine.bots.BotProfile;
import com.pokertrainer.engine.feedback.HeuristicFeedbackProvider;
import com.pokertrainer.server.api.Dto;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sessions. Fine for local development and a single server. Move to Postgres
 * (sessions, hands, decisions) in milestone 4, when the review and leak screens need history.
 */
public final class SessionStore {
    private final Map<String, TrainingSession> sessions = new ConcurrentHashMap<>();

    public Dto.LobbyView lobby() {
        List<Dto.BotView> bots = Arrays.stream(BotProfile.values())
            .map(b -> new Dto.BotView(b.id, b.displayName, b.style, b.description))
            .toList();
        Map<String, List<String>> presets = new LinkedHashMap<>();
        Lineups.PRESETS.forEach((k, v) -> presets.put(k, v.stream().map(b -> b.id).toList()));
        return new Dto.LobbyView(bots, presets);
    }

    public Dto.SessionView create(Dto.CreateSessionRequest req) {
        TableFormat format = req.format() == null ? TableFormat.SIX_MAX : parseFormat(req.format());
        int stackBb = req.stackBb() == null ? 100 : req.stackBb();
        if (stackBb < 20 || stackBb > 400) throw new IllegalArgumentException("Stacks must be 20 to 400bb");
        List<BotProfile> lineup = Lineups.resolve(format, req.preset(), req.lineup());
        Random rng = req.seed() == null ? new Random() : new Random(req.seed());
        String id = UUID.randomUUID().toString();
        TrainingSession s = new TrainingSession(id, format, lineup, stackBb, rng,
            new HeuristicFeedbackProvider(new Random(rng.nextLong()), 2500));
        sessions.put(id, s);
        return s.dealNext();
    }

    public TrainingSession get(String id) {
        TrainingSession s = sessions.get(id);
        if (s == null) throw new NoSuchElementException("No session " + id);
        return s;
    }

    private static TableFormat parseFormat(String f) {
        try {
            return TableFormat.valueOf(f.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown format: " + f);
        }
    }
}
