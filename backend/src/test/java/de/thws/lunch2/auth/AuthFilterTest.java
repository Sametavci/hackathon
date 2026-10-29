package de.thws.lunch2.auth;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthFilterTest {
    @Inject
    ParticipantRepository participants;
    @Inject
    TokenService tokens;
    @Inject
    @ConfigProperty(name = "lunch2.admin-key")
    String adminKey;
    @Inject
    @ConfigProperty(name = "lunch2.data-file")
    String dataFile;

    private String firstToken;
    private String secondToken;
    private Participant first;
    private Participant second;

    @BeforeEach
    void registerFixtures() {
        firstToken = tokens.newToken();
        secondToken = tokens.newToken();
        first = participant(tokens.hash(firstToken));
        second = participant(tokens.hash(secondToken));
        participants.save(first);
        participants.save(second);
    }

    @AfterEach
    void deleteFixtures() {
        participants.delete(first.id());
        participants.delete(second.id());
    }

    @Test
    void missingTokenReturnsUnauthorizedEnvelope() {
        given().when().get("/api/test/auth/participant")
                .then().statusCode(401).contentType(ContentType.JSON)
                .body("", is(unauthorized("A valid access token is required.")));
    }

    @Test
    void malformedAuthorizationAndUnknownTokensAreRejected() {
        for (String authorization : List.of("Basic " + firstToken, "Bearer", "Bearer short",
                "Bearer " + firstToken + " extra", "Bearer " + firstToken + "," + secondToken,
                "Bearer " + tokens.newToken())) {
            given().header("Authorization", authorization).when().get("/api/test/auth/participant")
                    .then().statusCode(401).contentType(ContentType.JSON)
                    .body("", is(unauthorized("A valid access token is required.")));
        }
    }

    @Test
    void validTokenPopulatesBothCurrentParticipantAccessors() {
        given().auth().oauth2(firstToken).when().get("/api/test/auth/participant")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("", is(Map.of("id", first.id(), "participantId", first.id())));
    }

    @Test
    void bearerSchemeIsCaseInsensitive() {
        given().header("Authorization", "bEaReR " + firstToken).when().get("/api/test/auth/participant")
                .then().statusCode(200).body("id", is(first.id()));
    }

    @Test
    void participantBindingAlsoProtectsClasses() {
        given().when().get("/api/test/class-auth").then().statusCode(401);
        given().auth().oauth2(firstToken).when().get("/api/test/class-auth")
                .then().statusCode(200).body("id", is(first.id()));
    }

    @Test
    void participantContextDoesNotLeakBetweenRequestsOrIntoUnboundEndpoints() {
        given().auth().oauth2(firstToken).when().get("/api/test/auth/participant")
                .then().statusCode(200).body("id", is(first.id()));
        given().auth().oauth2(secondToken).when().get("/api/test/auth/participant")
                .then().statusCode(200).body("id", is(second.id()));
        given().when().get("/api/test/auth/participant").then().statusCode(401);
        given().auth().oauth2(firstToken).when().get("/api/test/auth/unpopulated-context")
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
    }

    @Test
    void deletingParticipantRevokesTokenImmediately() {
        given().auth().oauth2(firstToken).when().get("/api/test/auth/participant").then().statusCode(200);
        participants.delete(first.id());
        given().auth().oauth2(firstToken).when().get("/api/test/auth/participant")
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
    }

    @Test
    void onlyHashesArePersistedAndHashCannotBeUsedAsBearerToken() throws IOException {
        String snapshot = Files.readString(Path.of(dataFile));
        assertTrue(snapshot.contains(first.tokenHash()));
        assertFalse(snapshot.contains(firstToken));
        assertFalse(snapshot.contains(secondToken));
        given().auth().oauth2(first.tokenHash()).when().get("/api/test/auth/participant")
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
    }

    @Test
    void adminBindingRequiresExactConfiguredKeyOnMethodsAndClasses() {
        for (String endpoint : List.of("/api/test/auth/admin", "/api/test/class-admin")) {
            given().when().get(endpoint).then().statusCode(401).contentType(ContentType.JSON)
                    .body("", is(unauthorized("A valid admin key is required.")));
            for (String invalid : List.of("invalid-key", adminKey + "x", "x" + adminKey)) {
                given().header("X-Admin-Key", invalid).when().get(endpoint)
                        .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
            }
            given().header("X-Admin-Key", adminKey).when().get(endpoint)
                    .then().statusCode(200).body("ok", is(true));
        }
    }

    @Test
    void participantAndAdminCredentialsAreSeparate() {
        given().auth().oauth2(firstToken).when().get("/api/test/auth/admin").then().statusCode(401);
        given().header("X-Admin-Key", adminKey).when().get("/api/test/auth/participant").then().statusCode(401);
    }

    @Test
    void nameBoundFiltersLeavePublicEndpointsAccessible() {
        given().when().get("/api/test/auth/public").then().statusCode(200).body("ok", is(true));
        given().header("Authorization", "invalid").header("X-Admin-Key", "invalid")
                .when().get("/api/ping").then().statusCode(200).body("ok", is(true));
    }

    private static Map<String, Object> unauthorized(String message) {
        return Map.of("error", Map.of("code", "UNAUTHORIZED", "message", message, "details", List.of()));
    }

    private static Participant participant(String tokenHash) {
        Instant now = Instant.now();
        return new Participant(UUID.randomUUID().toString(), tokenHash, "Test", Category.DOMESTIC, Campus.WUERZBURG,
                List.of("en"), List.of("travel"), List.of("2026-11-03T12:15"), null, MatchStatus.WAITING,
                null, ReasonCode.WAITING_FOR_NEXT_ROUND, null, now, now);
    }
}
