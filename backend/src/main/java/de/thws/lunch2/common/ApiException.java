package de.thws.lunch2.common;

import java.util.List;

/** An expected API failure whose message is safe to show to the caller. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<ErrorResponse.FieldError> details;

    public ApiException(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(int status, String code, String message, List<ErrorResponse.FieldError> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ErrorResponse.FieldError> details() {
        return details;
    }
}
