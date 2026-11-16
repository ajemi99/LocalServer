package com.localserver.exceptions;

public class ServerStartupException extends Exception {

    private static final long serialVersionUID = 1L;

    public ServerStartupException(String msg) { super(msg); }

    public ServerStartupException(String msg, Throwable cause) { super(msg, cause); }

    public ServerStartupException(Throwable cause) { super(cause); }
}
