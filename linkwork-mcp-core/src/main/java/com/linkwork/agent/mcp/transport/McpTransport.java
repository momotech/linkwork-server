package com.linkwork.agent.mcp.transport;

import com.linkwork.agent.mcp.core.model.McpEndpoint;
import com.linkwork.agent.mcp.core.protocol.JsonRpcRequest;

public interface McpTransport {

    boolean supports(String type);

    McpTransportResponse exchange(McpEndpoint endpoint, JsonRpcRequest request, String sessionId);
}
