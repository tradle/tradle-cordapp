package com.template;

import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowProgressHandle;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.CriteriaExpression;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.plaf.nimbus.State;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.lang.reflect.Field;
import java.util.ArrayList;
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
     * Accessible at /api/share/resolveparty.
     */
    @POST
    @Path("resolveparty")
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

    private List<StateAndRef<SharedItemState>> getUnconsumed() {
        return rpcOps
            .vaultQueryByCriteria(new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), SharedItemState.class)
            .getStates();
    }

    private List<StateAndRef<SharedItemState>> getStatesForLink(String link) {
        Field linkField;
        try {
            linkField = SharedItemState.class.getDeclaredField("link");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("expected SharedItemState to have field 'link'", e);
        }

        CriteriaExpression linkCriteria = Builder.equal(linkField, link);
        QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(new QueryCriteria.VaultCustomQueryCriteria(linkCriteria));

        return rpcOps
                .vaultQueryByCriteria(criteria, SharedItemState.class)
                .getStates();

    }

    /**
     * Displays all states with unresolved "to" that exist in the node's vault.
     */
    @GET
    @Path("unresolved")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<SharedItemState>> getUnresolvedPartiesHandler(@PathParam("partyTmpId") String partyTmpId) {
        List<StateAndRef<SharedItemState>> all = getUnconsumed();
        List<StateAndRef<SharedItemState>> unresolved = new ArrayList<>();
        for (StateAndRef<SharedItemState> wrapper: all) {
            SharedItemState state = wrapper.getState().component1();
            if (state.getTo() == null) {
                if (partyTmpId == null || state.getToTmpId().equals(partyTmpId)) {
                    unresolved.add(wrapper);
                }
            }
        }

        return unresolved;
    }

    /**
     * Displays all states with unresolved "to" that exist in the node's vault.
     */
    @GET
    @Path("link")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<SharedItemState>> getStatesForLinkHandler(@PathParam("link") String link) {
        return getStatesForLink(link);
    }

    /**
     * Displays all states with unresolved "to" that exist in the node's vault.
     */
    @GET
    @Path("links")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<SharedItemState>> getLinksHandler() {
        return getUnconsumed();
    }
}