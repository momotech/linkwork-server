package com.linkwork.agent.storage.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Extended file-storage operations beyond basic workspace upload/download.
 * Providers that support file-management scenarios should implement this interface.
 */
public interface FileStorageOps {

    boolean isConfigured();

    Path resolvePath(String objectName);

    String uploadToPath(InputStream input, String objectName, long size) throws IOException;

    String uploadText(String content, String objectName);

    Path downloadToTempFile(String objectName) throws IOException;

    void copyObject(String sourceObjectName, String destObjectName);

    boolean objectExists(String objectName);

    List<String> listObjects(String prefix);

    void deleteObject(String objectName);
}
