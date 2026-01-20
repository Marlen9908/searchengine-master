package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import searchengine.dto.index.IndexResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<IndexResponse> handleBadRequest(IllegalArgumentException ex) {
        IndexResponse response = new IndexResponse();
        response.setResult(false);
        response.setError(ex.getMessage());
        // Здесь мы явно указываем статус (например, 400 Bad Request)
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}