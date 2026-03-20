package com.linkwork.agent.storage.provider.nfs;

public class NfsStorageProperties {
    private String basePath = "/mnt/nfs/agent-workspaces";
    private String mountPath = "/workspace";
    private boolean readOnly = false;
    private Integer uid = 1000;
    private Integer gid = 1000;

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getMountPath() {
        return mountPath;
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public Integer getUid() {
        return uid;
    }

    public void setUid(Integer uid) {
        this.uid = uid;
    }

    public Integer getGid() {
        return gid;
    }

    public void setGid(Integer gid) {
        this.gid = gid;
    }
}
