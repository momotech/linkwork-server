# linkwork-k8s-starter

K8s sandbox starter for LinkWork.  
Default provider: `k8s-volcano`.

## Configuration

```yaml
linkwork:
  agent:
    sandbox:
      provider: k8s-volcano
      k8s:
        namespace: ai-workers
        kubeconfig-path: ~/.kube/config
        scheduler-name: volcano
        queue-name: default
        priority-class-name: high-priority
```

## Contract

- `SandboxOrchestrator`: create / destroy / query sandbox.
- `SandboxSpec`: business-neutral request model.
- `mounts`: generic mount list, no storage backend coupling.
- `injectedEnvs`: environment injection map from upper layers.

## Decoupling rule

This starter only mounts paths and injects env values.  
Storage ownership and path planning should be done by caller modules.
