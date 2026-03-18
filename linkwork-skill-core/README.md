# linkwork-skill-core

Core contracts and models for skill operations.

## Contains

- `SkillProvider` SPI
- `SkillClient` facade
- `SkillException` and `UnsupportedSkillProvider`
- Models: `SkillInfo`, `FileNode`, `CommitInfo`

## Does Not Contain

- Spring Boot auto-configuration
- Provider implementations (GitLab/GitHub)

Use `linkwork-skill-starter` for default Spring wiring and provider implementations.
