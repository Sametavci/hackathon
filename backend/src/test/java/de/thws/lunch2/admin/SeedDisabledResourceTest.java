package de.thws.lunch2.admin;

import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(SeedDisabledResourceTest.DisabledDemo.class)
class SeedDisabledResourceTest {
    @Inject DataAdmin data;
    @Inject ParticipantRepository participants;
    @Inject @ConfigProperty(name = "lunch2.admin-key") String adminKey;

    @Test
    void disabledDemoRejectsSeedEvenWithAValidAdminKey() {
        data.wipeAll();
        given().header("X-Admin-Key", adminKey).when().post("/api/admin/seed")
                .then().statusCode(403).body("error.code", is("FORBIDDEN"));
        assertTrue(participants.findAll().isEmpty());
    }

    public static class DisabledDemo implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("lunch2.demo-mode", "false");
        }
    }

    @Test
    void disabledDemoRejectsCompletionBeforeAnyMutation() {
        data.wipeAll();
        given().header("X-Admin-Key", adminKey).post("/api/admin/match?fillWithDemo=true")
                .then().statusCode(403).body("error.code", is("FORBIDDEN"));
        assertTrue(participants.findAll().isEmpty());
    }
}
