package searchengine.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(StartIndexingException.class)
    public ResponseEntity<ErrorResponse> handleStartIndexingException(StartIndexingException e) {
        ErrorResponse response = new ErrorResponse(false, e.getMessage());
        log.error("Ошибка индексации: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(StopIndexingException.class)
    public ResponseEntity<ErrorResponse> handleStopIndexingException(StopIndexingException e) {
        ErrorResponse response = new ErrorResponse(false, e.getMessage());
        log.error("Ошибка индексации: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IndexingException.class)
    public ResponseEntity<ErrorResponse> handleIndexingException(IndexingException e) {
        ErrorResponse response = new ErrorResponse(false, e.getMessage());
        log.error("Ошибка индексации: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IndexNotReadyException.class)
    public ResponseEntity<ErrorResponse> handleIndexNotReadyException(IndexNotReadyException e) {
        ErrorResponse response = new ErrorResponse(false, e.getMessage());
        log.error("Ошибка: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        ErrorResponse response = new ErrorResponse(false, e.getMessage());
        log.error("Ошибка: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
