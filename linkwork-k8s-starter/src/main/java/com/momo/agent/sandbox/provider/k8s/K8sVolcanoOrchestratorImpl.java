package com.momo.agent.sandbox.provider.k8s;

import com.momo.agent.sandbox.core.SandboxOrchestrator;
import com.momo.agent.sandbox.core.model.SandboxMode;
import com.momo.agent.sandbox.core.model.SandboxNaming;
import com.momo.agent.sandbox.core.model.SandboxPodStatus;
import com.momo.agent.sandbox.core.model.SandboxPreview;
import com.momo.agent.sandbox.core.model.SandboxResult;
import com.momo.agent.sandbox.core.model.SandboxScaleResult;
import com.momo.agent.sandbox.core.model.SandboxSpec;
import com.momo.agent.sandbox.core.model.SandboxStatus;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

        String namespace = resolveNamespace(spec.getNamespace());
        String podGroupName = SandboxNaming.podGroupName(spec.getSandboxId());
        List<String> podNames = new ArrayList<>();

        try {
            cleanupExistingPods(spec.getSandboxId(), namespace);
            createManagedResources(spec, namespace);

            if (properties.isCreatePodGroup()) {
                createPodGroup(spec, namespace);
                waitForPodGroupReady(namespace, podGroupName, properties.getWaitPodGroupReadySeconds());
            }

            for (int i = 0; i < spec.getPodCount(); i++) {
                Pod pod = podSpecGenerator.generate(spec, i, namespace, properties);
                Pod created = createPodWithRetry(namespace, pod, 3, 1000L);
                podNames.add(created.getMetadata().getName());
            }

            String scheduledNode = waitFirstScheduledNode(namespace, podNames, properties.getWaitScheduledNodeSeconds());
            return SandboxResult.success(spec.getSandboxId(), podGroupName, podNames, scheduledNode);
        } catch (Exception ex) {
            log.error("Failed to create sandbox {}: {}", spec.getSandboxId(), ex.getMessage(), ex);
            cleanupSilently(spec.getSandboxId(), namespace);
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
        return destroyInternal(sandboxId, namespace, graceful ? 30L : 0L, "K8S_STOP_FAILED");
    }

    @Override
    public SandboxResult destroySandbox(String sandboxId, String namespace) {
        return destroyInternal(sandboxId, namespace, 0L, "K8S_DESTROY_FAILED");
    }

    private SandboxResult destroyInternal(String sandboxId, String namespace, Long gracePeriodSeconds, String errorCode) {
        if (!StringUtils.hasText(sandboxId)) {
            return SandboxResult.failed(null, "INVALID_SANDBOX_ID", "sandboxId is required");
        }

        String resolvedNamespace = resolveNamespace(namespace);
        try {
            List<Pod> pods = listPodsBySandboxId(resolvedNamespace, sandboxId);
            List<String> podNames = pods.stream()
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toList());

            if (gracePeriodSeconds == null) {
                for (String podName : podNames) {
                    kubernetesClient.pods().inNamespace(resolvedNamespace).withName(podName).delete();
                }
            } else {
                kubernetesClient.pods()
                    .inNamespace(resolvedNamespace)
                    .withLabel("sandbox-id", sandboxId)
                    .withGracePeriod(gracePeriodSeconds)
                    .delete();
                kubernetesClient.pods()
                    .inNamespace(resolvedNamespace)
                    .withLabel("service-id", sandboxId)
                    .withGracePeriod(gracePeriodSeconds)
                    .delete();
            }

            if (properties.isCreatePodGroup()) {
                kubernetesClient.genericKubernetesResources("scheduling.volcano.sh/v1beta1", "PodGroup")
                    .inNamespace(resolvedNamespace)
                    .withName(SandboxNaming.podGroupName(sandboxId))
                    .delete();
            }
            cleanupManagedResources(sandboxId, resolvedNamespace);

            return SandboxResult.success(sandboxId, SandboxNaming.podGroupName(sandboxId), podNames, null);
        } catch (Exception ex) {
            log.error("Failed to destroy sandbox {}: {}", sandboxId, ex.getMessage(), ex);
            return SandboxResult.failed(sandboxId, errorCode, ex.getMessage());
        }
    }

    @Override
    public SandboxStatus querySandbox(String sandboxId, String namespace) {
        SandboxStatus status = new SandboxStatus();
        status.setSandboxId(sandboxId);
        status.setNamespace(resolveNamespace(namespace));
        status.setObservedAt(Instant.now());

        if (!StringUtils.hasText(sandboxId)) {
            status.setMessage("sandboxId is required");
            return status;
        }

        try {
            List<Pod> podList = listPodsBySandboxId(status.getNamespace(), sandboxId);
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

            Map<String, Integer> podGroupCounters = queryPodGroupCounters(sandboxId, status.getNamespace());
            status.setPodGroupPhase(queryPodGroupPhase(sandboxId, status.getNamespace()));
            status.setPodGroupMinMember(podGroupCounters.get("minMember"));
            status.setPodGroupRunning(podGroupCounters.get("running"));
            status.setPodGroupSucceeded(podGroupCounters.get("succeeded"));
            status.setPodGroupFailed(podGroupCounters.get("failed"));
            status.setPodGroupPending(podGroupCounters.get("pending"));

            if (pods.isEmpty()) {
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
        if (!StringUtils.hasText(sandboxId)) {
            return SandboxScaleResult.failed(null, "sandboxId is required");
        }
        if (!StringUtils.hasText(podName)) {
            return SandboxScaleResult.failed(sandboxId, "podName is required for scale-down");
        }
        String resolvedNamespace = resolveNamespace(namespace);

        try {
            List<String> runningPods = listRunningPods(sandboxId, resolvedNamespace);
            int previousCount = runningPods.size();
            if (previousCount == 0) {
                return SandboxScaleResult.failed(sandboxId, "No running pods to scale down");
            }
            if (!runningPods.contains(podName)) {
                return SandboxScaleResult.failed(sandboxId, "Pod not found: " + podName);
            }

            kubernetesClient.pods()
                .inNamespace(resolvedNamespace)
                .withName(podName)
                .withGracePeriod(0L)
                .delete();

            List<String> updated = new ArrayList<>(runningPods);
            updated.remove(podName);
            return SandboxScaleResult.success(
                sandboxId,
                "SCALE_DOWN",
                previousCount,
                updated.size(),
                previousCount,
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

        try {
            cleanupTerminatedPods(sandboxId, resolvedNamespace);

            List<Pod> existingPods = listPodsBySandboxId(resolvedNamespace, sandboxId);
            List<String> runningPods = listRunningPods(sandboxId, resolvedNamespace);
            int previousCount = runningPods.size();
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

            for (int i = 0; i < podsToCreate; i++) {
                while (existingIndices.contains(nextIndex)) {
                    nextIndex++;
                }

                Pod pod = podSpecGenerator.generate(fallbackSpec, nextIndex, resolvedNamespace, properties);
                Pod created = createPodWithRetry(resolvedNamespace, pod, 3, 1000L);
                String podName = created.getMetadata().getName();
                addedPods.add(podName);
                runningPods.add(podName);
                existingIndices.add(nextIndex);
                nextIndex++;
            }

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

    private void createPodGroup(SandboxSpec spec, String namespace) {
        Map<String, Object> podGroupMap = podGroupSpecGenerator.generate(
            spec,
            namespace,
            resolveQueueName(spec),
            resolvePriorityClassName(spec)
        );

        GenericKubernetesResource podGroup = new GenericKubernetesResource();
        podGroup.setApiVersion("scheduling.volcano.sh/v1beta1");
        podGroup.setKind("PodGroup");
        podGroup.setMetadata(new ObjectMetaBuilder()
            .withName(SandboxNaming.podGroupName(spec.getSandboxId()))
            .withNamespace(namespace)
            .addToLabels("app", "linkwork-sandbox")
            .addToLabels("sandbox-id", spec.getSandboxId())
            .addToLabels("service-id", spec.getSandboxId())
            .build());
        podGroup.setAdditionalProperties(Map.of("spec", podGroupMap.get("spec")));

        kubernetesClient.genericKubernetesResources("scheduling.volcano.sh/v1beta1", "PodGroup")
            .inNamespace(namespace)
            .resource(podGroup)
            .createOrReplace();
    }

    private void waitForPodGroupReady(String namespace, String podGroupName, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutSeconds) * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String phase = queryPodGroupPhaseByName(namespace, podGroupName);
            if (!StringUtils.hasText(phase) || !"Pending".equalsIgnoreCase(phase)) {
                return;
            }
            sleep(500L);
        }
        log.warn("PodGroup {} is still pending after {}s", podGroupName, timeoutSeconds);
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

    private void cleanupExistingPods(String sandboxId, String namespace) {
        List<Pod> existingPods = listPodsBySandboxId(namespace, sandboxId);
        if (existingPods.isEmpty()) {
            return;
        }

        for (Pod pod : existingPods) {
            kubernetesClient.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).delete();
        }
        waitForPodsDeleted(namespace, sandboxId, 60);
    }

    private void cleanupTerminatedPods(String sandboxId, String namespace) {
        List<Pod> allPods = listPodsBySandboxId(namespace, sandboxId);
        List<String> terminated = allPods.stream()
            .filter(pod -> pod.getStatus() != null)
            .filter(pod -> {
                String phase = pod.getStatus().getPhase();
                return "Succeeded".equalsIgnoreCase(phase) || "Failed".equalsIgnoreCase(phase);
            })
            .map(pod -> pod.getMetadata().getName())
            .collect(Collectors.toList());
        if (terminated.isEmpty()) {
            return;
        }
        for (String podName : terminated) {
            kubernetesClient.pods().inNamespace(namespace).withName(podName).withGracePeriod(0L).delete();
        }
        waitForPodsDeleted(namespace, sandboxId, 30);
    }

    private void waitForPodsDeleted(String namespace, String sandboxId, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutSeconds) * 1000L;
        while (System.currentTimeMillis() < deadline) {
            boolean exists = !listPodsBySandboxId(namespace, sandboxId).isEmpty();
            if (!exists) {
                return;
            }
            sleep(1000L);
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

    private void cleanupSilently(String sandboxId, String namespace) {
        try {
            destroySandbox(sandboxId, namespace);
        } catch (Exception ex) {
            log.warn("Failed to cleanup sandbox {} after create error: {}", sandboxId, ex.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Pod> listPodsBySandboxId(String namespace, String sandboxId) {
        if (!StringUtils.hasText(sandboxId)) {
            return List.of();
        }
        List<Pod> pods = kubernetesClient.pods()
            .inNamespace(namespace)
            .withLabel("sandbox-id", sandboxId)
            .list()
            .getItems();
        if (!pods.isEmpty()) {
            return pods;
        }
        return kubernetesClient.pods()
            .inNamespace(namespace)
            .withLabel("service-id", sandboxId)
            .list()
            .getItems();
    }

    private void createManagedResources(SandboxSpec spec, String namespace) {
        String sandboxId = spec.getSandboxId();

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
                .endMetadata()
                .withData(data)
                .build();
            kubernetesClient.configMaps().inNamespace(namespace).resource(configMap).createOrReplace();
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
                .endMetadata()
                .withType("Opaque")
                .withStringData(stringData)
                .build();
            kubernetesClient.secrets().inNamespace(namespace).resource(secret).createOrReplace();
        }
    }

    private void cleanupManagedResources(String sandboxId, String namespace) {
        kubernetesClient.configMaps()
            .inNamespace(namespace)
            .withLabel("sandbox-id", sandboxId)
            .withLabel("managed-by", "linkwork-k8s-starter")
            .delete();
        kubernetesClient.secrets()
            .inNamespace(namespace)
            .withLabel("sandbox-id", sandboxId)
            .withLabel("managed-by", "linkwork-k8s-starter")
            .delete();
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
}
