package com.linkwork.agent.storage;

import java.io.IOException;
import java.util.Map;

import com.linkwork.agent.storage.core.StorageProvider;
import com.linkwork.agent.storage.core.UnsupportedStorageProvider;
import com.linkwork.agent.storage.provider.nfs.NfsStorageProviderImpl;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class StoragePrefixCompatibilityTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void legacyPrefixBindsBeforeAutoConfiguration() throws IOException {
        String legacyBasePath = temporaryFolder.newFolder("legacy-base").getAbsolutePath();

        try (ConfigurableApplicationContext context = application(
                "agent.storage.nfs.base-path=" + legacyBasePath)) {
            AgentStorageProperties properties = context.getBean(AgentStorageProperties.class);

            assertThat(properties.getNfs().getBasePath())
                    .isEqualTo(legacyBasePath);
        }
    }

    @Test
    public void canonicalPrefixWinsPerPropertyWhileLegacyFillsMissingProperties()
            throws IOException {
        String legacyBasePath = temporaryFolder.newFolder("legacy-lower-priority").getAbsolutePath();
        String canonicalBasePath = temporaryFolder.newFolder("canonical-base").getAbsolutePath();

        try (ConfigurableApplicationContext context = application(
                "agent.storage.nfs.base-path=" + legacyBasePath,
                "linkwork.agent.storage.nfs.base-path=" + canonicalBasePath,
                "agent.storage.nfs.mount-path=/legacy-workspace",
                "agent.storage.nfs.uid=1100",
                "linkwork.agent.storage.nfs.uid=2200",
                "agent.storage.nfs.gid=3300")) {
            AgentStorageProperties properties = context.getBean(AgentStorageProperties.class);

            assertThat(properties.getNfs().getBasePath()).isEqualTo(canonicalBasePath);
            assertThat(properties.getNfs().getMountPath()).isEqualTo("/legacy-workspace");
            assertThat(properties.getNfs().getUid()).isEqualTo(2200);
            assertThat(properties.getNfs().getGid()).isEqualTo(3300);
        }
    }

    @Test
    public void legacyEnabledFlagControlsAutoConfiguration() {
        try (ConfigurableApplicationContext context = application(
                "agent.storage.enabled=false")) {
            assertThat(context.getBeansOfType(AgentStorageProperties.class)).isEmpty();
            assertThat(context.getBeansOfType(StorageProvider.class)).isEmpty();
        }
    }

    @Test
    public void legacyProviderControlsProviderSelection() {
        try (ConfigurableApplicationContext context = application(
                "agent.storage.provider=object-storage")) {
            assertThat(context.getBean(AgentStorageProperties.class).getProvider())
                    .isEqualTo("object-storage");
            assertThat(context.getBean(StorageProvider.class))
                    .isInstanceOf(UnsupportedStorageProvider.class);
        }
    }

    @Test
    public void canonicalProviderWinsOverLegacyProvider() throws IOException {
        String canonicalBasePath = temporaryFolder.newFolder("canonical-provider-base")
                .getAbsolutePath();

        try (ConfigurableApplicationContext context = application(
                "agent.storage.provider=object-storage",
                "linkwork.agent.storage.provider=nfs",
                "linkwork.agent.storage.nfs.base-path=" + canonicalBasePath)) {
            assertThat(context.getBean(AgentStorageProperties.class).getProvider()).isEqualTo("nfs");
            assertThat(context.getBean(StorageProvider.class)).isInstanceOf(NfsStorageProviderImpl.class);
        }
    }

    @Test
    public void legacyEnvironmentVariableUsesRelaxedBinding() throws IOException {
        String legacyBasePath = temporaryFolder.newFolder("legacy-environment-base")
                .getAbsolutePath();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "testLegacyEnvironment",
                Map.of("AGENT_STORAGE_NFS_BASE_PATH", legacyBasePath)));

        try (ConfigurableApplicationContext context = application(environment)) {
            assertThat(context.getBean(AgentStorageProperties.class).getNfs().getBasePath())
                    .isEqualTo(legacyBasePath);
        }
    }

    private ConfigurableApplicationContext application(String... properties) {
        return new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .properties("spring.main.banner-mode=off", "spring.main.log-startup-info=false")
                .run();
    }

    private ConfigurableApplicationContext application(StandardEnvironment environment) {
        return new SpringApplicationBuilder(TestApplication.class)
                .environment(environment)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "spring.main.log-startup-info=false")
                .run();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
