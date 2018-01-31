package com.template;

// Add these imports:
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.contracts.TimeWindow;
import net.corda.core.flows.*;
import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Command;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

import javax.annotation.Nullable;

import static com.template.SharedItemContract.SHARED_SPACE_CONTRACT_ID;

import java.security.PublicKey;
import java.security.SignatureException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@InitiatingFlow
@StartableByRPC
public class SharedItemCreateFlow extends FlowLogic<SignedTransaction> {
    private final Party to;
    private final String toTmpId;
    private final String link;
    private final long timestamp;
    private static final Step ID_OTHER_NODES = new Step("Identifying other nodes on the network.");
    private static final Step OTHER_TX_COMPONENTS = new Step("Gathering a transaction's other components.");
    private static final Step TX_BUILDING = new Step("Building a transaction.");
    private static final Step TX_VERIFICATION = new Step("Verifying a transaction.");
    private static final Step TX_SIGNING = new Step("Signing a transaction.");
    private static final Step SIGS_GATHERING = new Step("Gathering a transaction's signatures.") {
        // Wiring up a child progress tracker allows us to see the
        // subflow's progress steps in our flow's progress tracker.
        @Override
        public ProgressTracker childProgressTracker() {
            return CollectSignaturesFlow.tracker();
        }
    };
    private static final Step VERIFYING_SIGS = new Step("Verifying a transaction's signatures.");
    private static final Step FINALISATION = new Step("Finalising a transaction.") {
        @Override
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.tracker();
        }
    };

    private final ProgressTracker progressTracker = new ProgressTracker(
            ID_OTHER_NODES,
            OTHER_TX_COMPONENTS,
            TX_BUILDING,
            TX_VERIFICATION,
            TX_SIGNING,
            SIGS_GATHERING,
            VERIFYING_SIGS,
            FINALISATION
    );

    public SharedItemCreateFlow(Party to, String link) {
        this.to = to;
        this.toTmpId = null;
        this.link = link;
        this.timestamp = System.currentTimeMillis();
    }

    public SharedItemCreateFlow(String toTmpId, String link) {
        this.to = null;
        this.toTmpId = toTmpId;
        this.link = link;
        this.timestamp = System.currentTimeMillis();
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
    public SignedTransaction call() throws FlowException {
        progressTracker.setCurrentStep(ID_OTHER_NODES);

        // We retrieve the notary identity from the network map.
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        progressTracker.setCurrentStep(OTHER_TX_COMPONENTS);

        // We create the transaction components.
        SharedItemState outputState = to == null
            ?    new SharedItemState(getOurIdentity(), toTmpId, this.link, timestamp)
            :    new SharedItemState(getOurIdentity(), to, this.link, timestamp);

        StateAndContract outputContractAndState = new StateAndContract(outputState, SHARED_SPACE_CONTRACT_ID);
        // only consumer's signature is required
        List<PublicKey> requiredSigners = new ArrayList<>();
        requiredSigners.add(getOurIdentity().getOwningKey());
        if (to != null) requiredSigners.add(to.getOwningKey());

        Command cmd = new Command<>(new SharedItemContract.Create(), ImmutableList.copyOf(requiredSigners));
        final TimeWindow window = TimeWindow.withTolerance(getServiceHub().getClock().instant(), Duration.ofSeconds(30));

        progressTracker.setCurrentStep(TX_BUILDING);

        // We create a transaction builder and add the components.
        final TransactionBuilder txBuilder = new TransactionBuilder(notary);
        txBuilder.withItems(outputContractAndState, cmd, window);

        progressTracker.setCurrentStep(TX_VERIFICATION);
        txBuilder.verify(getServiceHub());

        progressTracker.setCurrentStep(TX_SIGNING);
        // Signing the transaction.
        final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        progressTracker.setCurrentStep(SIGS_GATHERING);

        if (to == null) {
            return subFlow(new FinalityFlow(signedTx));
        }

        // Creating a session with the other party.
        FlowSession recipientSession = initiateFlow(to);

        // Obtaining the counterparty's signature.
        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(
                signedTx, ImmutableList.of(recipientSession), CollectSignaturesFlow.tracker()));

        progressTracker.setCurrentStep(VERIFYING_SIGS);


        // Finalising the transaction.
        return subFlow(new FinalityFlow(fullySignedTx));
    }
}
