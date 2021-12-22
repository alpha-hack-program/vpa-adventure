package com.redhat.vpa.stress;
    
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import javax.enterprise.context.ApplicationScoped;
import java.util.UUID;

import org.jboss.logging.Logger;

@ApplicationScoped
public class CPUConsumer implements CPUWork {
    Logger logger = Logger.getLogger(CPUConsumer.class);

    int start;  // load factor % so 1 to 100%
    int end;    // for now assume endLoad > startLoad
    int duration;
    int steps;

    @Override
    public void doWork(Integer startLoad, Integer endLoad, Integer durationInSeconds, Integer steps, Integer threads) {
        logger.debug(String.format("Do work with %d %d %d %d %d", startLoad, endLoad, durationInSeconds, steps, threads));

        this.start = startLoad;
        this.end = endLoad;
        this.duration = durationInSeconds;
        this.steps = steps;

        if (steps == 0)
            this.steps = 1;

        logger.info("doWork() Start (%) " + start + " End (%) " + end + " Duration (s) " + duration + " Steps " + steps + " with cpu: " + Runtime.getRuntime().availableProcessors());

        if (threads == 0)
            threads = 1;

            // int threads = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < threads; i++)
        {
            Uni.createFrom().item(UUID::randomUUID).emitOn(Infrastructure.getDefaultWorkerPool()).subscribe().with(
                    this::worker, Throwable::printStackTrace
            );
        }
    }


    private Uni<Void> worker(UUID uuid) {

        int stepTime = duration / steps; // assume duration is in seconds, so stepTime is also seconds
        int chunks = (end - start) / steps; // chunks is the intensity delta
        int intensity = start;

        logger.info("Starting work: " + uuid + " stepTime (s) " + stepTime + " chunks " + chunks + " intensity % " +  intensity);

        for ( int i = 0; i < steps; i++)
        {
            try {

                workfor(uuid, stepTime, intensity);

                // increase the intensity for each step
                intensity += chunks;

            } catch (Throwable e) {
                logger.info("Could not finish work: " + uuid);
                throw new RuntimeException(e);
                // System.exit(1);
            }

        }

        logger.info("Finish work: " + uuid);
        return Uni.createFrom().voidItem();
    }

    private void workfor(UUID uuid, int duration, int intensity)
    {
        logger.info("workfor(): " + uuid + " duration (s) " + duration + " intensity % " + intensity);
        for (int i = 0; i < duration; i++)
        {
            workForASecond(uuid, intensity);
        }
    }
    private void workForASecond(UUID uuid, int intensity)
    {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 1000;

        // intensity is a % where 100 is very intense ie no delays, and 1% is low intensity where 99% of the time is spent 
        // asleep
        long sleepTime = ((100 - intensity)*1000) / 100; // take the reverse % of the duration and sleep that time

        logger.info("workforASecond(): " + uuid + " startTime " + startTime + " endTime " + endTime + " sleepTime (ms) " + sleepTime);
        try
        {
            Thread.sleep(sleepTime);

       } catch (Throwable e) {
            logger.info("Could not finish workfor() function: " + uuid);
            throw new RuntimeException(e);
        }

        double x = startTime;
        while (System.currentTimeMillis() < endTime)
        {
            x = x / 1234567; // hard sums
        }
    }
}

