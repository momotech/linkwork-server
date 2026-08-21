package com.linkwork.agent.sandbox.provider.k8s;

import com.linkwork.agent.sandbox.core.model.SandboxLifecycleMetadata;
import com.linkwork.agent.sandbox.core.model.SandboxNaming;
import com.linkwork.agent.sandbox.core.model.SandboxSpec;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LifecycleMetadataGeneratorTest {

    @Test
    public void podGeneratorAppliesCanonicalLifecycleLabelsAfterCallerLabels() {
        SandboxSpec spec = lifecycleSpec();
        spec.setLabels(Map.of(
            SandboxLifecycleMetadata.GENERATION, "stale-generation",
            SandboxLifecycleMetadata.FENCE_TOKEN, "1"
        ));

        K8sSandboxProperties properties = new K8sSandboxProperties();
        Pod pod = new PodSpecGenerator().generate(spec, 0, "robot", properties);

        assertLifecycleLabels(pod.getMetadata().getLabels());
        assertEquals(SandboxNaming.podGroupName("svc-1"),
            pod.getMetadata().getAnnotations().get("scheduling.volcano.sh/group-name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void podGroupGeneratorEmitsTheSameLifecycleIdentity() {
        SandboxSpec spec = lifecycleSpec();
        spec.setLabels(Map.of(
            "service-id", "231",
            SandboxLifecycleMetadata.GENERATION, "stale-generation",
            SandboxLifecycleMetadata.FENCE_TOKEN, "1"
        ));

        Map<String, Object> podGroup = new PodGroupSpecGenerator().generate(spec, "robot", "default", null);
        Map<String, Object> metadata = (Map<String, Object>) podGroup.get("metadata");
        Map<String, String> labels = (Map<String, String>) metadata.get("labels");

        assertLifecycleLabels(labels);
        assertEquals("231", labels.get("service-id"));
        assertEquals(1, ((Map<String, Object>) podGroup.get("spec")).get("minMember"));
    }

    private SandboxSpec lifecycleSpec() {
        SandboxSpec spec = new SandboxSpec();
        spec.setSandboxId("svc-1");
        spec.setPodCount(1);
        spec.setLifecycleGeneration("generation-2");
        spec.setFenceToken(2L);
        return spec;
    }

    private void assertLifecycleLabels(Map<String, String> labels) {
        assertEquals("true", labels.get(SandboxLifecycleMetadata.MANAGED));
        assertEquals("svc-1", labels.get(SandboxLifecycleMetadata.SERVICE_ID));
        assertEquals("svc-1", labels.get(SandboxLifecycleMetadata.SANDBOX_ID));
        assertEquals("generation-2", labels.get(SandboxLifecycleMetadata.GENERATION));
        assertEquals("2", labels.get(SandboxLifecycleMetadata.FENCE_TOKEN));
    }
}
