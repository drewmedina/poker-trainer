package com.pokertrainer.server.coach;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the coach off the request thread. The action endpoint returns immediately, the table
 * keeps moving, and the feedback sheet fetches the result when it is ready. If the AI coach is
 * not configured, times out or fails, the rules coach answers instead, so there is always a
 * verdict.
 */
public final class CoachService implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(CoachService.class.getName());

    private final Coach primary;          // may be null
    private final Coach fallback;
    private final ExecutorService pool;
    private final long timeoutSeconds;

    public CoachService(Coach primary, Coach fallback, int threads, long timeoutSeconds) {
        this.primary = primary;
        this.fallback = fallback;
        this.pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "coach");
            t.setDaemon(true);
            return t;
        });
        this.timeoutSeconds = timeoutSeconds;
    }

    /** AI coach from the environment when available, rules otherwise. */
    public static CoachService fromEnv() {
        Coach ai = AnthropicCoach.fromEnv().orElse(null);
        if (ai == null) {
            LOG.info("ANTHROPIC_API_KEY not set; using the rules coach for all feedback");
        }
        return new CoachService(ai, new RulesCoach(), 8, 20);
    }

    /** Rules only. Used by tests and when running offline. */
    public static CoachService rulesOnly() {
        return new CoachService(null, new RulesCoach(), 2, 5);
    }

    public boolean aiEnabled() {
        return primary != null;
    }

    public CompletableFuture<CoachFeedback> submit(DecisionContext context) {
        if (primary == null) {
            return CompletableFuture.completedFuture(safeFallback(context));
        }
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    return primary.review(context);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, pool)
            .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .exceptionally(e -> {
                LOG.log(Level.WARNING, "AI coach failed, using rules coach: " + e.getMessage());
                return safeFallback(context);
            });
    }

    private CoachFeedback safeFallback(DecisionContext context) {
        try {
            return fallback.review(context);
        } catch (Exception e) {
            throw new IllegalStateException("Rules coach failed", e);
        }
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
