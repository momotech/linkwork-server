package com.momo.agent.mcp.transport;

import com.momo.agent.mcp.core.model.McpEndpoint;
import com.momo.agent.mcp.core.protocol.JsonRpcRequest;

public interface McpTransport {

    boolean supports(String type);

    McpTransportResponse exchange(McpEndpoint endpoint, JsonRpcRequest request, String sessionId);
}
