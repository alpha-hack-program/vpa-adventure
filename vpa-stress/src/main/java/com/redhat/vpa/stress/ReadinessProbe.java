package com.redhat.vpa.stress;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

@ApplicationScoped // 1. CDI scope
@Readiness
public class ReadinessProbe implements HealthCheck {
    Logger logger = Logger.getLogger(ReadinessProbe.class);
    
    @Inject
    MemoryConsumer memoryConsumer;

    @Override
    public HealthCheckResponse call() {
        // return HealthCheckResponse.up("I'm ready");
        logger.info("memoryConsumer.memoryFailed " + memoryConsumer.isMemoryFailed());
        
        if (memoryConsumer.isMemoryFailed()) {
            return HealthCheckResponse.down(ReadinessProbe.class.getSimpleName());
        }
        return HealthCheckResponse.up(ReadinessProbe.class.getSimpleName());
    }
}
