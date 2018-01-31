package com.template;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.ContractState;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

import static net.corda.core.contracts.ContractsDSL.requireThat;

@InitiatingFlow
@StartableByRPC
public class SharedItemShareFlow extends FlowLogic<Void> {
    private static final Step LOOKUP_TX = new Step("Lookup tx by id.");
    private static final Step SIGS_GATHERING = new Step("Gathering a transaction's signatures.") {
        // Wiring up a child progress tracker allows us to see the
        // subflow's progress steps in our flow's progress tracker.
        @Override
        public ProgressTracker childProgressTracker() {
            return CollectSignaturesFlow.tracker();
        }
    };

    private static final Step FINALISATION = new Step("Finalising a transaction.") {
        @Override
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.tracker();
        }
    };

    private final Party to;
    private final String txId;
    private SignedTransaction tx;
    private final ProgressTracker progressTracker = new ProgressTracker(
        LOOKUP_TX,
        SIGS_GATHERING,
        FINALISATION
    );

    public SharedItemShareFlow(Party to, String txId) {
        this.txId = txId;
        this.to = to;
    }

//    public SharedItemShareFlow(final Party to, final SignedTransaction tx) {
//        this.to = to;
//        this.tx = tx;
//    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        progressTracker.setCurrentStep(LOOKUP_TX);
        tx = getServiceHub().getValidatedTransactions().getTransaction(SecureHash.parse(txId));
        if (tx == null) {
            throw new IllegalArgumentException(String.format("transaction not found with id: %s", txId));
        }

        progressTracker.setCurrentStep(SIGS_GATHERING);
        FlowSession session = initiateFlow(to);
        subFlow(new SendTransactionFlow(session, tx));
//        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(tx, ImmutableList.of(session), CollectSignaturesFlow.tracker()));

        progressTracker.setCurrentStep(FINALISATION);
        return null;
//        return subFlow(new FinalityFlow(fullySignedTx));
    }
}