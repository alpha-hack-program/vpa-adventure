package com.redhat.vpa.stress;
    
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import javax.enterprise.context.ApplicationScoped;
import java.util.UUID;

import org.jboss.logging.Logger;

@ApplicationScoped
public class CPUConsumer implements Work {
    Logger logger = Logger.getLogger(CPUConsumer.class);

    @Override
    public void doWork(Integer start, Integer end, Integer duration, Integer steps) {
        logger.info("Do work");

        Uni.createFrom().item(UUID::randomUUID).emitOn(Infrastructure.getDefaultWorkerPool()).subscribe().with(
                this::worker, Throwable::printStackTrace
        );

    }

    private Uni<Void> worker(UUID uuid) {
        logger.info("Starting work: " + uuid);
        try {
            
            Thread.sleep((long) 10000);

        } catch (InterruptedException ex) {
            logger.info("Could not finish work: " + uuid);
            throw new RuntimeException(ex);
        }
        logger.info("Finish work: " + uuid);
        return Uni.createFrom().voidItem();
    }
}

