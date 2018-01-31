package com.template;

import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowProgressHandle;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;

// This API is accessible from /api/template. The endpoint paths specified below are relative to it.
@Path("share")
public class SharedItemApi {
    static private final Logger logger = LoggerFactory.getLogger(SharedItemApi.class);

    private final CordaRPCOps rpcOps;

    public SharedItemApi(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
    }

    /**
     * Accessible at /api/template/templateGetEndpoint.
     */
    @POST
    @Path("link")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSharedItem(
            @QueryParam("link") String link,
            @QueryParam("partyName") CordaX500Name partyName,
            @QueryParam("partyTmpId") String partyTmpId
    ) {
        Party party = null;
        if (partyName == null) {
            if (partyTmpId == null) {
                return Response
                        .status(Status.BAD_REQUEST)
                        .entity("Query parameter 'partyName' or 'partyTmpId' must be provided.\n").build();
            }
        } else {
            final Party otherParty = rpcOps.wellKnownPartyFromX500Name(partyName);
            if (otherParty == null) {
                return Response
                        .status(Status.BAD_REQUEST)
                        .entity("Party named " + partyName + "cannot be found.\n").build();
            }
        }

        try {
            FlowProgressHandle<SignedTransaction> flowHandle;
            if (party != null) {
                flowHandle = rpcOps.startTrackedFlowDynamic(SharedItemCreateFlow.class, party, link);
            } else {
                flowHandle = rpcOps.startTrackedFlowDynamic(SharedItemCreateFlow.class, partyTmpId, link);
            }

            flowHandle.getProgress().subscribe(evt -> System.out.printf(">> %s\n", evt));

            // The line below blocks and waits for the flow to return.
            final SignedTransaction result = flowHandle
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\n", result.getId());
            return Response.status(Status.CREATED).entity(msg).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(Response.Status.BAD_REQUEST).entity(msg).build();
        }
    }

    /**
     * Accessible at /api/template/templateGetEndpoint.
     */
    @POST
    @Path("resolveparty")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSharedItem(
            @QueryParam("partyTmpId") String partyTmpId,
            @QueryParam("partyName") CordaX500Name partyName
    ) {
        if (partyName == null) {
            return Response
                    .status(Status.BAD_REQUEST)
                    .entity("Query parameter 'partyName' must be provided.\n").build();
        }

        final Party party = rpcOps.wellKnownPartyFromX500Name(partyName);
        if (party == null) {
            return Response
                    .status(Status.BAD_REQUEST)
                    .entity("Party named " + partyName + "cannot be found.\n").build();
        }

        try {
            FlowProgressHandle<List<SignedTransaction>> flowHandle = rpcOps.startTrackedFlowDynamic(ResolveToIdentityFlow.class, partyTmpId, party);
            flowHandle.getProgress().subscribe(evt -> System.out.printf(">> %s\n", evt));

            // The line below blocks and waits for the flow to return.
            final List<SignedTransaction> result = flowHandle
                    .getReturnValue()
                    .get();

            StringBuilder ids = new StringBuilder();
            for (SignedTransaction tx: result) {
                ids.append(tx.getId());
                ids.append(", ");
            }

            final String msg = String.format("Transactions %s committed to ledger.\n", ids.toString());
            return Response.status(Status.CREATED).entity(msg).build();
        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(Response.Status.BAD_REQUEST).entity(msg).build();
        }
    }
}