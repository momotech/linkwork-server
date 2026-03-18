package com.linkwork.agent.storage.core;

import com.linkwork.agent.storage.core.model.StorageVolumeDef;

public class UnsupportedStorageProvider implements StorageProvider {
    private final String provider;

    public UnsupportedStorageProvider(String provider) {
        this.provider = provider;
    }

    @Override
    public String upload(String workspaceId, byte[] data, String fileName) {
        throw unsupported();
    }

    @Override
    public byte[] download(String path) {
        throw unsupported();
    }

    @Override
    public StorageVolumeDef generateSandboxMountConfig(String workspaceId) {
        throw unsupported();
    }

    private StorageException unsupported() {
        return new StorageException("agent.storage.provider='" + provider + "' is not supported yet");
    }
}
