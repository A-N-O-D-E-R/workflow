package com.anode.workflow.mapper;

import com.anode.tool.document.Document;
import com.anode.tool.document.JDocument;
import com.anode.workflow.entities.sla.FutureMilestones;
import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.sla.Milestone.ClockStartOption;
import com.anode.workflow.entities.sla.Milestone.Setup;
import com.anode.workflow.entities.sla.Milestone.SetupOption;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MilestoneMapper extends AbstractMapper {
    private static final DateFormat sdf = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss.SSS z");

    static {
        sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // Set the time zone to UTC
    }

    public static Document toJDocument(List<Milestone> sla) {
        if (sla == null) {
            return null;
        }
        Document d = new JDocument();
        int i = 0;
        for (Milestone milestone : sla) {
            // Check for nullity before calling setString
            if (milestone.getName() != null) {
                d.setString("$.milestones[%].name", milestone.getName(), i + "");
            }
            if (milestone.getSetupOn() != null) {
                d.setString("$.milestones[%].setup_on", milestone.getSetupOn().name(), i + "");
            }
            if (milestone.getType() != null) {
                d.setString("$.milestones[%].type", milestone.getType().name(), i + "");
            }
            if (milestone.getWorkBasketName() != null) {
                d.setString(
                        "$.milestones[%].work_basket_name", milestone.getWorkBasketName(), i + "");
            }
            if (milestone.getAppliedAtAge() != null) {
                d.setString("$.milestones[%].applied_at_age", milestone.getAppliedAtAge(), i + "");
            }
            if (milestone.getAppliedAtTimestamp() != null) {
                d.setString(
                        "$.milestones[%].applied_at_ts",
                        sdf.format(milestone.getAppliedAtTimestamp()), i + "");
            }
            if (milestone.getClockStarts() != null) {
                d.setString(
                        "$.milestones[%].clock_starts", milestone.getClockStarts().name(), i + "");
            }
            if (milestone.getAction() != null) {
                d.setString("$.milestones[%].action", milestone.getAction(), i + "");
            }
            if (milestone.getUserData() != null) {
                d.setString("$.milestones[%].userdata", milestone.getUserData(), i + "");
            }

            // Handle furtherMilestones
            int j = 0;
            if (milestone.getFurtherMilestones() != null) {
                for (FutureMilestones futureMilestones : milestone.getFurtherMilestones()) {
                    if (futureMilestones.getAppliedAtTimestamp() != null) {
                        d.setString(
                                "$.milestones[%].further_milestones[%].applied_at_ts",
                                sdf.format(futureMilestones.getAppliedAtTimestamp()),
                                i + "",
                                j + "");
                    }
                    if (futureMilestones.getAppliedAtAge() != null) {
                        d.setString(
                                "$.milestones[%].further_milestones[%].applied_at_age",
                                futureMilestones.getAppliedAtAge(), i + "", j + "");
                    }
                    if (futureMilestones.getRepeat() != null) {
                        d.setInteger(
                                "$.milestones[%].further_milestones[%].repeat",
                                futureMilestones.getRepeat(), i + "", j + "");
                    }
                    if (futureMilestones.getOffset() != null) {
                        d.setString(
                                "$.milestones[%].further_milestones[%].offset",
                                formatDuration(futureMilestones.getOffset()), i + "", j + "");
                    }
                    j++;
                }
            }
            i++;
        }
        return d;
    }

    public static List<Milestone> toEntities(Document document) {
        if (document == null) {
            return null;
        }
        int size = document.getArraySize("$.milestones[]");
        List<Milestone> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Milestone object = new Milestone();
            object.setName(document.getString("$.milestones[%].name", i + ""));
            object.setSetupOn(
                    Setup.valueOf(document.getString("$.milestones[%].setup_on", i + "")));
            object.setType(SetupOption.valueOf(document.getString("$.milestones[%].type", i + "")));
            object.setWorkBasketName(
                    document.getString("$.milestones[%].work_basket_name", i + ""));
            object.setAppliedAtAge(document.getString("$.milestones[%].applied_at_age", i + ""));
            String tsString = document.getString("$.milestones[%].applied_at_ts", i + "");
            if (!(tsString == null || tsString.isBlank() || tsString.isEmpty())) {
                try {
                    object.setAppliedAtTimestamp(sdf.parse(tsString));
                } catch (ParseException e) {
                    log.error("Unable to parse : " + tsString, e);
                }
            }
            object.setClockStarts(
                    ClockStartOption.valueOf(
                            document.getString("$.milestones[%].clock_starts", i + "")));
            object.setAction(document.getString("$.milestones[%].action", i + ""));
            object.setUserData(document.getString("$.milestones[%].userdata", i + ""));

            int further_milestones_size =
                    document.getArraySize("$.milestones[%].further_milestones[]", i + "");
            List<FutureMilestones> sublist = new ArrayList<>(further_milestones_size);
            for (int j = 0; j < further_milestones_size; j++) {
                FutureMilestones futureMilestones = new FutureMilestones();
                String appliedAtTs =
                        document.getString(
                                "$.milestones[%].further_milestones[%].applied_at_ts",
                                i + "", j + "");
                if (!(appliedAtTs == null || appliedAtTs.isBlank() || appliedAtTs.isEmpty())) {
                    try {
                        futureMilestones.setAppliedAtTimestamp(sdf.parse(appliedAtTs));
                    } catch (ParseException e) {
                        log.error("Unable to parse : " + tsString, e);
                    }
                }
                futureMilestones.setAppliedAtAge(
                        document.getString(
                                "$.milestones[%].further_milestones[%].applied_at_age",
                                i + "", j + ""));
                futureMilestones.setRepeat(
                        document.getInteger(
                                "$.milestones[%].further_milestones[%].repeat", i + "", j + ""));
                String offsetString =
                        document.getString(
                                "$.milestones[%].further_milestones[%].offset", i + "", j + "");
                if (offsetString != null) {
                    futureMilestones.setOffset(parseDuration(offsetString));
                }
                sublist.add(futureMilestones);
            }
            if (!sublist.isEmpty()) {
                object.setFurtherMilestones(sublist);
            }
            list.add(object);
        }
        return list;
    }

    public static String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24; // Get hours within the 24-hour period
        long minutes = duration.toMinutes() % 60; // Get minutes within the hour
        return String.format("%d:%d:%d", days, hours, minutes);
    }

    public static Duration parseDuration(String durationString) {
        // Split the string by the colon ":"
        String[] parts = durationString.split(":");

        // Parse the parts (days, hours, and minutes)
        long days = Long.parseLong(parts[0]);
        long hours = Long.parseLong(parts[1]);
        long minutes = Long.parseLong(parts[2]);

        // Construct the Duration
        return Duration.ofDays(days).plusHours(hours).plusMinutes(minutes);
    }
}
