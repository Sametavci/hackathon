package de.thws.lunch2.common;

import java.util.List;

public record ErrorResponse(ErrorBody error) {
    public ErrorResponse(String code, String message, List<FieldError> details) {
        this(new ErrorBody(code, message, List.copyOf(details)));
    }

    public record ErrorBody(String code, String message, List<FieldError> details) {}

    public record FieldError(String field, String message) {}
}
