package com.localserver.exceptions;

public class ConfigurationException extends Exception {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String msg) { super(msg); }

    public ConfigurationException(String msg, Throwable cause) { super(msg, cause); }

    public ConfigurationException(Throwable cause) { super(cause); }
}
