package com.linkwork.agent.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Makes the pre-0.1.1 {@code agent.storage} prefix available to the canonical
 * {@code linkwork.agent.storage} binding and auto-configuration conditions.
 */
public final class LegacyAgentStorageEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    static final String LEGACY_PREFIX = "agent.storage.";
    static final String CANONICAL_PREFIX = "linkwork.agent.storage.";
    static final String PROPERTY_SOURCE_NAME = "legacyAgentStorageCompatibility";

    private static final List<String> PROPERTY_SUFFIXES = List.of(
            "enabled",
            "provider",
            "nfs.base-path",
            "nfs.mount-path",
            "nfs.read-only",
            "nfs.uid",
            "nfs.gid");

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application) {
        Map<String, Object> compatibleProperties = new LinkedHashMap<>();

        for (String suffix : PROPERTY_SUFFIXES) {
            String canonicalKey = CANONICAL_PREFIX + suffix;
            if (environment.containsProperty(canonicalKey)) {
                continue;
            }

            String legacyValue = environment.getProperty(LEGACY_PREFIX + suffix);
            if (legacyValue != null) {
                compatibleProperties.put(canonicalKey, legacyValue);
            }
        }

        if (!compatibleProperties.isEmpty()) {
            environment.getPropertySources().addLast(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, compatibleProperties));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
