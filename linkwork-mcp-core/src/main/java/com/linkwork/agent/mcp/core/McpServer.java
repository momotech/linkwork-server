package com.linkwork.agent.mcp.core;

import com.linkwork.agent.mcp.core.protocol.JsonRpcRequest;

import java.util.Map;

public interface McpServer {

    Map<String, Object> handle(JsonRpcRequest request);
}
