0. Create a project without LimitRange
```
oc new-project test-vpa-2
```

1. Delete any preexistent LimitRange.
```
oc -n test-vpa-2 delete limitrange --all
```

2. Deploy Hamster application
```
cat << EOF 
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hamster
spec:
  selector:
    matchLabels:
      app: hamster
  replicas: 2
  template:
    metadata:
      labels:
        app: hamster
    spec:
      containers:
        - name: hamster
          image: k8s.gcr.io/ubuntu-slim:0.1
          resources:
            requests:
              cpu: 100m
              memory: 50Mi
            limits:
              cpu: 200m
              memory: 75Mi
          command: ["/bin/sh"]
          args:
            - "-c"
            - "while true; do timeout 0.25s yes >/dev/null; sleep 0.25s; done"
EOF | oc apply -f -
```
3. Check that the request and limits generated in Pod

```
oc get pod -l app=hamster -o yaml | grep limit -A2
```

4. Apply the VPA 
```
apiVersion: "autoscaling.k8s.io/v1"
kind: VerticalPodAutoscaler
metadata:
  name: hamster-vpa
spec:
  # recommenders field can be unset when using the default recommender.
  # When using an alternative recommender, the alternative recommender's name
  # can be specified as the following in a list.
  # recommenders: 
  #   - name: 'alternative'
  targetRef:
    apiVersion: "apps/v1"
    kind: Deployment
    name: hamster
  resourcePolicy:
    containerPolicies:
      - containerName: '*'
        minAllowed:
          cpu: 100m
          memory: 50Mi
        maxAllowed:
          cpu: 2
          memory: 2048Mi
        controlledResources: ["cpu", "memory"]
```

NOTE: we're setting the maxAllowed higher than the potential LimitRange, to see that the LimitRange max will cap/override the expected result.

5. Check the VPA resources applied:

```
oc get vpa 
NAME          MODE   CPU    MEM       PROVIDED   AGE
hamster-vpa   Auto   247m   262144k   True       3m44s

oc get vpa hamster-vpa -o jsonpath='{.status}' | jq -r .
{
  "conditions": [
    {
      "lastTransitionTime": "2021-12-20T10:54:41Z",
      "status": "True",
      "type": "RecommendationProvided"
    }
  ],
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
    ]
  }
}
```

6. Check the VPA resources:

```
oc get pod -l app=hamster -o yaml | grep vpa
      vpaObservedContainers: hamster
      vpaUpdates: 'Pod resources updated by hamster-vpa: container 0: cpu request,
    namespace: test-vpa-2
      vpaObservedContainers: hamster
      vpaUpdates: 'Pod resources updated by hamster-vpa: container 0: memory request,
    namespace: test-vpa-2
```

7. Check that the VPA changed automatically the requests and limits in the POD, but NOT in the deployment or replicaset:
```
oc get pod -l app=hamster -o yaml | grep requests -A2
        requests:
          cpu: 143m
          memory: 262144k
--
        requests:
          cpu: 143m
          memory: 262144k
```

```
oc get pod -l app=hamster -o yaml | grep limit -A2
        memory request, cpu limit, memory limit'
    creationTimestamp: "2021-12-20T10:55:41Z"
    generateName: hamster-69df47984f-
--
        limits:
          cpu: 286m
          memory: 375Mi
--
        cpu request, cpu limit, memory limit'
    creationTimestamp: "2021-12-20T10:54:41Z"
    generateName: hamster-69df47984f-
--
        limits:
          cpu: 286m
          memory: 375Mi
```

8. The deployment of hamster app is not changed at all, the VPA just is changing the Pod spec definition:

```
oc get deployment hamster -o yaml | egrep -i 'limits|request' -A2          
          limits:
            cpu: 200m
            memory: 75Mi
          requests:
            cpu: 100m
            memory: 50Mi


9. Define a LimitRange for capp / limit the maximum values:
```
kind: LimitRange
apiVersion: v1
metadata:
  name: test-vpa-limit-range
  namespace: test-vpa
spec:
  limits:
    - type: Container
      max:
        cpu: '1'
        memory: 1Gi
      default:
        cpu: 500m
        memory: 1024Mi
      defaultRequest:
        cpu: 50m
        memory: 256Mi
    - type: Pod
      max:
        cpu: '1'
        memory: 1Gi
```
