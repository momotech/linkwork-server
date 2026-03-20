package com.linkwork.agent.skill.provider.gitlab;

import java.net.URI;
import java.net.URISyntaxException;

public class GitLabProperties {
    private String url = "https://gitlab.com";
    private String token;
    private String repoUrl;
    private String deployToken;
    private String projectId;
    private String branch = "main";
    private String rootPath = "skills";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getDeployToken() {
        return deployToken;
    }

    public void setDeployToken(String deployToken) {
        this.deployToken = deployToken;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String effectiveToken() {
        // 与旧实现对齐：优先使用 deploy-token（对应 PRIVATE-TOKEN 直连方式）
        if (isUsableToken(deployToken)) {
            return deployToken;
        }
        if (isUsableToken(token)) {
            return token;
        }
        return null;
    }

    public String effectiveUrl() {
        if (url != null && !url.isBlank()) {
            return url;
        }
        if (repoUrl == null || repoUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(repoUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getHost();
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    public boolean hasDeployToken() {
        return isUsableToken(deployToken);
    }

    public boolean hasOauthToken() {
        return isUsableToken(token);
    }

    public String deployTokenValue() {
        return isUsableToken(deployToken) ? deployToken : null;
    }

    public String oauthTokenValue() {
        return isUsableToken(token) ? token : null;
    }

    private boolean isUsableToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !normalized.startsWith("dev-placeholder");
    }
}
