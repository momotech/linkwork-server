package com.linkwork.agent.skill.core.model;

public record FileNode(
        String name,
        String path,
        NodeType type,
        String sha,
        Long size
) {
    public enum NodeType {
        FILE,
        DIRECTORY
    }
}
