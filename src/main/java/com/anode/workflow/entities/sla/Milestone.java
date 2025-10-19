package com.anode.workflow.entities.sla;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "milestone")
public class Milestone implements Serializable {
    private static final String SLA_ACTION_REGEX =
            "^(CORR|CORR:([a-zA-Z_]+)|CORR_ALL|RESUME|RETRY|CANCEL:([a-zA-Z0-9]+)|CANCEL:([a-zA-Z0-9]+):(C|D)|DECLINE:([a-zA-Z0-9]+)|CHG_WB:([a-zA-Z][a-zA-Z0-9_]*)|RAISE_INC:([a-zA-Z0-9_]*)|CUSTOM:(([a-zA-Z][a-zA-Z0-9_]*)|(([a-zA-Z][a-zA-Z0-9_]*(.))+([a-zA-Z][a-zA-Z0-9_]*)))|CUSTOM:(([a-zA-Z][a-zA-Z0-9_]*)|(([a-zA-Z][a-zA-Z0-9_]*(.))+([a-zA-Z][a-zA-Z0-9_]*))):([a-zA-Z0-9]+))$";
    @Id private Long hibid;
    private String name;
    private Setup setupOn;
    private SetupOption type;
    private String workBasketName;
    // specifies the age at which the milestone is to be executed. Specifed as a number followed by
    // an "m" or a "d"
    // example is "5m" meaning 5 minutes or 10d meaning 10 days
    private String appliedAtAge;
    private Date appliedAtTimestamp;
    private Boolean followBusinessCalendar;
    private ClockStartOption clockStarts;
    private String action;
    private String userData;
    @OneToMany @JoinColumn(name = "milestone_id") private List<FutureMilestones> furtherMilestones;

    public Milestone(
            String name,
            Setup setup,
            SetupOption type,
            String workBasket,
            String appliedAtAge,
            Date appliedAtTimestamp,
            Boolean followBusinessCalendar,
            ClockStartOption clockStarts,
            @JsonProperty("action") String action,
            String userData,
            List<FutureMilestones> furtherMilestones) {
        if (Objects.nonNull(action) && !isValidType(action)) {
            throw new IllegalArgumentException("Invalid type value: " + action);
        }
        this.action = action;
        this.name = name;
        this.setupOn = setup;
        this.type = type;
        this.workBasketName = workBasket;
        this.appliedAtAge = appliedAtAge;
        this.followBusinessCalendar = followBusinessCalendar;
        this.clockStarts = clockStarts;
        this.userData = userData;
        this.furtherMilestones = furtherMilestones;
    }

    // Setter with validation
    public void setAction(String action) {
        if (!isValidType(action)) {
            throw new IllegalArgumentException("Invalid type value: " + action);
        }
        this.action = action;
    }

    // Validation method to check if the type matches the regex
    private boolean isValidType(String action) {
        return Pattern.matches(SLA_ACTION_REGEX, action);
    }

    public enum SetupOption {
        case_level, // means that it is a case level sla and the reference point is the case start
        // timestamp
        work_basket // means that it is a work basket level sla and the reference point is when the
        // application entered the work basket
    }

    public enum ClockStartOption {
        immediately,
        next_day
    }

    public enum Setup {
        case_start,
        case_restart,
        work_basket_entry,
        work_basket_exit
    }

    // Deep copy method
    public static Milestone deepCopy(Milestone milestone) {
        Milestone copy = new Milestone();

        // Copy primitive fields (String, Boolean)
        copy.setName(milestone.getName());
        copy.setWorkBasketName(milestone.getWorkBasketName());
        copy.setAppliedAtAge(milestone.getAppliedAtAge());
        copy.setAction(milestone.getAction());
        copy.setUserData(milestone.getUserData());
        copy.setFollowBusinessCalendar(milestone.getFollowBusinessCalendar());

        // Copy immutable enums (no need to deep copy)
        copy.setSetupOn(milestone.getSetupOn()); // Enum copy
        copy.setType(milestone.getType()); // Enum copy
        copy.setClockStarts(milestone.getClockStarts()); // Enum copy

        // Deep copy mutable objects
        if (milestone.getAppliedAtTimestamp() != null) {
            copy.setAppliedAtTimestamp(
                    new Date(milestone.getAppliedAtTimestamp().getTime())); // Clone the Date
        }
        // Deep copy the list of FutureMilestones
        if (milestone.getFurtherMilestones() != null) {
            List<FutureMilestones> copiedList = new ArrayList<>();
            for (FutureMilestones futureMilestones : milestone.getFurtherMilestones()) {
                copiedList.add(
                        FutureMilestones.deepCopy(
                                futureMilestones)); // Assuming FutureMilestones has a deepCopy
                // method
            }
            copy.setFurtherMilestones(copiedList);
        }

        return copy;
    }
}
