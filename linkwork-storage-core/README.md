# linkwork-storage-core

Core contracts and models for workspace storage.

## Contains

- `StorageProvider` SPI
- `StorageClient` facade
- `StorageException` and `UnsupportedStorageProvider`
- Models: `StorageVolumeDef`, `WorkspaceInfo`

## Does Not Contain

- Spring Boot auto-configuration
- Provider implementations (NFS/OSS/etc.)

Use `linkwork-storage-starter` for Spring wiring and default provider implementations.
