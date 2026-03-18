package com.momo.agent.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "linkwork.agent.mcp")
public class McpProperties {

    private boolean enabled = true;
    private McpMode mode = McpMode.DIRECT;
    private Gateway gateway = new Gateway();
    private Client client = new Client();
    private Security security = new Security();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public McpMode getMode() {
        return mode;
    }

    public void setMode(McpMode mode) {
        this.mode = mode;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public enum McpMode {
        DIRECT,
        GATEWAY
    }

    public static class Gateway {
        private String agentBaseUrl;
        private String proxyBaseUrl;

        public String getAgentBaseUrl() {
            return agentBaseUrl;
        }

        public void setAgentBaseUrl(String agentBaseUrl) {
            this.agentBaseUrl = agentBaseUrl;
        }

        public String getProxyBaseUrl() {
            return proxyBaseUrl;
        }

        public void setProxyBaseUrl(String proxyBaseUrl) {
            this.proxyBaseUrl = proxyBaseUrl;
        }
    }

    public static class Client {
        private int connectTimeoutMs = 3_000;
        private int readTimeoutMs = 10_000;

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    public static class Security {
        private String encryptionKey;

        public String getEncryptionKey() {
            return encryptionKey;
        }

        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }
    }
}
