package com.template;

// Add these imports:
import com.google.common.collect.ImmutableList;
import com.template.schema.SharedItemSchemaV1;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Command;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.*;
import net.corda.core.node.services.vault.QueryCriteria.*;
import net.corda.core.node.services.vault.QueryCriteriaUtils;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

import static com.template.SharedItemContract.SHARED_SPACE_CONTRACT_ID;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@InitiatingFlow
@StartableByRPC
public class ResolveToIdentityFlow extends FlowLogic<List<SignedTransaction>> {
    public static int PAGE_SIZE = 200;
    private final Party party;
    private final String tmpId;
    private static final Step ID_OTHER_NODES = new Step("Identifying other nodes on the network.");
    private static final Step QUERY_VAULT = new Step("Querying vault for items with unresolved 'to'.");
    private static final Step RESOLVE_TO = new Step("Creating transactions per 'to' party resolved.");
    private static final Step TX_BUILDING = new Step("Building a transaction.");
    private static final Step TX_VERIFICATION = new Step("Verifying a transaction.");
    private static final Step TX_SIGNING = new Step("Signing a transaction.");
    private static final Step TX_SHARING = new Step("Sharing transaction with resolved party.");
    private static final Step FINALISATION = new Step("Finalising a transaction.") {
        @Override
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.tracker();
        }
    };

    private final ProgressTracker progressTracker = new ProgressTracker(
            ID_OTHER_NODES,
            QUERY_VAULT,
            RESOLVE_TO,
            TX_BUILDING,
            TX_VERIFICATION,
            TX_SIGNING,
            TX_SHARING,
            FINALISATION
    );

    public ResolveToIdentityFlow(String tmpId, Party party) {
        this.party = party;
        this.tmpId = tmpId;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    @Override
    public List<SignedTransaction> call() throws FlowException {
        progressTracker.setCurrentStep(ID_OTHER_NODES);

        // We retrieve the notary identity from the network map.
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        progressTracker.setCurrentStep(QUERY_VAULT);

        Field toField;
        try {
            toField = SharedItemSchemaV1.PersistentSharedItem.class.getDeclaredField("to");
        } catch (NoSuchFieldException f) {
            throw new FlowException("expected schema to have field 'to'", f);
        }

        CriteriaExpression tmpIdCriteria = Builder.isNull(toField);
//        QueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
        QueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(new VaultCustomQueryCriteria(tmpIdCriteria));

        Vault.Page<SharedItemState> results = getServiceHub()
                .getVaultService()
                .queryBy(SharedItemState.class, criteria, new PageSpecification(QueryCriteriaUtils.DEFAULT_PAGE_NUM, PAGE_SIZE));

        List<SignedTransaction> result = new ArrayList<>();
        progressTracker.setCurrentStep(RESOLVE_TO);
        for (StateAndRef<SharedItemState> stateAndRef: results.getStates()) {
            SignedTransaction signedTx = setTo(stateAndRef, party, notary);
            result.add(signedTx);
        }

        return result;
    }

    @Suspendable
    private SignedTransaction setTo(StateAndRef<SharedItemState> stateAndRef, Party to, Party notary) throws FlowException {
//        final TimeWindow window = TimeWindow.withTolerance(getServiceHub().getClock().instant(), Duration.ofSeconds(30));
        SharedItemState inputState = stateAndRef.getState().getData();
        Command cmd = new Command<>(new SharedItemContract.ResolveTo(), ImmutableList.of(getOurIdentity().getOwningKey()));
        SharedItemState outputState = new SharedItemState(inputState.getFrom(), to, inputState.getToTmpId(), inputState.getLink(), inputState.getTimestamp());
        StateAndContract outputContractAndState = new StateAndContract(outputState, SHARED_SPACE_CONTRACT_ID);

        progressTracker.setCurrentStep(TX_BUILDING);

        // We create a transaction builder and add the components.
        final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addInputState(getServiceHub().toStateAndRef(stateAndRef.getRef()))
                .withItems(outputContractAndState, cmd);

        progressTracker.setCurrentStep(TX_VERIFICATION);
        txBuilder.verify(getServiceHub());

        progressTracker.setCurrentStep(TX_SIGNING);
        final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

//        progressTracker.setCurrentStep(TX_SHARING);
//        FlowSession session = initiateFlow(party);
//        subFlow(new SendTransactionFlow(session, signedTx));

        progressTracker.setCurrentStep(FINALISATION);
        return subFlow(new FinalityFlow(signedTx));
    }
}
