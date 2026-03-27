# linkwork-server

English | [中文](./README_zh-CN.md)

`linkwork-server` is the Java capability-layer repository for LinkWork. It provides reusable core contracts and Spring Boot starters consumed by upper-layer services (for example `LinkWork/back`).

## Module Layout

| Module | Purpose |
|---|---|
| `linkwork-bom` | Dependency version management (BOM) |
| `linkwork-skill-core` / `linkwork-skill-starter` | Skills abstraction and default provider wiring |
| `linkwork-storage-core` / `linkwork-storage-starter` | Storage abstraction and default provider wiring |
| `linkwork-sandbox-core` / `linkwork-k8s-starter` | Sandbox abstraction and K8s/Volcano implementation |
| `linkwork-mcp-core` / `linkwork-mcp-starter` | MCP abstraction and Spring integration |

## Local Development

### 1) Requirements

- JDK 21
- Maven 3.9+

### 2) Build

```bash
cd linkwork-server
mvn -DskipTests install
```

### 3) Consumption by application service

`LinkWork/back/pom.xml` consumes `io.linkwork:*` artifacts, with version controlled by `linkwork.server.version`.

## Deploy Flow

`linkwork-server` is a library repository, not a standalone runtime service. Deployment means publishing Maven artifacts for downstream services.

### 1) Publish to GitHub Packages

Built-in script: `scripts/deploy-github-packages.sh`

```bash
cd linkwork-server
export GITHUB_TOKEN=<your_token>
./scripts/deploy-github-packages.sh
```

Optional variables:

- `GITHUB_OWNER` (default: `momotech`)
- `GITHUB_REPO` (default: `linkwork-server`)
- `SETTINGS_FILE` (default: `settings-github.xml.example`)

### 2) Upgrade downstream services

- Update `linkwork.server.version` in consumer services
- Rebuild and redeploy consumer service images

## References

- `linkwork-*/README.md` for module-level details
- `LinkWork/docs/architecture/components.md`
