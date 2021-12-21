# Use Case 4 - VPA and Cluster Autoscaler

1. Create a project without LimitRange

```bash
PROJECT=test-vpa-uc4-$RANDOM
```

```bash
oc new-project $PROJECT
```

2. Deploy the Operator of Vertical Pod Autoscaler in the cluster:

```bash
cat <<EOF | oc -n $PROJECT apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  labels:
    operators.coreos.com/vertical-pod-autoscaler.openshift-vertical-pod-autoscaler: ''
  name: vertical-pod-autoscaler
  namespace: openshift-vertical-pod-autoscaler
spec:
  channel: '4.8'
  installPlanApproval: Automatic
  name: vertical-pod-autoscaler
  source: redhat-operators
  sourceNamespace: openshift-marketplace
```

3. Modify the minReplicas to 1 in the VerticalPodAutoscalerController:

By default, workload objects must specify a minimum of two replicas in order for the VPA to automatically delete and update their pods.

As a result, workload objects that specify fewer than two replicas are not automatically acted upon by the VPA. The VPA does update new pods from these workload objects if the pods are restarted by some process external to the VPA.

We can change this cluster-wide minimum value by modifying the minReplicas parameter in the VerticalPodAutoscalerController custom resource (CR).

```sh
kubectl -n openshift-vertical-pod-autoscaler patch VerticalPodAutoscalerController default --type='json' -p='[{"op": "replace", "path": "/spec/minReplicas", "value":1}]'
```

* Check the VPA Controller parameters to check the minReplicas:

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

3. Extract the machineset for scale the worker nodes to 1:

```bash
MACHINESET=$(oc get machineset -n openshift-machine-api --no-headers=true | awk '{ print $1 }')

echo $MACHINESET
ocp-8vr6j-worker-0
```

* Scale down to 1 replica the worker nodes available, to stress easily our cluster:

```bash
oc scale machineset --replicas 1 -n openshift-machine-api $MACHINESET
machineset.machine.openshift.io/ocp-8vr6j-worker-0 scaled
```

* After couple of minutes, check the workers nodes that are present in the cluster:

```bash
oc get nodes -l kubernetes.io/os=linux,node-role.kubernetes.io/worker=
NAME                       STATUS   ROLES    AGE     VERSION
ocp-8vr6j-worker-0-vs4xr   Ready    worker   5m56s   v1.22.0-rc.0+a44d0f0
```

* Extract the name of the worker that it's on the cluster:

```bash
WORKER1=$(oc get nodes -l kubernetes.io/os=linux,node-role.kubernetes.io/worker= --no-headers=true | awk '{ print $1 }')
```

* Extract the [Node Allocatable](https://kubernetes.io/docs/tasks/administer-cluster/reserve-compute-resources/#node-allocatable) information:

Node Allocatable: Describes the resources available on the node: CPU, memory, and the maximum number of pods that can be scheduled onto the node.

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

The fields in the capacity block indicate the total amount of resources that a Node has. The allocatable block indicates the amount of resources on a Node that is available to be consumed by normal Pods.

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

4. Patch the ingress controller operator to have one replica only of the openshift ingress deployed in the worker node:

```bash
oc patch -n openshift-ingress-operator ingresscontroller/default --patch '{"spec":{"replicas": 1}}' --type=merge
ingresscontroller.operator.openshift.io/default patched
```

* Check that the pod of OpenShift ingress have only one replica:

```bash
oc get pod -n openshift-ingress
NAME                              READY   STATUS    RESTARTS   AGE
router-default-785c88c8b4-vb64b   1/1     Running   0          4h23m
```

5. Delete any preexistent LimitRange:

```bash
oc -n $PROJECT delete limitrange --all
```

6. Deploy stress application into the ns:

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
        resources:
          requests:
            memory: 4000Mi
EOF
```

On the other hand, we used the stress image, and as in the [Image Stress Documentation](https://linux.die.net/man/1/stress) is described, we can define the arguments for allocate certain amount of memory:

```md
- -m, --vm N: spawn N workers spinning on malloc()/free()
- --vm-bytes B: malloc B bytes per vm worker (default is 256MB)
```

So we defined 50G of memory allocation by the stress process.

7. Check that the pod is up && running:

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
          memory: 60Gi
        controlledResources: ["cpu", "memory"]
EOF
```

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
cat <<EOF | oc -n $PROJECT apply -f -
apiVersion: autoscaling.openshift.io/v1
kind: ClusterAutoscaler
metadata:
  name: default
spec:
  podPriorityThreshold: -10
  resourceLimits:
    cores:
      max: 256
      min: 2
    maxNodesTotal: 8
    memory:
      max: 512
      min: 4
  scaleDown:
    delayAfterAdd: 10m
    delayAfterDelete: 5m
    delayAfterFailure: 30s
    enabled: true
    unneededTime: 5m
EOF
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
