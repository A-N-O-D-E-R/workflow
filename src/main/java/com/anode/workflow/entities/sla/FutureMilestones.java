package com.anode.workflow.entities.sla;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.io.Serializable;
import java.time.Duration;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "next_milestone")
public class FutureMilestones implements Serializable {
    @Id private Long hibid;
    public Date appliedAtTimestamp;
    public String appliedAtAge;
    public Integer repeat;
    @Column(name = "offset_seconds")
    private Long offsetSeconds;

    @Transient
    private Duration offset;
    

    @PrePersist
    @PreUpdate
    private void convertDurationToSeconds() {
        if (offset != null) {
            this.offsetSeconds = offset.getSeconds();
        }
    }

    @PostLoad
    private void convertSecondsToDuration() {
        if (offsetSeconds != null) {
            this.offset = Duration.ofSeconds(offsetSeconds);
        }
    }

    public static FutureMilestones deepCopy(FutureMilestones futureMilestones) {
        FutureMilestones copy = new FutureMilestones();

        // Copy primitive fields
        copy.setAppliedAtAge(futureMilestones.getAppliedAtAge());
        copy.setRepeat(futureMilestones.getRepeat());

        // Deep copy mutable Date
        if (futureMilestones.getAppliedAtTimestamp() != null) {
            copy.setAppliedAtTimestamp(
                    new Date(futureMilestones.getAppliedAtTimestamp().getTime())); // Copy Date
        }

        // Copy Duration (it's immutable, so we can copy directly)
        if (futureMilestones.getOffset() != null) {
            copy.setOffset(
                    futureMilestones
                            .getOffset()
                            .plusSeconds(
                                    0)); // Copy Duration (it's immutable, so copy the reference)
        }

        return copy;
    }
}
