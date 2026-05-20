package com.mangatrack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    static final String GENERIC_500_MESSAGE = "An unexpected error occurred.";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                fieldErrors.put(err.getField(), err.getDefaultMessage()));

        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Validation failed", request);
        body.put("fieldErrors", fieldErrors);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = (ex.getReason() != null && !ex.getReason().isBlank())
                ? ex.getReason()
                : status.getReasonPhrase();
        return handleExceptionInternal(ex, baseBody(status, message, request),
                new HttpHeaders(), status, request);
    }

    // Last-resort: anything not handled by the parent's framework-exception arms
    // (HttpMessageNotReadable, NoHandlerFound, MethodNotAllowed, ...) or by the
    // ResponseStatusException arm above lands here as a sanitized 500.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return handleExceptionInternal(ex,
                baseBody(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_500_MESSAGE, request),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body == null) {
            HttpStatus status = HttpStatus.valueOf(statusCode.value());
            body = baseBody(status, status.getReasonPhrase(), request);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static Map<String, Object> baseBody(HttpStatus status, String message, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        String path = pathOf(request);
        if (path != null) body.put("path", path);
        String requestId = MDC.get("requestId");
        if (requestId != null) body.put("requestId", requestId);
        return body;
    }

    private static String pathOf(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return null;
    }
}
