package tn.esprit.formation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns framework-level failures into the {status, error, message} shape the Angular
 * client already reads, instead of the default error body.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * MaxUploadSizeExceededException is raised while the multipart request is being parsed,
     * before any handler method is resolved — so it must be handled by advice rather than a
     * controller-local @ExceptionHandler. Without this, an oversized upload surfaces to the
     * browser as an opaque 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(Map.of(
                "status", HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "error", "Payload Too Large",
                "message", "Le fichier dépasse la taille maximale autorisée (5 Mo)."));
    }

    /** Raised by @Valid when a request DTO breaks one of its constraints. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(" · "));

        return ResponseEntity.badRequest()
            .body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", message.isBlank() ? "Requête invalide." : message));
    }
}
