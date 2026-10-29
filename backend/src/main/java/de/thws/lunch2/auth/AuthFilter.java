package de.thws.lunch2.auth;

import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.persistence.ParticipantRepository;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

import java.util.regex.Pattern;

@Provider
@Authenticated
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class AuthFilter implements ContainerRequestFilter {
    private static final Pattern BEARER = Pattern.compile("(?i:Bearer) +([A-Za-z0-9_-]{43})");

    private final ParticipantRepository participants;
    private final TokenService tokens;
    private final CurrentParticipant currentParticipant;

    @Inject
    public AuthFilter(ParticipantRepository participants, TokenService tokens, CurrentParticipant currentParticipant) {
        this.participants = participants;
        this.tokens = tokens;
        this.currentParticipant = currentParticipant;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            throw unauthorized();
        }
        var match = BEARER.matcher(authorization);
        if (!match.matches()) {
            throw unauthorized();
        }
        var participant = participants.findByTokenHash(tokens.hash(match.group(1))).orElseThrow(AuthFilter::unauthorized);
        currentParticipant.set(participant);
    }

    private static ApiException unauthorized() {
        return new ApiException(401, "UNAUTHORIZED", "A valid access token is required.");
    }
}
