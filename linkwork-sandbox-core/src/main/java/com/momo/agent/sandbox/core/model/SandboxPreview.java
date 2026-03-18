package com.momo.agent.sandbox.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SandboxPreview {

    private String sandboxId;
    private Map<String, Object> podGroupSpec = new LinkedHashMap<>();
    private List<Map<String, Object>> podSpecs = new ArrayList<>();

    public String getSandboxId() {
        return sandboxId;
    }

    public void setSandboxId(String sandboxId) {
        this.sandboxId = sandboxId;
    }

    public Map<String, Object> getPodGroupSpec() {
        if (podGroupSpec == null) {
            podGroupSpec = new LinkedHashMap<>();
        }
        return podGroupSpec;
    }

    public void setPodGroupSpec(Map<String, Object> podGroupSpec) {
        this.podGroupSpec = podGroupSpec;
    }

    public List<Map<String, Object>> getPodSpecs() {
        if (podSpecs == null) {
            podSpecs = new ArrayList<>();
        }
        return podSpecs;
    }

    public void setPodSpecs(List<Map<String, Object>> podSpecs) {
        this.podSpecs = podSpecs;
    }
}
