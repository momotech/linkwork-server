package com.momo.agent.mcp.core.model;

public class McpProbeResponse {

    private boolean success;
    private String message;
    private int latencyMs;

    public static McpProbeResponse success(int latencyMs) {
        McpProbeResponse response = new McpProbeResponse();
        response.success = true;
        response.message = "ok";
        response.latencyMs = latencyMs;
        return response;
    }

    public static McpProbeResponse failure(String message, int latencyMs) {
        McpProbeResponse response = new McpProbeResponse();
        response.success = false;
        response.message = message;
        response.latencyMs = latencyMs;
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

    public int getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(int latencyMs) {
        this.latencyMs = latencyMs;
    }
}
