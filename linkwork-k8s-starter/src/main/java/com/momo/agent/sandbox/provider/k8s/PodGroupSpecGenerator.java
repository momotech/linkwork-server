package com.momo.agent.sandbox.provider.k8s;

import com.momo.agent.sandbox.core.model.ResourceSpec;
import com.momo.agent.sandbox.core.model.SandboxNaming;
import com.momo.agent.sandbox.core.model.SandboxSpec;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Volcano PodGroup generator.
 */
public class PodGroupSpecGenerator {

    public Map<String, Object> generate(SandboxSpec spec, String namespace, String queueName, String priorityClassName) {
        String podGroupName = SandboxNaming.podGroupName(spec.getSandboxId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiVersion", "scheduling.volcano.sh/v1beta1");
        result.put("kind", "PodGroup");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", podGroupName);
        metadata.put("namespace", namespace);
        metadata.put("labels", Map.of(
            "app", "linkwork-sandbox",
            "sandbox-id", spec.getSandboxId(),
            "service-id", spec.getSandboxId()
        ));
        result.put("metadata", metadata);

        Map<String, Object> pgSpec = new LinkedHashMap<>();
        pgSpec.put("minMember", Math.max(1, nullSafe(spec.getPodCount(), 1)));
        pgSpec.put("queue", queueName);
        if (StringUtils.hasText(priorityClassName)) {
            pgSpec.put("priorityClassName", priorityClassName);
        }
        pgSpec.put("minResources", buildMinResources(spec));
        result.put("spec", pgSpec);

        return result;
    }

    private Map<String, String> buildMinResources(SandboxSpec spec) {
        Map<String, String> resources = new HashMap<>();
        int podCount = Math.max(1, nullSafe(spec.getPodCount(), 1));
        ResourceSpec agent = spec.getAgentResources();

        double totalCpu = parseCpu(agent.getCpuRequest()) * podCount;
        resources.put("cpu", String.valueOf((int) Math.ceil(totalCpu)));

        long totalMemory = parseMemory(agent.getMemoryRequest()) * podCount;
        resources.put("memory", formatMemory(totalMemory));
        return resources;
    }

    private int nullSafe(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private double parseCpu(String cpu) {
        if (!StringUtils.hasText(cpu)) {
            return 1.0;
        }
        if (cpu.endsWith("m")) {
            return Double.parseDouble(cpu.substring(0, cpu.length() - 1)) / 1000.0;
        }
        return Double.parseDouble(cpu);
    }

    private long parseMemory(String memory) {
        if (!StringUtils.hasText(memory)) {
            return 2L * 1024 * 1024 * 1024;
        }
        String normalized = memory.trim();
        if (normalized.endsWith("Gi")) {
            return Long.parseLong(normalized.replace("Gi", "")) * 1024 * 1024 * 1024;
        }
        if (normalized.endsWith("Mi")) {
            return Long.parseLong(normalized.replace("Mi", "")) * 1024 * 1024;
        }
        return Long.parseLong(normalized);
    }

    private String formatMemory(long bytes) {
        long gib = Math.max(1, bytes / (1024 * 1024 * 1024));
        return gib + "Gi";
    }
}
