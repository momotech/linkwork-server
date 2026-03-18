package com.linkwork.agent.skill.core.model;

import java.time.Instant;

public record SkillInfo(
        String name,
        String path,
        String branch,
        String lastCommitId,
        Instant updatedAt
) {
}
