package org.tavall.agentwebmcp.provider;

public final class ProviderException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public ProviderException(String code, String message, int httpStatus) {
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
