package com.linkwork.agent.mcp.core.model;

import java.util.ArrayList;
import java.util.List;

public class McpDiscoverResponse {

    private boolean success;
    private String message;
    private String sessionId;
    private String serverName;
    private String serverVersion;
    private String protocolVersion;
    private List<McpTool> tools = new ArrayList<>();

    public static McpDiscoverResponse success(List<McpTool> tools, String sessionId) {
        return success(tools, sessionId, null, null, null);
    }

    public static McpDiscoverResponse success(List<McpTool> tools,
                                              String sessionId,
                                              String serverName,
                                              String serverVersion,
                                              String protocolVersion) {
        McpDiscoverResponse response = new McpDiscoverResponse();
        response.success = true;
        response.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
        response.sessionId = sessionId;
        response.serverName = serverName;
        response.serverVersion = serverVersion;
        response.protocolVersion = protocolVersion;
        response.message = "ok";
        return response;
    }

    public static McpDiscoverResponse failure(String message) {
        McpDiscoverResponse response = new McpDiscoverResponse();
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public List<McpTool> getTools() {
        return tools;
    }

    public void setTools(List<McpTool> tools) {
        this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
    }
}
