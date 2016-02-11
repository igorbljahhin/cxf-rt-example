package com.example.rt;

import com.example.dto.VersionDTO;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

public interface VersionResource {
    @GET
    @Path("/current")
    @Produces(MediaType.APPLICATION_JSON)
    VersionDTO getCurrentVersion();
}
