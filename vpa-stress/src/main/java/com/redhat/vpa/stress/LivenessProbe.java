package com.redhat.vpa.stress;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@ApplicationScoped
@Liveness
public class LivenessProbe implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        // return HealthCheckResponse.up("I'm ready");
        return HealthCheckResponse.up(LivenessProbe.class.getSimpleName());
    }

}
