# Vertical Pod Autoscaler Adventure

Vertical Pod Autoscaler (VPA) frees the users from necessity of setting up-to-date resource limits and requests for the containers in their pods.

When configured, it will set the requests automatically based on usage and thus allow proper scheduling onto nodes so that appropriate resource amount is available for each pod. It will also maintain ratios between limits and requests that were specified in initial containers configuration.

## VPA Operator

The Vertical Pod Autoscaler Operator (VPA) is implemented as an API resource and a custom resource (CR). The CR determines the actions the Vertical Pod Autoscaler Operator should take with the pods associated with a specific workload object, such as a daemon set, replication controller, and so forth, in a project.

The VPA automatically computes historic and current CPU and memory usage for the containers in those pods and uses this data to determine optimized resource limits and requests to ensure that these pods are operating efficiently at all times.

## When use VPA?

For developers, you can use the VPA to help ensure your pods stay up during periods of high demand by scheduling pods onto nodes that have appropriate resources for each pod.

Administrators can use the VPA to better utilize cluster resources, such as preventing pods from reserving more CPU resources than needed.

The VPA monitors the resources that workloads are actually using and adjusts the resource requirements so capacity is available to other workloads. The VPA also maintains the ratios between limits and requests that are specified in initial container configuration.

## Use Cases

* **[Use Case 1](https://github.com/alpha-hack-program/vpa-adventure/blob/main/use_case1.md)** - Autoscaling and Applying requests and limits automatically

* **[Use Case 2](https://github.com/alpha-hack-program/vpa-adventure/blob/main/use_case2.md)** - Automatically adjust requests / limits when Apps are OOMKilled

* **[Use Case 3](https://github.com/alpha-hack-program/vpa-adventure/tree/main/vpa-stress)** - Generate VPA with our own Quarkus application that will allocate and grow in memory and CPU on demand

* **[Use Case 4](https://github.com/alpha-hack-program/vpa-adventure/blob/main/use_case4.md)** - VPA + Cluster Autoscaler - Black Friday Crisis
VPA recommendation might exceed available resources (e.g. Node size, available size, available quota) and cause pods to go pending. This can be partly addressed by using VPA together with Cluster Autoscaler.

* **Use Case x - Optional** - autoscale down the limits pod - VPA

* **Use Case x - Optional** - Integrate Alarms when you do a recommendation - AlertManager? Prometheus?

* **Use Case x - Optional** - Test Multitenancy of the VPA Operator (more than one VPA in the same OCP/K8s cluster)

## VPA Recommendation Parameters

```
  "recommendation": {
    "containerRecommendations": [
      {
        "containerName": "hamster",
        "lowerBound": {
          "cpu": "100m",
          "memory": "262144k"
        },
        "target": {
          "cpu": "247m",
          "memory": "262144k"
        },
        "uncappedTarget": {
          "cpu": "247m",
          "memory": "262144k"
        },
        "upperBound": {
          "cpu": "2",
          "memory": "2Gi"
        }
      }
```

* **Uncapped Target**: what would be the resource request configured on your pod if you didn’t configure upper limits in the VPA definition.

* **Target**: this will be the actual amount configured at the next execution of the admission webhook. (If it already has this config, no changes will happen (your pod won’t be in a restart/evict loop). Otherwise, the pod will be evicted and restarted using this target setting.)

* **Lower Bound**: when your pod goes below this usage, it will be evicted and downscaled.

* **Upper Bound**: when your pod goes above this usage, it will be evicted and upscaled.

## VPA Modes

You use the VPA CR to associate a workload object and specify which mode the VPA operates in:

* **auto** to automatically apply the recommended resources on pods associated with the controller. The VPA terminates existing pods and creates new pods with the recommended resource limits and requests.

* **recreate** to automatically apply the recommended resources on pods associated with the workload object. The VPA terminates existing pods and creates new pods with the recommended resource limits and requests. The recreate mode should be used rarely, only if you need to ensure that the pods are restarted whenever the resource request changes.

* **initial** to automatically apply the recommended resources when pods associated with the workload object are created. The VPA does not update the pods as it learns new resource recommendations.

* **off** to only generate resource recommendations for the pods associated with the workload object. The VPA does not update the pods as it learns new resource recommendations and does not apply the recommendations to new pods.


## Useful Links

* [VPA Docs Upstream](https://github.com/kubernetes/autoscaler/tree/master/vertical-pod-autoscaler)
* [Example Upstream](https://github.com/kubernetes/autoscaler/tree/master/vertical-pod-autoscaler#test-your-installation)
* [OpenShift Docs VPA](https://docs.openshift.com/container-platform/4.9/nodes/pods/nodes-pods-vertical-autoscaler.html)
* [Blog Post VPA](https://cloud.redhat.com/blog/how-full-is-my-cluster-part-4-right-sizing-pods-with-vertical-pod-autoscaler)
* [Ralvares HPA Example](https://github.com/ralvares/ocp-hpa/tree/c504b30d21596a7595fdf951737bb925d5ec04c1)
* [Stress Image Deployment](https://k8s.io/examples/pods/resource/memory-request-limit-2.yaml)
* [Memory & Limits in Kubernetes](https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource/#specify-a-memory-request-and-a-memory-limit)
