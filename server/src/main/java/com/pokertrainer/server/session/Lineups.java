package com.pokertrainer.server.session;

import com.pokertrainer.engine.bots.BotProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.pokertrainer.engine.bots.BotProfile.*;

/** Table lineup presets, matching the lobby design. */
public final class Lineups {
    public static final Map<String, List<BotProfile>> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("mixed", List.of(VERA, REX, SOL, NELL, OTIS));
        PRESETS.put("soft", List.of(OTIS, OTIS, NELL, REX, NELL));
        PRESETS.put("tough", List.of(VERA, VERA, SOL, SOL, VERA));
    }

    private Lineups() {}

    /** Resolves the bots for a table. Explicit ids win over a preset. Heads-up uses the first bot. */
    public static List<BotProfile> resolve(TableFormat format, String preset, List<String> ids) {
        List<BotProfile> bots = new ArrayList<>();
        if (ids != null && !ids.isEmpty()) {
            for (String id : ids) {
                bots.add(BotProfile.byId(id).orElseThrow(() -> new IllegalArgumentException("Unknown bot: " + id)));
            }
        } else {
            String key = preset == null ? "mixed" : preset.toLowerCase();
            List<BotProfile> p = PRESETS.get(key);
            if (p == null) throw new IllegalArgumentException("Unknown preset: " + preset);
            bots.addAll(format == TableFormat.HEADS_UP ? List.of(REX) : p);
        }
        int needed = format.seats - 1;
        if (bots.size() < needed) {
            throw new IllegalArgumentException("Need " + needed + " bots for " + format);
        }
        return List.copyOf(bots.subList(0, needed));
    }
}
