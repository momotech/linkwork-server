package com.linkwork.agent.storage;

import com.linkwork.agent.storage.provider.nfs.NfsStorageProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.storage")
public class AgentStorageProperties {
    private boolean enabled = true;
    private String provider = "nfs";
    private NfsStorageProperties nfs = new NfsStorageProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public NfsStorageProperties getNfs() {
        return nfs;
    }

    public void setNfs(NfsStorageProperties nfs) {
        this.nfs = nfs;
    }
}
