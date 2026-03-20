package com.linkwork.agent.skill.core;

import com.linkwork.agent.skill.core.model.CommitInfo;
import com.linkwork.agent.skill.core.model.FileNode;
import com.linkwork.agent.skill.core.model.SkillInfo;

import java.util.List;

public class UnsupportedSkillProvider implements SkillProvider {
    private final String provider;

    public UnsupportedSkillProvider(String provider) {
        this.provider = provider;
    }

    @Override
    public List<SkillInfo> listSkills() {
        throw unsupported();
    }

    @Override
    public List<FileNode> getTree(String skillName) {
        throw unsupported();
    }

    @Override
    public String getFile(String skillName, String filePath) {
        throw unsupported();
    }

    @Override
    public CommitInfo upsertFile(String skillName, String filePath, String content, String commitMessage) {
        throw unsupported();
    }

    @Override
    public CommitInfo deleteFile(String skillName, String filePath, String commitMessage) {
        throw unsupported();
    }

    @Override
    public List<CommitInfo> listCommits(String skillName, int page, int pageSize) {
        throw unsupported();
    }

    private SkillException unsupported() {
        return new SkillException("linkwork.agent.skill.provider='" + provider + "' is not supported yet");
    }
}
