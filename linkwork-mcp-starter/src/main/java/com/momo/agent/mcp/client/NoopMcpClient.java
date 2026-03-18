package com.momo.agent.mcp.client;

import com.momo.agent.mcp.core.McpClient;
import com.momo.agent.mcp.core.model.McpDiscoverResponse;
import com.momo.agent.mcp.core.model.McpEndpoint;
import com.momo.agent.mcp.core.model.McpProbeResponse;
import com.momo.agent.mcp.core.model.McpToolCallResponse;

import java.util.Map;

public class NoopMcpClient implements McpClient {

    private static final String DISABLED_MESSAGE = "mcp client is disabled";

    @Override
    public McpDiscoverResponse discover(McpEndpoint endpoint) {
        return McpDiscoverResponse.failure(DISABLED_MESSAGE);
    }

    @Override
    public McpProbeResponse probe(McpEndpoint endpoint) {
        return McpProbeResponse.failure(DISABLED_MESSAGE, 0);
    }

    @Override
    public McpToolCallResponse callTool(McpEndpoint endpoint, String toolName, Map<String, Object> arguments) {
        return McpToolCallResponse.failure(DISABLED_MESSAGE);
    }
}
