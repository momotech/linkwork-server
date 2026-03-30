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
      # tree(default): one branch + root-path directories as skills
      # branch-per-skill: each git branch is one skill
      mode: tree
      branch: main
      root-path: skills
```

Notes:
- `token` and `deploy-token` are interchangeable; starter uses the first non-blank value.
- `url` can be omitted if `repo-url` is provided.
- `mode=tree` keeps legacy directory model (`root-path/<skillName>/...`).
- `mode=branch-per-skill` uses branch model (`skillName` == git branch).

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
