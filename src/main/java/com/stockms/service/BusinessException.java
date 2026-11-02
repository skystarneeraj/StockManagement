package com.stockms.service;

/** A rule violation whose message is safe to show to the user. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
