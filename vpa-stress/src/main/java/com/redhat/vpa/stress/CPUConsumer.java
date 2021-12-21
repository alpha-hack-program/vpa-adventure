package com.redhat.vpa.stress;
    
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import javax.enterprise.context.ApplicationScoped;
import java.util.UUID;

import org.jboss.logging.Logger;

@ApplicationScoped
public class CPUConsumer implements Work {
    Logger logger = Logger.getLogger(CPUConsumer.class);

    Integer startLoad;
    Integer endLoad;
    Integer durationInSeconds;
    Integer steps;

    @Override
    public void doWork(Integer startLoad, Integer endLoad, Integer durationInSeconds, Integer steps) {
        logger.debug(String.format("Do work with %d %d %d %d", startLoad, endLoad, durationInSeconds, steps));

        this.startLoad = startLoad;
        this.endLoad = endLoad;
        this.durationInSeconds = durationInSeconds;
        this.steps = steps;

        Uni.createFrom().item(UUID::randomUUID).emitOn(Infrastructure.getDefaultWorkerPool()).subscribe().with(
                this::worker, Throwable::printStackTrace
        );

    }

    private Uni<Void> worker(UUID uuid) {
        logger.info("Starting work: " + uuid + " with cpu: " + Runtime.getRuntime().availableProcessors());
        
        double stepTime = (durationInSeconds / steps) * 1000;
        double stepLoad = (this.endLoad - this.startLoad) / steps;

        logger.debug(String.format("stepTime=%f stepLoad=%f", stepTime, stepLoad));

        for (int step = 0; step < steps; step++) {
            long startTime = System.currentTimeMillis();

            logger.debug(String.format("Math.floor((1 - (startLoad + stepLoad * step)/100)*100) = 1 - (%d + %f * %d)/100 = %d", startLoad, stepLoad, step, (long)Math.floor((1 - (startLoad + stepLoad * step)/100)*100) ));
            try {
                // Loop for the given duration
                long currentTime = System.currentTimeMillis();
                logger.debug(String.format("currentTime - startTime < stepTime = %d - %d < %f", currentTime, startTime, stepTime));
                while (currentTime - startTime < stepTime) {
                    // Every 100ms, sleep for the percentage of unladen time
                    if (System.currentTimeMillis() % 100 == 0) {
                        long sleepTime = (long) Math.floor((1 - (startLoad + stepLoad * step++)/100) * 100);
                        logger.debug(String.format("sleepTime = %d", sleepTime));
                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        }
                    }
                }
            } catch (InterruptedException e) {
                logger.info("Could not finish work: " + uuid);
                throw new RuntimeException(e);
            }    
        }

        logger.info("Finish work: " + uuid);
        return Uni.createFrom().voidItem();
    }
}

