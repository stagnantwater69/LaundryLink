package com.laundrylink.service;

/**
 * A broken rule or bad input. The message is safe to show the user.
 */
public class ServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Creates the error with a message the user can read. */
    public ServiceException(String message) {
        super(message);
    }
}
