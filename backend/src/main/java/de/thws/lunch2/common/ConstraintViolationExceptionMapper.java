package de.thws.lunch2.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Comparator;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        var details = exception.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        lastNode(violation.getPropertyPath()), violation.getMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field)
                        .thenComparing(ErrorResponse.FieldError::message))
                .toList();
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("VALIDATION_FAILED", "Please check the highlighted fields.", details))
                .build();
    }

    private static String lastNode(Path path) {
        String field = "";
        for (Path.Node node : path) {
            field = node.getName();
        }
        return field == null ? "" : field;
    }
}
