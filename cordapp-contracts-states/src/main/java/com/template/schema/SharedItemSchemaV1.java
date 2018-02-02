package com.template.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

public class SharedItemSchemaV1 extends MappedSchema {
    public SharedItemSchemaV1() {
        super(SharedItemSchema.class, 1, ImmutableList.of(PersistentSharedItem.class));
    }

    @Entity
    @Table(name = "shared_item_states")
    public static class PersistentSharedItem extends PersistentState {
        @Column(name = "share_from") private final String from;
        @Column(name = "share_to") private final String to;
        @Column(name = "share_to_tmp") private final String toTmpId;
        @Column(name = "share_link") private final String link;
        @Column(name = "share_timestamp") private final long timestamp;
        @Column(name = "linear_id") private final UUID linearId;

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public String getToTmpId() {
            return to;
        }

        public String getLink() {
            return link;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public UUID getLinearId() {
            return linearId;
        }

        public PersistentSharedItem() {
            this("", "", "", "", 0L, UUID.randomUUID());
        }

        public PersistentSharedItem(String from, String to, String toTmpId, String link, Long timestamp, UUID linearId) {
            this.from = from;
            this.to = to;
            this.toTmpId = toTmpId;
            this.link = link;
            this.timestamp = timestamp;
            this.linearId = linearId;
        }

    }
}
