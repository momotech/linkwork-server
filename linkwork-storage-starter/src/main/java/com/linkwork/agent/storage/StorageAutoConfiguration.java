package com.linkwork.agent.storage;

import com.linkwork.agent.storage.core.StorageClient;
import com.linkwork.agent.storage.core.StorageProvider;
import com.linkwork.agent.storage.core.UnsupportedStorageProvider;
import com.linkwork.agent.storage.provider.nfs.NfsStorageProviderImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AgentStorageProperties.class)
@ConditionalOnProperty(prefix = "linkwork.agent.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "linkwork.agent.storage", name = "provider", havingValue = "nfs", matchIfMissing = true)
    public StorageProvider storageProvider(AgentStorageProperties properties) {
        return new NfsStorageProviderImpl(properties.getNfs());
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageProvider unsupportedStorageProvider(AgentStorageProperties properties) {
        return new UnsupportedStorageProvider(properties.getProvider());
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageClient storageClient(StorageProvider storageProvider) {
        return new StorageClient(storageProvider);
    }
}
