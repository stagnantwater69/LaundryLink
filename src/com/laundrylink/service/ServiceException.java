package com.laundrylink.service;

/**
 * A business-rule or validation failure. The message is safe to show to the user.
 */
public class ServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    public ServiceException(String message) {
        super(message);
    }
}
