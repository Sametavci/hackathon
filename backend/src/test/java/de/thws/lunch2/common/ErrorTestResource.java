package de.thws.lunch2.common;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Only compiled into the test application. */
@Path("/api/test/errors")
@Produces(MediaType.APPLICATION_JSON)
public class ErrorTestResource {
    private final Validator validator;

    @Inject
    public ErrorTestResource(Validator validator) {
        this.validator = validator;
    }

    @GET
    @Path("/expected")
    public void expected() {
        throw new ApiException(409, "X", "msg");
    }

    @GET
    @Path("/unexpected")
    public void unexpected() {
        throw new IllegalStateException("Private diagnostic containing sensitive data");
    }

    @POST
    @Path("/validation")
    @Consumes(MediaType.APPLICATION_JSON)
    public void validation(@Valid ValidationRequest request) {
    }

    @GET
    @Path("/constraint")
    public void constraint() {
        throw new ConstraintViolationException(validator.validate(new ValidationRequest("")));
    }

    public record ValidationRequest(@NotBlank(message = "must not be blank") String displayName) {}
}
