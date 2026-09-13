package com.linkwork.agent.skill;

import com.linkwork.agent.skill.core.SkillClient;
import com.linkwork.agent.skill.core.SkillProvider;
import com.linkwork.agent.skill.core.UnsupportedSkillProvider;
import com.linkwork.agent.skill.provider.gitlab.GitLabProviderImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@AutoConfiguration
@EnableConfigurationProperties(AgentSkillProperties.class)
@ConditionalOnProperty(prefix = "linkwork.agent.skill", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SkillAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "skillGitLabRestClient")
    @ConditionalOnProperty(prefix = "linkwork.agent.skill", name = "provider", havingValue = "gitlab", matchIfMissing = true)
    public RestClient skillGitLabRestClient(AgentSkillProperties properties,
                                            ObjectProvider<RestClient.Builder> builderProvider) {
        String baseUrl = properties.getGitlab().effectiveUrl();
        String token = properties.getGitlab().effectiveToken();
        RestClient.Builder builder = builderProvider.getIfAvailable(RestClient::builder);
        RestClient.Builder configured = builder.baseUrl(baseUrl);
        if (token != null && !token.isBlank()) {
            // 与旧版 SkillGitLabService 对齐：deploy-token 走 PRIVATE-TOKEN。
            if (properties.getGitlab().hasDeployToken()) {
                configured.defaultHeader("PRIVATE-TOKEN", properties.getGitlab().deployTokenValue());
            } else if (token.startsWith("glpat-")) {
                configured.defaultHeader("PRIVATE-TOKEN", token);
            } else {
                configured.defaultHeader("Authorization", "Bearer " + token);
            }
        }
        return configured.build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "linkwork.agent.skill", name = "provider", havingValue = "gitlab", matchIfMissing = true)
    public SkillProvider skillProvider(RestClient skillGitLabRestClient,
                                       AgentSkillProperties properties) {
        return new GitLabProviderImpl(skillGitLabRestClient, properties.getGitlab());
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillProvider unsupportedSkillProvider(AgentSkillProperties properties) {
        return new UnsupportedSkillProvider(properties.getProvider());
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillClient skillClient(SkillProvider skillProvider,
                                   AgentSkillProperties properties) {
        return new SkillClient(
                skillProvider,
                properties.getRetryTimes(),
                Duration.ofMillis(properties.getRetryBackoffMs()),
                Duration.ofMillis(properties.getCacheTtlMs())
        );
    }
}
