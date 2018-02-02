package com.template;

import com.google.common.collect.ImmutableList;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCClientConfiguration;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.CriteriaExpression;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.utilities.NetworkHostAndPort;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    public List<StateAndRef<SharedItemState>> list() {
        QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
        return rpcOps
                .vaultQueryByCriteria(criteria, SharedItemState.class)
                .getStates();
    }

    public List<Party> listParties() {
        Set<Party> parties = new HashSet<>();
        list().forEach(item -> {
            SharedItemState state = item.getState().getData();
            parties.add(state.getFrom());
            if (state.getTo() != null) {
                parties.add(state.getTo());
            }
        });

        return ImmutableList.copyOf(parties);
    }

    public List<StateAndRef<SharedItemState>> listWithFilter(Predicate<SharedItemState> filter) {
        QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
        return rpcOps
                .vaultQueryByCriteria(criteria, SharedItemState.class)
                .getStates()
                .stream()
                .filter(state -> filter.test(state.getState().getData()))
                .collect(Collectors.toList());
    }

    public List<StateAndRef<SharedItemState>> listWithMatch(SharedItemState state) {
        return listWithFilter(item -> {
            if (state.getLink() != null && !item.getLink().equals(state.getLink())) return false;
            if (state.getTo() != null && !item.getTo().equals(state.getTo())) return false;
            if (state.getToTmpId() != null && !item.getToTmpId().equals(state.getToTmpId())) return false;
            if (state.getFrom() != null && !item.getFrom().equals(state.getFrom())) return false;
            if (state.getTimestamp() > 0 && item.getTimestamp() != state.getTimestamp()) return false;
            return true;
        });
    }


    /**
     * @return state tips with unresolved to
     * @implNote: this method is inefficient as it filters in memory
     * remove when the custom schema registration is figured out
     */
    public List<StateAndRef<SharedItemState>> getSharedItemsWithUnresolvedTo1(String partyTmpId) {
        return listWithFilter(state -> {
            if (state.getTo() != null) return false;

            return partyTmpId == null || state.getToTmpId().equals(partyTmpId);
        });
    }

    /**
     * @return state tips with unresolved to
     * @implNote: this method is inefficient as it filters in memory
     * remove when the custom schema registration is figured out
     */
    public List<StateAndRef<SharedItemState>> getSharedItemsWithUnresolvedTo1() {
        return getSharedItemsWithUnresolvedTo1(null);
    }

    /**
     * @return state tips with unresolved to
     * @implNote: this method is inefficient as it filters in memory
     * remove when the custom schema registration is figured out
     */
    public List<StateAndRef<SharedItemState>> getStatesWithLink1(String link) {
        return listWithFilter(state -> state.getLink().equals(link));
    }

    public List<StateAndRef<SharedItemState>> getSharedItemsWithUnresolvedTo() {
        return getSharedItemsWithUnresolvedTo(null);
    }

    /**
     * @param partyTmpId optional
     * @return state tips with unresolved identities
     */
    public List<StateAndRef<SharedItemState>> getSharedItemsWithUnresolvedTo(String partyTmpId) {
        Field toTmpIdField;
        try {
            toTmpIdField = SharedItemState.class.getDeclaredField("toTmpId");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("expected SharedItemState to have field 'toTmpId'", e);
        }

        Field toField;
        try {
            toField = SharedItemState.class.getDeclaredField("to");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("expected SharedItemState to have field 'to'", e);
        }

        CriteriaExpression toTmpIdCriteria = Builder.equal(toTmpIdField, partyTmpId);
        CriteriaExpression toCriteria = Builder.isNull(toField);
        QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(new QueryCriteria.VaultCustomQueryCriteria(toCriteria));

        if (partyTmpId != null) {
            criteria = criteria.and(new QueryCriteria.VaultCustomQueryCriteria(toTmpIdCriteria));
        }

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