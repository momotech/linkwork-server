package com.linkwork.agent.sandbox.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SandboxStatus {

    private String sandboxId;
    private String namespace;
    private String podGroupPhase;
    private Integer totalPods = 0;
    private Integer readyPods = 0;
    private Integer podGroupMinMember = 0;
    private Integer podGroupRunning = 0;
    private Integer podGroupSucceeded = 0;
    private Integer podGroupFailed = 0;
    private Integer podGroupPending = 0;
    private String message;
    private List<SandboxPodStatus> pods = new ArrayList<>();
    private Instant observedAt;

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

    public String getPodGroupPhase() {
        return podGroupPhase;
    }

    public void setPodGroupPhase(String podGroupPhase) {
        this.podGroupPhase = podGroupPhase;
    }

    public Integer getTotalPods() {
        return totalPods;
    }

    public void setTotalPods(Integer totalPods) {
        this.totalPods = totalPods;
    }

    public Integer getReadyPods() {
        return readyPods;
    }

    public void setReadyPods(Integer readyPods) {
        this.readyPods = readyPods;
    }

    public Integer getPodGroupMinMember() {
        return podGroupMinMember;
    }

    public void setPodGroupMinMember(Integer podGroupMinMember) {
        this.podGroupMinMember = podGroupMinMember;
    }

    public Integer getPodGroupRunning() {
        return podGroupRunning;
    }

    public void setPodGroupRunning(Integer podGroupRunning) {
        this.podGroupRunning = podGroupRunning;
    }

    public Integer getPodGroupSucceeded() {
        return podGroupSucceeded;
    }

    public void setPodGroupSucceeded(Integer podGroupSucceeded) {
        this.podGroupSucceeded = podGroupSucceeded;
    }

    public Integer getPodGroupFailed() {
        return podGroupFailed;
    }

    public void setPodGroupFailed(Integer podGroupFailed) {
        this.podGroupFailed = podGroupFailed;
    }

    public Integer getPodGroupPending() {
        return podGroupPending;
    }

    public void setPodGroupPending(Integer podGroupPending) {
        this.podGroupPending = podGroupPending;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<SandboxPodStatus> getPods() {
        if (pods == null) {
            pods = new ArrayList<>();
        }
        return pods;
    }

    public void setPods(List<SandboxPodStatus> pods) {
        this.pods = pods;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }
}
