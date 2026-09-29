package com.pokertrainer.server.coach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pokertrainer.engine.feedback.Verdict;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Asks Claude for feedback through the Messages API. The reply is forced through a tool call
 * with a fixed schema at temperature 0, so the shape and tone stay consistent from decision to
 * decision.
 *
 * <p>Configuration (environment variables):
 * <ul>
 *   <li>{@code ANTHROPIC_API_KEY}: required to enable the AI coach</li>
 *   <li>{@code COACH_MODEL}: defaults to a fast model, since this runs on every decision</li>
 *   <li>{@code ANTHROPIC_BASE_URL}: defaults to https://api.anthropic.com (tests point it at a fake)</li>
 * </ul>
 */
public final class AnthropicCoach implements Coach {
    public static final String DEFAULT_MODEL = "claude-haiku-4-5-20251001";
    private static final String TOOL = "give_feedback";

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public AnthropicCoach(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** The AI coach when an API key is configured, empty otherwise. */
    public static Optional<AnthropicCoach> fromEnv() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) return Optional.empty();
        String model = System.getenv().getOrDefault("COACH_MODEL", DEFAULT_MODEL);
        String base = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://api.anthropic.com");
        return Optional.of(new AnthropicCoach(key, model, base));
    }

    public String model() {
        return model;
    }

    @Override
    public CoachFeedback review(DecisionContext context) throws IOException, InterruptedException {
        String body = json.writeValueAsString(requestBody(context));
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
            .timeout(Duration.ofSeconds(20))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            String snippet = res.body().length() > 300 ? res.body().substring(0, 300) : res.body();
            throw new IOException("Anthropic API returned " + res.statusCode() + ": " + snippet);
        }
        return parse(json.readTree(res.body()));
    }

    ObjectNode requestBody(DecisionContext context) {
        ObjectNode root = json.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", 400);
        root.put("temperature", 0);
        root.put("system", CoachPrompt.SYSTEM);

        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", CoachPrompt.user(context));

        ObjectNode tool = root.putArray("tools").addObject();
        tool.put("name", TOOL);
        tool.put("description", "Record the coaching feedback for the player's decision.");
        ObjectNode schema = tool.putObject("input_schema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode verdict = props.putObject("verdict");
        verdict.put("type", "string");
        verdict.putArray("enum").add("GOOD").add("INACCURACY").add("MISTAKE");
        props.putObject("headline").put("type", "string").put("description", "2 to 4 words");
        props.putObject("explanation").put("type", "string").put("description", "1 or 2 sentences, at most 45 words");
        props.putObject("tip").put("type", "string").put("description", "one rule of thumb, at most 20 words");
        schema.putArray("required").add("verdict").add("headline").add("explanation").add("tip");

        ObjectNode choice = root.putObject("tool_choice");
        choice.put("type", "tool");
        choice.put("name", TOOL);
        return root;
    }

    static CoachFeedback parse(JsonNode response) throws IOException {
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && TOOL.equals(block.path("name").asText())) {
                JsonNode in = block.path("input");
                Verdict v;
                try {
                    v = Verdict.valueOf(in.path("verdict").asText());
                } catch (IllegalArgumentException e) {
                    throw new IOException("Coach returned an unknown verdict: " + in.path("verdict"));
                }
                if (v == Verdict.INFO) throw new IOException("Coach returned INFO");
                return new CoachFeedback(v, clean(in.path("headline").asText()), clean(in.path("explanation").asText()),
                    clean(in.path("tip").asText()), "ai");
            }
        }
        throw new IOException("No give_feedback tool call in the response");
    }

    /** House style: no em dashes, trimmed. */
    static String clean(String s) {
        return s.replace(" — ", ", ").replace("—", ", ").trim();
    }
}
