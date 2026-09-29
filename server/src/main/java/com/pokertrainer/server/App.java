package com.pokertrainer.server;

import com.pokertrainer.server.api.Dto;
import com.pokertrainer.server.session.SessionStore;
import io.javalin.Javalin;

import java.util.NoSuchElementException;

/**
 * HTTP entry point. Kept deliberately thin: every route hands off to SessionStore or
 * TrainingSession, which have no web dependencies and are tested directly.
 *
 * <p>The web app's dev server proxies /api to this port, so no CORS setup is needed locally.
 */
public final class App {
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        SessionStore store = new SessionStore();

        Javalin app = Javalin.create();

        app.get("/api/health", ctx -> ctx.result("ok"));
        app.get("/api/lobby", ctx -> ctx.json(store.lobby()));
        app.post("/api/sessions", ctx -> ctx.json(store.create(ctx.bodyAsClass(Dto.CreateSessionRequest.class))));
        app.get("/api/sessions/{id}", ctx -> ctx.json(store.get(ctx.pathParam("id")).view()));
        app.post("/api/sessions/{id}/hands", ctx -> ctx.json(store.get(ctx.pathParam("id")).dealNext()));
        app.post("/api/sessions/{id}/actions", ctx ->
            ctx.json(store.get(ctx.pathParam("id")).act(ctx.bodyAsClass(Dto.ActionRequest.class))));

        app.exception(IllegalArgumentException.class, (e, ctx) -> ctx.status(400).json(new Dto.ErrorView(e.getMessage())));
        app.exception(IllegalStateException.class, (e, ctx) -> ctx.status(409).json(new Dto.ErrorView(e.getMessage())));
        app.exception(NoSuchElementException.class, (e, ctx) -> ctx.status(404).json(new Dto.ErrorView(e.getMessage())));

        app.start(port);
    }
}
