# linkwork-skill-starter

Agent Skill starter for skill/prompt/workflow definitions.

## Configuration

```yaml
agent:
  skill:
    enabled: true
    provider: gitlab
    retry-times: 2
    retry-backoff-ms: 200
    cache-ttl-ms: 10000
    gitlab:
      url: ${LINKWORK_GITLAB_URL:}
      repo-url: ${LINKWORK_GITLAB_REPO_URL:}
      token: ${LINKWORK_GITLAB_TOKEN:}
      deploy-token: ${LINKWORK_GITLAB_DEPLOY_TOKEN:}
      project-id: 10086
      branch: main
      root-path: skills
```

Notes:
- `token` and `deploy-token` are interchangeable; starter uses the first non-blank value.
- `url` can be omitted if `repo-url` is provided.

## Usage

Inject `SkillClient` in your service:

```java
@Service
public class SkillService {
    private final SkillClient skillClient;

    public SkillService(SkillClient skillClient) {
        this.skillClient = skillClient;
    }

    public List<FileNode> tree(String skillName) {
        return skillClient.getTree(skillName);
    }
}
```
