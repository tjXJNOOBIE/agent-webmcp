package org.tavall.agentwebmcp.operation;

public final class OperationException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public OperationException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
