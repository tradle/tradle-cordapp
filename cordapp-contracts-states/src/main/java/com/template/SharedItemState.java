package com.template;

//import com.template.schema.SharedSpaceSchemaV1;
//import com.template.schema.Link;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The state object recording shared data agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 */
public class SharedItemState implements QueryableState, LinearState {
    private final Party from;
    private final Party to;
    private final long timestamp;
    private final String link;
    private final UniqueIdentifier linearId;

    /**
     * @param link links to shared data.
     * @param from the party sharing data.
     * @param to the party receiving shared data.
     */
    public SharedItemState(Party from, Party to, String link, long timestamp) {
        this.from = from;
        this.to = to;
        this.link = link;
        this.timestamp = timestamp;
        this.linearId = new UniqueIdentifier();
    }

    public String getLink() { return link; }
    public long getTimestamp() { return timestamp; }
    public Party getFrom() { return from; }
    public Party getTo() { return to; }
    @Override
    public UniqueIdentifier getLinearId() { return linearId; }

    @Override
    public List<AbstractParty> getParticipants() { return Arrays.asList(from, to); }

    @Override public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof SharedItemSchemaV1) {
            return new SharedItemSchemaV1.PersistentSharedItem(
                    this.from.getName().toString(),
                    this.to.getName().toString(),
                    this.link,
                    this.timestamp,
                    this.linearId.getId());
        } else {
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @Override public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new SharedItemSchemaV1());
    }

    @Override
    public String toString() {
        return String.format("%s(from=%s, to=%s, link=%s, timestamp=%d, linearId=%s)", getClass().getSimpleName(), from, to, link, timestamp, linearId);
    }
}
