package com.redhat.vpa.stress;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import javax.ws.rs.QueryParam;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jboss.logging.Logger;


@Path("/stress")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StressResource {
    Logger logger = Logger.getLogger(StressResource.class);

    @Inject
    MemoryConsumer memoryConsumer;

    @Inject
    CPUConsumer cpuConsumer;
    
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello RESTEasy";
    }

    @GET
    @Path("memory")
    public String memory(@QueryParam("start") Integer start, @QueryParam("end") Integer end, @QueryParam("duration") Integer duration, @QueryParam("steps") Integer steps) {

        memoryConsumer.doWork(start, end, duration, steps);
        
        return "Hello RESTEasy";
    }

    @GET
    @Path("cpu")
    public String cpu(@QueryParam("start") Integer startLoad, @QueryParam("end") Integer endLoad, @QueryParam("duration") Integer durationInSeconds, 
            @QueryParam("steps") Integer steps, @QueryParam("threads") Integer threads) 
    {
        logger.info(String.format("Do work with %d %d %d %d %d", startLoad, endLoad, durationInSeconds, steps, threads));
        cpuConsumer.doWork(startLoad, endLoad, durationInSeconds, steps, threads);

        return "Hello RESTEasy";
    }
}