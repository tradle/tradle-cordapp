package com.template;

//import com.template.schema.SharedSpaceSchemaV1;
//import com.template.schema.Link;
import com.google.common.collect.ImmutableList;
import com.template.schema.SharedItemSchemaV1;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;

import java.util.Arrays;
import java.util.List;

/**
 * The state object recording shared data agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 */
public class SharedItemState implements QueryableState, LinearState {
    private final Party from;
    private final Party to;
    private final String toTmpId;
    private final long timestamp;
    private final String link;
    private final UniqueIdentifier linearId;

    /**
     * @param link links to shared data.
     * @param from the party sharing data.
     * @param toTmpId the tmp id of the party receiving shared data.
     */
    public SharedItemState(Party from, Party to, String toTmpId, String link, long timestamp) {
        this.from = from;
        this.to = to;
        this.toTmpId = toTmpId;
        this.link = link;
        this.timestamp = timestamp;
        this.linearId = new UniqueIdentifier();
    }

    public String getLink() { return link; }
    public long getTimestamp() { return timestamp; }
    public Party getFrom() { return from; }
    public Party getTo() { return to; }
    public String getToTmpId() { return toTmpId; }
    @Override
    public UniqueIdentifier getLinearId() { return linearId; }

    @Override
    public List<AbstractParty> getParticipants() {
        if (to == null) return Arrays.asList(from);

        return Arrays.asList(from, to);
    }

    @Override
    public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof SharedItemSchemaV1) {
            return new SharedItemSchemaV1.PersistentSharedItem(
                    from.getName().toString(),
                    to == null ? null : to.getName().toString(),
                    toTmpId,
                    link,
                    timestamp,
                    linearId.getId()
            );
        } else {
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @Override
    public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new SharedItemSchemaV1());
    }

    @Override
    public String toString() {
        return String.format("%s(from=%s, to=%s, link=%s, timestamp=%d, linearId=%s)", getClass().getSimpleName(), from, to, link, timestamp, linearId);
    }
}
