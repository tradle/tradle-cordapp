package com.template;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.ContractState;
import net.corda.core.flows.*;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

import static net.corda.core.contracts.ContractsDSL.requireThat;

@InitiatedBy(SharedItemShareFlow.class)
public class ShareFlowResponder extends FlowLogic<Void> {
    private final FlowSession senderSession;

    public ShareFlowResponder(final FlowSession senderSession) {
        this.senderSession = senderSession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        // Start the matching side of SendTransactionFlow above, but tell it to record all visible states even
        // though they (as far as the node can tell) are nothing to do with us.
//        class SignTxFlow extends SignTransactionFlow {
//            private SignTxFlow(FlowSession otherPartySession, ProgressTracker progressTracker) {
//                super(otherPartySession, progressTracker);
//            }
//
//            @Override
//            protected void checkTransaction(SignedTransaction stx) {
//                requireThat(require -> {
////                    progressTracker.setCurrentStep(PARSE_TX);
//                    ContractState output = stx.getTx().getOutputs().get(0).getData();
//
////                    progressTracker.setCurrentStep(VALIDATE);
//                    require.using("This must be a SharedSpace transaction.", output instanceof SharedItemState);
//                    SharedItemState sss = (SharedItemState) output;
//
////                    progressTracker.setCurrentStep(CHECK_LINKED_DATA);
////                    ensureReceivedObjectCorrespondingToLink(sss.getFrom(), sss.getLink());
//
//                    return null;
//                });
//            }
//        }

        subFlow(new ReceiveTransactionFlow(senderSession, true, StatesToRecord.ALL_VISIBLE));
        return null;
//        return subFlow(new SignTxFlow(senderSession, SignTransactionFlow.Companion.tracker()));
    }
}