# linkwork-storage-starter

Multi-tenant storage starter for agent workspaces in containerized environments.

## Configuration

```yaml
agent:
  storage:
    enabled: true
    provider: nfs
    nfs:
      base-path: /mnt/nfs/agent-workspaces
      mount-path: /workspace
      read-only: false
      uid: 1000
      gid: 1000
```

Notes:
- `generateSandboxMountConfig(workspaceId)` returns the workspace-specific mount definition.
- `upload`/`download` only allow files under `nfs.base-path` to avoid path traversal.

## Usage

Inject `StorageClient` in your service:

```java
@Service
public class AgentWorkspaceService {
    private final StorageClient storageClient;

    public AgentWorkspaceService(StorageClient storageClient) {
        this.storageClient = storageClient;
    }

    public StorageVolumeDef mountConfig(String workspaceId) {
        return storageClient.generateSandboxMountConfig(workspaceId);
    }
}
```
