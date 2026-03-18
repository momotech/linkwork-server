package com.momo.agent.mcp.transport.stdio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.agent.mcp.core.model.McpEndpoint;
import com.momo.agent.mcp.core.protocol.JsonRpcRequest;
import com.momo.agent.mcp.transport.McpTransport;
import com.momo.agent.mcp.transport.McpTransportResponse;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StdioMcpTransport implements McpTransport {

    private final ObjectMapper objectMapper;

    public StdioMcpTransport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String type) {
        String normalized = StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "";
        return "stdio".equals(normalized);
    }

    @Override
    public McpTransportResponse exchange(McpEndpoint endpoint, JsonRpcRequest request, String sessionId) {
        List<String> command = endpoint.getCommand();
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("mcp stdio endpoint command is required");
        }

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            if (endpoint.getEnv() != null && !endpoint.getEnv().isEmpty()) {
                processBuilder.environment().putAll(endpoint.getEnv());
            }
            process = processBuilder.start();

            String payload = objectMapper.writeValueAsString(request) + System.lineSeparator();
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(payload);
                writer.flush();
            }

            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                line = reader.readLine();
            }

            process.waitFor(5, TimeUnit.SECONDS);
            Map<String, Object> body = parseBody(line);

            McpTransportResponse response = new McpTransportResponse();
            response.setStatusCode(200);
            response.setBody(body);
            response.setHeaders(new LinkedHashMap<>());
            return response;
        } catch (Exception ex) {
            throw new IllegalStateException("mcp stdio request failed: " + ex.getMessage(), ex);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Map<String, Object> parseBody(String raw) throws IOException {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(raw, new TypeReference<>() {
        });
    }
}
