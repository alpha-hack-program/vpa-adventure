package com.redhat.vpa.stress;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import javax.ws.rs.QueryParam;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Path("/stress")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StressResource {

    @Inject
    Work work;
    
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello RESTEasy";
    }

    @GET
    @Path("memory")
    public String memory(@QueryParam("start") Integer start, @QueryParam("end") Integer end, @QueryParam("period") Integer period) {

        for(int i = 0; i < 10; i++) {
            work.doWork();
        }

        return "Hello RESTEasy";
    }

    @GET
    @Path("cpu")
    public String cpu(@QueryParam("start") Integer start, @QueryParam("end") Integer end, @QueryParam("period") Integer period) {

        for(int i = 0; i < 10; i++) {
            work.doWork();
        }

        return "Hello RESTEasy";
    }
}