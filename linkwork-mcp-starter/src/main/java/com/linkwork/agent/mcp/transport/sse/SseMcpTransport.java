package com.linkwork.agent.mcp.transport.sse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkwork.agent.mcp.core.model.McpEndpoint;
import com.linkwork.agent.mcp.core.protocol.JsonRpcRequest;
import com.linkwork.agent.mcp.transport.McpTransport;
import com.linkwork.agent.mcp.transport.McpTransportResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SseMcpTransport implements McpTransport {

    private static final String HEADER_MCP_SESSION_ID = "Mcp-Session-Id";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SseMcpTransport(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String type) {
        String normalized = normalizeType(type);
        return "sse".equals(normalized) || "http".equals(normalized);
    }

    @Override
    public McpTransportResponse exchange(McpEndpoint endpoint, JsonRpcRequest request, String sessionId) {
        if (!StringUtils.hasText(endpoint.getUrl())) {
            throw new IllegalArgumentException("mcp endpoint url is required");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
            endpoint.getHeaders().forEach(headers::set);
            if (StringUtils.hasText(sessionId)) {
                headers.set(HEADER_MCP_SESSION_ID, sessionId);
            }

            String payload = objectMapper.writeValueAsString(request);
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(endpoint.getUrl(), HttpMethod.POST, entity, String.class);

            McpTransportResponse output = new McpTransportResponse();
            output.setStatusCode(response.getStatusCode().value());
            output.setHeaders(extractHeaders(response.getHeaders()));
            output.setBody(parseBody(response.getBody()));
            return output;
        } catch (Exception ex) {
            throw new IllegalStateException("mcp transport request failed: " + ex.getMessage(), ex);
        }
    }

    private String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "sse";
    }

    private Map<String, String> extractHeaders(HttpHeaders headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                result.put(key, values.get(0));
            }
        });
        return result;
    }

    private Map<String, Object> parseBody(String rawBody) throws Exception {
        if (!StringUtils.hasText(rawBody)) {
            return new LinkedHashMap<>();
        }

        String body = rawBody.trim();
        if (body.startsWith("{")) {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        }

        String sseJson = extractSseData(body);
        if (StringUtils.hasText(sseJson) && sseJson.trim().startsWith("{")) {
            return objectMapper.readValue(sseJson, new TypeReference<>() {
            });
        }
        return new LinkedHashMap<>();
    }

    private String extractSseData(String body) {
        String[] lines = body.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                sb.append(trimmed.substring("data:".length()).trim());
            }
        }
        return sb.toString();
    }
}
