package com.momo.agent.mcp.client;

import com.momo.agent.mcp.core.McpClient;
import com.momo.agent.mcp.core.model.McpDiscoverResponse;
import com.momo.agent.mcp.core.model.McpEndpoint;
import com.momo.agent.mcp.core.model.McpProbeResponse;
import com.momo.agent.mcp.core.model.McpTool;
import com.momo.agent.mcp.core.model.McpToolCallResponse;
import com.momo.agent.mcp.core.protocol.JsonRpcRequest;
import com.momo.agent.mcp.transport.McpTransport;
import com.momo.agent.mcp.transport.McpTransportResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DirectMcpClient implements McpClient {

    private static final String HEADER_MCP_SESSION_ID = "Mcp-Session-Id";

    private final List<McpTransport> transports;

    public DirectMcpClient(List<McpTransport> transports) {
        this.transports = transports;
    }

    @Override
    public McpDiscoverResponse discover(McpEndpoint endpoint) {
        try {
            McpTransport transport = resolveTransport(endpoint);
            McpTransportResponse initResponse = exchangeInitialize(endpoint, transport);
            String initError = readJsonRpcError(initResponse.getBody());
            if (StringUtils.hasText(initError)) {
                return McpDiscoverResponse.failure(initError);
            }
            String sessionId = resolveSessionId(initResponse.getHeaders());
            Map<String, Object> initResult = asMap(initResponse.getBody().get("result"));
            Map<String, Object> serverInfo = asMap(initResult.get("serverInfo"));
            String serverName = readText(serverInfo.get("name"));
            String serverVersion = readText(serverInfo.get("version"));
            String protocolVersion = readText(initResult.get("protocolVersion"));

            sendInitializedNotification(endpoint, transport, sessionId);

            McpTransportResponse toolsResponse = transport.exchange(
                endpoint,
                JsonRpcRequest.of(nextRequestId(), "tools/list", new LinkedHashMap<>()),
                sessionId
            );

            String rpcError = readJsonRpcError(toolsResponse.getBody());
            if (StringUtils.hasText(rpcError)) {
                return McpDiscoverResponse.failure(rpcError);
            }

            Map<String, Object> result = asMap(toolsResponse.getBody().get("result"));
            List<McpTool> tools = parseTools(result.get("tools"));
            return McpDiscoverResponse.success(tools, sessionId, serverName, serverVersion, protocolVersion);
        } catch (Exception ex) {
            return McpDiscoverResponse.failure(ex.getMessage());
        }
    }

    @Override
    public McpProbeResponse probe(McpEndpoint endpoint) {
        long start = System.currentTimeMillis();
        try {
            McpTransport transport = resolveTransport(endpoint);
            McpTransportResponse response = exchangeInitialize(endpoint, transport);
            String rpcError = readJsonRpcError(response.getBody());
            if (StringUtils.hasText(rpcError)) {
                return McpProbeResponse.failure(rpcError, elapsed(start));
            }
            return McpProbeResponse.success(elapsed(start));
        } catch (Exception ex) {
            return McpProbeResponse.failure(ex.getMessage(), elapsed(start));
        }
    }

    @Override
    public McpToolCallResponse callTool(McpEndpoint endpoint, String toolName, Map<String, Object> arguments) {
        if (!StringUtils.hasText(toolName)) {
            return McpToolCallResponse.failure("toolName is required");
        }

        try {
            McpTransport transport = resolveTransport(endpoint);
            McpTransportResponse initResponse = exchangeInitialize(endpoint, transport);
            String sessionId = resolveSessionId(initResponse.getHeaders());

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", toolName);
            params.put("arguments", arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arguments));

            McpTransportResponse callResponse = transport.exchange(
                endpoint,
                JsonRpcRequest.of(nextRequestId(), "tools/call", params),
                sessionId
            );

            String rpcError = readJsonRpcError(callResponse.getBody());
            if (StringUtils.hasText(rpcError)) {
                return McpToolCallResponse.failure(rpcError);
            }

            Map<String, Object> result = asMap(callResponse.getBody().get("result"));
            return McpToolCallResponse.success(result);
        } catch (Exception ex) {
            return McpToolCallResponse.failure(ex.getMessage());
        }
    }

    private McpTransportResponse exchangeInitialize(McpEndpoint endpoint, McpTransport transport) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", "2025-03-26");
        params.put("capabilities", new LinkedHashMap<>());
        params.put("clientInfo", Map.of("name", "linkwork-mcp-starter", "version", "0.1.0-SNAPSHOT"));
        return transport.exchange(endpoint, JsonRpcRequest.of(nextRequestId(), "initialize", params), null);
    }

    private void sendInitializedNotification(McpEndpoint endpoint, McpTransport transport, String sessionId) {
        try {
            transport.exchange(endpoint, JsonRpcRequest.of(nextRequestId(), "notifications/initialized", new LinkedHashMap<>()), sessionId);
        } catch (Exception ignored) {
        }
    }

    private String nextRequestId() {
        return "req-" + Instant.now().toEpochMilli();
    }

    private int elapsed(long start) {
        return (int) Math.max(1, System.currentTimeMillis() - start);
    }

    private McpTransport resolveTransport(McpEndpoint endpoint) {
        if (CollectionUtils.isEmpty(transports)) {
            throw new IllegalStateException("no mcp transport implementation available");
        }

        String endpointType = normalizeType(endpoint.getType());
        for (McpTransport transport : transports) {
            if (transport.supports(endpointType)) {
                return transport;
            }
        }
        throw new IllegalArgumentException("unsupported mcp endpoint type: " + endpointType);
    }

    private String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "sse";
    }

    private String resolveSessionId(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (HEADER_MCP_SESSION_ID.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String readJsonRpcError(Map<String, Object> body) {
        Map<String, Object> error = asMap(body.get("error"));
        if (error.isEmpty()) {
            return null;
        }
        Object message = error.get("message");
        return message == null ? "mcp json-rpc error" : String.valueOf(message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private String readText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<McpTool> parseTools(Object value) {
        List<McpTool> tools = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return tools;
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            McpTool tool = new McpTool();
            Object name = map.get("name");
            Object description = map.get("description");
            tool.setName(name == null ? null : String.valueOf(name));
            tool.setDescription(description == null ? null : String.valueOf(description));

            Object schema = map.get("inputSchema");
            if (schema instanceof Map<?, ?> schemaMap) {
                tool.setInputSchema((Map<String, Object>) schemaMap);
            }
            tools.add(tool);
        }
        return tools;
    }
}
