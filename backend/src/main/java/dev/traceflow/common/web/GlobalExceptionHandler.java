package dev.traceflow.common.web;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into a uniform {@link ApiError} payload. Internal details (stack traces, SQL, credentials)
 * are logged server side only and never returned to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", request, List.of());
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> badRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ResponseEntity<ApiError> forbidden(ForbiddenOperationException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiError> validation(BindException ex, HttpServletRequest request) {
        var violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException ex, HttpServletRequest request) {
        var violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class})
    ResponseEntity<ApiError> badParameter(Exception ex, HttpServletRequest request) {
        String message = ex instanceof MethodArgumentTypeMismatchException mm
                ? "Invalid value for parameter '" + mm.getName() + "'"
                : ex.getMessage();
        return build(HttpStatus.BAD_REQUEST, message, request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "Uploaded content exceeds the configured size limit", request,
                List.of());
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> multipart(MultipartException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid multipart request", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                           List<ApiError.FieldViolation> violations) {
        var body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), violations);
        return ResponseEntity.status(status).body(body);
    }
}
