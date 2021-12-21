package com.redhat.vpa.stress;
    
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.UUID;


import org.jboss.logging.Logger;

@ApplicationScoped
public class MemoryConsumer implements Work {
    Logger logger = Logger.getLogger(MemoryConsumer.class);

    private boolean memoryFailed = false;

    private int start;
    private int end;
    private int duration;
    private int steps;

    @Override
    public void doWork(Integer start, Integer end, Integer duration, Integer steps) {
        logger.info("Do work");

        this.start = start;
        this.end = end;
        this.duration = duration;
        this.steps = steps;

        if (steps == 0)
            this.steps = 1;

        Uni.createFrom().item(UUID::randomUUID).emitOn(Infrastructure.getDefaultWorkerPool()).subscribe().with(
                this::worker, Throwable::printStackTrace
        );

    }

    public boolean isMemoryFailed() {
        return memoryFailed;
    }

    public void setMemoryFailed(boolean memoryFailed) {
        this.memoryFailed = memoryFailed;
    }

    private Uni<Void> worker(UUID uuid) {

        final int megaBytes = 1024*1024;
        int stepTime = duration / steps; // assume duration is in seconds, so stepTime is also seconds
        int chunks = (end - start) / steps; // Mb chunks to allocate over time

        logger.info("Starting work: " + uuid + " Start (Mb) " + start + " End (Mb) " + end + " Duration (s) " + duration + " Steps " + steps + " stepTime (s) " + stepTime + " chunks (Mb)" + chunks);

        Object[] bytes = new Object[steps+1];
        bytes[0] = new byte[start*megaBytes];  // initial allocation

        for ( int i = 0; i < steps; i++)
        {
            try {
                Thread.sleep((long) stepTime * 1000); // need miliseconds here

                byte[] newChunk = new byte[chunks*megaBytes];
                bytes[i] = newChunk;

                // touch the memory
                for (int j = 0; j < chunks*megaBytes; j++)
                {
                    newChunk[j] = 7;  // fill value
                }
                logger.info("Working: " + uuid + " Added " + chunks + " Mb of memory");

            } catch (InterruptedException e) {
                logger.info("Could not finish work: " + uuid);
                throw new RuntimeException(e);
            } catch (OutOfMemoryError e) {
                logger.info("Could not finish work: " + uuid);
                this.setMemoryFailed(true);
                throw new RuntimeException(e);
            } catch (Throwable e) {
                logger.info("Could not finish work: " + uuid);
                this.setMemoryFailed(true);
                throw new RuntimeException(e);
            }
        }

        // Delete memory...
        for (Object chunk : bytes) {
            chunk = null;
        }
        bytes = null;

        System.gc();

        logger.info("Finish work: " + uuid);
        return Uni.createFrom().voidItem();
    }
}

