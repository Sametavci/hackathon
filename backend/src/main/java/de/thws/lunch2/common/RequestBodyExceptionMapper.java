package de.thws.lunch2.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class RequestBodyExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Override
    public Response toResponse(WebApplicationException exception) {
        // Quarkus wraps JSON syntax errors in an HTTP 400 WebApplicationException.
        if (exception.getResponse().getStatus() == 400 && exception.getCause() instanceof JsonProcessingException) {
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("VALIDATION_FAILED", "Please provide a valid JSON request.", List.of()))
                    .build();
        }
        return exception.getResponse();
    }
}
