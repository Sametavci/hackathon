package de.thws.lunch2.cards;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.thws.lunch2.common.CatalogService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GeminiCardGenerator implements ConversationCardGenerator {
    private static final String GENERIC = "generic";
    private final ObjectMapper mapper;
    private final CatalogService catalog;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient client;
    private final URI endpoint;

    @Inject
    public GeminiCardGenerator(ObjectMapper mapper, CatalogService catalog,
            @ConfigProperty(name = "lunch2.ai.api-key") Optional<String> apiKey,
            @ConfigProperty(name = "lunch2.ai.model") String model,
            @ConfigProperty(name = "lunch2.ai.timeout-ms") long timeoutMs) {
        this(mapper, catalog, apiKey.orElse(""), Duration.ofMillis(Math.max(1, Math.min(timeoutMs, 8000))),
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.max(1, Math.min(timeoutMs, 8000)))).build(),
                endpoint(model));
    }

    GeminiCardGenerator(ObjectMapper mapper, CatalogService catalog, String apiKey,
            Duration timeout, HttpClient client, URI endpoint) {
        this.mapper = mapper;
        this.catalog = catalog;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.client = client;
        this.endpoint = endpoint;
    }

    private static URI endpoint(String model) {
        if (!model.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Invalid Gemini model name");
        return URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent");
    }

    @Override
    public boolean available() {
        return !apiKey.isBlank();
    }

    @Override
    public List<GeneratedCard> generate(List<String> interestIds, String languageCode, List<String> avoid) throws Exception {
        if (apiKey.isBlank()) return List.of();
        if (!catalog.hasLanguage(languageCode)) throw new IllegalArgumentException("Unknown card language");
        List<String> topics = new ArrayList<>(interestIds.stream().distinct().filter(catalog::hasInterest).toList());
        StringBuilder interests = new StringBuilder();
        for (String id : topics) interests.append(interests.isEmpty() ? "" : ", ").append(id).append(" (").append(catalog.interestLabel(id)).append(')');
        topics.add(GENERIC);
        String prompt = "You write icebreaker questions for a lunch table of 4 university students in Germany: "
                + "two have lived in Germany for a while, two are new to the country. "
                + "Their interests, most shared first: " + (interests.isEmpty() ? "none given" : interests) + ". "
                + "Write 5 different, friendly, open questions in " + catalog.languageLabel(languageCode) + " (" + languageCode + "). "
                + "Cover as many different interests as possible and set each question's topic to the interest ID it is about; "
                + "at most one question may use the topic \"generic\". Questions must be easy and fun for everyone to answer "
                + "and invite stories, tips or opinions. Never ask about religion, politics, health, money, relationships, "
                + "family status, appearance, or anything that makes someone's origin feel strange. Max 120 characters each."
                + (avoid.isEmpty() ? "" : " Do not repeat or rephrase any of these questions: "
                        + mapper.writeValueAsString(avoid.stream().limit(20).toList()) + ".");
        // JSON mode plus a schema keeps the reply parseable; no thinking budget so the output is never truncated.
        Map<String, Object> schema = Map.of("type", "ARRAY", "items", Map.of("type", "OBJECT",
                "properties", Map.of("text", Map.of("type", "STRING"), "topic", Map.of("type", "STRING", "enum", topics)),
                "required", List.of("text", "topic")));
        String payload = mapper.writeValueAsString(Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("maxOutputTokens", 1024, "temperature", 1.0,
                        "responseMimeType", "application/json", "responseSchema", schema,
                        "thinkingConfig", Map.of("thinkingBudget", 0))));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("x-goog-api-key", apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> response;
        try {
            response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException exception) {
            pending.cancel(true);
            throw exception;
        }
        if (response.statusCode() != 200) throw new IOException("Gemini request failed with HTTP " + response.statusCode());
        var candidate = mapper.readTree(response.body()).path("candidates").path(0);
        if (!"STOP".equals(candidate.path("finishReason").asText())) throw new IOException("Incomplete Gemini response");
        StringBuilder text = new StringBuilder();
        for (var part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) text.append(part.path("text").textValue());
        }
        var array = mapper.readTree(text.toString());
        if (array == null || !array.isArray() || array.size() < 3 || array.size() > 6) throw new IOException("Invalid Gemini card array");
        List<GeneratedCard> cards = new ArrayList<>();
        for (var value : array) {
            if (!value.path("text").isTextual()) throw new IOException("Invalid Gemini question");
            String topic = value.path("topic").asText(GENERIC);
            cards.add(new GeneratedCard(value.path("text").textValue(), topics.contains(topic) ? topic : GENERIC));
        }
        return List.copyOf(cards);
    }
}
