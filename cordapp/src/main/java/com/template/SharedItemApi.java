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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// This API is accessible from /api/template. The endpoint paths specified below are relative to it.
@Path("share")
public class SharedItemApi {
    static private final Logger logger = LoggerFactory.getLogger(SharedItemApi.class);
    private final List<String> apiKeys;

    private final CordaRPCOps rpcOps;
    private final SharedItemClient client;
//    private final Response forbidden = Response.status(Status.FORBIDDEN)
//        .entity("invalid api key")
//        .build();

    public SharedItemApi(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
        this.client = new SharedItemClient(rpcOps);
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        InputStream is = classloader.getResourceAsStream("certificates/apikeys.txt");
        List<String> lines = null;
        if (is != null) {
            try {
                lines = readInputStream(is);
            } catch (IOException i) {
                logger.info("No API Key set for API");
                lines = null;
            }

        }
        if (lines == null) {
            this.apiKeys = null;
        } else {
            this.apiKeys = lines.stream().filter(item -> !item.isEmpty()).collect(Collectors.toList());
        }
    }

    private void auth(String apiKey) {
        if (this.apiKeys == null || this.apiKeys.isEmpty()) return;
        if (!this.apiKeys.contains((apiKey))) {
            throw new WebApplicationException(Status.FORBIDDEN);
        }
    }

    private List<String> readInputStream(InputStream is) throws IOException {
        InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(streamReader);
        List<String> lines = new ArrayList<>();
        for (String line; (line = reader.readLine()) != null;) {
            lines.add(line);
        }

        return lines;
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
    public List<StateAndRef<SharedItemState>> getUnresolvedPartiesHandler(
            @HeaderParam("Authorization") String apiKey,
            @QueryParam("partyTmpId") String partyTmpId) {
        auth(apiKey);

        return client.getSharedItemsWithUnresolvedTo1(partyTmpId);
    }

    /**
     * List parties for which there are shared items
     */
    @GET
    @Path("parties")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Party> getUnresolvedPartiesHandler(
            @HeaderParam("Authorization") String apiKey
    ) {
        auth(apiKey);

        return client.listParties();
    }

    /**
     * Displays all states with unresolved "to" that exist in the node's vault.
     */
    @GET
    @Path("items")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<SharedItemState>> listWithMatchHandler(
            @HeaderParam("Authorization") String apiKey,
            @QueryParam("link") String link,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("toTmpId") String toTmpId,
            @QueryParam("timestamp") Long timestamp
    ) {
        auth(apiKey);

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
            @HeaderParam("Authorization") String apiKey,
            @FormParam("link") String link,
            @FormParam("partyName") CordaX500Name partyName,
            @FormParam("partyTmpId") String partyTmpId
    ) {
        auth(apiKey);

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
            @HeaderParam("Authorization") String apiKey,
            @FormParam("partyTmpId") String partyTmpId,
            @FormParam("partyName") CordaX500Name partyName
    ) {
        auth(apiKey);

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