package com.linkwork.agent.sandbox.core.model;

/**
 * Sandbox query with optional generation preconditions.
 */
public class SandboxQuery {

    private String sandboxId;
    private String namespace;
    private String expectedGeneration;
    private Long expectedFenceToken;

    public static SandboxQuery of(String sandboxId, String namespace) {
        SandboxQuery query = new SandboxQuery();
        query.setSandboxId(sandboxId);
        query.setNamespace(namespace);
        return query;
    }

    public String getSandboxId() {
        return sandboxId;
    }

    public void setSandboxId(String sandboxId) {
        this.sandboxId = sandboxId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getExpectedGeneration() {
        return expectedGeneration;
    }

    public void setExpectedGeneration(String expectedGeneration) {
        this.expectedGeneration = expectedGeneration;
    }

    public Long getExpectedFenceToken() {
        return expectedFenceToken;
    }

    public void setExpectedFenceToken(Long expectedFenceToken) {
        this.expectedFenceToken = expectedFenceToken;
    }
}
