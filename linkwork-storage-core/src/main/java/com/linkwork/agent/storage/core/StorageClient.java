package com.linkwork.agent.storage.core;

import com.linkwork.agent.storage.core.model.StorageVolumeDef;

public class StorageClient {
    private final StorageProvider storageProvider;

    public StorageClient(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String upload(String workspaceId, byte[] data, String fileName) {
        return storageProvider.upload(workspaceId, data, fileName);
    }

    public byte[] download(String path) {
        return storageProvider.download(path);
    }

    public StorageVolumeDef generateSandboxMountConfig(String workspaceId) {
        return storageProvider.generateSandboxMountConfig(workspaceId);
    }
}
