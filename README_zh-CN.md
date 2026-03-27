# linkwork-server

`linkwork-server` 是 LinkWork 的 Java 能力层仓库，提供可复用的 **core contract + Spring Boot starter**，供上层业务服务（如 `LinkWork/backend`）集成。

## 模块结构

| 模块 | 作用 |
|---|---|
| `linkwork-bom` | 统一依赖版本管理（BOM） |
| `linkwork-skill-core` / `linkwork-skill-starter` | Skills 能力抽象与默认实现 |
| `linkwork-storage-core` / `linkwork-storage-starter` | 存储能力抽象与默认实现 |
| `linkwork-sandbox-core` / `linkwork-k8s-starter` | 沙箱编排抽象与 K8s/Volcano 实现 |
| `linkwork-mcp-core` / `linkwork-mcp-starter` | MCP 协议抽象与 Spring 集成 |

## 本地开发

### 1) 环境要求

- JDK 21
- Maven 3.9+

### 2) 构建

```bash
cd linkwork-server
mvn -DskipTests install
```

### 3) 在业务服务中使用

`LinkWork/backend/pom.xml` 通过依赖 `io.linkwork:*` 的 starter 复用本仓库能力，版本由 `linkwork.server.version` 控制。

## Deploy 流程

`linkwork-server` 本身是 **库仓库**，不是独立 Web 服务；部署流程是发布 Maven 包给上游服务使用。

### 1) 发布到 GitHub Packages

仓库内已提供脚本：`scripts/deploy-github-packages.sh`

```bash
cd linkwork-server
export GITHUB_TOKEN=<your_token>
./scripts/deploy-github-packages.sh
```

可选变量：

- `GITHUB_OWNER`（默认 `momotech`）
- `GITHUB_REPO`（默认 `linkwork-server`）
- `SETTINGS_FILE`（默认 `settings-github.xml.example`）

### 2) 上游服务消费新版本

- 在上游项目更新 `linkwork.server.version`
- 重新执行 Maven 构建并发布上游服务镜像

## 相关文档

- `linkwork-*/README.md`（各子模块能力与配置）
- 根仓库 `LinkWork/docs/architecture/components_zh-CN.md`
