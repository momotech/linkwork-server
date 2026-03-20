package com.linkwork.agent.storage.core;

import com.linkwork.agent.storage.core.model.StorageVolumeDef;

public interface StorageProvider {

    String upload(String workspaceId, byte[] data, String fileName);

    byte[] download(String path);

    StorageVolumeDef generateSandboxMountConfig(String workspaceId);
}
