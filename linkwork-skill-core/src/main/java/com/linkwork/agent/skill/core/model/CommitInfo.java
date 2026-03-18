package com.linkwork.agent.skill.core.model;

import java.time.Instant;

public record CommitInfo(
        String id,
        String title,
        String message,
        String authorName,
        Instant authoredAt,
        String webUrl
) {
}
