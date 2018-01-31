package com.template;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.TimeWindow;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;

import java.lang.reflect.Field;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * Define your contract here.
 */
public class SharedItemContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String SHARED_SPACE_CONTRACT_ID = "com.template.SharedItemContract";

    public static class Create implements CommandData {}
    public static class ResolveTo implements CommandData {}
//    public static class Share implements CommandData {}

    /**
     * A transaction is considered valid if the verify() function of the contract of each of the transaction's input
     * and output states does not throw an exception.
     */
    @Override
    public void verify(LedgerTransaction tx) {
        List<CommandWithParties<CommandData>> commands = tx.getCommands();
        if (commands.size() != 1) throw new IllegalArgumentException("expected only one command");

        CommandWithParties<CommandData> command = commands.get(0);
        CommandData value = command.getValue();
        if (value instanceof Create) {
            verifyCreate(tx, command);
        } else if (value instanceof ResolveTo) {
            verifyResolveTo(tx, command);
        }
//        else if (value instanceof Share) {
//            verifyShare(tx, command);
//        }
    }

    private void verifyCreate(LedgerTransaction tx, CommandWithParties<CommandData> command) {
        requireThat(check -> {
            // Constraints on the shape of the transaction.
            check.using("No inputs should be consumed when creating a shared space.", tx.getInputs().isEmpty());
            check.using("There should be one output state.", tx.getOutputs().size() == 1);

            final SharedItemState out = tx.outputsOfType(SharedItemState.class).get(0);
            check.using("the output state should be of type SharedItemState", out != null);

            check.using("The shared link should be non-null.", out.getLink() != null);

            final Party from = out.getFrom();
            final Party to = out.getTo();
            final String toTmpId = out.getToTmpId();

            // Constraints on the signers.
            final List<PublicKey> signers = command.getSigners();
            check.using("'From' is a signer", signers.contains(from.getOwningKey()));
            if (to == null) {
                check.using("'toTmpId' is not null", toTmpId != null);
            } else {
                // TODO
//                check.using("'To' is a signer", signers.contains(to));
                check.using("'from' and 'to' cannot be the same entity.", !from.equals(to));
            }

            TimeWindow window = tx.getTimeWindow();
            long timestamp = out.getTimestamp();
            check.using(
                    String.format("timestamp %d is in time window %d to %d", timestamp, window.getFromTime().toEpochMilli(), window.getUntilTime().toEpochMilli()),
                    window.contains(Instant.ofEpochMilli(timestamp))
            );
            return null;
        });
    }

    private void verifyResolveTo(LedgerTransaction tx, CommandWithParties<CommandData> command) {
        requireThat(check -> {
            // Constraints on the shape of the transaction.
            check.using("There should be one input", tx.getInputs().size() == 1);
            check.using("There should be one output state", tx.getOutputs().size() == 1);
            SharedItemState before = (SharedItemState) tx.getInput(0);
            SharedItemState after = (SharedItemState) tx.getOutput(0);
            check.using("'from' should not have changed", before.getFrom().equals(after.getFrom()));
            check.using("'timestamp' should not have changed",before.getTimestamp() == after.getTimestamp());
            check.using("'link' should not have changed",before.getLink().equals(after.getLink()));
            check.using("previous 'to' should have been null",before.getTo() == null);
            check.using("'to' should be non-null",after.getTo() != null);
            check.using("previous 'toTmpId' should have been non-null",before.getToTmpId() != null);
            check.using("'toTmpId' should be null",after.getToTmpId() == null);
            check.using("'from' and 'to' cannot be the same entity.", !after.getFrom().equals(after.getTo()));
            return null;
        });
    }

//    private void verifyShare(LedgerTransaction tx, CommandWithParties<CommandData> command) {
//        requireThat(check -> {
//            // Constraints on the shape of the transaction.
//            check.using("There should be one input state", tx.getInputs().size() == 1);
//            check.using("There should be one output state.", tx.getOutputs().size() == 1);
//            final SharedItemState in = (SharedItemState) tx.getInput(0);
//            check.using("the input state should be of type SharedItemState", in != null);
//            final SharedItemState out = (SharedItemState) tx.getOutput(0);
//            check.using("the output state should be of type SharedItemState", out != null);
//            check.using("parties should not have changed",
//                    in.getTo() == out.getTo() && in.getFrom() == out.getFrom());
//            final Map<String, Long> sharedBefore = in.getShared();
//            final Map<String, Long> sharedAfter = out.getShared();
//            check.using("shared count should have increased",
//                    sharedAfter.size() > sharedBefore.size());
//            check.using("no shared data was deleted", out.getShared().keySet().containsAll(in.getShared().keySet()));
//            Set<String> added = new HashSet<>(out.getShared().keySet());
//            added.removeAll(in.getShared().keySet());
//            TimeWindow window = tx.getTimeWindow();
//            for (String link: added) {
//                long timestamp = out.getShared().get(link);
//                check.using("timestamp is in time window", window.contains(Instant.ofEpochMilli(timestamp)));
//            }
//
//            return null;
//        });
//    }
//    public interface Commands extends CommandData {
//        class Action implements Commands {}
//    }
}
