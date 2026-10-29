package de.thws.lunch2.auth;

import de.thws.lunch2.common.ApiException;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Provider
@AdminOnly
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class AdminFilter implements ContainerRequestFilter {
    private final byte[] adminKey;

    @Inject
    public AdminFilter(@ConfigProperty(name = "lunch2.admin-key") String adminKey) {
        this.adminKey = adminKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String supplied = requestContext.getHeaderString("X-Admin-Key");
        if (supplied == null || adminKey.length == 0
                || !MessageDigest.isEqual(adminKey, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(401, "UNAUTHORIZED", "A valid admin key is required.");
        }
    }
}
