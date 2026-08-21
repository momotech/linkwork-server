package com.linkwork.agent.skill.provider.gitlab;

import org.junit.Test;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

public class GitLabProviderUriTest {

    @Test
    public void preservesSingleEncodingForNestedProjectAndFilePaths() {
        GitLabProperties properties = new GitLabProperties();
        properties.setUrl("https://gitlab.example.test/");
        properties.setToken("test-token");
        properties.setProjectId("group/repository");
        GitLabProviderImpl provider = new GitLabProviderImpl(RestClient.create(), properties);

        URI fileUri = provider.fileEndpointUri("design/design.md");
        URI fileAtRefUri = provider.fileEndpointUriWithRef("design/design.md", "feature/one");

        assertThat(fileUri.toASCIIString()).isEqualTo(
            "https://gitlab.example.test/api/v4/projects/group%2Frepository/repository/files/design%2Fdesign.md"
        );
        assertThat(fileAtRefUri.toASCIIString()).isEqualTo(
            "https://gitlab.example.test/api/v4/projects/group%2Frepository/repository/files/"
                + "design%2Fdesign.md?ref=feature/one"
        );
        assertThat(fileAtRefUri.toASCIIString()).doesNotContain("%252F");
    }
}
