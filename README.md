# Vertical Pod Autoscaler Adventure

Vertical Pod Autoscaler (VPA) frees the users from necessity of setting up-to-date resource limits and requests for the containers in their pods. 

When configured, it will set the requests automatically based on usage and thus allow proper scheduling onto nodes so that appropriate resource amount is available for each pod. It will also maintain ratios between limits and requests that were specified in initial containers configuration.

NOTE: The VPA will proportionally scale limits.

## Use Cases

* **Use Case 1** - Generate VPA in the Upstream Example (https://github.com/kubernetes/autoscaler/blob/master/vertical-pod-autoscaler/examples/hamster.yaml)

* **Use Case 2** - Generate VPA with our own application (java?)

* **Use Case 3** - Integrate Alarms when you do a recommendation - AlertManager? Prometheus?  
VPA Update Off - https://docs.openshift.com/container-platform/4.9/nodes/pods/nodes-pods-vertical-autoscaler.html

* **Use Case 4** - VPA + Cluster Autoscaler - Black Friday Crisis
VPA recommendation might exceed available resources (e.g. Node size, available size, available quota) and cause pods to go pending. This can be partly addressed by using VPA together with Cluster Autoscaler.

* **Use Case x** - autoscale down the limits pod - VPA 
Use Case 4 but in reverse, autoscale down all 

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

## Useful Links

* [VPA Docs Upstream](https://github.com/kubernetes/autoscaler/tree/master/vertical-pod-autoscaler)
* [Example Upstream](https://github.com/kubernetes/autoscaler/tree/master/vertical-pod-autoscaler#test-your-installation)
* [OpenShift Docs VPA](https://docs.openshift.com/container-platform/4.9/nodes/pods/nodes-pods-vertical-autoscaler.html)
* [Blog Post VPA](https://cloud.redhat.com/blog/how-full-is-my-cluster-part-4-right-sizing-pods-with-vertical-pod-autoscaler)
