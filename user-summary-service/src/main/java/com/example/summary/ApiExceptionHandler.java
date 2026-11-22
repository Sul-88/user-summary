package com.example.summary;

import com.example.summary.api.model.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * An exception handler
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String CODE_PROPERTY = "code";

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User " + ex.userId() + " not found");
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    ProblemDetail handleUserServiceUnavailable(UserServiceUnavailableException ex) {
        return ex.timeout()
                ? problem(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.USER_SERVICE_TIMEOUT, "User data is temporarily unavailable")
                : problem(HttpStatus.BAD_GATEWAY, ErrorCode.USER_SERVICE_ERROR, "User data is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        LOGGER.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Unexpected internal error");
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return invalidUserId(ex, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        return invalidUserId(ex, headers, request);
    }

    private ResponseEntity<Object> invalidUserId(Exception ex, HttpHeaders headers, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_USER_ID,
                "User id must be a positive number");
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code.name());
        return problem;
    }

}
