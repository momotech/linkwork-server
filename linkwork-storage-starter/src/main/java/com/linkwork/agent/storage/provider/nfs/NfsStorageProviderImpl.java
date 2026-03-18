package com.linkwork.agent.storage.provider.nfs;

import com.linkwork.agent.storage.core.StorageException;
import com.linkwork.agent.storage.core.StorageProvider;
import com.linkwork.agent.storage.core.model.StorageVolumeDef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public class NfsStorageProviderImpl implements StorageProvider {
    private final NfsStorageProperties properties;
    private final Path baseRoot;

    public NfsStorageProviderImpl(NfsStorageProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.baseRoot = resolveBaseRoot(properties.getBasePath());
        initBaseRoot();
    }

    @Override
    public String upload(String workspaceId, byte[] data, String fileName) {
        if (data == null) {
            throw new StorageException("data cannot be null");
        }
        Path workspaceRoot = ensureWorkspaceRoot(workspaceId);
        String safeFileName = sanitizeFileName(fileName);
        Path target = workspaceRoot.resolve(safeFileName).normalize();
        assertWithinBase(target);
        try {
            Files.write(target, data);
            return target.toString();
        } catch (IOException ex) {
            throw new StorageException("failed to write file into workspace '" + workspaceId + "'", ex);
        }
    }

    @Override
    public byte[] download(String path) {
        if (path == null || path.isBlank()) {
            throw new StorageException("path cannot be blank");
        }
        Path target = Path.of(path);
        if (!target.isAbsolute()) {
            target = baseRoot.resolve(target).normalize();
        } else {
            target = target.normalize();
        }
        assertWithinBase(target);
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new StorageException("failed to read file: " + target, ex);
        }
    }

    @Override
    public StorageVolumeDef generateSandboxMountConfig(String workspaceId) {
        Path workspaceRoot = ensureWorkspaceRoot(workspaceId);
        String safeWorkspaceId = sanitizeWorkspaceId(workspaceId);
        return new StorageVolumeDef(
                "nfs",
                safeWorkspaceId,
                workspaceRoot.toString(),
                properties.getMountPath(),
                properties.isReadOnly(),
                properties.getUid(),
                properties.getGid()
        );
    }

    private Path ensureWorkspaceRoot(String workspaceId) {
        String safeWorkspaceId = sanitizeWorkspaceId(workspaceId);
        Path workspaceRoot = baseRoot.resolve(safeWorkspaceId).normalize();
        assertWithinBase(workspaceRoot);
        try {
            Files.createDirectories(workspaceRoot);
            return workspaceRoot;
        } catch (IOException ex) {
            throw new StorageException("failed to prepare workspace: " + safeWorkspaceId, ex);
        }
    }

    private String sanitizeWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new StorageException("workspaceId cannot be blank");
        }
        String sanitized = workspaceId.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            throw new StorageException("workspaceId is invalid");
        }
        return sanitized;
    }

    private String sanitizeFileName(String fileName) {
        String input = (fileName == null || fileName.isBlank()) ? "file-" + Instant.now().toEpochMilli() : fileName;
        String nameOnly = Path.of(input).getFileName().toString();
        String sanitized = nameOnly.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            throw new StorageException("fileName is invalid");
        }
        return sanitized;
    }

    private void assertWithinBase(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(baseRoot)) {
            throw new StorageException("path escapes base-path: " + path);
        }
    }

    private Path resolveBaseRoot(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            throw new StorageException("agent.storage.nfs.base-path is required");
        }
        return Path.of(basePath).toAbsolutePath().normalize();
    }

    private void initBaseRoot() {
        try {
            Files.createDirectories(baseRoot);
        } catch (IOException ex) {
            throw new StorageException("failed to init base-path: " + baseRoot, ex);
        }
    }
}
