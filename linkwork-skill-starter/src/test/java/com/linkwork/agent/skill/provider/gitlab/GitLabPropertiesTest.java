package com.linkwork.agent.skill.provider.gitlab;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GitLabPropertiesTest {

    @Test
    public void defaultsToTreeMode() {
        GitLabProperties properties = new GitLabProperties();

        assertThat(properties.getMode()).isEqualTo("tree");
        assertThat(properties.isBranchPerSkillMode()).isFalse();
    }

    @Test
    public void acceptsCompatibleBranchModeAliases() {
        GitLabProperties properties = new GitLabProperties();

        properties.setMode(" branch-per-skill ");
        assertThat(properties.isBranchPerSkillMode()).isTrue();

        properties.setMode("BRANCH_PER_SKILL");
        assertThat(properties.isBranchPerSkillMode()).isTrue();

        properties.setMode("branch");
        assertThat(properties.isBranchPerSkillMode()).isTrue();
    }
}
