package com.patobloxfruits.bot.amplopay;

public class AmploPayException extends RuntimeException {

    public AmploPayException(String message) {
        super(message);
    }

    public AmploPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
