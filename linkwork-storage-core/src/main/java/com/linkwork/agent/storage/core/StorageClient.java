package com.linkwork.agent.storage.core;

import com.linkwork.agent.storage.core.model.StorageVolumeDef;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

    public boolean isConfigured() {
        return storageProvider != null;
    }

    public boolean supportsFileStorageOps() {
        return isConfigured();
    }

    public Path resolvePath(String path) {
        if (path == null || path.isBlank()) {
            throw new StorageException("path cannot be blank");
        }
        return Path.of(path).toAbsolutePath().normalize();
    }

    public Path uploadToPath(InputStream inputStream, String targetPath, long size) {
        if (inputStream == null) {
            throw new StorageException("inputStream cannot be null");
        }
        Path target = resolvePath(targetPath);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new StorageException("failed to upload to path: " + target, ex);
        }
    }

    public Path downloadToTempFile(String sourcePath) {
        Path source = resolvePath(sourcePath);
        try {
            Path temp = Files.createTempFile("linkwork-storage-", ".tmp");
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            return temp;
        } catch (IOException ex) {
            throw new StorageException("failed to download to temp file: " + source, ex);
        }
    }

    public void deleteObject(String path) {
        Path target = resolvePath(path);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new StorageException("failed to delete object: " + target, ex);
        }
    }

    public void copyObject(String sourcePath, String targetPath) {
        Path source = resolvePath(sourcePath);
        Path target = resolvePath(targetPath);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new StorageException("failed to copy object from " + source + " to " + target, ex);
        }
    }
}
