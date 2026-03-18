package com.momo.agent.mcp.core;

import com.momo.agent.mcp.core.model.McpDiscoverResponse;
import com.momo.agent.mcp.core.model.McpEndpoint;
import com.momo.agent.mcp.core.model.McpProbeResponse;
import com.momo.agent.mcp.core.model.McpToolCallResponse;

import java.util.Map;

public interface McpClient {

    McpDiscoverResponse discover(McpEndpoint endpoint);

    McpProbeResponse probe(McpEndpoint endpoint);

    McpToolCallResponse callTool(McpEndpoint endpoint, String toolName, Map<String, Object> arguments);
}
