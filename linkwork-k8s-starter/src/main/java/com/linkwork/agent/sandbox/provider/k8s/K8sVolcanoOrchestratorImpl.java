package com.linkwork.agent.sandbox.provider.k8s;

import com.linkwork.agent.sandbox.core.SandboxOrchestrator;
import com.linkwork.agent.sandbox.core.model.SandboxMode;
import com.linkwork.agent.sandbox.core.model.SandboxDestroyRequest;
import com.linkwork.agent.sandbox.core.model.SandboxLifecycleMetadata;
import com.linkwork.agent.sandbox.core.model.SandboxNaming;
import com.linkwork.agent.sandbox.core.model.SandboxPodStatus;
import com.linkwork.agent.sandbox.core.model.SandboxPreview;
import com.linkwork.agent.sandbox.core.model.SandboxQuery;
import com.linkwork.agent.sandbox.core.model.SandboxResult;
import com.linkwork.agent.sandbox.core.model.SandboxScaleDownRequest;
import com.linkwork.agent.sandbox.core.model.SandboxScaleResult;
import com.linkwork.agent.sandbox.core.model.SandboxSpec;
import com.linkwork.agent.sandbox.core.model.SandboxStatus;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.DeleteOptions;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Preconditions;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * K8s + Volcano sandbox orchestrator.
 */
public class K8sVolcanoOrchestratorImpl implements SandboxOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(K8sVolcanoOrchestratorImpl.class);

    private final KubernetesClient kubernetesClient;
    private final PodGroupSpecGenerator podGroupSpecGenerator;
    private final PodSpecGenerator podSpecGenerator;
    private final K8sSandboxProperties properties;

    public K8sVolcanoOrchestratorImpl(KubernetesClient kubernetesClient,
                                      PodGroupSpecGenerator podGroupSpecGenerator,
                                      PodSpecGenerator podSpecGenerator,
                                      K8sSandboxProperties properties) {
        this.kubernetesClient = kubernetesClient;
        this.podGroupSpecGenerator = podGroupSpecGenerator;
        this.podSpecGenerator = podSpecGenerator;
        this.properties = properties;
    }

    @Override
    public SandboxResult createSandbox(SandboxSpec spec) {
        if (spec == null || !StringUtils.hasText(spec.getSandboxId())) {
            return SandboxResult.failed(null, "INVALID_SANDBOX_ID", "sandboxId is required");
        }
        if (spec.getPodCount() == null || spec.getPodCount() <= 0) {
            return SandboxResult.failed(spec.getSandboxId(), "INVALID_POD_COUNT", "podCount must be greater than 0");
        }
        if (!StringUtils.hasText(spec.getLifecycleGeneration()) || spec.getFenceToken() == null) {
            return SandboxResult.failed(spec.getSandboxId(), "LIFECYCLE_PRECONDITION_REQUIRED",
                "lifecycleGeneration and fenceToken are required");
        }
        if (!properties.isCreatePodGroup()) {
            return SandboxResult.failed(spec.getSandboxId(), "PODGROUP_REQUIRED",
                "managed sandboxes require PodGroup creation");
        }

        String namespace = resolveNamespace(spec.getNamespace());
        String podGroupName = SandboxNaming.podGroupName(spec.getSandboxId());
        List<String> podNames = new ArrayList<>();
        GenericKubernetesResource podGroup = null;

        try {
            assertSandboxNameAvailable(spec.getSandboxId(), namespace);
            createImagePullSecretIfNeeded(spec, namespace);
            podGroup = createPodGroup(spec, namespace);
            podGroup = waitForPodGroupReady(
                namespace,
                podGroupName,
                spec.getLifecycleGeneration(),
                spec.getFenceToken(),
                properties.getWaitPodGroupReadySeconds()
            );
            OwnerReference podGroupOwner = podGroupOwnerReference(podGroup);
            createManagedResources(spec, namespace, podGroupOwner);

            for (int i = 0; i < spec.getPodCount(); i++) {
                Pod pod = podSpecGenerator.generate(spec, i, namespace, properties);
                pod.getMetadata().setOwnerReferences(List.of(podGroupOwner));
                Pod created = createPodWithRetry(namespace, pod, 3, 1000L);
                podNames.add(created.getMetadata().getName());
            }

            verifyCreatedSandbox(spec, namespace, podGroup, podNames);
            String scheduledNode = waitFirstScheduledNode(namespace, podNames, properties.getWaitScheduledNodeSeconds());
            SandboxResult result = SandboxResult.success(spec.getSandboxId(), podGroupName, podNames, scheduledNode);
            result.setPodGroupUid(podGroup.getMetadata().getUid());
            result.setLifecycleGeneration(spec.getLifecycleGeneration());
            result.setFenceToken(spec.getFenceToken());
            return result;
        } catch (Exception ex) {
            log.error("Failed to create sandbox {}: {}", spec.getSandboxId(), ex.getMessage(), ex);
            if (ex instanceof LifecycleConflictException
                || (podGroup == null && ex instanceof KubernetesClientException clientException
                && clientException.getCode() == 409)) {
                return SandboxResult.failed(spec.getSandboxId(), "SANDBOX_NAME_CONFLICT", ex.getMessage());
            }
            SandboxDestroyRequest cleanupRequest = new SandboxDestroyRequest();
            cleanupRequest.setSandboxId(spec.getSandboxId());
            cleanupRequest.setNamespace(namespace);
            cleanupRequest.setExpectedGeneration(spec.getLifecycleGeneration());
            cleanupRequest.setExpectedFenceToken(spec.getFenceToken());
            cleanupRequest.setExpectedPodGroupUid(podGroup == null || podGroup.getMetadata() == null
                ? null : podGroup.getMetadata().getUid());
            cleanupRequest.setGracePeriodSeconds(0L);
            SandboxResult cleanup = destroySandbox(cleanupRequest);
            if (!cleanup.isSuccess()) {
                String cleanupMessage = cleanup.getErrorMessage() == null ? "unknown cleanup error" : cleanup.getErrorMessage();
                return SandboxResult.failed(spec.getSandboxId(), "CREATE_FAILED_CLEANUP_PENDING",
                    ex.getMessage() + "; compensation failed: " + cleanupMessage);
            }
            return SandboxResult.failed(spec.getSandboxId(), "K8S_CREATE_FAILED", ex.getMessage());
        }
    }

    @Override
    public SandboxPreview previewSandbox(SandboxSpec spec) {
        SandboxPreview preview = new SandboxPreview();
        if (spec == null || !StringUtils.hasText(spec.getSandboxId())) {
            return preview;
        }
        String namespace = resolveNamespace(spec.getNamespace());
        preview.setSandboxId(spec.getSandboxId());
        preview.setPodGroupSpec(podGroupSpecGenerator.generate(
            spec,
            namespace,
            resolveQueueName(spec),
            resolvePriorityClassName(spec)
        ));

        List<Map<String, Object>> podSpecs = new ArrayList<>();
        int podCount = Math.max(1, spec.getPodCount() == null ? 1 : spec.getPodCount());
        for (int i = 0; i < podCount; i++) {
            Pod pod = podSpecGenerator.generate(spec, i, namespace, properties);
            podSpecs.add(toPreviewPodMap(pod));
        }
        preview.setPodSpecs(podSpecs);
        return preview;
    }

    @Override
    public SandboxResult stopSandbox(String sandboxId, String namespace, boolean graceful) {
        SandboxDestroyRequest request = SandboxDestroyRequest.legacy(sandboxId, namespace);
        request.setGracePeriodSeconds(graceful ? 30L : 0L);
        return destroyInternal(request, "K8S_STOP_FAILED");
    }

    @Override
    public SandboxResult destroySandbox(String sandboxId, String namespace) {
        return destroySandbox(SandboxDestroyRequest.legacy(sandboxId, namespace));
    }

    @Override
    public SandboxResult destroySandbox(SandboxDestroyRequest request) {
        return destroyInternal(request, "K8S_DESTROY_FAILED");
    }

    private SandboxResult destroyInternal(SandboxDestroyRequest request, String errorCode) {
        if (request == null || !StringUtils.hasText(request.getSandboxId())) {
            return SandboxResult.failed(null, "INVALID_SANDBOX_ID", "sandboxId is required");
        }
        String sandboxId = request.getSandboxId();
        if (!request.isAllowLegacy()
            && (!StringUtils.hasText(request.getExpectedGeneration()) || request.getExpectedFenceToken() == null)) {
            return SandboxResult.failed(sandboxId, "LIFECYCLE_PRECONDITION_REQUIRED",
                "expectedGeneration and expectedFenceToken are required");
        }

        String resolvedNamespace = resolveNamespace(request.getNamespace());
        GenericKubernetesResource podGroup;
        List<Pod> pods;
        List<ConfigMap> configMaps;
        List<Secret> secrets;
        try {
            podGroup = getPodGroup(resolvedNamespace, sandboxId);
            String preconditionError = validateDestroyPreconditions(request, podGroup);
            if (preconditionError != null) {
                return SandboxResult.failed(sandboxId, "LIFECYCLE_PRECONDITION_FAILED", preconditionError);
            }
            pods = filterOwnedResources(listPodsBySandboxId(resolvedNamespace, sandboxId), request);
            configMaps = filterOwnedResources(listManagedConfigMaps(sandboxId, resolvedNamespace), request);
            secrets = filterOwnedResources(listManagedSecrets(sandboxId, resolvedNamespace), request);
            if (!request.isAllowLegacy() && hasConflictingManagedPods(resolvedNamespace, request, pods)) {
                return SandboxResult.failed(sandboxId, "LIFECYCLE_PRECONDITION_FAILED",
                    "managed pods belong to a different lifecycle generation");
            }
        } catch (Exception ex) {
            log.error("Failed to inventory sandbox {} before destroy: {}", sandboxId, ex.getMessage(), ex);
            return SandboxResult.failed(sandboxId, errorCode, "inventory failed: " + ex.getMessage());
        }

        List<String> podNames = pods.stream()
            .map(pod -> pod.getMetadata().getName())
            .collect(Collectors.toList());
        List<String> errors = new ArrayList<>();

        if (podGroup != null) {
            captureDeleteError(errors, "PodGroup/" + podGroup.getMetadata().getName(), () ->
                deleteResourceWithUidPrecondition(
                    podGroupPath(resolvedNamespace, podGroup.getMetadata().getName()),
                    podGroup.getMetadata(),
                    request.getGracePeriodSeconds(),
                    DeletionPropagation.FOREGROUND
                ));
        }
        for (Pod pod : pods) {
            captureDeleteError(errors, "Pod/" + pod.getMetadata().getName(), () ->
                deleteResourceWithUidPrecondition(
                    coreResourcePath(resolvedNamespace, "pods", pod.getMetadata().getName()),
                    pod.getMetadata(),
                    request.getGracePeriodSeconds(),
                    DeletionPropagation.BACKGROUND
                ));
        }
        for (ConfigMap configMap : configMaps) {
            captureDeleteError(errors, "ConfigMap/" + configMap.getMetadata().getName(), () ->
                deleteResourceWithUidPrecondition(
                    coreResourcePath(resolvedNamespace, "configmaps", configMap.getMetadata().getName()),
                    configMap.getMetadata(),
                    0L,
                    DeletionPropagation.BACKGROUND
                ));
        }
        for (Secret secret : secrets) {
            captureDeleteError(errors, "Secret/" + secret.getMetadata().getName(), () ->
                deleteResourceWithUidPrecondition(
                    coreResourcePath(resolvedNamespace, "secrets", secret.getMetadata().getName()),
                    secret.getMetadata(),
                    0L,
                    DeletionPropagation.BACKGROUND
                ));
        }

        try {
            if (!waitForGenerationGone(request, resolvedNamespace, 30)) {
                errors.add("generation resources still exist after delete");
            }
        } catch (Exception ex) {
            errors.add("verification failed: " + ex.getMessage());
            log.error("Failed to verify sandbox {} destruction: {}", sandboxId, ex.getMessage(), ex);
        }

        if (!errors.isEmpty()) {
            return SandboxResult.failed(sandboxId, errorCode, String.join("; ", errors));
        }
        SandboxResult result = SandboxResult.success(
            sandboxId,
            SandboxNaming.podGroupName(sandboxId),
            podNames,
            null
        );
        result.setPodGroupUid(podGroup == null ? request.getExpectedPodGroupUid() : podGroup.getMetadata().getUid());
        result.setLifecycleGeneration(request.getExpectedGeneration());
        result.setFenceToken(request.getExpectedFenceToken());
        return result;
    }

    @Override
    public SandboxStatus querySandbox(String sandboxId, String namespace) {
        return querySandbox(SandboxQuery.of(sandboxId, namespace));
    }

    @Override
    public SandboxStatus querySandbox(SandboxQuery query) {
        SandboxStatus status = new SandboxStatus();
        String sandboxId = query == null ? null : query.getSandboxId();
        String namespace = query == null ? null : query.getNamespace();
        status.setSandboxId(sandboxId);
        status.setNamespace(resolveNamespace(namespace));
        status.setObservedAt(Instant.now());

        if (!StringUtils.hasText(sandboxId)) {
            status.setMessage("sandboxId is required");
            return status;
        }

        try {
            List<Pod> podList = listPodsBySandboxId(status.getNamespace(), sandboxId);
            if (StringUtils.hasText(query.getExpectedGeneration()) || query.getExpectedFenceToken() != null) {
                podList = podList.stream()
                    .filter(pod -> matchesExpectedLifecycle(pod.getMetadata(), query))
                    .collect(Collectors.toList());
            }
            List<SandboxPodStatus> pods = new ArrayList<>();
            int readyCount = 0;

            for (Pod pod : podList) {
                SandboxPodStatus podStatus = new SandboxPodStatus();
                podStatus.setPodName(pod.getMetadata().getName());
                podStatus.setPhase(pod.getStatus() == null ? null : pod.getStatus().getPhase());
                podStatus.setNodeName(pod.getSpec() == null ? null : pod.getSpec().getNodeName());
                podStatus.setReady(isReadyPod(pod));
                if (podStatus.isReady()) {
                    readyCount++;
                }
                pods.add(podStatus);
            }

            status.setPods(pods);
            status.setTotalPods(pods.size());
            status.setReadyPods(readyCount);

            GenericKubernetesResource podGroup = getPodGroup(status.getNamespace(), sandboxId);
            if (podGroup != null && podGroup.getMetadata() != null) {
                Map<String, String> labels = labelsOf(podGroup.getMetadata());
                status.setPodGroupUid(podGroup.getMetadata().getUid());
                status.setLifecycleGeneration(labels.get(SandboxLifecycleMetadata.GENERATION));
                status.setFenceToken(parseLong(labels.get(SandboxLifecycleMetadata.FENCE_TOKEN)));
                if (!matchesExpectedLifecycle(podGroup.getMetadata(), query)) {
                    status.setMessage("PodGroup lifecycle precondition mismatch");
                }
            }
            Map<String, Integer> podGroupCounters = queryPodGroupCounters(sandboxId, status.getNamespace());
            status.setPodGroupPhase(queryPodGroupPhase(sandboxId, status.getNamespace()));
            status.setPodGroupMinMember(podGroupCounters.get("minMember"));
            status.setPodGroupRunning(podGroupCounters.get("running"));
            status.setPodGroupSucceeded(podGroupCounters.get("succeeded"));
            status.setPodGroupFailed(podGroupCounters.get("failed"));
            status.setPodGroupPending(podGroupCounters.get("pending"));

            if (pods.isEmpty() && !StringUtils.hasText(status.getMessage())) {
                status.setMessage("No pods found for sandbox");
            }
            return status;
        } catch (Exception ex) {
            status.setMessage("Failed to query sandbox: " + ex.getMessage());
            log.warn("Failed to query sandbox {}: {}", sandboxId, ex.getMessage());
            return status;
        }
    }

    @Override
    public SandboxScaleResult scaleDown(String sandboxId, String podName, String namespace) {
        return scaleDown(SandboxScaleDownRequest.legacy(sandboxId, podName, namespace));
    }

    @Override
    public SandboxScaleResult scaleDown(SandboxScaleDownRequest request) {
        if (request == null || !StringUtils.hasText(request.getSandboxId())) {
            return SandboxScaleResult.failed(null, "sandboxId is required");
        }
        String sandboxId = request.getSandboxId();
        String podName = request.getPodName();
        if (!StringUtils.hasText(podName)) {
            return SandboxScaleResult.failed(sandboxId, "podName is required for scale-down");
        }
        if (!request.isAllowLegacy()
            && (!StringUtils.hasText(request.getExpectedGeneration())
            || request.getExpectedFenceToken() == null
            || request.getTargetPodCount() == null
            || request.getTargetPodCount() < 0)) {
            return SandboxScaleResult.failed(sandboxId,
                "expectedGeneration, expectedFenceToken and non-negative targetPodCount are required");
        }
        String resolvedNamespace = resolveNamespace(request.getNamespace());

        try {
            GenericKubernetesResource podGroup = getPodGroup(resolvedNamespace, sandboxId);
            SandboxDestroyRequest destroyRequest = toDestroyRequest(request);
            String preconditionError = validateDestroyPreconditions(destroyRequest, podGroup);
            if (preconditionError != null) {
                return SandboxScaleResult.failed(sandboxId, preconditionError);
            }
            List<Pod> ownedPods = filterOwnedResources(
                listPodsBySandboxId(resolvedNamespace, sandboxId),
                destroyRequest
            );
            Pod targetPod = ownedPods.stream()
                .filter(pod -> podName.equals(pod.getMetadata().getName()))
                .findFirst()
                .orElse(null);
            if (targetPod == null) {
                return SandboxScaleResult.failed(sandboxId, "Pod not found: " + podName);
            }
            List<String> activePods = ownedPods.stream()
                .filter(this::isActivePod)
                .map(pod -> pod.getMetadata().getName())
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
            int previousCount = activePods.size();
            boolean targetIsActive = activePods.contains(podName);
            int targetPodCount = request.getTargetPodCount() == null
                ? Math.max(0, previousCount - (targetIsActive ? 1 : 0))
                : request.getTargetPodCount();

            if (previousCount <= 1 && targetPodCount == 0) {
                SandboxResult destroyResult = destroySandbox(destroyRequest);
                if (!destroyResult.isSuccess()) {
                    return SandboxScaleResult.failed(sandboxId,
                        "full destroy for last pod failed: " + destroyResult.getErrorMessage());
                }
                return SandboxScaleResult.success(
                    sandboxId,
                    "DESTROY_SANDBOX",
                    previousCount,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(podName)
                );
            }
            if (previousCount <= 1) {
                return SandboxScaleResult.failed(sandboxId,
                    "REPAIR_REQUIRED: refusing to delete the last active pod while targetPodCount is positive");
            }

            deleteResourceWithUidPrecondition(
                coreResourcePath(resolvedNamespace, "pods", podName),
                targetPod.getMetadata(),
                0L,
                DeletionPropagation.BACKGROUND
            );
            if (!waitForResourceGone(
                () -> kubernetesClient.pods().inNamespace(resolvedNamespace).withName(podName).get(),
                targetPod.getMetadata().getUid(),
                30
            )) {
                return SandboxScaleResult.failed(sandboxId, "Pod still exists after scale-down: " + podName);
            }

            List<String> updated = new ArrayList<>(activePods);
            updated.remove(podName);
            return SandboxScaleResult.success(
                sandboxId,
                "SCALE_DOWN",
                previousCount,
                updated.size(),
                targetPodCount,
                updated,
                List.of(),
                List.of(podName)
            );
        } catch (Exception ex) {
            log.error("Failed to scale down sandbox {}: {}", sandboxId, ex.getMessage(), ex);
            return SandboxScaleResult.failed(sandboxId, ex.getMessage());
        }
    }

    @Override
    public SandboxScaleResult scaleUp(String sandboxId, int targetPodCount, String namespace, SandboxSpec templateSpec) {
        if (!StringUtils.hasText(sandboxId)) {
            return SandboxScaleResult.failed(null, "sandboxId is required");
        }
        String resolvedNamespace = resolveNamespace(namespace);
        if (targetPodCount <= 0) {
            return SandboxScaleResult.failed(sandboxId, "targetPodCount must be greater than 0");
        }
        if (templateSpec == null
            || !StringUtils.hasText(templateSpec.getLifecycleGeneration())
            || templateSpec.getFenceToken() == null) {
            return SandboxScaleResult.failed(sandboxId,
                "templateSpec with lifecycleGeneration and fenceToken is required for scale-up");
        }

        try {
            GenericKubernetesResource podGroup = getPodGroup(resolvedNamespace, sandboxId);
            String preconditionError = validateScaleUpPreconditions(templateSpec, podGroup);
            if (preconditionError != null) {
                return SandboxScaleResult.failed(sandboxId, preconditionError);
            }
            SandboxDestroyRequest lifecycle = lifecycleRequest(templateSpec, resolvedNamespace);
            List<Pod> existingPods = filterOwnedResources(
                listPodsBySandboxId(resolvedNamespace, sandboxId),
                lifecycle
            );
            List<String> runningPods = existingPods.stream()
                .filter(this::isActivePod)
                .map(pod -> pod.getMetadata().getName())
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
            int previousCount = runningPods.size();
            if (previousCount == 0) {
                return SandboxScaleResult.failed(sandboxId,
                    "REPAIR_REQUIRED: PodGroup has no active members; full sandbox rebuild is required");
            }
            cleanupTerminatedPods(sandboxId, resolvedNamespace, lifecycle);
            existingPods = filterOwnedResources(listPodsBySandboxId(resolvedNamespace, sandboxId), lifecycle);
            if (targetPodCount <= previousCount) {
                return SandboxScaleResult.success(
                    sandboxId,
                    "NO_CHANGE",
                    previousCount,
                    previousCount,
                    targetPodCount,
                    runningPods,
                    List.of(),
                    List.of()
                );
            }

            int podsToCreate = targetPodCount - previousCount;
            List<Integer> existingIndices = existingPods.stream()
                .map(pod -> extractPodIndex(pod.getMetadata().getName()))
                .filter(index -> index >= 0)
                .collect(Collectors.toCollection(ArrayList::new));

            List<String> addedPods = new ArrayList<>();
            int nextIndex = existingIndices.isEmpty() ? 0 : Collections.max(existingIndices) + 1;
            SandboxSpec fallbackSpec = buildFallbackScaleSpec(sandboxId, templateSpec, existingPods, resolvedNamespace);
            OwnerReference podGroupOwner = podGroupOwnerReference(podGroup);

            for (int i = 0; i < podsToCreate; i++) {
                while (existingIndices.contains(nextIndex)) {
                    nextIndex++;
                }

                Pod pod = podSpecGenerator.generate(fallbackSpec, nextIndex, resolvedNamespace, properties);
                pod.getMetadata().setOwnerReferences(List.of(podGroupOwner));
                Pod created = createPodWithRetry(resolvedNamespace, pod, 3, 1000L);
                String podName = created.getMetadata().getName();
                addedPods.add(podName);
                runningPods.add(podName);
                existingIndices.add(nextIndex);
                nextIndex++;
            }

            verifyScaledSandbox(fallbackSpec, resolvedNamespace, podGroup, runningPods);
            runningPods.sort(String::compareTo);
            return SandboxScaleResult.success(
                sandboxId,
                "SCALE_UP",
                previousCount,
                runningPods.size(),
                targetPodCount,
                runningPods,
                addedPods,
                List.of()
            );
        } catch (Exception ex) {
            log.error("Failed to scale up sandbox {}: {}", sandboxId, ex.getMessage(), ex);
            return SandboxScaleResult.failed(sandboxId, ex.getMessage());
        }
    }

    @Override
    public List<String> listRunningPods(String sandboxId, String namespace) {
        String resolvedNamespace = resolveNamespace(namespace);
        return listPodsBySandboxId(resolvedNamespace, sandboxId).stream()
            .filter(this::isReadyPod)
            .map(pod -> pod.getMetadata().getName())
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());
    }

    private GenericKubernetesResource createPodGroup(SandboxSpec spec, String namespace) {
        Map<String, Object> podGroupMap = podGroupSpecGenerator.generate(
            spec,
            namespace,
            resolveQueueName(spec),
            resolvePriorityClassName(spec)
        );

        GenericKubernetesResource podGroup = new GenericKubernetesResource();
        podGroup.setApiVersion("scheduling.volcano.sh/v1beta1");
        podGroup.setKind("PodGroup");
        Map<String, String> labels = new LinkedHashMap<>();
        Object metadataValue = podGroupMap.get("metadata");
        if (metadataValue instanceof Map<?, ?> metadataMap) {
            Object labelsValue = metadataMap.get("labels");
            if (labelsValue instanceof Map<?, ?> generatedLabels) {
                generatedLabels.forEach((key, value) -> labels.put(String.valueOf(key), String.valueOf(value)));
            }
        }
        podGroup.setMetadata(new ObjectMetaBuilder()
            .withName(SandboxNaming.podGroupName(spec.getSandboxId()))
            .withNamespace(namespace)
            .addToLabels(labels)
            .build());
        podGroup.setAdditionalProperties(Map.of("spec", podGroupMap.get("spec")));

        return kubernetesClient.genericKubernetesResources("scheduling.volcano.sh/v1beta1", "PodGroup")
            .inNamespace(namespace)
            .resource(podGroup)
            .create();
    }

    private GenericKubernetesResource waitForPodGroupReady(String namespace,
                                                            String podGroupName,
                                                            String expectedGeneration,
                                                            Long expectedFenceToken,
                                                            int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutSeconds) * 1000L;
        while (System.currentTimeMillis() < deadline) {
            GenericKubernetesResource podGroup = getPodGroupByName(namespace, podGroupName);
            if (podGroup != null) {
                assertLifecycleMetadata(
                    podGroup.getMetadata(),
                    expectedGeneration,
                    expectedFenceToken,
                    "PodGroup/" + podGroupName
                );
                String phase = podGroupPhase(podGroup);
                if ("Inqueue".equalsIgnoreCase(phase) || "Running".equalsIgnoreCase(phase)) {
                    return podGroup;
                }
                if (StringUtils.hasText(phase)
                    && !"Pending".equalsIgnoreCase(phase)) {
                    throw new IllegalStateException(
                        "PodGroup " + podGroupName + " entered unexpected phase " + phase
                    );
                }
            }
            sleep(500L);
        }
        throw new IllegalStateException(
            "PodGroup " + podGroupName + " did not reach Inqueue within " + timeoutSeconds + "s"
        );
    }

    private Pod createPodWithRetry(String namespace, Pod pod, int maxRetries, long retryDelayMs) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return kubernetesClient.pods().inNamespace(namespace).resource(pod).create();
            } catch (Exception ex) {
                lastException = ex;
                String message = ex.getMessage() == null ? "" : ex.getMessage();
                boolean retryable = message.contains("podgroup phase is Pending") || message.contains("object is being deleted");
                if (!retryable || attempt == maxRetries) {
                    break;
                }
                sleep(retryDelayMs);
            }
        }
        throw new IllegalStateException("Failed to create pod " + pod.getMetadata().getName(), lastException);
    }

    private void cleanupTerminatedPods(String sandboxId,
                                       String namespace,
                                       SandboxDestroyRequest lifecycle) {
        List<Pod> terminated = filterOwnedResources(listPodsBySandboxId(namespace, sandboxId), lifecycle).stream()
            .filter(pod -> pod.getStatus() != null)
            .filter(pod -> {
                String phase = pod.getStatus().getPhase();
                return "Succeeded".equalsIgnoreCase(phase) || "Failed".equalsIgnoreCase(phase);
            })
            .collect(Collectors.toList());
        if (terminated.isEmpty()) {
            return;
        }
        for (Pod pod : terminated) {
            deleteResourceWithUidPrecondition(
                coreResourcePath(namespace, "pods", pod.getMetadata().getName()),
                pod.getMetadata(),
                0L,
                DeletionPropagation.BACKGROUND
            );
        }
        for (Pod pod : terminated) {
            if (!waitForResourceGone(
                () -> kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(pod.getMetadata().getName())
                    .get(),
                pod.getMetadata().getUid(),
                30
            )) {
                throw new IllegalStateException(
                    "Terminated pod still exists after delete: " + pod.getMetadata().getName()
                );
            }
        }
    }

    private String waitFirstScheduledNode(String namespace, List<String> podNames, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutSeconds) * 1000L;
        while (System.currentTimeMillis() < deadline) {
            for (String podName : podNames) {
                Pod pod = kubernetesClient.pods().inNamespace(namespace).withName(podName).get();
                if (pod != null && pod.getSpec() != null && StringUtils.hasText(pod.getSpec().getNodeName())) {
                    return pod.getSpec().getNodeName();
                }
            }
            sleep(500L);
        }
        return null;
    }

    private String queryPodGroupPhase(String sandboxId, String namespace) {
        if (!properties.isCreatePodGroup()) {
            return null;
        }
        return queryPodGroupPhaseByName(namespace, SandboxNaming.podGroupName(sandboxId));
    }

    private Map<String, Integer> queryPodGroupCounters(String sandboxId, String namespace) {
        Map<String, Integer> counters = new LinkedHashMap<>();
        counters.put("minMember", 0);
        counters.put("running", 0);
        counters.put("succeeded", 0);
        counters.put("failed", 0);
        counters.put("pending", 0);
        if (!properties.isCreatePodGroup()) {
            return counters;
        }

        GenericKubernetesResource podGroup = kubernetesClient.genericKubernetesResources("scheduling.volcano.sh/v1beta1", "PodGroup")
            .inNamespace(namespace)
            .withName(SandboxNaming.podGroupName(sandboxId))
            .get();
        if (podGroup == null) {
            return counters;
        }

        Object specObj = podGroup.getAdditionalProperties().get("spec");
        if (specObj instanceof Map<?, ?> specMap) {
            counters.put("minMember", parseInt(specMap.get("minMember")));
        }
        Object statusObj = podGroup.getAdditionalProperties().get("status");
        if (statusObj instanceof Map<?, ?> statusMap) {
            counters.put("running", parseInt(statusMap.get("running")));
            counters.put("succeeded", parseInt(statusMap.get("succeeded")));
            counters.put("failed", parseInt(statusMap.get("failed")));
            counters.put("pending", parseInt(statusMap.get("pending")));
        }
        return counters;
    }

    private String queryPodGroupPhaseByName(String namespace, String podGroupName) {
        GenericKubernetesResource podGroup = kubernetesClient.genericKubernetesResources("scheduling.volcano.sh/v1beta1", "PodGroup")
            .inNamespace(namespace)
            .withName(podGroupName)
            .get();
        if (podGroup == null) {
            return null;
        }
        Object statusObj = podGroup.getAdditionalProperties().get("status");
        if (!(statusObj instanceof Map<?, ?> statusMap)) {
            return null;
        }
        Object phaseObj = statusMap.get("phase");
        return phaseObj == null ? null : phaseObj.toString();
    }

    private boolean isReadyPod(Pod pod) {
        if (pod == null || pod.getMetadata() == null || pod.getMetadata().getDeletionTimestamp() != null) {
            return false;
        }
        if (pod.getStatus() == null || !StringUtils.hasText(pod.getStatus().getPhase())) {
            return false;
        }
        if (!"Running".equalsIgnoreCase(pod.getStatus().getPhase())) {
            return false;
        }
        List<PodCondition> conditions = pod.getStatus().getConditions();
        if (conditions == null) {
            return false;
        }
        for (PodCondition condition : conditions) {
            if ("Ready".equals(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private String resolveNamespace(String specNamespace) {
        return StringUtils.hasText(specNamespace) ? specNamespace : properties.getNamespace();
    }

    private String resolveQueueName(SandboxSpec spec) {
        return StringUtils.hasText(spec.getQueueName()) ? spec.getQueueName() : properties.getQueueName();
    }

    private String resolvePriorityClassName(SandboxSpec spec) {
        return StringUtils.hasText(spec.getPriorityClassName()) ? spec.getPriorityClassName() : properties.getPriorityClassName();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Kubernetes state", ex);
        }
    }

    private List<Pod> listPodsBySandboxId(String namespace, String sandboxId) {
        if (!StringUtils.hasText(sandboxId)) {
            return List.of();
        }
        Map<String, Pod> pods = new LinkedHashMap<>();
        kubernetesClient.pods()
            .inNamespace(namespace)
            .withLabel("sandbox-id", sandboxId)
            .list()
            .getItems()
            .forEach(pod -> pods.put(pod.getMetadata().getName(), pod));
        kubernetesClient.pods()
            .inNamespace(namespace)
            .withLabel("service-id", sandboxId)
            .list()
            .getItems()
            .forEach(pod -> pods.put(pod.getMetadata().getName(), pod));
        kubernetesClient.pods()
            .inNamespace(namespace)
            .withLabel(SandboxLifecycleMetadata.SERVICE_ID, sandboxId)
            .list()
            .getItems()
            .forEach(pod -> pods.put(pod.getMetadata().getName(), pod));
        return new ArrayList<>(pods.values());
    }

    private void createImagePullSecretIfNeeded(SandboxSpec spec, String namespace) {
        if (!properties.isAutoCreateImagePullSecret()) {
            return;
        }
        if (!StringUtils.hasText(spec.getImagePullSecret())) {
            return;
        }
        if (!StringUtils.hasText(properties.getRegistry())
            || !StringUtils.hasText(properties.getRegistryUsername())
            || !StringUtils.hasText(properties.getRegistryPassword())) {
            return;
        }

        String secretName = spec.getImagePullSecret();
        Secret existing = kubernetesClient.secrets().inNamespace(namespace).withName(secretName).get();
        if (existing != null) {
            return;
        }

        String registryHost = properties.getRegistry();
        int slashIndex = registryHost.indexOf('/');
        if (slashIndex > 0) {
            registryHost = registryHost.substring(0, slashIndex);
        }

        String auth = Base64.getEncoder().encodeToString(
            (properties.getRegistryUsername() + ":" + properties.getRegistryPassword()).getBytes(StandardCharsets.UTF_8)
        );
        String dockerConfigJson = String.format(
            "{\"auths\":{\"%s\":{\"username\":\"%s\",\"password\":\"%s\",\"auth\":\"%s\"}}}",
            registryHost, properties.getRegistryUsername(), properties.getRegistryPassword(), auth
        );
        Secret secret = new SecretBuilder()
            .withNewMetadata()
                .withName(secretName)
                .withNamespace(namespace)
                .addToLabels("app", "linkwork-sandbox")
                .addToLabels("managed-by", "linkwork-k8s-starter")
                .addToLabels("secret-kind", "image-pull")
            .endMetadata()
            .withType("kubernetes.io/dockerconfigjson")
            .addToData(".dockerconfigjson", Base64.getEncoder().encodeToString(dockerConfigJson.getBytes(StandardCharsets.UTF_8)))
            .build();
        try {
            kubernetesClient.secrets().inNamespace(namespace).resource(secret).create();
        } catch (KubernetesClientException ex) {
            if (ex.getCode() != 409
                || kubernetesClient.secrets().inNamespace(namespace).withName(secretName).get() == null) {
                throw ex;
            }
        }
    }

    private void createManagedResources(SandboxSpec spec, String namespace, OwnerReference podGroupOwner) {
        String sandboxId = spec.getSandboxId();
        Map<String, String> lifecycleLabels = lifecycleLabels(spec);

        for (Map.Entry<String, Map<String, String>> entry : spec.getConfigMaps().entrySet()) {
            String configMapName = entry.getKey();
            if (!StringUtils.hasText(configMapName)) {
                continue;
            }
            Map<String, String> data = entry.getValue() == null ? Map.of() : entry.getValue();
            ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(configMapName)
                    .withNamespace(namespace)
                    .addToLabels("app", "linkwork-sandbox")
                    .addToLabels("sandbox-id", sandboxId)
                    .addToLabels("service-id", sandboxId)
                    .addToLabels("managed-by", "linkwork-k8s-starter")
                    .addToLabels(lifecycleLabels)
                    .addToOwnerReferences(podGroupOwner)
                .endMetadata()
                .withData(data)
                .build();
            kubernetesClient.configMaps().inNamespace(namespace).resource(configMap).create();
        }

        for (Map.Entry<String, Map<String, String>> entry : spec.getSecrets().entrySet()) {
            String secretName = entry.getKey();
            if (!StringUtils.hasText(secretName)) {
                continue;
            }
            Map<String, String> stringData = entry.getValue() == null ? Map.of() : entry.getValue();
            Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(namespace)
                    .addToLabels("app", "linkwork-sandbox")
                    .addToLabels("sandbox-id", sandboxId)
                    .addToLabels("service-id", sandboxId)
                    .addToLabels("managed-by", "linkwork-k8s-starter")
                    .addToLabels(lifecycleLabels)
                    .addToOwnerReferences(podGroupOwner)
                .endMetadata()
                .withType("Opaque")
                .withStringData(stringData)
                .build();
            kubernetesClient.secrets().inNamespace(namespace).resource(secret).create();
        }
    }

    private void assertSandboxNameAvailable(String sandboxId, String namespace) {
        boolean podGroupExists = getPodGroup(namespace, sandboxId) != null;
        boolean podsExist = !listPodsBySandboxId(namespace, sandboxId).isEmpty();
        boolean configMapsExist = !listManagedConfigMaps(sandboxId, namespace).isEmpty();
        boolean secretsExist = !listManagedSecrets(sandboxId, namespace).isEmpty();
        if (podGroupExists || podsExist || configMapsExist || secretsExist) {
            throw new LifecycleConflictException(
                "Sandbox resources already exist for " + namespace + "/" + sandboxId
            );
        }
    }

    private OwnerReference podGroupOwnerReference(GenericKubernetesResource podGroup) {
        if (podGroup == null || podGroup.getMetadata() == null
            || !StringUtils.hasText(podGroup.getMetadata().getUid())) {
            throw new IllegalStateException("PodGroup UID is required before creating child resources");
        }
        return new OwnerReferenceBuilder()
            .withApiVersion(podGroup.getApiVersion())
            .withKind(podGroup.getKind())
            .withName(podGroup.getMetadata().getName())
            .withUid(podGroup.getMetadata().getUid())
            .withController(true)
            .withBlockOwnerDeletion(false)
            .build();
    }

    private void verifyCreatedSandbox(SandboxSpec spec,
                                      String namespace,
                                      GenericKubernetesResource createdPodGroup,
                                      List<String> expectedPodNames) {
        GenericKubernetesResource observedPodGroup = getPodGroup(namespace, spec.getSandboxId());
        if (observedPodGroup == null || observedPodGroup.getMetadata() == null) {
            throw new IllegalStateException("PodGroup disappeared during create verification");
        }
        if (!Objects.equals(
            createdPodGroup.getMetadata().getUid(),
            observedPodGroup.getMetadata().getUid()
        )) {
            throw new IllegalStateException("PodGroup UID changed during create verification");
        }
        assertLifecycleMetadata(
            observedPodGroup.getMetadata(),
            spec.getLifecycleGeneration(),
            spec.getFenceToken(),
            "PodGroup/" + observedPodGroup.getMetadata().getName()
        );
        verifyPods(spec, namespace, observedPodGroup, expectedPodNames);
    }

    private void verifyScaledSandbox(SandboxSpec spec,
                                     String namespace,
                                     GenericKubernetesResource podGroup,
                                     List<String> expectedPodNames) {
        GenericKubernetesResource observedPodGroup = getPodGroup(namespace, spec.getSandboxId());
        if (observedPodGroup == null
            || !Objects.equals(podGroup.getMetadata().getUid(), observedPodGroup.getMetadata().getUid())) {
            throw new IllegalStateException("PodGroup changed during scale-up");
        }
        verifyPods(spec, namespace, observedPodGroup, expectedPodNames);
    }

    private void verifyPods(SandboxSpec spec,
                            String namespace,
                            GenericKubernetesResource podGroup,
                            List<String> expectedPodNames) {
        SandboxDestroyRequest lifecycle = lifecycleRequest(spec, namespace);
        List<Pod> observedPods = filterOwnedResources(
            listPodsBySandboxId(namespace, spec.getSandboxId()),
            lifecycle
        );
        Map<String, Pod> podsByName = observedPods.stream().collect(Collectors.toMap(
            pod -> pod.getMetadata().getName(),
            pod -> pod,
            (left, right) -> left,
            LinkedHashMap::new
        ));
        if (podsByName.size() != expectedPodNames.size()
            || !podsByName.keySet().containsAll(expectedPodNames)) {
            throw new IllegalStateException(
                "Pod verification failed: expected " + expectedPodNames + " but observed " + podsByName.keySet()
            );
        }
        for (String podName : expectedPodNames) {
            Pod pod = podsByName.get(podName);
            assertLifecycleMetadata(
                pod.getMetadata(),
                spec.getLifecycleGeneration(),
                spec.getFenceToken(),
                "Pod/" + podName
            );
            Map<String, String> annotations = pod.getMetadata().getAnnotations();
            String expectedPodGroupName = podGroup.getMetadata().getName();
            if (annotations == null
                || !expectedPodGroupName.equals(annotations.get("scheduling.volcano.sh/group-name"))) {
                throw new IllegalStateException("Pod/" + podName + " is not attached to the expected PodGroup");
            }
            boolean ownedByPodGroup = pod.getMetadata().getOwnerReferences() != null
                && pod.getMetadata().getOwnerReferences().stream()
                .anyMatch(owner -> Objects.equals(owner.getUid(), podGroup.getMetadata().getUid()));
            if (!ownedByPodGroup) {
                throw new IllegalStateException("Pod/" + podName + " is missing the PodGroup owner reference");
            }
        }
    }

    private String validateDestroyPreconditions(SandboxDestroyRequest request,
                                                GenericKubernetesResource podGroup) {
        if (podGroup == null) {
            return null;
        }
        ObjectMeta metadata = podGroup.getMetadata();
        if (metadata == null || !StringUtils.hasText(metadata.getUid())) {
            return "PodGroup UID is missing";
        }
        if (request.isAllowLegacy()) {
            return null;
        }
        if (!matchesLifecycle(metadata, request)) {
            return "PodGroup generation/fence does not match the destroy request";
        }
        if (StringUtils.hasText(request.getExpectedPodGroupUid())
            && !request.getExpectedPodGroupUid().equals(metadata.getUid())) {
            return "PodGroup UID does not match the destroy request";
        }
        return null;
    }

    private String validateScaleUpPreconditions(SandboxSpec spec,
                                                GenericKubernetesResource podGroup) {
        if (podGroup == null || podGroup.getMetadata() == null) {
            return "REPAIR_REQUIRED: PodGroup is missing";
        }
        try {
            assertLifecycleMetadata(
                podGroup.getMetadata(),
                spec.getLifecycleGeneration(),
                spec.getFenceToken(),
                "PodGroup/" + podGroup.getMetadata().getName()
            );
        } catch (IllegalStateException ex) {
            return "REPAIR_REQUIRED: " + ex.getMessage();
        }
        if (StringUtils.hasText(spec.getExpectedPodGroupUid())
            && !spec.getExpectedPodGroupUid().equals(podGroup.getMetadata().getUid())) {
            return "REPAIR_REQUIRED: PodGroup UID changed";
        }
        String phase = podGroupPhase(podGroup);
        if (StringUtils.hasText(phase)
            && !"Pending".equalsIgnoreCase(phase)
            && !"Inqueue".equalsIgnoreCase(phase)
            && !"Running".equalsIgnoreCase(phase)) {
            return "REPAIR_REQUIRED: PodGroup phase is " + phase;
        }
        return null;
    }

    private SandboxDestroyRequest lifecycleRequest(SandboxSpec spec, String namespace) {
        SandboxDestroyRequest request = new SandboxDestroyRequest();
        request.setSandboxId(spec.getSandboxId());
        request.setNamespace(namespace);
        request.setExpectedGeneration(spec.getLifecycleGeneration());
        request.setExpectedFenceToken(spec.getFenceToken());
        request.setExpectedPodGroupUid(spec.getExpectedPodGroupUid());
        request.setGracePeriodSeconds(0L);
        return request;
    }

    private SandboxDestroyRequest toDestroyRequest(SandboxScaleDownRequest scaleRequest) {
        SandboxDestroyRequest request = new SandboxDestroyRequest();
        request.setSandboxId(scaleRequest.getSandboxId());
        request.setNamespace(scaleRequest.getNamespace());
        request.setExpectedGeneration(scaleRequest.getExpectedGeneration());
        request.setExpectedFenceToken(scaleRequest.getExpectedFenceToken());
        request.setExpectedPodGroupUid(scaleRequest.getExpectedPodGroupUid());
        request.setGracePeriodSeconds(0L);
        request.setAllowLegacy(scaleRequest.isAllowLegacy());
        return request;
    }

    private <T extends HasMetadata> List<T> filterOwnedResources(List<T> resources,
                                                                 SandboxDestroyRequest request) {
        if (request.isAllowLegacy()) {
            return new ArrayList<>(resources);
        }
        return resources.stream()
            .filter(resource -> matchesLifecycle(resource.getMetadata(), request))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean matchesLifecycle(ObjectMeta metadata, SandboxDestroyRequest request) {
        if (metadata == null) {
            return false;
        }
        Map<String, String> labels = labelsOf(metadata);
        return "true".equalsIgnoreCase(labels.get(SandboxLifecycleMetadata.MANAGED))
            && request.getSandboxId().equals(labels.get(SandboxLifecycleMetadata.SERVICE_ID))
            && request.getSandboxId().equals(labels.get(SandboxLifecycleMetadata.SANDBOX_ID))
            && request.getExpectedGeneration().equals(labels.get(SandboxLifecycleMetadata.GENERATION))
            && String.valueOf(request.getExpectedFenceToken())
                .equals(labels.get(SandboxLifecycleMetadata.FENCE_TOKEN));
    }

    private boolean matchesExpectedLifecycle(ObjectMeta metadata, SandboxQuery query) {
        if (metadata == null) {
            return false;
        }
        Map<String, String> labels = labelsOf(metadata);
        if (StringUtils.hasText(query.getExpectedGeneration())
            && !query.getExpectedGeneration().equals(labels.get(SandboxLifecycleMetadata.GENERATION))) {
            return false;
        }
        return query.getExpectedFenceToken() == null
            || String.valueOf(query.getExpectedFenceToken())
                .equals(labels.get(SandboxLifecycleMetadata.FENCE_TOKEN));
    }

    private boolean hasConflictingManagedPods(String namespace,
                                              SandboxDestroyRequest request,
                                              List<Pod> ownedPods) {
        List<String> ownedNames = ownedPods.stream()
            .filter(pod -> pod.getMetadata() != null)
            .map(pod -> pod.getMetadata().getName())
            .collect(Collectors.toList());
        for (Pod pod : listPodsBySandboxId(namespace, request.getSandboxId())) {
            ObjectMeta metadata = pod.getMetadata();
            Map<String, String> labels = labelsOf(metadata);
            boolean managedForSandbox = "true".equalsIgnoreCase(labels.get(SandboxLifecycleMetadata.MANAGED))
                && request.getSandboxId().equals(labels.get(SandboxLifecycleMetadata.SERVICE_ID));
            if (managedForSandbox && !ownedNames.contains(metadata.getName())) {
                return true;
            }
        }
        return false;
    }

    private List<ConfigMap> listManagedConfigMaps(String sandboxId, String namespace) {
        return kubernetesClient.configMaps()
            .inNamespace(namespace)
            .withLabel("sandbox-id", sandboxId)
            .withLabel("managed-by", "linkwork-k8s-starter")
            .list()
            .getItems();
    }

    private List<Secret> listManagedSecrets(String sandboxId, String namespace) {
        return kubernetesClient.secrets()
            .inNamespace(namespace)
            .withLabel("sandbox-id", sandboxId)
            .withLabel("managed-by", "linkwork-k8s-starter")
            .list()
            .getItems();
    }

    private void captureDeleteError(List<String> errors, String resource, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            errors.add(resource + ": " + ex.getMessage());
            log.error("Failed to delete {}: {}", resource, ex.getMessage(), ex);
        }
    }

    private void deleteResourceWithUidPrecondition(String resourcePath,
                                                   ObjectMeta metadata,
                                                   Long gracePeriodSeconds,
                                                   DeletionPropagation propagation) {
        if (metadata == null || !StringUtils.hasText(metadata.getUid())) {
            throw new IllegalStateException("Cannot delete resource without UID precondition");
        }
        Preconditions preconditions = new Preconditions();
        preconditions.setUid(metadata.getUid());
        if (StringUtils.hasText(metadata.getResourceVersion())) {
            preconditions.setResourceVersion(metadata.getResourceVersion());
        }
        DeleteOptions options = new DeleteOptions();
        options.setApiVersion("v1");
        options.setKind("DeleteOptions");
        options.setGracePeriodSeconds(gracePeriodSeconds == null ? 0L : gracePeriodSeconds);
        options.setPropagationPolicy(propagation.toString());
        options.setPreconditions(preconditions);
        try {
            kubernetesClient.raw(resourcePath, "DELETE", options);
        } catch (KubernetesClientException ex) {
            if (ex.getCode() != 404) {
                throw ex;
            }
        }
    }

    private boolean waitForGenerationGone(SandboxDestroyRequest request,
                                          String namespace,
                                          int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutSeconds) * 1000L;
        while (System.currentTimeMillis() < deadline) {
            GenericKubernetesResource podGroup = getPodGroup(namespace, request.getSandboxId());
            boolean podGroupRemains = podGroup != null
                && (request.isAllowLegacy() || matchesLifecycle(podGroup.getMetadata(), request));
            boolean podsRemain = !filterOwnedResources(
                listPodsBySandboxId(namespace, request.getSandboxId()),
                request
            ).isEmpty();
            boolean configMapsRemain = !filterOwnedResources(
                listManagedConfigMaps(request.getSandboxId(), namespace),
                request
            ).isEmpty();
            boolean secretsRemain = !filterOwnedResources(
                listManagedSecrets(request.getSandboxId(), namespace),
                request
            ).isEmpty();
            if (!podGroupRemains && !podsRemain && !configMapsRemain && !secretsRemain) {
                return true;
            }
            sleep(500L);
        }
        return false;
    }

    private boolean waitForResourceGone(Supplier<? extends HasMetadata> getter,
                                        String expectedUid,
                                        int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutSeconds) * 1000L;
        while (System.currentTimeMillis() < deadline) {
            HasMetadata resource = getter.get();
            if (resource == null || resource.getMetadata() == null
                || !Objects.equals(expectedUid, resource.getMetadata().getUid())) {
                return true;
            }
            sleep(500L);
        }
        return false;
    }

    private GenericKubernetesResource getPodGroup(String namespace, String sandboxId) {
        return getPodGroupByName(namespace, SandboxNaming.podGroupName(sandboxId));
    }

    private GenericKubernetesResource getPodGroupByName(String namespace, String podGroupName) {
        if (!properties.isCreatePodGroup()) {
            return null;
        }
        return kubernetesClient.genericKubernetesResources("scheduling.volcano.sh/v1beta1", "PodGroup")
            .inNamespace(namespace)
            .withName(podGroupName)
            .get();
    }

    private String podGroupPath(String namespace, String podGroupName) {
        return "/apis/scheduling.volcano.sh/v1beta1/namespaces/" + namespace
            + "/podgroups/" + podGroupName;
    }

    private String coreResourcePath(String namespace, String plural, String name) {
        return "/api/v1/namespaces/" + namespace + "/" + plural + "/" + name;
    }

    private void assertLifecycleMetadata(ObjectMeta metadata,
                                         String expectedGeneration,
                                         Long expectedFenceToken,
                                         String resource) {
        Map<String, String> labels = labelsOf(metadata);
        if (!"true".equalsIgnoreCase(labels.get(SandboxLifecycleMetadata.MANAGED))
            || !StringUtils.hasText(labels.get(SandboxLifecycleMetadata.SERVICE_ID))
            || !StringUtils.hasText(labels.get(SandboxLifecycleMetadata.SANDBOX_ID))
            || !Objects.equals(expectedGeneration, labels.get(SandboxLifecycleMetadata.GENERATION))
            || !Objects.equals(
                String.valueOf(expectedFenceToken),
                labels.get(SandboxLifecycleMetadata.FENCE_TOKEN)
            )) {
            throw new IllegalStateException(resource + " lifecycle metadata mismatch");
        }
    }

    private Map<String, String> labelsOf(ObjectMeta metadata) {
        return metadata == null || metadata.getLabels() == null ? Map.of() : metadata.getLabels();
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String podGroupPhase(GenericKubernetesResource podGroup) {
        if (podGroup == null) {
            return null;
        }
        Object statusObj = podGroup.getAdditionalProperties().get("status");
        if (!(statusObj instanceof Map<?, ?> statusMap)) {
            return null;
        }
        Object phaseObj = statusMap.get("phase");
        return phaseObj == null ? null : phaseObj.toString();
    }

    private Map<String, String> lifecycleLabels(SandboxSpec spec) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(SandboxLifecycleMetadata.MANAGED, "true");
        labels.put(SandboxLifecycleMetadata.SERVICE_ID, spec.getSandboxId());
        labels.put(SandboxLifecycleMetadata.SANDBOX_ID, spec.getSandboxId());
        labels.put(SandboxLifecycleMetadata.GENERATION, spec.getLifecycleGeneration());
        labels.put(SandboxLifecycleMetadata.FENCE_TOKEN, String.valueOf(spec.getFenceToken()));
        labels.put(SandboxLifecycleMetadata.CREATED_AT, String.valueOf(Instant.now().toEpochMilli()));
        return labels;
    }

    private boolean isActivePod(Pod pod) {
        if (pod == null || pod.getMetadata() == null
            || pod.getMetadata().getDeletionTimestamp() != null) {
            return false;
        }
        String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
        return !"Succeeded".equalsIgnoreCase(phase) && !"Failed".equalsIgnoreCase(phase);
    }

    private SandboxSpec buildFallbackScaleSpec(String sandboxId, SandboxSpec templateSpec, List<Pod> existingPods, String namespace) {
        if (templateSpec != null) {
            templateSpec.setSandboxId(sandboxId);
            templateSpec.setNamespace(namespace);
            return templateSpec;
        }
        if (existingPods.isEmpty()) {
            throw new IllegalArgumentException("No existing pods and no template spec, cannot scale-up");
        }

        Pod basePod = existingPods.get(0);
        PodSpec baseSpec = basePod.getSpec();
        if (baseSpec == null || baseSpec.getContainers() == null || baseSpec.getContainers().isEmpty()) {
            throw new IllegalArgumentException("Existing pod has no container spec, cannot scale-up");
        }

        SandboxSpec spec = new SandboxSpec();
        spec.setSandboxId(sandboxId);
        spec.setNamespace(namespace);
        spec.setMode(baseSpec.getContainers().size() > 1 ? SandboxMode.SIDECAR : SandboxMode.ALONE);
        spec.setImagePullPolicy(baseSpec.getContainers().get(0).getImagePullPolicy());
        if (baseSpec.getImagePullSecrets() != null && !baseSpec.getImagePullSecrets().isEmpty()) {
            spec.setImagePullSecret(baseSpec.getImagePullSecrets().get(0).getName());
        }

        Container agent = findContainerByName(baseSpec.getContainers(), "agent");
        if (agent == null) {
            agent = baseSpec.getContainers().get(0);
        }
        spec.setAgentImage(agent.getImage());
        if (agent.getCommand() != null) {
            spec.setAgentCommand(new ArrayList<>(agent.getCommand()));
        }
        if (agent.getEnv() != null) {
            Map<String, String> envMap = new LinkedHashMap<>();
            for (EnvVar envVar : agent.getEnv()) {
                if (StringUtils.hasText(envVar.getName()) && envVar.getValue() != null) {
                    envMap.put(envVar.getName(), envVar.getValue());
                }
            }
            spec.setInjectedEnvs(envMap);
        }

        Container runner = findContainerByName(baseSpec.getContainers(), "runner");
        if (runner != null) {
            spec.setRunnerImage(runner.getImage());
            if (runner.getCommand() != null) {
                spec.setRunnerCommand(new ArrayList<>(runner.getCommand()));
            }
        }

        Map<String, String> labels = basePod.getMetadata() != null && basePod.getMetadata().getLabels() != null
            ? new LinkedHashMap<>(basePod.getMetadata().getLabels()) : new LinkedHashMap<>();
        labels.remove("pod-index");
        labels.put("sandbox-id", sandboxId);
        labels.put("service-id", sandboxId);
        spec.setLabels(labels);

        Map<String, String> annotations = basePod.getMetadata() != null && basePod.getMetadata().getAnnotations() != null
            ? new LinkedHashMap<>(basePod.getMetadata().getAnnotations()) : new LinkedHashMap<>();
        if (properties.isCreatePodGroup()) {
            annotations.put("scheduling.k8s.io/group-name", SandboxNaming.podGroupName(sandboxId));
            annotations.put("scheduling.volcano.sh/group-name", SandboxNaming.podGroupName(sandboxId));
        }
        spec.setAnnotations(annotations);

        if (baseSpec.getVolumes() != null) {
            Integer workspaceSizeGi = extractWorkspaceSizeGi(baseSpec.getVolumes());
            if (workspaceSizeGi != null) {
                spec.setWorkspaceSizeGi(workspaceSizeGi);
            }
        }
        return spec;
    }

    private Container findContainerByName(List<Container> containers, String name) {
        for (Container container : containers) {
            if (name.equalsIgnoreCase(container.getName())) {
                return container;
            }
        }
        return null;
    }

    private Integer extractWorkspaceSizeGi(List<Volume> volumes) {
        for (Volume volume : volumes) {
            if (!"workspace".equals(volume.getName()) || volume.getEmptyDir() == null || volume.getEmptyDir().getSizeLimit() == null) {
                continue;
            }
            String size = volume.getEmptyDir().getSizeLimit().getAmount();
            if (!StringUtils.hasText(size)) {
                continue;
            }
            String normalized = size.toLowerCase(Locale.ROOT);
            if (normalized.endsWith("gi")) {
                try {
                    return Integer.parseInt(normalized.substring(0, normalized.length() - 2));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private int extractPodIndex(String podName) {
        if (!StringUtils.hasText(podName)) {
            return -1;
        }
        String[] parts = podName.split("-");
        if (parts.length == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private int parseInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Map<String, Object> toPreviewPodMap(Pod pod) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiVersion", pod.getApiVersion());
        result.put("kind", pod.getKind());

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (pod.getMetadata() != null) {
            metadata.put("name", pod.getMetadata().getName());
            metadata.put("namespace", pod.getMetadata().getNamespace());
            metadata.put("labels", pod.getMetadata().getLabels());
            metadata.put("annotations", pod.getMetadata().getAnnotations());
        }
        result.put("metadata", metadata);

        Map<String, Object> spec = new LinkedHashMap<>();
        PodSpec podSpec = pod.getSpec();
        if (podSpec != null) {
            spec.put("schedulerName", podSpec.getSchedulerName());
            spec.put("restartPolicy", podSpec.getRestartPolicy());
            spec.put("priorityClassName", podSpec.getPriorityClassName());
            spec.put("terminationGracePeriodSeconds", podSpec.getTerminationGracePeriodSeconds());

            List<Map<String, Object>> containers = new ArrayList<>();
            if (podSpec.getContainers() != null) {
                for (Container container : podSpec.getContainers()) {
                    Map<String, Object> containerMap = new LinkedHashMap<>();
                    containerMap.put("name", container.getName());
                    containerMap.put("image", container.getImage());
                    containerMap.put("imagePullPolicy", container.getImagePullPolicy());
                    containerMap.put("command", container.getCommand());
                    containerMap.put("env", container.getEnv());
                    containerMap.put("volumeMounts", toVolumeMountMaps(container.getVolumeMounts()));
                    containerMap.put("resources", container.getResources());
                    containers.add(containerMap);
                }
            }
            spec.put("containers", containers);
            spec.put("volumes", toVolumeMaps(podSpec.getVolumes()));
        }
        result.put("spec", spec);
        return result;
    }

    private List<Map<String, Object>> toVolumeMountMaps(List<VolumeMount> volumeMounts) {
        if (volumeMounts == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (VolumeMount mount : volumeMounts) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", mount.getName());
            map.put("mountPath", mount.getMountPath());
            map.put("readOnly", mount.getReadOnly());
            map.put("subPath", mount.getSubPath());
            map.put("mountPropagation", mount.getMountPropagation());
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> toVolumeMaps(List<Volume> volumes) {
        if (volumes == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Volume volume : volumes) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", volume.getName());
            if (volume.getEmptyDir() != null) {
                Map<String, Object> emptyDir = new LinkedHashMap<>();
                emptyDir.put("medium", volume.getEmptyDir().getMedium());
                emptyDir.put("sizeLimit", volume.getEmptyDir().getSizeLimit());
                map.put("emptyDir", emptyDir);
            }
            if (volume.getHostPath() != null) {
                Map<String, Object> hostPath = new LinkedHashMap<>();
                hostPath.put("path", volume.getHostPath().getPath());
                hostPath.put("type", volume.getHostPath().getType());
                map.put("hostPath", hostPath);
            }
            result.add(map);
        }
        return result;
    }

    private static final class LifecycleConflictException extends IllegalStateException {

        private LifecycleConflictException(String message) {
            super(message);
        }
    }
}
