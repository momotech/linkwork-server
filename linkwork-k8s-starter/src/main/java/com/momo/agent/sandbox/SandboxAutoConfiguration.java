package com.momo.agent.sandbox;

import com.momo.agent.sandbox.core.NoopSandboxOrchestrator;
import com.momo.agent.sandbox.core.SandboxOrchestrator;
import com.momo.agent.sandbox.provider.k8s.K8sSandboxProperties;
import com.momo.agent.sandbox.provider.k8s.K8sVolcanoOrchestratorImpl;
import com.momo.agent.sandbox.provider.k8s.PodGroupSpecGenerator;
import com.momo.agent.sandbox.provider.k8s.PodSpecGenerator;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@AutoConfiguration
@ConditionalOnClass(KubernetesClient.class)
@EnableConfigurationProperties(K8sSandboxProperties.class)
public class SandboxAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "momo.agent.sandbox", name = "provider", havingValue = "k8s-volcano")
    @ConditionalOnMissingBean
    public KubernetesClient sandboxKubernetesClient(K8sSandboxProperties properties) {
        return new KubernetesClientBuilder().withConfig(buildK8sConfig(properties)).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "momo.agent.sandbox", name = "provider", havingValue = "k8s-volcano")
    @ConditionalOnMissingBean
    public PodGroupSpecGenerator podGroupSpecGenerator() {
        return new PodGroupSpecGenerator();
    }

    @Bean
    @ConditionalOnProperty(prefix = "momo.agent.sandbox", name = "provider", havingValue = "k8s-volcano")
    @ConditionalOnMissingBean
    public PodSpecGenerator podSpecGenerator() {
        return new PodSpecGenerator();
    }

    @Bean
    @ConditionalOnProperty(prefix = "momo.agent.sandbox", name = "provider", havingValue = "k8s-volcano")
    @ConditionalOnMissingBean(SandboxOrchestrator.class)
    public SandboxOrchestrator k8sVolcanoOrchestrator(KubernetesClient kubernetesClient,
                                                      PodGroupSpecGenerator podGroupSpecGenerator,
                                                      PodSpecGenerator podSpecGenerator,
                                                      K8sSandboxProperties properties) {
        return new K8sVolcanoOrchestratorImpl(kubernetesClient, podGroupSpecGenerator, podSpecGenerator, properties);
    }

    @Bean
    @ConditionalOnMissingBean(SandboxOrchestrator.class)
    public SandboxOrchestrator noopSandboxOrchestrator() {
        return new NoopSandboxOrchestrator();
    }

    private Config buildK8sConfig(K8sSandboxProperties properties) {
        Config config;
        if (StringUtils.hasText(properties.getKubeconfigPath())) {
            config = readFromKubeconfig(properties.getKubeconfigPath());
        } else {
            config = Config.autoConfigure(null);
        }

        if (StringUtils.hasText(properties.getNamespace())) {
            config.setNamespace(properties.getNamespace());
        }
        return config;
    }

    private Config readFromKubeconfig(String rawPath) {
        String expandedPath = expandHome(rawPath);
        try {
            String kubeconfig = Files.readString(Path.of(expandedPath), StandardCharsets.UTF_8);
            return Config.fromKubeconfig(kubeconfig);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read kubeconfig from " + expandedPath, ex);
        }
    }

    private String expandHome(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (path.equals("~")) {
            return System.getProperty("user.home");
        }
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
