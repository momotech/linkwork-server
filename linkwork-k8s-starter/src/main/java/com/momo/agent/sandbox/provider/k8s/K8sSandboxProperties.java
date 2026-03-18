package com.momo.agent.sandbox.provider.k8s;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "momo.agent.sandbox.k8s")
public class K8sSandboxProperties {

    private String namespace = "ai-workers";
    private String schedulerName = "volcano";
    private String queueName = "default";
    private String priorityClassName;
    private boolean createPodGroup = true;
    private int waitPodGroupReadySeconds = 20;
    private int waitScheduledNodeSeconds = 10;
    private String kubeconfigPath;
    private String defaultAgentImage;
    private String defaultRunnerImage;
    private String defaultImagePullPolicy = "IfNotPresent";
    private boolean autoCreateImagePullSecret = true;
    private String registry;
    private String registryUsername;
    private String registryPassword;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getSchedulerName() {
        return schedulerName;
    }

    public void setSchedulerName(String schedulerName) {
        this.schedulerName = schedulerName;
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

    public boolean isCreatePodGroup() {
        return createPodGroup;
    }

    public void setCreatePodGroup(boolean createPodGroup) {
        this.createPodGroup = createPodGroup;
    }

    public int getWaitPodGroupReadySeconds() {
        return waitPodGroupReadySeconds;
    }

    public void setWaitPodGroupReadySeconds(int waitPodGroupReadySeconds) {
        this.waitPodGroupReadySeconds = waitPodGroupReadySeconds;
    }

    public int getWaitScheduledNodeSeconds() {
        return waitScheduledNodeSeconds;
    }

    public void setWaitScheduledNodeSeconds(int waitScheduledNodeSeconds) {
        this.waitScheduledNodeSeconds = waitScheduledNodeSeconds;
    }

    public String getKubeconfigPath() {
        return kubeconfigPath;
    }

    public void setKubeconfigPath(String kubeconfigPath) {
        this.kubeconfigPath = kubeconfigPath;
    }

    public String getDefaultAgentImage() {
        return defaultAgentImage;
    }

    public void setDefaultAgentImage(String defaultAgentImage) {
        this.defaultAgentImage = defaultAgentImage;
    }

    public String getDefaultRunnerImage() {
        return defaultRunnerImage;
    }

    public void setDefaultRunnerImage(String defaultRunnerImage) {
        this.defaultRunnerImage = defaultRunnerImage;
    }

    public String getDefaultImagePullPolicy() {
        return defaultImagePullPolicy;
    }

    public void setDefaultImagePullPolicy(String defaultImagePullPolicy) {
        this.defaultImagePullPolicy = defaultImagePullPolicy;
    }

    public boolean isAutoCreateImagePullSecret() {
        return autoCreateImagePullSecret;
    }

    public void setAutoCreateImagePullSecret(boolean autoCreateImagePullSecret) {
        this.autoCreateImagePullSecret = autoCreateImagePullSecret;
    }

    public String getRegistry() {
        return registry;
    }

    public void setRegistry(String registry) {
        this.registry = registry;
    }

    public String getRegistryUsername() {
        return registryUsername;
    }

    public void setRegistryUsername(String registryUsername) {
        this.registryUsername = registryUsername;
    }

    public String getRegistryPassword() {
        return registryPassword;
    }

    public void setRegistryPassword(String registryPassword) {
        this.registryPassword = registryPassword;
    }
}
