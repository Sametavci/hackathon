package de.thws.lunch2.common;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/** Overrides the framework's deserialization report with the frozen error envelope. */
@Provider
@Priority(Priorities.USER - 1)
public class JsonInputExceptionMapper implements ExceptionMapper<MismatchedInputException> {
    @Override
    public Response toResponse(MismatchedInputException exception) {
        var path = exception.getPath();
        String field = path.isEmpty() ? null : path.getLast().getFieldName();
        List<ErrorResponse.FieldError> details = field == null ? List.of()
                : List.of(new ErrorResponse.FieldError(field, "Please provide a valid value."));
        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("VALIDATION_FAILED", "Please provide a valid JSON request.", details)).build();
    }
}
