/*
 * File: ApiExceptionHandler.java
 * Purpose: Converts expected failures into RFC 9457 problem details without stack leakage.
 * Symbols: exception-specific handlers and problem() builder; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.web;

import java.net.URI;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import jakarta.validation.ConstraintViolationException;

/** Central error mapping keeps API failure shapes consistent. */
@RestControllerAdvice
public final class ApiExceptionHandler {
    /** @return a 400 response for invalid bodies, cursors, or connector names */
    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail badRequest(Exception exception) {
        String detail = exception instanceof IllegalArgumentException
                ? exception.getMessage() : "Request validation failed";
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    /** @return a 404 response for unknown resources */
    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    /** @return a 409 response for invalid state transitions or retry timing */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException exception) {
        return problem(HttpStatus.CONFLICT, "State conflict", exception.getMessage());
    }

    /** @return a 403 response for viewer mutation attempts */
    @ExceptionHandler(ForbiddenOperationException.class)
    ProblemDetail forbidden(ForbiddenOperationException exception) {
        return problem(HttpStatus.FORBIDDEN, "Operator role required", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://github.com/JasonStys/integration-operations-console/blob/main/docs/api.md#errors"));
        return problem;
    }
}
