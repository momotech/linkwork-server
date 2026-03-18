# linkwork-mcp-starter

Pure MCP protocol stack starter for Java:

- JSON-RPC client contract (`McpClient`)
- Direct mode (`sse/http`, `stdio`)
- Gateway mode (`/proxy/discover`, `/proxy/probe`, `/proxy/call-tool`)
- Spring Boot auto-configuration and properties binding

## Configuration

```yaml
linkwork:
  agent:
    mcp:
      enabled: true
      mode: direct # direct | gateway
      gateway:
        agent-base-url: http://host.docker.internal:8080
        proxy-base-url: http://mcp-proxy-service:8080
      client:
        connect-timeout-ms: 3000
        read-timeout-ms: 10000
      security:
        encryption-key: your-32-byte-secret
```

## Usage

Inject `com.momo.agent.mcp.core.McpClient` and call:

- `discover(McpEndpoint endpoint)`
- `probe(McpEndpoint endpoint)`
- `callTool(McpEndpoint endpoint, String toolName, Map<String, Object> args)`
