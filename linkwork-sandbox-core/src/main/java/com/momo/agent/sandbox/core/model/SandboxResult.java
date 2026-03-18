package com.momo.agent.sandbox.core.model;

import java.time.Instant;
import java.util.List;

public class SandboxResult {

    public enum Status {
        SUCCESS,
        FAILED
    }

    private String sandboxId;
    private boolean success;
    private Status status;
    private String podGroupName;
    private List<String> podNames;
    private String scheduledNode;
    private String errorCode;
    private String errorMessage;
    private Instant timestamp;

    public static SandboxResult success(String sandboxId, String podGroupName, List<String> podNames, String scheduledNode) {
        SandboxResult result = new SandboxResult();
        result.setSandboxId(sandboxId);
        result.setSuccess(true);
        result.setStatus(Status.SUCCESS);
        result.setPodGroupName(podGroupName);
        result.setPodNames(podNames);
        result.setScheduledNode(scheduledNode);
        result.setTimestamp(Instant.now());
        return result;
    }

    public static SandboxResult failed(String sandboxId, String errorCode, String errorMessage) {
        SandboxResult result = new SandboxResult();
        result.setSandboxId(sandboxId);
        result.setSuccess(false);
        result.setStatus(Status.FAILED);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        result.setTimestamp(Instant.now());
        return result;
    }

    public String getSandboxId() {
        return sandboxId;
    }

    public void setSandboxId(String sandboxId) {
        this.sandboxId = sandboxId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getPodGroupName() {
        return podGroupName;
    }

    public void setPodGroupName(String podGroupName) {
        this.podGroupName = podGroupName;
    }

    public List<String> getPodNames() {
        return podNames;
    }

    public void setPodNames(List<String> podNames) {
        this.podNames = podNames;
    }

    public String getScheduledNode() {
        return scheduledNode;
    }

    public void setScheduledNode(String scheduledNode) {
        this.scheduledNode = scheduledNode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
