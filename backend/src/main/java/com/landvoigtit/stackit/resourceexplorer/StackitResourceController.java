package com.landvoigtit.stackit.resourceexplorer;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/resources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StackitResourceController {

    private final StackitResourceService service;

    @Inject
    public StackitResourceController(final StackitResourceService service) {
        this.service = service;
    }

    @POST
    public final Response create(final StackitResourceDto dto) {
        final StackitResourceDto created = service.save(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}")
    public final Response getById(final @PathParam("id") String id) {
        final StackitResourceDto dto = service.findById(id);
        if (dto == null) {
            throw new NotFoundException("Resource with ID " + id + " not found");
        }
        return Response.ok(dto).build();
    }

    @GET
    public final ResourceSearchResultDto listAll(@QueryParam("q") final String query) {
        return service.searchResources(query);
    }

    @GET
    @Path("/billing-summary")
    public final List<BillingSummaryDto> getBillingSummary() {
        return service.getBillingSummary();
    }
}
