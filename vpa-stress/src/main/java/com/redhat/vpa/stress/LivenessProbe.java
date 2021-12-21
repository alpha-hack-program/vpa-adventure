package com.redhat.vpa.stress;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.jboss.logging.Logger;

@ApplicationScoped
@Liveness
public class LivenessProbe implements HealthCheck {
    Logger logger = Logger.getLogger(LivenessProbe.class);

    @Inject
    MemoryConsumer memoryConsumer;

    @Override
    public HealthCheckResponse call() {
        logger.info("memoryConsumer.memoryFailed " + memoryConsumer.isMemoryFailed());

        if (memoryConsumer.isMemoryFailed()) {
            return HealthCheckResponse.down(LivenessProbe.class.getSimpleName());
        }

        return HealthCheckResponse.up(LivenessProbe.class.getSimpleName());
    }

}
