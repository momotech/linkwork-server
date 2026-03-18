package com.linkwork.agent.skill.core;

import com.linkwork.agent.skill.core.model.CommitInfo;

/**
 * Optional provider extensions for commit/branch lifecycle operations
 * beyond the base {@link SkillProvider} CRUD contract.
 * <p>
 * Providers that support branch-per-skill workflows (e.g. GitLab branch mode)
 * should implement this interface. Directory-mode providers may implement it
 * with sensible defaults (e.g. returning latest commit for the skill path).
 */
public interface SkillProviderExtendedOps {

    String getHeadCommitId(String skillName);

    String getFileAtCommit(String skillName, String filePath, String commitSha);

    CommitInfo createSkillBranch(String skillName, String fromRef);

    void deleteSkillBranch(String skillName);
}
