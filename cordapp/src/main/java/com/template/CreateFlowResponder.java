package com.template;

// Add these imports:
import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.ContractState;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

import static net.corda.core.contracts.ContractsDSL.requireThat;

// Define IOUFlowResponder:
@InitiatedBy(SharedItemCreateFlow.class)
public class CreateFlowResponder extends FlowLogic<SignedTransaction> {
    private final FlowSession otherPartySession;
    private static final ProgressTracker.Step PARSE_TX = new ProgressTracker.Step("Parse state from tx.");
    private static final ProgressTracker.Step VALIDATE = new ProgressTracker.Step("Validate outputs.");
    private static final ProgressTracker.Step CHECK_LINKED_DATA = new ProgressTracker.Step("Check have linked data.");
    private final ProgressTracker progressTracker = new ProgressTracker(
            PARSE_TX,
            VALIDATE,
            CHECK_LINKED_DATA
    );

    public CreateFlowResponder(FlowSession otherPartySession) {
        this.otherPartySession = otherPartySession;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {

        class SignTxFlow extends SignTransactionFlow {
            private SignTxFlow(FlowSession otherPartySession, ProgressTracker progressTracker) {
                super(otherPartySession, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction stx) {
                requireThat(require -> {
                    progressTracker.setCurrentStep(PARSE_TX);
                    ContractState output = stx.getTx().getOutputs().get(0).getData();

                    progressTracker.setCurrentStep(VALIDATE);
                    require.using("This must be a SharedSpace transaction.", output instanceof SharedItemState);
                    SharedItemState sss = (SharedItemState) output;

                    progressTracker.setCurrentStep(CHECK_LINKED_DATA);
                    ensureReceivedObjectCorrespondingToLink(sss.getFrom(), sss.getLink());

                    return null;
                });
            }
        }

        // sign it
        return subFlow(new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker()));
    }

    private void ensureReceivedObjectCorrespondingToLink(Party from, String link) {
        // TODO check received object and timestamp when it was received
    }
}