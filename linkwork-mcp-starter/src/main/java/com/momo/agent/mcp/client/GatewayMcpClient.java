package com.momo.agent.mcp.client;

import com.momo.agent.mcp.McpProperties;
import com.momo.agent.mcp.core.McpClient;
import com.momo.agent.mcp.core.model.McpDiscoverResponse;
import com.momo.agent.mcp.core.model.McpEndpoint;
import com.momo.agent.mcp.core.model.McpProbeResponse;
import com.momo.agent.mcp.core.model.McpTool;
import com.momo.agent.mcp.core.model.McpToolCallResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GatewayMcpClient implements McpClient {

    private final RestTemplate restTemplate;
    private final McpProperties properties;

    public GatewayMcpClient(RestTemplate restTemplate, McpProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public McpDiscoverResponse discover(McpEndpoint endpoint) {
        String baseUrl = trimBaseUrl(properties.getGateway().getProxyBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            return McpDiscoverResponse.failure("linkwork.agent.mcp.gateway.proxy-base-url is required in gateway mode");
        }

        try {
            Map<String, Object> payload = endpointPayload(endpoint);
            Map<String, Object> response = post(baseUrl + "/proxy/discover", payload);
            if (!readSuccess(response)) {
                return McpDiscoverResponse.failure(readMessage(response));
            }
            List<McpTool> tools = parseTools(response.get("tools"));
            String sessionId = response.get("sessionId") == null ? null : String.valueOf(response.get("sessionId"));
            String serverName = response.get("serverName") == null ? null : String.valueOf(response.get("serverName"));
            String serverVersion = response.get("serverVersion") == null ? null : String.valueOf(response.get("serverVersion"));
            String protocolVersion = response.get("protocolVersion") == null ? null : String.valueOf(response.get("protocolVersion"));
            return McpDiscoverResponse.success(tools, sessionId, serverName, serverVersion, protocolVersion);
        } catch (Exception ex) {
            return McpDiscoverResponse.failure(ex.getMessage());
        }
    }

    @Override
    public McpProbeResponse probe(McpEndpoint endpoint) {
        String baseUrl = trimBaseUrl(properties.getGateway().getProxyBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            return McpProbeResponse.failure("linkwork.agent.mcp.gateway.proxy-base-url is required in gateway mode", 0);
        }

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> payload = endpointPayload(endpoint);
            Map<String, Object> response = post(baseUrl + "/proxy/probe", payload);
            boolean success = readSuccess(response);
            int latencyMs = readLatency(response, (int) (System.currentTimeMillis() - start));
            if (!success) {
                return McpProbeResponse.failure(readMessage(response), latencyMs);
            }
            return McpProbeResponse.success(latencyMs);
        } catch (Exception ex) {
            int latencyMs = (int) Math.max(1, System.currentTimeMillis() - start);
            return McpProbeResponse.failure(ex.getMessage(), latencyMs);
        }
    }

    @Override
    public McpToolCallResponse callTool(McpEndpoint endpoint, String toolName, Map<String, Object> arguments) {
        String baseUrl = trimBaseUrl(properties.getGateway().getProxyBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            return McpToolCallResponse.failure("linkwork.agent.mcp.gateway.proxy-base-url is required in gateway mode");
        }
        if (!StringUtils.hasText(toolName)) {
            return McpToolCallResponse.failure("toolName is required");
        }

        try {
            Map<String, Object> payload = endpointPayload(endpoint);
            payload.put("toolName", toolName);
            payload.put("arguments", arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arguments));
            Map<String, Object> response = post(baseUrl + "/proxy/call-tool", payload);
            if (!readSuccess(response)) {
                return McpToolCallResponse.failure(readMessage(response));
            }

            Object result = response.get("result");
            if (result instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return McpToolCallResponse.success(typed);
            }
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("value", result);
            return McpToolCallResponse.success(wrapped);
        } catch (Exception ex) {
            return McpToolCallResponse.failure(ex.getMessage());
        }
    }

    private Map<String, Object> endpointPayload(McpEndpoint endpoint) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", endpoint.getType());
        payload.put("url", endpoint.getUrl());
        payload.put("headers", endpoint.getHeaders());
        payload.put("command", endpoint.getCommand());
        payload.put("env", endpoint.getEnv());
        return payload;
    }

    private Map<String, Object> post(String url, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        if (response.getBody() == null) {
            return new LinkedHashMap<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        return body;
    }

    private String trimBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private boolean readSuccess(Map<String, Object> body) {
        Object success = body.get("success");
        return Boolean.TRUE.equals(success);
    }

    private String readMessage(Map<String, Object> body) {
        Object message = body.get("message");
        if (message == null) {
            Object error = body.get("error");
            if (error != null) {
                return String.valueOf(error);
            }
            return "mcp gateway request failed";
        }
        return String.valueOf(message);
    }

    private int readLatency(Map<String, Object> body, int fallback) {
        Object latency = body.get("latencyMs");
        if (latency instanceof Number number) {
            return number.intValue();
        }
        return Math.max(1, fallback);
    }

    @SuppressWarnings("unchecked")
    private List<McpTool> parseTools(Object toolsValue) {
        List<McpTool> tools = new ArrayList<>();
        if (!(toolsValue instanceof List<?> list)) {
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
