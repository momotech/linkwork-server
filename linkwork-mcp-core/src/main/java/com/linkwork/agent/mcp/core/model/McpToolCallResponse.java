package com.linkwork.agent.mcp.core.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class McpToolCallResponse {

    private boolean success;
    private String message;
    private Map<String, Object> result = new LinkedHashMap<>();

    public static McpToolCallResponse success(Map<String, Object> result) {
        McpToolCallResponse response = new McpToolCallResponse();
        response.success = true;
        response.message = "ok";
        response.result = result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
        return response;
    }

    public static McpToolCallResponse failure(String message) {
        McpToolCallResponse response = new McpToolCallResponse();
        response.success = false;
        response.message = message;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
    }
}
