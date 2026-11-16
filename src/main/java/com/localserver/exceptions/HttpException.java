package com.localserver.exceptions;

public class HttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HttpException(String msg) { super(msg); }

    public HttpException(String msg, Throwable cause) { super(msg, cause); }

    public HttpException(Throwable cause) { super(cause); }
}
