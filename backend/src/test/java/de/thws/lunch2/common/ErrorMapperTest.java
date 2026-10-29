package de.thws.lunch2.common;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class ErrorMapperTest {
    @Test
    void expectedErrorPreservesStatusCodeAndMessage() {
        given().when().get("/api/test/errors/expected")
                .then().statusCode(409).contentType(ContentType.JSON)
                .body("", is(Map.of("error", Map.of("code", "X", "message", "msg", "details", List.of()))));
    }

    @Test
    void unexpectedErrorDoesNotExposeDiagnostics() {
        given().when().get("/api/test/errors/unexpected")
                .then().statusCode(500).contentType(ContentType.JSON)
                .body("", is(Map.of("error", Map.of("code", "INTERNAL",
                        "message", "An unexpected error occurred.", "details", List.of()))));
    }

    @Test
    void endpointValidationUsesLastPathNode() {
        given().contentType(ContentType.JSON).body(Map.of("displayName", ""))
                .when().post("/api/test/errors/validation")
                .then().statusCode(400).contentType(ContentType.JSON)
                .body("error.code", is("VALIDATION_FAILED"))
                .body("error.message", is("Please check the highlighted fields."))
                .body("error.details", is(List.of(Map.of("field", "displayName", "message", "must not be blank"))));
    }

    @Test
    void explicitConstraintViolationUsesEnvelope() {
        given().when().get("/api/test/errors/constraint")
                .then().statusCode(400).contentType(ContentType.JSON)
                .body("error.code", is("VALIDATION_FAILED"))
                .body("error.details", is(List.of(Map.of("field", "displayName", "message", "must not be blank"))));
    }
}
