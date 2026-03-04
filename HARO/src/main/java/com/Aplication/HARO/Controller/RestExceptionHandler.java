package com.Aplication.HARO.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleInvalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(ex.getBindingResult().toString());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                    HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "METHOD_NOT_ALLOWED");
        body.put("path", request.getRequestURI());
        body.put("methodReceived", request.getMethod());
        body.put("message", ex.getMessage());
        body.put("supportedMethods",
                ex.getSupportedMethods() == null ? java.util.List.of() : Arrays.asList(ex.getSupportedMethods()));
        return ResponseEntity.status(405).body(body);
    }
}
