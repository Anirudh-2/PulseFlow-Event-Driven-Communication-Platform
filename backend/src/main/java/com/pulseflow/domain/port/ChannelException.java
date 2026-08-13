package com.pulseflow.domain.port;

public class ChannelException extends Exception {
    public ChannelException(String message) {
        super(message);
    }

    public ChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
