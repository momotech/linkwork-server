package com.linkwork.agent.sandbox.core.model;

/**
 * Generation-aware scale-down request.
 */
public class SandboxScaleDownRequest {

    private String sandboxId;
    private String podName;
    private String namespace;
    private String expectedGeneration;
    private Long expectedFenceToken;
    private String expectedPodGroupUid;
    private Integer targetPodCount;
    private boolean allowLegacy;

    public static SandboxScaleDownRequest legacy(String sandboxId, String podName, String namespace) {
        SandboxScaleDownRequest request = new SandboxScaleDownRequest();
        request.setSandboxId(sandboxId);
        request.setPodName(podName);
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

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
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

    public Integer getTargetPodCount() {
        return targetPodCount;
    }

    public void setTargetPodCount(Integer targetPodCount) {
        this.targetPodCount = targetPodCount;
    }

    public boolean isAllowLegacy() {
        return allowLegacy;
    }

    public void setAllowLegacy(boolean allowLegacy) {
        this.allowLegacy = allowLegacy;
    }
}
