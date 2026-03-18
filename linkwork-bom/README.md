# linkwork-bom

Bill of Materials for LinkWork server artifacts.

## Managed Artifacts

- `io.linkwork:linkwork-skill-core`
- `io.linkwork:linkwork-skill-starter`
- `io.linkwork:linkwork-storage-core`
- `io.linkwork:linkwork-storage-starter`

## Usage

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.linkwork</groupId>
      <artifactId>linkwork-bom</artifactId>
      <version>0.1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

After importing BOM, declare LinkWork dependencies without version.
