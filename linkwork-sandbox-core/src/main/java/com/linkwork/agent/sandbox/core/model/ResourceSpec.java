package com.linkwork.agent.sandbox.core.model;

public class ResourceSpec {

    private String cpuRequest;
    private String cpuLimit;
    private String memoryRequest;
    private String memoryLimit;

    public static ResourceSpec defaultAgent() {
        ResourceSpec spec = new ResourceSpec();
        spec.setCpuRequest("1");
        spec.setCpuLimit("2");
        spec.setMemoryRequest("2Gi");
        spec.setMemoryLimit("4Gi");
        return spec;
    }

    public static ResourceSpec defaultRunner() {
        ResourceSpec spec = new ResourceSpec();
        spec.setCpuRequest("1");
        spec.setCpuLimit("4");
        spec.setMemoryRequest("2Gi");
        spec.setMemoryLimit("8Gi");
        return spec;
    }

    public String getCpuRequest() {
        return cpuRequest;
    }

    public void setCpuRequest(String cpuRequest) {
        this.cpuRequest = cpuRequest;
    }

    public String getCpuLimit() {
        return cpuLimit;
    }

    public void setCpuLimit(String cpuLimit) {
        this.cpuLimit = cpuLimit;
    }

    public String getMemoryRequest() {
        return memoryRequest;
    }

    public void setMemoryRequest(String memoryRequest) {
        this.memoryRequest = memoryRequest;
    }

    public String getMemoryLimit() {
        return memoryLimit;
    }

    public void setMemoryLimit(String memoryLimit) {
        this.memoryLimit = memoryLimit;
    }
}
