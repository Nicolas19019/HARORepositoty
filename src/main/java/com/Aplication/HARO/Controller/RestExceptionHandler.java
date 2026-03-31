package com.Aplication.HARO.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

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
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("No se pudo guardar el archivo en S3")
                || message.contains("Falta configuracion S3")) {
            return ResponseEntity.status(500).body(message);
        }
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

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<?> handleNotFoundEndpoint(Exception ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "NOT_FOUND");
        body.put("path", request.getRequestURI());
        body.put("methodReceived", request.getMethod());
        body.put("message", "Endpoint no encontrado");
        return ResponseEntity.status(404).body(body);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<?> handleAuthMissing(AuthenticationCredentialsNotFoundException ex) {
        return ResponseEntity.status(401).body("UNAUTHORIZED");
    }

    @ExceptionHandler({AuthenticationException.class, InsufficientAuthenticationException.class})
    public ResponseEntity<?> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(401).body("UNAUTHORIZED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        Object jwtError = request.getAttribute("jwt_error");
        if ("TOKEN_EXPIRED".equals(jwtError)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "SESSION_EXPIRED",
                    "message", "Tu sesion expiro. Inicia sesion nuevamente."
            ));
        }
        if ("TOKEN_INVALID".equals(jwtError)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "INVALID_TOKEN",
                    "message", "Token invalido. Inicia sesion nuevamente."
            ));
        }
        return ResponseEntity.status(403).body("FORBIDDEN");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<?> handleAuthorizationDenied(AuthorizationDeniedException ex, HttpServletRequest request) {
        Object jwtError = request.getAttribute("jwt_error");
        if ("TOKEN_EXPIRED".equals(jwtError)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "SESSION_EXPIRED",
                    "message", "Tu sesion expiro. Inicia sesion nuevamente."
            ));
        }
        if ("TOKEN_INVALID".equals(jwtError)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "INVALID_TOKEN",
                    "message", "Token invalido. Inicia sesion nuevamente."
            ));
        }
        return ResponseEntity.status(403).body("FORBIDDEN");
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body("Conflicto de datos: " + ex.getMostSpecificCause().getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(413).body("El archivo excede el tamaño máximo permitido (50MB).");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(415).body("Tipo de contenido no soportado.");
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<?> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado [{} {}]: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(500).body("Error interno del servidor");
    }
}
