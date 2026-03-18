package com.linkwork.agent.storage.core;

import com.linkwork.agent.storage.core.model.StorageVolumeDef;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public class StorageClient {
    private final StorageProvider storageProvider;
    private final FileStorageOps fileStorageOps;

    public StorageClient(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
        this.fileStorageOps = storageProvider instanceof FileStorageOps ops ? ops : null;
    }

    // ==================== Workspace Ops ====================

    public String upload(String workspaceId, byte[] data, String fileName) {
        return storageProvider.upload(workspaceId, data, fileName);
    }

    public byte[] download(String path) {
        return storageProvider.download(path);
    }

    public StorageVolumeDef generateSandboxMountConfig(String workspaceId) {
        return storageProvider.generateSandboxMountConfig(workspaceId);
    }

    // ==================== File Storage Ops ====================

    public boolean supportsFileStorageOps() {
        return fileStorageOps != null;
    }

    public boolean isConfigured() {
        return requireFileOps().isConfigured();
    }

    public Path resolvePath(String objectName) {
        return requireFileOps().resolvePath(objectName);
    }

    public String uploadToPath(InputStream input, String objectName, long size) throws IOException {
        return requireFileOps().uploadToPath(input, objectName, size);
    }

    public String uploadText(String content, String objectName) {
        return requireFileOps().uploadText(content, objectName);
    }

    public Path downloadToTempFile(String objectName) throws IOException {
        return requireFileOps().downloadToTempFile(objectName);
    }

    public void copyObject(String sourceObjectName, String destObjectName) {
        requireFileOps().copyObject(sourceObjectName, destObjectName);
    }

    public boolean objectExists(String objectName) {
        return requireFileOps().objectExists(objectName);
    }

    public List<String> listObjects(String prefix) {
        return requireFileOps().listObjects(prefix);
    }

    public void deleteObject(String objectName) {
        requireFileOps().deleteObject(objectName);
    }

    private FileStorageOps requireFileOps() {
        if (fileStorageOps == null) {
            throw new StorageException("Current storage provider does not support file storage operations");
        }
        return fileStorageOps;
    }
}
