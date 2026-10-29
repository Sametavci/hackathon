package de.thws.lunch2.cards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import de.thws.lunch2.common.CatalogService;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeminiCardGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CatalogService catalog = new CatalogService("Würzburg", "Schweinfurt");
    private HttpServer server;
    private HttpClient client;
    private java.util.concurrent.ExecutorService executor;
    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicReference<String> header = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicInteger count = new AtomicInteger();
    private final CountDownLatch releaseSlowRequest = new CountDownLatch(1);
    private volatile int status = 200;
    private volatile boolean slow;

    @BeforeEach
    void startStub() throws Exception {
        body.set(envelope("[{\"text\":\"One?\",\"topic\":\"tech\"},{\"text\":\"Two?\",\"topic\":\"travel\"},{\"text\":\"Three?\",\"topic\":\"generic\"},{\"text\":\"Four?\",\"topic\":\"unknown\"},{\"text\":\"Five?\",\"topic\":\"tech\"}]"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/generate", exchange -> {
            count.incrementAndGet();
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            header.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            if (slow) {
                try { releaseSlowRequest.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopStub() {
        releaseSlowRequest.countDown();
        server.stop(0);
        executor.shutdownNow();
        client.close();
    }

    @Test
    void sendsOnlyInterestsLanguageAndSeenQuestionsWithKeyInHeaderAndParsesTopics() throws Exception {
        var cards = generator("test-key", Duration.ofSeconds(2)).generate(List.of("tech", "travel"), "en", List.of("Seen before?"));
        assertEquals(List.of("One?", "Two?", "Three?", "Four?", "Five?"), cards.stream().map(ConversationCardGenerator.GeneratedCard::text).toList());
        assertEquals(List.of("tech", "travel", "generic", "generic", "tech"), cards.stream().map(ConversationCardGenerator.GeneratedCard::topic).toList());
        assertEquals("test-key", header.get());
        var payload = mapper.readTree(request.get());
        assertEquals(Set.of("contents", "generationConfig"), mapper.convertValue(payload, Map.class).keySet());
        var config = payload.path("generationConfig");
        assertEquals(1024, config.path("maxOutputTokens").intValue());
        assertEquals("application/json", config.path("responseMimeType").textValue());
        assertEquals(0, config.path("thinkingConfig").path("thinkingBudget").intValue());
        assertEquals(List.of("tech", "travel", "generic"), mapper.convertValue(
                config.path("responseSchema").path("items").path("properties").path("topic").path("enum"), List.class));
        String prompt = payload.path("contents").path(0).path("parts").path(0).path("text").textValue();
        assertTrue(prompt.contains("Technology"));
        assertTrue(prompt.contains("Travel"));
        assertTrue(prompt.contains("(en)"));
        assertTrue(prompt.contains("Seen before?"));
        assertFalse(prompt.contains("WUERZBURG"));
        assertFalse(request.get().contains("test-key"));
    }

    @Test
    void unknownInterestIdsAreNotSentAsTopics() throws Exception {
        generator("k", Duration.ofSeconds(2)).generate(List.of("travel", "not-a-real-interest"), "en", List.of());
        var topics = mapper.readTree(request.get()).path("generationConfig").path("responseSchema").path("items").path("properties").path("topic").path("enum");
        assertEquals(List.of("travel", "generic"), mapper.convertValue(topics, List.class));
    }

    @Test
    void missingKeyMakesNoHttpRequest() throws Exception {
        var none = generator("", Duration.ofSeconds(1));
        assertFalse(none.available());
        assertEquals(List.of(), none.generate(List.of("travel"), "en", List.of()));
        assertTrue(generator("k", Duration.ofSeconds(1)).available());
        assertEquals(0, count.get());
    }

    @Test
    void providerErrorsAreRejectedWithoutExposingTheResponseBody() {
        status = 429;
        body.set("secret provider error details");
        var error = assertThrows(java.io.IOException.class,
                () -> generator("test", Duration.ofSeconds(2)).generate(List.of(), "en", List.of()));
        assertFalse(error.getMessage().contains("secret"));
    }

    @Test
    void malformedBlockedTruncatedAndNonStringResponsesAreRejected() throws Exception {
        for (String invalid : List.of("not json", "{}", "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}",
                envelope("[{\"text\":\"only one\",\"topic\":\"generic\"}]"), envelope("[\"plain\",\"strings\",\"rejected\"]"),
                envelope("[{\"text\":1},{\"text\":\"b\"},{\"text\":\"c\"}]"), envelope("```json\n[]\n```"))) {
            body.set(invalid);
            assertThrows(Exception.class, () -> generator("test", Duration.ofSeconds(2)).generate(List.of(), "en", List.of()));
        }
    }

    @Test
    void slowProviderIsCancelledWithinTheConfiguredOverallTimeout() {
        slow = true;
        long started = System.nanoTime();
        assertThrows(Exception.class, () -> generator("test", Duration.ofMillis(150)).generate(List.of(), "en", List.of()));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1500);
    }

    private GeminiCardGenerator generator(String key, Duration timeout) {
        return new GeminiCardGenerator(mapper, catalog, key, timeout, client,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/generate"));
    }

    private String envelope(String array) throws Exception {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content",
                Map.of("parts", List.of(Map.of("text", array)))))));
    }
}
