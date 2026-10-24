package com.notifyflow.delivery;

public class DeliveryFailedException extends RuntimeException {

    public DeliveryFailedException(String message) {
        super(message);
    }
}
