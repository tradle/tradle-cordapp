package com.template;

import net.corda.core.contracts.StateAndRef;
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
import java.util.stream.Collectors;

// This API is accessible from /api/template. The endpoint paths specified below are relative to it.
@Path("share")
public class SharedItemApi {
    static private final Logger logger = LoggerFactory.getLogger(SharedItemApi.class);

    private final CordaRPCOps rpcOps;
    private final SharedItemClient client;

    public SharedItemApi(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
        this.client = new SharedItemClient(rpcOps);
    }

    private List<TransactionIdWrapper> txsToIds (List<SignedTransaction> txs) {
        return txs.stream().map(TransactionIdWrapper::new).collect(Collectors.toList());
    }

    /**
     * Displays all states with unresolved "to" that exist in the node's vault.
     */
    @GET
    @Path("unresolved")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<SharedItemState>> getUnresolvedPartiesHandler(@QueryParam("partyTmpId") String partyTmpId) {
        return client.getSharedItemsWithUnresolvedTo1(partyTmpId);
    }

    /**
     * List parties for which there are shared items
     */
    @GET
    @Path("parties")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Party> getUnresolvedPartiesHandler() {
        return client.listParties();
    }

    /**
     * Displays all states with unresolved "to" that exist in the node's vault.
     */
    @GET
    @Path("items")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<SharedItemState>> listWithMatchHandler(
            @QueryParam("link") String link,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("toTmpId") String toTmpId,
            @QueryParam("timestamp") Long timestamp
    ) {
        if (link == null && from == null && to == null && toTmpId == null && timestamp == null) {
            return client.list();
        }

        return client.listWithMatch(new SharedItemState(
                from == null ? null : rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse(from)),
                to == null ? null : rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse(from)),
                toTmpId,
                link,
                timestamp == null ? -1 : timestamp
        ));
    }

    /**
     * Accessible at /api/share/item
     */
    @POST
    @Path("item")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSharedItemHandler(
            @FormParam("link") String link,
            @FormParam("partyName") CordaX500Name partyName,
            @FormParam("partyTmpId") String partyTmpId
    ) {
        Party party = null;
        if (partyName == null) {
            if (partyTmpId == null) {
                return Response
                        .status(Status.BAD_REQUEST)
                        .entity("Query parameter 'partyName' or 'partyTmpId' must be provided.\n")
                        .build();
            }
        } else {
            final Party otherParty = rpcOps.wellKnownPartyFromX500Name(partyName);
            if (otherParty == null) {
                return Response
                        .status(Status.BAD_REQUEST)
                        .entity("Party named " + partyName + "cannot be found.\n")
                        .build();
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

            return Response
                    .status(Status.CREATED)
                    .entity(new TransactionIdWrapper(result.getId().toString()))
                    .build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(msg)
                    .build();
        }
    }

    /**
     * Accessible at /api/share/resolveparty
     */
    @POST
    @Path("resolveparty")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response resolvePartyHandler(
            @FormParam("partyTmpId") String partyTmpId,
            @FormParam("partyName") CordaX500Name partyName
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

            return Response.status(Status.CREATED)
                    .entity(txsToIds(result))
                    .build();
        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(Response.Status.BAD_REQUEST).entity(msg).build();
        }
    }
}