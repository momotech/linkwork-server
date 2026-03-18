package com.momo.agent.mcp.transport;

import java.util.LinkedHashMap;
import java.util.Map;

public class McpTransportResponse {

    private int statusCode;
    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, Object> body = new LinkedHashMap<>();

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setBody(Map<String, Object> body) {
        this.body = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
    }
}
