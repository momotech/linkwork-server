package com.linkwork.agent.mcp.core.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class McpTool {

    private String name;
    private String description;
    private Map<String, Object> inputSchema = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputSchema);
    }
}
