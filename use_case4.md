# Use Case 4 - VPA and Cluster Autoscaler

1. Create a project without LimitRange

```bash
PROJECT=test-vpa-uc4-$RANDOM
```

```bash
oc new-project $PROJECT
```

2. Modify the minReplicas to 1 in the VerticalPodAutoscalerController:

```sh
kubectl -n openshift-vertical-pod-autoscaler patch VerticalPodAutoscalerController default --type='json' -p='[{"op": "replace", "path": "/spec/minReplicas", "value":1}]'
```

```sh
 oc get VerticalPodAutoscalerControllers default -n openshift-vertical-pod-autoscaler -o jsonpath='{.spec}' | jq -r .
{
  "minReplicas": 1,
  "podMinCPUMillicores": 25,
  "podMinMemoryMb": 250,
  "recommendationOnly": false,
  "safetyMarginFraction": 0.15
}
```

2. Scale down to 1 replica the worker nodes available:

```bash
MACHINESET=$(oc get machineset -n openshift-machine-api --no-headers=true | awk '{ print $1 }')

echo $MACHINESET
ocp-8vr6j-worker-0
```

```bash
oc scale machineset --replicas 1 -n openshift-machine-api $MACHINESET
machineset.machine.openshift.io/ocp-8vr6j-worker-0 scaled
```

```bash
oc get nodes -l kubernetes.io/os=linux,node-role.kubernetes.io/worker=
NAME                       STATUS   ROLES    AGE     VERSION
ocp-8vr6j-worker-0-vs4xr   Ready    worker   5m56s   v1.22.0-rc.0+a44d0f0
```

```bash
WORKER1=$(oc get nodes -l kubernetes.io/os=linux,node-role.kubernetes.io/worker= --no-headers=true | awk '{ print $1 }')
```

* [Node Allocatable](https://kubernetes.io/docs/tasks/administer-cluster/reserve-compute-resources/#node-allocatable)

Describes the resources available on the node: CPU, memory, and the maximum number of pods that can be scheduled onto the node.

The fields in the capacity block indicate the total amount of resources that a Node has. The allocatable block indicates the amount of resources on a Node that is available to be consumed by normal Pods.

```bash
oc get nodes $WORKER1 -o jsonpath='{.status.allocatable}' | jq -r .
{
  "cpu": "3500m",
  "ephemeral-storage": "144461562846",
  "hugepages-1Gi": "0",
  "hugepages-2Mi": "0",
  "memory": "19387132Ki",
  "pods": "250"
}
```

```bash
oc get nodes $WORKER1 -o jsonpath='{.status.capacity}' | jq -r .
{
  "cpu": "4",
  "ephemeral-storage": "156750828Ki",
  "hugepages-1Gi": "0",
  "hugepages-2Mi": "0",
  "memory": "20538108Ki",
  "pods": "250"
}
```

```bash
oc patch -n openshift-ingress-operator ingresscontroller/default --patch '{"spec":{"replicas": 1}}' --type=merge
ingresscontroller.operator.openshift.io/default patched
```

3. Delete any preexistent LimitRange:

```bash
oc -n $PROJECT delete limitrange --all
```

3. Deploy stress application into the ns:

```bash
cat <<EOF | oc -n $PROJECT apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: stress
spec:
  selector:
    matchLabels:
      app: stress
  replicas: 1
  template:
    metadata:
      labels:
        app: stress
    spec:
      containers:
      - name: stress
        image: polinux/stress
        command: ["stress"]
        args: ["--vm", "1", "--vm-bytes", "8000M"]
EOF
```

On the other hand, we used the stress image, and as in the [Image Stress Documentation](https://linux.die.net/man/1/stress) is described, we can define the arguments for allocate certain amount of memory:

```md
- -m, --vm N: spawn N workers spinning on malloc()/free()
- --vm-bytes B: malloc B bytes per vm worker (default is 256MB)
```

So we defined 8G of memory allocation by the stress process.

4. Check that the pod is up && running:

```sh
oc get pod
NAME                      READY   STATUS    RESTARTS   AGE
stress-7d48fdb6fb-j46b8   1/1     Running   0          35s

oc logs -l app=stress
stress: info: [1] dispatching hogs: 0 cpu, 0 io, 1 vm, 0 hdd
```

6. Check the metrics of the pod deployed:

```bash
oc adm top pod --namespace=$PROJECT --use-protocol-buffers
NAME                      CPU(cores)   MEMORY(bytes)
stress-57f968d6bb-l2dvb   490m         7092Mi
```

7. It is possible to set a range for the autoscaling: minimum and maximum values, for the requests. Apply the VPA with the minAllowed and maxAllowed as described:

```sh
cat <<EOF | oc -n $PROJECT apply -f -
apiVersion: "autoscaling.k8s.io/v1"
kind: VerticalPodAutoscaler
metadata:
  name: stress-vpa
spec:
  targetRef:
    apiVersion: "apps/v1"
    kind: Deployment
    name: stress
  resourcePolicy:
    containerPolicies:
      - containerName: '*'
        minAllowed:
          cpu: 100m
          memory: 50Mi
        maxAllowed:
          cpu: 200m
          memory: 24Gi
        controlledResources: ["cpu", "memory"]
EOF
```

NOTE: We need to be aware that the VPA will block the resources not only for memory, also for the CPU, causing the Pending state when applies the Auto Recommendation.

8. After a couple of minutes, check the VPA to see the Memory and CPU suggested:

```sh
oc get vpa
NAME         MODE   CPU    MEM          PROVIDED   AGE
stress-vpa   Auto   200m   9616998332   True       10m
```

```sh
oc get vpa stress-vpa -o jsonpath='{.status}' | jq -r .
{
  "conditions": [
    {
      "lastTransitionTime": "2021-12-21T10:34:27Z",
      "status": "True",
      "type": "RecommendationProvided"
    }
  ],
  "recommendation": {
    "containerRecommendations": [
      {
        "containerName": "stress",
        "lowerBound": {
          "cpu": "200m",
          "memory": "8597984471"
        },
        "target": {
          "cpu": "200m",
          "memory": "9616998332"
        },
        "uncappedTarget": {
          "cpu": "1469m",
          "memory": "9616998332"
        },
        "upperBound": {
          "cpu": "200m",
          "memory": "24Gi"
        }
      }
    ]
  }
}
```

* **Lower Bound**: when your pod goes below this usage, it will be evicted and downscaled.

* **Target**: this will be the actual amount configured at the next execution of the admission webhook. (If it already has this config, no changes will happen (your pod won’t be in a restart/evict loop). Otherwise, the pod will be evicted and restarted using this target setting.)

* **Uncapped Target**: what would be the resource request configured on your pod if you didn’t configure upper limits in the VPA definition.

* **Upper Bound**: when your pod goes above this usage, it will be evicted and upscaled.

9. Scale replicas 2 for our Stress:

```sh
oc scale --replicas 2 deploy stress
```

```sh
oc get pod
NAME                      READY   STATUS    RESTARTS   AGE
stress-57f968d6bb-8r958   1/1     Running   0          26m
stress-57f968d6bb-jmv6x   0/1     Pending   0          24m
```

```sh
oc adm top nodes $WORKER1 --use-protocol-buffers
NAME                       CPU(cores)   CPU%   MEMORY(bytes)   MEMORY%
ocp-8vr6j-worker-0-vs4xr   2131m        60%    12698Mi         67%

oc adm top pod --use-protocol-buffers
NAME                      CPU(cores)   MEMORY(bytes)
stress-57f968d6bb-8r958   512m         6313Mi
```

9. Let's increase the memory allocation by the stress process in our container in our stress pod, above the defined limit:

```sh
oc get pod -l app=stress -n $PROJECT -o yaml | grep requests -A2
        requests:
          cpu: 200m
          memory: "9616998332"
--
        requests:
          cpu: 200m
          memory: "9616998332"
```

```sh
oc get pod -l app=stress -n $PROJECT -o yaml | grep vpa
      vpaObservedContainers: stress
      vpaUpdates: 'Pod resources updated by stress-vpa: container 0: cpu request,
    namespace: test-vpa-uc4-2589

      vpaObservedContainers: stress
      vpaUpdates: 'Pod resources updated by stress-vpa: container 0: cpu request,
    namespace: test-vpa-uc4-2589
```

## Enable Cluster Autoscaler

* [Docs Cluster Autoscaling](https://docs.openshift.com/container-platform/4.9/machine_management/applying-autoscaling.html#cluster-autoscaler-cr_applying-autoscaling)

```sh
apiVersion: autoscaling.openshift.io/v1
kind: ClusterAutoscaler
metadata:
  name: default
  resourceVersion: '23534024'
  uid: 79a19789-e689-4c40-8a14-1218a3337ead
spec:
  podPriorityThreshold: -10
  resourceLimits:
    maxNodesTotal: 6
  scaleDown:
    delayAfterAdd: 10m
    delayAfterDelete: 5m
    delayAfterFailure: 30s
    enabled: true
    unneededTime: 5m
```

```sh
oc get machineautoscaler -n openshift-machine-api ocp-8vr6j-worker-0 -o yaml
apiVersion: autoscaling.openshift.io/v1beta1
kind: MachineAutoscaler
metadata:
  creationTimestamp: "2021-12-21T11:12:04Z"
  finalizers:
  - machinetarget.autoscaling.openshift.io
  generation: 1
  name: ocp-8vr6j-worker-0
  namespace: openshift-machine-api
  resourceVersion: "23531668"
  uid: 0e1fe7e2-c7f5-4bdc-8a5f-9824b1b1e8c6
spec:
  maxReplicas: 3
  minReplicas: 1
  scaleTargetRef:
    apiVersion: machine.openshift.io/v1beta1
    kind: MachineSet
    name: ocp-8vr6j-worker-0
status:
  lastTargetRef:
    apiVersion: machine.openshift.io/v1beta1
    kind: MachineSet
    name: ocp-8vr6j-worker-0
```














10. Check the pods to see if the OOMKilled or Crashloopbackoff state it's in our stress pod:

```sh
oc get pod -w
NAME                      READY   STATUS        RESTARTS   AGE
stress-7b9459559c-ntnrv   1/1     Running       0          5s
stress-7d48fdb6fb-j46b8   1/1     Terminating   0          22m
```

11. Check the VPA resources and :

```sh
 oc get pod -l app=stress -o yaml | grep vpa
      vpaObservedContainers: stress
      vpaUpdates: 'Pod resources updated by stress-vpa: container 0: cpu request,
```

8. Check that the VPA changed automatically the requests and limits in the POD, but NOT in the deployment or replicaset:

```sh
oc get pod -l app=stress -o yaml | grep requests -A2
        requests:
          cpu: "1"
          memory: 262144k
```

```sh
oc get pod -l app=stress -o yaml | grep limits -A1
        limits:
          memory: 500Mi
```

So what happens to the limits parameter of your pod? Of course they will be also adapted, when you touch the requests line. The VPA will proportionally scale limits.

As mentioned above, this is proportional scaling: in our default stress deployment manifest, we have the following requests to limits ratio:

* CPU: 100m -> 200m: 1:4 ratio
* memory: 100Mi -> 250Mi: 1:2.5 ratio

So when you get a scaling recommendation, it will respect and keep the same ratio you originally configured, and proportionally set the new values based on your original ratio.

8. The deployment of stress app is not changed at all, the VPA just is changing the Pod spec definition:

```
oc get deployment stress -o yaml | egrep -i 'limits|request' -A1
         requests:
            memory: "100Mi"
          limits:
            memory: "200Mi"
```

But don’t forget, your limits are almost irrelevant, as the scheduling decision (and therefore, resource contention) will be always done based on the requests.

Limits are only useful when there's resource contention or when you want to avoid uncontrollable memory leaks.
