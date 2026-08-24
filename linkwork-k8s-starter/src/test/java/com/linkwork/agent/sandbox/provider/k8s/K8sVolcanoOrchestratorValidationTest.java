package com.linkwork.agent.sandbox.provider.k8s;

import com.linkwork.agent.sandbox.core.model.SandboxResult;
import com.linkwork.agent.sandbox.core.model.SandboxLifecycleMetadata;
import com.linkwork.agent.sandbox.core.model.SandboxScaleDownRequest;
import com.linkwork.agent.sandbox.core.model.SandboxScaleResult;
import com.linkwork.agent.sandbox.core.model.SandboxSpec;
import com.linkwork.agent.sandbox.core.model.SandboxStatus;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class K8sVolcanoOrchestratorValidationTest {

    @Test
    public void createRejectsMissingLifecycleIdentityBeforeCallingKubernetes() {
        K8sVolcanoOrchestratorImpl orchestrator = new K8sVolcanoOrchestratorImpl(
            null,
            new PodGroupSpecGenerator(),
            new PodSpecGenerator(),
            new K8sSandboxProperties()
        );
        SandboxSpec spec = new SandboxSpec();
        spec.setSandboxId("svc-1");
        spec.setPodCount(1);

        SandboxResult result = orchestrator.createSandbox(spec);

        assertEquals("LIFECYCLE_PRECONDITION_REQUIRED", result.getErrorCode());
    }

    @Test
    public void legacyDestroyOnlyAcceptsResourcesWithoutLifecycleMetadata() {
        assertFalse(K8sVolcanoOrchestratorImpl.isLifecycleManagedResource(
            new ObjectMetaBuilder()
                .withLabels(Map.of(
                    "app", "linkwork-sandbox",
                    "sandbox-id", "svc-1",
                    "service-id", "svc-1"
                ))
                .build()
        ));

        assertTrue(K8sVolcanoOrchestratorImpl.isLifecycleManagedResource(
            new ObjectMetaBuilder()
                .withLabels(Map.of(SandboxLifecycleMetadata.GENERATION, "generation-1"))
                .build()
        ));
        assertTrue(K8sVolcanoOrchestratorImpl.isLifecycleManagedResource(
            new ObjectMetaBuilder()
                .withLabels(Map.of(SandboxLifecycleMetadata.MANAGED, "true"))
                .build()
        ));
        assertTrue(K8sVolcanoOrchestratorImpl.isLifecycleManagedResource(
            new ObjectMetaBuilder()
                .withOwnerReferences(new OwnerReferenceBuilder()
                    .withApiVersion("scheduling.volcano.sh/v1beta1")
                    .withKind("PodGroup")
                    .withName("svc-svc-1-pg")
                    .withUid("pod-group-uid")
                    .build())
                .build()
        ));
    }

    @Test
    public void managedScaleOperationsRequireThePodGroupUid() {
        K8sVolcanoOrchestratorImpl orchestrator = new K8sVolcanoOrchestratorImpl(
            null,
            new PodGroupSpecGenerator(),
            new PodSpecGenerator(),
            new K8sSandboxProperties()
        );
        SandboxScaleDownRequest down = new SandboxScaleDownRequest();
        down.setSandboxId("svc-1");
        down.setPodName("svc-svc-1-0");
        down.setExpectedGeneration("generation-1");
        down.setExpectedFenceToken(1L);
        down.setTargetPodCount(0);

        SandboxScaleResult downResult = orchestrator.scaleDown(down);

        SandboxSpec up = new SandboxSpec();
        up.setSandboxId("svc-1");
        up.setLifecycleGeneration("generation-1");
        up.setFenceToken(1L);
        SandboxScaleResult upResult = orchestrator.scaleUp("svc-1", 2, "ai-worker", up);

        assertTrue(downResult.getErrorMessage().contains("expectedPodGroupUid"));
        assertTrue(upResult.getErrorMessage().contains("expectedPodGroupUid"));
    }

    @Test
    public void lifecycleIdentityRequiresMatchingServiceAndSandboxValues() {
        var metadata = new ObjectMetaBuilder()
            .withLabels(Map.of(
                SandboxLifecycleMetadata.MANAGED, "true",
                SandboxLifecycleMetadata.SERVICE_ID, "svc-1",
                SandboxLifecycleMetadata.SANDBOX_ID, "svc-1",
                SandboxLifecycleMetadata.GENERATION, "generation-1",
                SandboxLifecycleMetadata.FENCE_TOKEN, "1"
            ))
            .build();

        assertTrue(K8sVolcanoOrchestratorImpl.matchesLifecycleIdentity(
            metadata, "svc-1", "generation-1", 1L
        ));
        assertFalse(K8sVolcanoOrchestratorImpl.matchesLifecycleIdentity(
            metadata, "svc-2", "generation-1", 1L
        ));
    }

    @Test
    public void managedInventoryRejectsAnySameSandboxResourceOutsideTheExpectedGeneration() {
        Pod expected = new PodBuilder().withNewMetadata().withName("svc-1-0").withUid("uid-1")
            .endMetadata().build();
        Pod legacyOrDifferentGeneration = new PodBuilder().withNewMetadata()
            .withName("svc-1-1").withUid("uid-2")
            .endMetadata().build();

        assertFalse(K8sVolcanoOrchestratorImpl.hasConflictingManagedResources(
            List.of(expected), List.of(expected)
        ));
        assertTrue(K8sVolcanoOrchestratorImpl.hasConflictingManagedResources(
            List.of(expected, legacyOrDifferentGeneration), List.of(expected)
        ));
    }

    @Test
    public void completeInventoryCountsLegacyConfigMapsAndSecrets() {
        SandboxStatus status = new SandboxStatus();

        K8sVolcanoOrchestratorImpl.completeResourceInventory(
            status,
            false,
            List.of(new ConfigMapBuilder().withNewMetadata()
                .withName("svc-1-agent-config")
                .addToLabels("sandbox-id", "svc-1")
                .addToLabels("managed-by", "linkwork-k8s-starter")
                .endMetadata().build()),
            List.of(new SecretBuilder().withNewMetadata()
                .withName("svc-1-secret")
                .addToLabels("sandbox-id", "svc-1")
                .addToLabels("managed-by", "linkwork-k8s-starter")
                .endMetadata().build())
        );

        assertEquals(Integer.valueOf(1), status.getConfigMapCount());
        assertEquals(Integer.valueOf(1), status.getSecretCount());
        assertFalse(status.isLifecycleManaged());
        assertTrue(status.isResourceInventoryComplete());
    }

    @Test
    public void completeInventoryIncludesAuxiliaryLifecycleIdentity() {
        SandboxStatus status = new SandboxStatus();

        K8sVolcanoOrchestratorImpl.completeResourceInventory(
            status,
            false,
            List.of(),
            List.of(new SecretBuilder().withNewMetadata()
                .withName("svc-1-secret")
                .addToLabels(SandboxLifecycleMetadata.GENERATION, "generation-1")
                .endMetadata().build())
        );

        assertTrue(status.isLifecycleManaged());
        assertTrue(status.isResourceInventoryComplete());
    }

    @Test
    public void legacyAuxiliaryDiscoveryRequiresHistoricalOwnershipAndExactNames() {
        String sandboxId = "1000000000108";
        ConfigMap managed = new ConfigMapBuilder().withNewMetadata()
            .withName("managed-config").withUid("managed-config-uid")
            .addToLabels("sandbox-id", sandboxId)
            .addToLabels("managed-by", "linkwork-k8s-starter")
            .endMetadata().build();
        ConfigMap legacy = new ConfigMapBuilder().withNewMetadata()
            .withName("svc-" + sandboxId + "-agent-config").withUid("legacy-config-uid")
            .addToLabels("app", "ai-worker-service")
            .addToLabels("service-id", sandboxId)
            .endMetadata().build();
        ConfigMap shared = new ConfigMapBuilder().withNewMetadata()
            .withName("runner-scripts").withUid("shared-config-uid")
            .addToLabels("app", "ai-worker-service")
            .addToLabels("service-id", sandboxId)
            .endMetadata().build();
        ConfigMap wrongService = new ConfigMapBuilder().withNewMetadata()
            .withName("svc-" + sandboxId + "-agent-config").withUid("wrong-service-config-uid")
            .addToLabels("app", "ai-worker-service")
            .addToLabels("service-id", "another-service")
            .endMetadata().build();

        List<ConfigMap> configMaps = K8sVolcanoOrchestratorImpl.mergeSandboxAuxiliaryResources(
            sandboxId,
            "svc-" + sandboxId + "-agent-config",
            List.of(managed, legacy),
            List.of(legacy, shared, wrongService)
        );

        assertEquals(List.of("managed-config", "svc-" + sandboxId + "-agent-config"),
            configMaps.stream().map(item -> item.getMetadata().getName()).toList());

        Secret legacySecret = new SecretBuilder().withNewMetadata()
            .withName("svc-" + sandboxId + "-token").withUid("legacy-secret-uid")
            .addToLabels("app", "ai-worker-service")
            .addToLabels("service-id", sandboxId)
            .endMetadata().build();
        Secret wrongName = new SecretBuilder().withNewMetadata()
            .withName("shared-token").withUid("shared-secret-uid")
            .addToLabels("app", "ai-worker-service")
            .addToLabels("service-id", sandboxId)
            .endMetadata().build();

        List<Secret> secrets = K8sVolcanoOrchestratorImpl.mergeSandboxAuxiliaryResources(
            sandboxId,
            "svc-" + sandboxId + "-token",
            List.<Secret>of(),
            List.of(legacySecret, wrongName)
        );

        assertEquals(List.of("svc-" + sandboxId + "-token"),
            secrets.stream().map(item -> item.getMetadata().getName()).toList());
    }
}
