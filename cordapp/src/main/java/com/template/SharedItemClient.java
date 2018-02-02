package com.template;

import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCClientConfiguration;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.CriteriaExpression;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.utilities.NetworkHostAndPort;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Demonstration of how to use the CordaRPCClient to connect to a Corda Node and
 * stream the contents of the node's vault.
 */
public class SharedItemClient {
    private static final Logger logger = LoggerFactory.getLogger(SharedItemClient.class);

    private static void logState(StateAndRef<SharedItemState> state) {
        logger.info("{}", state.getState().getData());
    }

    private final CordaRPCOps rpcOps;

    public SharedItemClient (CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
    }

    /**
     * @return state tips
     */
    public List<StateAndRef<SharedItemState>> getSharedItems() {
        QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
        return rpcOps
                .vaultQueryByCriteria(criteria, SharedItemState.class)
                .getStates();
    }

    /**
     * @param partyTmpId optional
     * @return state tips, optionally limited to those with unresolved identities
     */
    public List<StateAndRef<SharedItemState>> getUnresolvedParties(String partyTmpId) {
        Field toTmpIdField;
        try {
            toTmpIdField = SharedItemState.class.getDeclaredField("toTmpId");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("expected SharedItemState to have field 'toTmpId'", e);
        }

        CriteriaExpression linkCriteria = Builder.equal(toTmpIdField, partyTmpId);
        QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(new QueryCriteria.VaultCustomQueryCriteria(linkCriteria));

        return rpcOps
                .vaultQueryByCriteria(criteria, SharedItemState.class)
                .getStates();
    }

    public List<StateAndRef<SharedItemState>> getStatesWithLink(String link) {
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

    public static void main(String[] args) throws ActiveMQException, InterruptedException, ExecutionException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: TemplateClient <node address>");
        }

        final NetworkHostAndPort nodeAddress = NetworkHostAndPort.parse(args[0]);
        final CordaRPCClient client = new CordaRPCClient(nodeAddress, CordaRPCClientConfiguration.DEFAULT);

        // Can be amended in the Main file.
        final CordaRPCOps proxy = client.start("user1", "test").getProxy();
        final SharedItemClient sClient = new SharedItemClient(proxy);
        sClient.getStatesWithLink("abc");
//        // Grab all existing TemplateStates and all future TemplateStates.
//        final DataFeed<Vault.Page<SharedItemState>, Vault.Update<SharedItemState>> dataFeed = proxy.vaultTrack(SharedItemState.class);
//
//        final Vault.Page<SharedItemState> snapshot = dataFeed.getSnapshot();
//        final Observable<Vault.Update<SharedItemState>> updates = dataFeed.getUpdates();
//
//        // Log the existing TemplateStates and listen for new ones.
//        snapshot.getStates().forEach(SharedItemClient::logState);
//        updates.toBlocking().subscribe(update -> update.getProduced().forEach(SharedItemClient::logState));
    }
}