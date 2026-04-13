package com.tus.orderservice.exception;

public class InventoryServiceException extends RuntimeException {

    public InventoryServiceException(String message) {
        super(message);
    }

    public InventoryServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
