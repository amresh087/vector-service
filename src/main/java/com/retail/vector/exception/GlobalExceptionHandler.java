package com.retail.vector.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(VectorException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleProductException(VectorException ex) {
        return ex.getMessage();
    }
}
