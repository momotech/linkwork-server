package com.momo.agent.sandbox.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sandbox request contract.
 * It is intentionally business-neutral and only carries orchestration data.
 */
public class SandboxSpec {

    private String sandboxId;
    private SandboxMode mode = SandboxMode.SIDECAR;
    private Integer podCount = 1;
    private String namespace;
    private String queueName;
    private String priorityClassName;
    private String preferredNode;
    private String imagePullSecret;
    private String agentImage;
    private String runnerImage;
    private String imagePullPolicy;
    private Integer workspaceSizeGi = 20;

    private List<String> agentCommand = new ArrayList<>();
    private List<String> runnerCommand = new ArrayList<>();
    private Map<String, String> labels = new LinkedHashMap<>();
    private Map<String, String> annotations = new LinkedHashMap<>();
    private Map<String, String> injectedEnvs = new LinkedHashMap<>();
    private Map<String, Map<String, String>> configMaps = new LinkedHashMap<>();
    private Map<String, Map<String, String>> secrets = new LinkedHashMap<>();
    private List<VolumeMountDef> mounts = new ArrayList<>();

    private ResourceSpec agentResources = ResourceSpec.defaultAgent();
    private ResourceSpec runnerResources = ResourceSpec.defaultRunner();

    public String getSandboxId() {
        return sandboxId;
    }

    public void setSandboxId(String sandboxId) {
        this.sandboxId = sandboxId;
    }

    public SandboxMode getMode() {
        return mode;
    }

    public void setMode(SandboxMode mode) {
        this.mode = mode;
    }

    public Integer getPodCount() {
        return podCount;
    }

    public void setPodCount(Integer podCount) {
        this.podCount = podCount;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getPriorityClassName() {
        return priorityClassName;
    }

    public void setPriorityClassName(String priorityClassName) {
        this.priorityClassName = priorityClassName;
    }

    public String getPreferredNode() {
        return preferredNode;
    }

    public void setPreferredNode(String preferredNode) {
        this.preferredNode = preferredNode;
    }

    public String getImagePullSecret() {
        return imagePullSecret;
    }

    public void setImagePullSecret(String imagePullSecret) {
        this.imagePullSecret = imagePullSecret;
    }

    public String getAgentImage() {
        return agentImage;
    }

    public void setAgentImage(String agentImage) {
        this.agentImage = agentImage;
    }

    public String getRunnerImage() {
        return runnerImage;
    }

    public void setRunnerImage(String runnerImage) {
        this.runnerImage = runnerImage;
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public void setImagePullPolicy(String imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
    }

    public Integer getWorkspaceSizeGi() {
        return workspaceSizeGi;
    }

    public void setWorkspaceSizeGi(Integer workspaceSizeGi) {
        this.workspaceSizeGi = workspaceSizeGi;
    }

    public List<String> getAgentCommand() {
        if (agentCommand == null) {
            agentCommand = new ArrayList<>();
        }
        return agentCommand;
    }

    public void setAgentCommand(List<String> agentCommand) {
        this.agentCommand = agentCommand;
    }

    public List<String> getRunnerCommand() {
        if (runnerCommand == null) {
            runnerCommand = new ArrayList<>();
        }
        return runnerCommand;
    }

    public void setRunnerCommand(List<String> runnerCommand) {
        this.runnerCommand = runnerCommand;
    }

    public Map<String, String> getLabels() {
        if (labels == null) {
            labels = new LinkedHashMap<>();
        }
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public Map<String, String> getAnnotations() {
        if (annotations == null) {
            annotations = new LinkedHashMap<>();
        }
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public Map<String, String> getInjectedEnvs() {
        if (injectedEnvs == null) {
            injectedEnvs = new LinkedHashMap<>();
        }
        return injectedEnvs;
    }

    public void setInjectedEnvs(Map<String, String> injectedEnvs) {
        this.injectedEnvs = injectedEnvs;
    }

    public List<VolumeMountDef> getMounts() {
        if (mounts == null) {
            mounts = new ArrayList<>();
        }
        return mounts;
    }

    public void setMounts(List<VolumeMountDef> mounts) {
        this.mounts = mounts;
    }

    public Map<String, Map<String, String>> getConfigMaps() {
        if (configMaps == null) {
            configMaps = new LinkedHashMap<>();
        }
        return configMaps;
    }

    public void setConfigMaps(Map<String, Map<String, String>> configMaps) {
        this.configMaps = configMaps;
    }

    public Map<String, Map<String, String>> getSecrets() {
        if (secrets == null) {
            secrets = new LinkedHashMap<>();
        }
        return secrets;
    }

    public void setSecrets(Map<String, Map<String, String>> secrets) {
        this.secrets = secrets;
    }

    public ResourceSpec getAgentResources() {
        if (agentResources == null) {
            agentResources = ResourceSpec.defaultAgent();
        }
        return agentResources;
    }

    public void setAgentResources(ResourceSpec agentResources) {
        this.agentResources = agentResources;
    }

    public ResourceSpec getRunnerResources() {
        if (runnerResources == null) {
            runnerResources = ResourceSpec.defaultRunner();
        }
        return runnerResources;
    }

    public void setRunnerResources(ResourceSpec runnerResources) {
        this.runnerResources = runnerResources;
    }
}
