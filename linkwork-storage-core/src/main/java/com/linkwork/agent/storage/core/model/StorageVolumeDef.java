package com.linkwork.agent.storage.core.model;

public record StorageVolumeDef(
        String provider,
        String workspaceId,
        String sourcePath,
        String mountPath,
        boolean readOnly,
        Integer uid,
        Integer gid
) {
}
