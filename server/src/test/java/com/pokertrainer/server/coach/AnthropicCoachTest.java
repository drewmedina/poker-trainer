package com.pokertrainer.server.coach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokertrainer.engine.feedback.Verdict;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real HTTP client against a fake Messages API on localhost. */
class AnthropicCoachTest {

    private static final DecisionContext SPOT = RulesCoachTest.spot("RIVER", 2, 5, false, "FOLD", "Fold",
        RulesCoachTest.ref(0.6, null, "MISTAKE"), "Aggressive");

    private interface Handler { String respond(String requestBody); }

    private static <T> T withServer(int status, Handler handler, java.util.function.Function<String, T> test) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = handler.respond(body).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("content-type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        try {
            return test.apply("http://127.0.0.1:" + server.getAddress().getPort());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsAForcedToolCallAndParsesTheReply() throws Exception {
        AtomicReference<String> sent = new AtomicReference<>();
        String reply = """
            {"content":[{"type":"tool_use","id":"t1","name":"give_feedback","input":{
              "verdict":"MISTAKE","headline":"Too tight a fold",
              "explanation":"You had a strong hand — folding gave up the pot.","tip":"Call more vs bluffers."}}]}
            """;
        CoachFeedback f = withServer(200, body -> { sent.set(body); return reply; }, base -> {
            try {
                return new AnthropicCoach("test-key", AnthropicCoach.DEFAULT_MODEL, base).review(SPOT);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(Verdict.MISTAKE, f.verdict());
        assertEquals("Too tight a fold", f.headline());
        assertFalse(f.explanation().contains("—"), "em dashes are stripped");
        assertEquals("ai", f.source());

        JsonNode req = new ObjectMapper().readTree(sent.get());
        assertEquals("give_feedback", req.path("tool_choice").path("name").asText());
        assertEquals(0, req.path("temperature").asInt());
        assertTrue(req.path("messages").get(0).path("content").asText().contains("Hero chose: Fold"));
    }

    @Test
    void errorsSurfaceSoTheServiceCanFallBack() throws Exception {
        Exception e = withServer(529, body -> "{\"type\":\"error\"}", base -> {
            try {
                new AnthropicCoach("k", "m", base).review(SPOT);
                return null;
            } catch (Exception ex) {
                return ex;
            }
        });
        assertNotNull(e);
        assertTrue(e.getMessage().contains("529"));
    }

    @Test
    void rejectsRepliesWithoutTheTool() {
        assertThrows(java.io.IOException.class, () ->
            AnthropicCoach.parse(new ObjectMapper().readTree("{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}")));
    }
}
