package com.linkwork.agent.sandbox.core.model;

import java.time.Instant;

public class SandboxPodStatus {

    private String podName;
    private String phase;
    private String nodeName;
    private boolean ready;
    private boolean terminating;
    private Instant createdAt;
    private Instant terminalAt;
    private String lifecycleGeneration;
    private Long fenceToken;

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isTerminating() {
        return terminating;
    }

    public void setTerminating(boolean terminating) {
        this.terminating = terminating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getTerminalAt() {
        return terminalAt;
    }

    public void setTerminalAt(Instant terminalAt) {
        this.terminalAt = terminalAt;
    }

    public String getLifecycleGeneration() {
        return lifecycleGeneration;
    }

    public void setLifecycleGeneration(String lifecycleGeneration) {
        this.lifecycleGeneration = lifecycleGeneration;
    }

    public Long getFenceToken() {
        return fenceToken;
    }

    public void setFenceToken(Long fenceToken) {
        this.fenceToken = fenceToken;
    }
}
