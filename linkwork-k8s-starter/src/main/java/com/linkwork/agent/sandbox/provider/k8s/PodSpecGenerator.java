package com.linkwork.agent.sandbox.provider.k8s;

import com.linkwork.agent.sandbox.core.model.ResourceSpec;
import com.linkwork.agent.sandbox.core.model.SandboxMode;
import com.linkwork.agent.sandbox.core.model.SandboxNaming;
import com.linkwork.agent.sandbox.core.model.SandboxSpec;
import com.linkwork.agent.sandbox.core.model.VolumeMountDef;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.KeyToPathBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kubernetes Pod generator for sandbox workloads.
 */
public class PodSpecGenerator {

    public Pod generate(SandboxSpec spec, int podIndex, String namespace, K8sSandboxProperties properties) {
        String podName = podName(spec.getSandboxId(), podIndex);
        String podGroupName = podGroupName(spec.getSandboxId());
        String queueName = resolveQueueName(spec, properties);

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", "linkwork-sandbox");
        labels.put("sandbox-id", spec.getSandboxId());
        labels.put("service-id", spec.getSandboxId());
        labels.put("pod-index", String.valueOf(podIndex));
        labels.put("sandbox-mode", spec.getMode().name().toLowerCase(Locale.ROOT));
        labels.put("pod-mode", spec.getMode().name().toLowerCase(Locale.ROOT));
        labels.putAll(spec.getLabels());

        Map<String, String> annotations = new LinkedHashMap<>();
        if (properties.isCreatePodGroup()) {
            annotations.put("scheduling.k8s.io/group-name", podGroupName);
            annotations.put("scheduling.volcano.sh/group-name", podGroupName);
            annotations.put("volcano.sh/queue-name", queueName);
        }
        annotations.putAll(spec.getAnnotations());

        PodBuilder builder = new PodBuilder()
            .withNewMetadata()
                .withName(podName)
                .withNamespace(namespace)
                .addToLabels(labels)
                .addToAnnotations(annotations)
            .endMetadata()
            .withNewSpec()
                .withSchedulerName(properties.getSchedulerName())
                .withRestartPolicy("Never")
                .withTerminationGracePeriodSeconds(30L)
                .withImagePullSecrets(buildImagePullSecrets(spec))
                .addAllToVolumes(buildVolumes(spec))
                .addToContainers(buildAgentContainer(spec, properties))
            .endSpec();

        String priorityClassName = resolvePriorityClassName(spec, properties);
        if (StringUtils.hasText(priorityClassName)) {
            builder.editSpec().withPriorityClassName(priorityClassName).endSpec();
        }

        if (spec.getMode() == SandboxMode.SIDECAR) {
            builder.editSpec().addToContainers(buildRunnerContainer(spec, properties)).endSpec();
        }

        if (StringUtils.hasText(spec.getPreferredNode())) {
            builder.editSpec().withAffinity(buildPreferredNodeAffinity(spec.getPreferredNode())).endSpec();
        }

        return builder.build();
    }

    static String podGroupName(String sandboxId) {
        return SandboxNaming.podGroupName(sandboxId);
    }

    static String podName(String sandboxId, int podIndex) {
        return SandboxNaming.podName(sandboxId, podIndex);
    }

    private Container buildAgentContainer(SandboxSpec spec, K8sSandboxProperties properties) {
        ContainerBuilder builder = new ContainerBuilder()
            .withName("agent")
            .withImage(resolveAgentImage(spec, properties))
            .withImagePullPolicy(resolveImagePullPolicy(spec, properties))
            .withEnv(buildContainerEnvs(spec, "agent"))
            .withResources(buildResources(spec.getAgentResources()))
            .withVolumeMounts(buildVolumeMounts(spec, "agent"));

        if (!spec.getAgentCommand().isEmpty()) {
            builder.withCommand(spec.getAgentCommand());
        }
        return builder.build();
    }

    private Container buildRunnerContainer(SandboxSpec spec, K8sSandboxProperties properties) {
        ContainerBuilder builder = new ContainerBuilder()
            .withName("runner")
            .withImage(resolveRunnerImage(spec, properties))
            .withImagePullPolicy(resolveImagePullPolicy(spec, properties))
            .withEnv(buildContainerEnvs(spec, "runner"))
            .withResources(buildResources(spec.getRunnerResources()))
            .withVolumeMounts(buildVolumeMounts(spec, "runner"))
            .addToPorts(new ContainerPort(22, null, null, "ssh", "TCP"));

        if (!spec.getRunnerCommand().isEmpty()) {
            builder.withCommand(spec.getRunnerCommand());
        }
        return builder.build();
    }

    private List<EnvVar> buildContainerEnvs(SandboxSpec spec, String containerName) {
        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(new EnvVar("SANDBOX_ID", spec.getSandboxId(), null));
        envVars.add(new EnvVar("SANDBOX_MODE", spec.getMode().name().toLowerCase(Locale.ROOT), null));
        envVars.add(new EnvVar("SANDBOX_CONTAINER", containerName, null));
        spec.getInjectedEnvs().forEach((key, value) -> envVars.add(new EnvVar(key, value, null)));
        envVars.add(new EnvVarBuilder()
            .withName("POD_NAME")
            .withNewValueFrom()
                .withNewFieldRef()
                    .withFieldPath("metadata.name")
                .endFieldRef()
            .endValueFrom()
            .build());
        return envVars;
    }

    private ResourceRequirements buildResources(ResourceSpec spec) {
        ResourceRequirementsBuilder builder = new ResourceRequirementsBuilder();
        if (spec == null) {
            return builder.build();
        }
        if (StringUtils.hasText(spec.getCpuRequest())) {
            builder.addToRequests("cpu", new Quantity(spec.getCpuRequest()));
        }
        if (StringUtils.hasText(spec.getMemoryRequest())) {
            builder.addToRequests("memory", new Quantity(spec.getMemoryRequest()));
        }
        if (StringUtils.hasText(spec.getCpuLimit())) {
            builder.addToLimits("cpu", new Quantity(spec.getCpuLimit()));
        }
        if (StringUtils.hasText(spec.getMemoryLimit())) {
            builder.addToLimits("memory", new Quantity(spec.getMemoryLimit()));
        }
        return builder.build();
    }

    private List<Volume> buildVolumes(SandboxSpec spec) {
        List<Volume> volumes = new ArrayList<>();
        int workspaceSizeGi = spec.getWorkspaceSizeGi() == null ? 20 : Math.max(1, spec.getWorkspaceSizeGi());

        volumes.add(new VolumeBuilder()
            .withName("workspace")
            .withEmptyDir(new EmptyDirVolumeSourceBuilder()
                .withSizeLimit(new Quantity(workspaceSizeGi + "Gi"))
                .build())
            .build());

        if (spec.getMode() == SandboxMode.SIDECAR) {
            volumes.add(new VolumeBuilder()
                .withName("shared-keys")
                .withNewEmptyDir()
                    .withMedium("Memory")
                .endEmptyDir()
                .build());
        }

        int index = 0;
        for (VolumeMountDef mountDef : spec.getMounts()) {
            boolean hasVolumeSource = StringUtils.hasText(mountDef.getHostPath())
                || StringUtils.hasText(mountDef.getConfigMapName())
                || StringUtils.hasText(mountDef.getSecretName())
                || mountDef.isEmptyDir();
            if (!hasVolumeSource || !StringUtils.hasText(mountDef.getMountPath())) {
                index++;
                continue;
            }
            String volumeName = customVolumeName(index, mountDef.getName());
            if (StringUtils.hasText(mountDef.getHostPath())) {
                volumes.add(new VolumeBuilder()
                    .withName(volumeName)
                    .withNewHostPath()
                        .withPath(mountDef.getHostPath())
                        .withType(StringUtils.hasText(mountDef.getHostPathType()) ? mountDef.getHostPathType() : "DirectoryOrCreate")
                    .endHostPath()
                    .build());
            } else if (StringUtils.hasText(mountDef.getConfigMapName())) {
                ConfigMapVolumeSourceBuilder sourceBuilder = new ConfigMapVolumeSourceBuilder()
                    .withName(mountDef.getConfigMapName());
                if (mountDef.getConfigMapDefaultMode() != null) {
                    sourceBuilder.withDefaultMode(mountDef.getConfigMapDefaultMode());
                }
                if (StringUtils.hasText(mountDef.getConfigMapKey())) {
                    sourceBuilder.addToItems(new KeyToPathBuilder()
                        .withKey(mountDef.getConfigMapKey())
                        .withPath(mountDef.getConfigMapKey())
                        .build());
                }
                volumes.add(new VolumeBuilder()
                    .withName(volumeName)
                    .withConfigMap(sourceBuilder.build())
                    .build());
            } else if (StringUtils.hasText(mountDef.getSecretName())) {
                SecretVolumeSourceBuilder sourceBuilder = new SecretVolumeSourceBuilder()
                    .withSecretName(mountDef.getSecretName());
                if (mountDef.getSecretDefaultMode() != null) {
                    sourceBuilder.withDefaultMode(mountDef.getSecretDefaultMode());
                }
                if (StringUtils.hasText(mountDef.getSecretKey())) {
                    sourceBuilder.addToItems(new KeyToPathBuilder()
                        .withKey(mountDef.getSecretKey())
                        .withPath(mountDef.getSecretKey())
                        .build());
                }
                volumes.add(new VolumeBuilder()
                    .withName(volumeName)
                    .withSecret(sourceBuilder.build())
                    .build());
            } else if (mountDef.isEmptyDir()) {
                EmptyDirVolumeSourceBuilder sourceBuilder = new EmptyDirVolumeSourceBuilder()
                    .withMedium(mountDef.getEmptyDirMedium());
                if (StringUtils.hasText(mountDef.getEmptyDirSizeLimit())) {
                    sourceBuilder.withSizeLimit(new Quantity(mountDef.getEmptyDirSizeLimit()));
                }
                volumes.add(new VolumeBuilder()
                    .withName(volumeName)
                    .withEmptyDir(sourceBuilder.build())
                    .build());
            }
            index++;
        }
        return volumes;
    }

    private List<VolumeMount> buildVolumeMounts(SandboxSpec spec, String containerName) {
        List<VolumeMount> mounts = new ArrayList<>();
        mounts.add(new VolumeMountBuilder()
            .withName("workspace")
            .withMountPath("/workspace")
            .withReadOnly(false)
            .build());

        if (spec.getMode() == SandboxMode.SIDECAR) {
            mounts.add(new VolumeMountBuilder()
                .withName("shared-keys")
                .withMountPath("/shared-keys")
                .withReadOnly(false)
                .build());
        }

        int index = 0;
        for (VolumeMountDef mountDef : spec.getMounts()) {
            boolean hasVolumeSource = StringUtils.hasText(mountDef.getHostPath())
                || StringUtils.hasText(mountDef.getConfigMapName())
                || StringUtils.hasText(mountDef.getSecretName())
                || mountDef.isEmptyDir();
            if (!hasVolumeSource || !StringUtils.hasText(mountDef.getMountPath())) {
                index++;
                continue;
            }
            if (!attachToContainer(mountDef, containerName)) {
                index++;
                continue;
            }
            VolumeMountBuilder volumeMountBuilder = new VolumeMountBuilder()
                .withName(customVolumeName(index, mountDef.getName()))
                .withMountPath(mountDef.getMountPath())
                .withReadOnly(mountDef.isReadOnly());
            if (StringUtils.hasText(mountDef.getSubPath())) {
                volumeMountBuilder.withSubPath(mountDef.getSubPath());
            }
            if (StringUtils.hasText(mountDef.getMountPropagation())) {
                volumeMountBuilder.withMountPropagation(mountDef.getMountPropagation());
            }
            mounts.add(volumeMountBuilder.build());
            index++;
        }
        return mounts;
    }

    private boolean attachToContainer(VolumeMountDef def, String containerName) {
        List<String> targets = def.getContainerTargets();
        if (targets.isEmpty()) {
            return true;
        }
        for (String target : targets) {
            if (containerName.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private String customVolumeName(int index, String rawName) {
        String seed = StringUtils.hasText(rawName) ? rawName : "mount-" + index;
        String normalized = seed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        if (normalized.isBlank()) {
            normalized = "mount-" + index;
        }
        return "m-" + normalized + "-" + index;
    }

    private List<LocalObjectReference> buildImagePullSecrets(SandboxSpec spec) {
        if (StringUtils.hasText(spec.getImagePullSecret())) {
            return Collections.singletonList(new LocalObjectReference(spec.getImagePullSecret()));
        }
        return Collections.emptyList();
    }

    private String resolveAgentImage(SandboxSpec spec, K8sSandboxProperties properties) {
        if (StringUtils.hasText(spec.getAgentImage())) {
            return spec.getAgentImage();
        }
        return properties.getDefaultAgentImage();
    }

    private String resolveRunnerImage(SandboxSpec spec, K8sSandboxProperties properties) {
        if (StringUtils.hasText(spec.getRunnerImage())) {
            return spec.getRunnerImage();
        }
        if (StringUtils.hasText(properties.getDefaultRunnerImage())) {
            return properties.getDefaultRunnerImage();
        }
        return resolveAgentImage(spec, properties);
    }

    private String resolveImagePullPolicy(SandboxSpec spec, K8sSandboxProperties properties) {
        if (StringUtils.hasText(spec.getImagePullPolicy())) {
            return spec.getImagePullPolicy();
        }
        return properties.getDefaultImagePullPolicy();
    }

    private String resolveQueueName(SandboxSpec spec, K8sSandboxProperties properties) {
        return StringUtils.hasText(spec.getQueueName()) ? spec.getQueueName() : properties.getQueueName();
    }

    private String resolvePriorityClassName(SandboxSpec spec, K8sSandboxProperties properties) {
        return StringUtils.hasText(spec.getPriorityClassName()) ? spec.getPriorityClassName() : properties.getPriorityClassName();
    }

    private Affinity buildPreferredNodeAffinity(String preferredNode) {
        return new AffinityBuilder()
            .withNewNodeAffinity()
                .addNewPreferredDuringSchedulingIgnoredDuringExecution()
                    .withWeight(100)
                    .withNewPreference()
                        .addNewMatchExpression()
                            .withKey("kubernetes.io/hostname")
                            .withOperator("In")
                            .withValues(preferredNode)
                        .endMatchExpression()
                    .endPreference()
                .endPreferredDuringSchedulingIgnoredDuringExecution()
            .endNodeAffinity()
            .build();
    }
}
