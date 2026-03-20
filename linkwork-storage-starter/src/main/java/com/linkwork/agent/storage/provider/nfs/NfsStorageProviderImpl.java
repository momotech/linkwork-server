package com.linkwork.agent.storage.provider.nfs;

import com.linkwork.agent.storage.core.FileStorageOps;
import com.linkwork.agent.storage.core.StorageException;
import com.linkwork.agent.storage.core.StorageProvider;
import com.linkwork.agent.storage.core.model.StorageVolumeDef;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class NfsStorageProviderImpl implements StorageProvider, FileStorageOps {
    private final NfsStorageProperties properties;
    private final Path baseRoot;

    public NfsStorageProviderImpl(NfsStorageProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.baseRoot = resolveBaseRoot(properties.getBasePath());
        initBaseRoot();
    }

    // ==================== StorageProvider ====================

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

    // ==================== FileStorageOps ====================

    @Override
    public boolean isConfigured() {
        return baseRoot != null && Files.isDirectory(baseRoot);
    }

    @Override
    public Path resolvePath(String objectName) {
        return baseRoot.resolve(objectName);
    }

    @Override
    public String uploadToPath(InputStream input, String objectName, long size) throws IOException {
        if (!isConfigured()) {
            throw new StorageException("NFS storage not configured");
        }
        Path target = baseRoot.resolve(objectName);
        assertWithinBase(target.normalize());
        ensureParentDirs(target.getParent());
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        return objectName;
    }

    @Override
    public String uploadText(String content, String objectName) {
        if (!isConfigured()) {
            throw new StorageException("NFS storage not configured");
        }
        Path target = baseRoot.resolve(objectName);
        assertWithinBase(target.normalize());
        try {
            ensureParentDirs(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("failed to upload text to " + objectName, e);
        }
        return objectName;
    }

    @Override
    public Path downloadToTempFile(String objectName) throws IOException {
        if (!isConfigured()) {
            throw new StorageException("NFS storage not configured");
        }
        Path source = baseRoot.resolve(objectName);
        if (!Files.exists(source)) {
            throw new IOException("file does not exist: " + objectName);
        }
        Path tempPath = Files.createTempFile("nfs-download-", ".tmp");
        Files.copy(source, tempPath, StandardCopyOption.REPLACE_EXISTING);
        return tempPath;
    }

    @Override
    public void copyObject(String sourceObjectName, String destObjectName) {
        if (!isConfigured()) {
            throw new StorageException("NFS storage not configured");
        }
        Path src = baseRoot.resolve(sourceObjectName);
        Path dest = baseRoot.resolve(destObjectName);
        assertWithinBase(src.normalize());
        assertWithinBase(dest.normalize());
        try {
            ensureParentDirs(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("failed to copy " + sourceObjectName + " -> " + destObjectName, e);
        }
    }

    @Override
    public boolean objectExists(String objectName) {
        if (!isConfigured()) {
            return false;
        }
        return Files.exists(baseRoot.resolve(objectName));
    }

    @Override
    public List<String> listObjects(String prefix) {
        if (!isConfigured()) {
            return List.of();
        }
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        Path dirPath = baseRoot.resolve(normalizedPrefix);
        List<String> objectNames = new ArrayList<>();
        if (!Files.isDirectory(dirPath)) {
            return objectNames;
        }
        try (Stream<Path> walker = Files.walk(dirPath)) {
            walker.filter(Files::isRegularFile)
                    .forEach(p -> objectNames.add(baseRoot.relativize(p).toString()));
        } catch (IOException e) {
            // degraded: return empty list
        }
        return objectNames;
    }

    @Override
    public void deleteObject(String objectName) {
        if (!isConfigured()) {
            return;
        }
        Path target = baseRoot.resolve(objectName);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // best effort
        }
    }

    // ==================== Internal helpers ====================

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

    private void ensureParentDirs(Path dir) throws IOException {
        if (dir != null && !Files.isDirectory(dir)) {
            Files.createDirectories(dir);
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
