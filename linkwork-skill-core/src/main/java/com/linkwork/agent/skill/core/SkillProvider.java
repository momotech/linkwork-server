package com.linkwork.agent.skill.core;

import com.linkwork.agent.skill.core.model.CommitInfo;
import com.linkwork.agent.skill.core.model.FileNode;
import com.linkwork.agent.skill.core.model.SkillInfo;

import java.util.List;

public interface SkillProvider {

    List<SkillInfo> listSkills();

    List<FileNode> getTree(String skillName);

    String getFile(String skillName, String filePath);

    CommitInfo upsertFile(String skillName, String filePath, String content, String commitMessage);

    CommitInfo deleteFile(String skillName, String filePath, String commitMessage);

    List<CommitInfo> listCommits(String skillName, int page, int pageSize);
}
