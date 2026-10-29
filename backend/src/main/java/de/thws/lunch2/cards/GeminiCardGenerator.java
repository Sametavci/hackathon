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
    public List<String> generate(List<String> interestIds, String languageCode) throws Exception {
        if (apiKey.isBlank()) return List.of();
        if (!catalog.hasLanguage(languageCode)) throw new IllegalArgumentException("Unknown card language");
        List<String> labels = interestIds.stream().distinct().map(catalog::interestLabel).toList();
        String prompt = "Write 5 friendly, inclusive lunch icebreaker questions for 4 university students "
                + "(mix of locals and internationals in Germany) who share these interests: "
                + mapper.writeValueAsString(labels) + ". Language: " + languageCode
                + ". No questions about religion, politics, health, money, family status, appearance or origin-shaming. "
                + "Max 120 characters each. Reply with a JSON array of strings only.";
        String payload = mapper.writeValueAsString(Map.of("contents", List.of(Map.of("role", "user", "parts",
                List.of(Map.of("text", prompt)))), "generationConfig", Map.of("maxOutputTokens", 400)));
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
        if (response.statusCode() != 200) throw new IOException("Gemini request failed");
        var candidate = mapper.readTree(response.body()).path("candidates").path(0);
        if (!"STOP".equals(candidate.path("finishReason").asText())) throw new IOException("Incomplete Gemini response");
        StringBuilder text = new StringBuilder();
        for (var part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) text.append(part.path("text").textValue());
        }
        var array = mapper.readTree(text.toString());
        if (array == null || !array.isArray() || array.size() < 3 || array.size() > 6) throw new IOException("Invalid Gemini card array");
        List<String> questions = new ArrayList<>();
        for (var value : array) {
            if (!value.isTextual()) throw new IOException("Invalid Gemini question");
            questions.add(value.textValue());
        }
        return List.copyOf(questions);
    }
}
