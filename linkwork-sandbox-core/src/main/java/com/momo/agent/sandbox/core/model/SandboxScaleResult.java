package com.momo.agent.sandbox.core.model;

import java.util.ArrayList;
import java.util.List;

public class SandboxScaleResult {

    private String sandboxId;
    private boolean success;
    private String scaleType;
    private int previousPodCount;
    private int currentPodCount;
    private int targetPodCount;
    private List<String> runningPods = new ArrayList<>();
    private List<String> addedPods = new ArrayList<>();
    private List<String> removedPods = new ArrayList<>();
    private String errorMessage;

    public static SandboxScaleResult failed(String sandboxId, String errorMessage) {
        SandboxScaleResult result = new SandboxScaleResult();
        result.setSandboxId(sandboxId);
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public static SandboxScaleResult success(String sandboxId,
                                             String scaleType,
                                             int previousPodCount,
                                             int currentPodCount,
                                             int targetPodCount,
                                             List<String> runningPods,
                                             List<String> addedPods,
                                             List<String> removedPods) {
        SandboxScaleResult result = new SandboxScaleResult();
        result.setSandboxId(sandboxId);
        result.setSuccess(true);
        result.setScaleType(scaleType);
        result.setPreviousPodCount(previousPodCount);
        result.setCurrentPodCount(currentPodCount);
        result.setTargetPodCount(targetPodCount);
        result.setRunningPods(runningPods);
        result.setAddedPods(addedPods);
        result.setRemovedPods(removedPods);
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

    public String getScaleType() {
        return scaleType;
    }

    public void setScaleType(String scaleType) {
        this.scaleType = scaleType;
    }

    public int getPreviousPodCount() {
        return previousPodCount;
    }

    public void setPreviousPodCount(int previousPodCount) {
        this.previousPodCount = previousPodCount;
    }

    public int getCurrentPodCount() {
        return currentPodCount;
    }

    public void setCurrentPodCount(int currentPodCount) {
        this.currentPodCount = currentPodCount;
    }

    public int getTargetPodCount() {
        return targetPodCount;
    }

    public void setTargetPodCount(int targetPodCount) {
        this.targetPodCount = targetPodCount;
    }

    public List<String> getRunningPods() {
        if (runningPods == null) {
            runningPods = new ArrayList<>();
        }
        return runningPods;
    }

    public void setRunningPods(List<String> runningPods) {
        this.runningPods = runningPods;
    }

    public List<String> getAddedPods() {
        if (addedPods == null) {
            addedPods = new ArrayList<>();
        }
        return addedPods;
    }

    public void setAddedPods(List<String> addedPods) {
        this.addedPods = addedPods;
    }

    public List<String> getRemovedPods() {
        if (removedPods == null) {
            removedPods = new ArrayList<>();
        }
        return removedPods;
    }

    public void setRemovedPods(List<String> removedPods) {
        this.removedPods = removedPods;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
