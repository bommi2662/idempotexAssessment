package com.fde.processor.exception;

public class TransientProcessingException extends RuntimeException {
    public TransientProcessingException(String message) {
        super(message);
    }

    public TransientProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
