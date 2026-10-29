---
name: backend-endpoint
description: Implement or change a Lunch² Quarkus REST endpoint (resource, service, DTOs, validation, errors, RestAssured tests). Use for any backend HACK-1xx/2xx ticket that adds or modifies an /api endpoint.
---

# Lunch² backend endpoint

1. Open the endpoint in `docs/API_CONTRACT.md` §3 and copy its exact path, status codes, field names and error codes. Do not deviate.
2. Place code in your owned package `backend/src/main/java/de/thws/lunch2/<module>/`:
   - `XxxResource` – `@Path`, `@Produces/@Consumes(APPLICATION_JSON)`, `@Authenticated` or `@AdminOnly` as the contract says, no logic.
   - `XxxService` – `@ApplicationScoped`, constructor injection, all rules and repository calls.
   - DTOs as Java `record`s named `XxxRequest` / `XxxResponse`; Bean Validation (`@NotNull`, `@Size`, `@Pattern`) on request records; resource parameter annotated `@Valid`.
3. Errors: `throw new ApiException(status, "CODE", "User-facing sentence.")` with codes from API_CONTRACT §1. Never return raw exceptions.
4. Auth: get the caller via injected `CurrentParticipant`; membership checks return 403 `FORBIDDEN`.
5. Privacy: build response DTOs field by field; never serialise domain objects directly (they contain `tokenHash`).
6. Strings: `toUpperCase(Locale.ROOT)` / `toLowerCase(Locale.ROOT)` only.
7. Tests in `backend/src/test/java/de/thws/lunch2/<module>/XxxResourceTest.java` with `@QuarkusTest` + RestAssured: one test per acceptance criterion, including the error cases. Register a participant through `POST /api/participants` to get a token.
8. Verify: `cd backend && ./mvnw -q verify`; then `./mvnw quarkus:dev` and `curl` the sample payload from the contract.
