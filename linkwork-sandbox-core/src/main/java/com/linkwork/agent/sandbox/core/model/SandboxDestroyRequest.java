package com.linkwork.agent.sandbox.core.model;

/**
 * Generation-aware sandbox destroy request.
 */
public class SandboxDestroyRequest {

    private String sandboxId;
    private String namespace;
    private String expectedGeneration;
    private Long expectedFenceToken;
    private String expectedPodGroupUid;
    private Long gracePeriodSeconds = 0L;
    private boolean allowLegacy;

    public static SandboxDestroyRequest legacy(String sandboxId, String namespace) {
        SandboxDestroyRequest request = new SandboxDestroyRequest();
        request.setSandboxId(sandboxId);
        request.setNamespace(namespace);
        request.setAllowLegacy(true);
        return request;
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

    public String getExpectedPodGroupUid() {
        return expectedPodGroupUid;
    }

    public void setExpectedPodGroupUid(String expectedPodGroupUid) {
        this.expectedPodGroupUid = expectedPodGroupUid;
    }

    public Long getGracePeriodSeconds() {
        return gracePeriodSeconds;
    }

    public void setGracePeriodSeconds(Long gracePeriodSeconds) {
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    public boolean isAllowLegacy() {
        return allowLegacy;
    }

    public void setAllowLegacy(boolean allowLegacy) {
        this.allowLegacy = allowLegacy;
    }
}
