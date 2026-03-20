package com.linkwork.agent.skill.core;

public class SkillException extends RuntimeException {
    public SkillException(String message) {
        super(message);
    }

    public SkillException(String message, Throwable cause) {
        super(message, cause);
    }
}
