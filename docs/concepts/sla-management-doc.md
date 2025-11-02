# SLA Management Deep Dive

## Overview

Simple Workflow provides built-in Service Level Agreement (SLA) management through milestones. This guide covers everything you need to implement, monitor, and manage SLAs in your workflows.

## Table of Contents
- [Core Concepts](#core-concepts)
- [Milestone Structure](#milestone-structure)
- [Configuring SLAs](#configuring-slas)
- [SLA Service API](#sla-service-api)
- [Milestone States](#milestone-states)
- [Time Calculations](#time-calculations)
- [Business Calendar Integration](#business-calendar-integration)
- [Monitoring and Reporting](#monitoring-and-reporting)
- [SLA Breach Handling](#sla-breach-handling)
- [Advanced Patterns](#advanced-patterns)
- [Best Practices](#best-practices)
- [Real-World Examples](#real-world-examples)

## Core Concepts

### What are SLAs?

Service Level Agreements define time-based commitments for workflow completion or milestone achievement. In Simple Workflow:

- **Milestones**: Named checkpoints with target completion times
- **Target Time**: When the milestone should be achieved
- **Actual Time**: When the milestone was actually achieved
- **Breach**: When actual time exceeds target time
- **Grace Period**: Optional buffer before considering SLA breached

```
Timeline:
─────────────────────────────────────────────────────────────▶
Start        Target Time              Actual Time
  │              │                        │
  │              │                        │
  │◄─────────────┤                        │
  │   SLA Period │                        │
  │              │◄───────────────────────┤
  │              │    Breach Duration     │
```

### Why Use SLA Management?

1. **Customer Commitments**: Track adherence to promised delivery times
2. **Process Monitoring**: Identify bottlenecks and delays
3. **Performance Metrics**: Measure operational efficiency
4. **Compliance**: Demonstrate regulatory compliance
5. **Escalation Triggers**: Automatically escalate overdue items
6. **Reporting**: Generate SLA compliance reports

### SLA Terminology

| Term | Definition | Example |
|------|------------|---------|
| **Milestone** | A named checkpoint in workflow | "Initial Review", "Manager Approval" |
| **Target Time** | Expected completion time | "2 hours from start" |
| **Actual Time** | When milestone was achieved | "1.5 hours from start" |
| **SLA Period** | Time allowed to complete | "120 minutes" |
| **Breach** | Milestone missed target | Target: 2h, Actual: 3h |
| **At Risk** | Approaching target time | 90% of SLA period elapsed |
| **Met** | Completed within target | Completed in 1.5h for 2h target |

## Milestone Structure

### Milestone Entity

```java
public class Milestone implements Serializable {
    
    private String name;              // Milestone identifier
    private Long targetTimestamp;     // When it should complete (ms since epoch)
    private Long actualTimestamp;     // When it actually completed (null if pending)
    private MilestoneStatus status;   // PENDING, MET, BREACHED
    private Integer slaMinutes;       // SLA duration in minutes
    
    // Additional fields
    private String description;       // Human-readable description
    private String responsibleParty;  // Who owns this milestone
    private Integer gracePeriodMinutes; // Optional grace period
}
```

### Milestone Status

```java
public enum MilestoneStatus {
    PENDING,    // Not yet achieved
    MET,        // Achieved within SLA
    BREACHED    // Achieved after SLA expired
}
```

### Milestone Lifecycle

```
┌──────────────┐
│   Created    │ (When workflow starts)
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   PENDING    │ (Waiting for achievement)
└──────┬───────┘
       │
       ├─── (Completed before target) ──▶ ┌──────────┐
       │                                  │   MET    │
       │                                  └──────────┘
       │
       └─── (Completed after target) ───▶ ┌──────────┐
                                          │ BREACHED │
                                          └──────────┘
```

## Configuring SLAs

### Basic SLA Configuration

```java
public class SLAConfiguration {
    
    public static List<Milestone> createOrderProcessingSLA(String caseId) {
        List<Milestone> milestones = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        // Milestone 1: Order Validation (15 minutes)
        Milestone validation = new Milestone();
        validation.setName("order_validation");
        validation.setDescription("Order must be validated");
        validation.setSlaMinutes(15);
        validation.setTargetTimestamp(startTime + (15 * 60 * 1000));
        validation.setStatus(MilestoneStatus.PENDING);
        validation.setResponsibleParty("validation_team");
        milestones.add(validation);
        
        // Milestone 2: Payment Processing (30 minutes)
        Milestone payment = new Milestone();
        payment.setName("payment_processing");
        payment.setDescription("Payment must be processed");
        payment.setSlaMinutes(30);
        payment.setTargetTimestamp(startTime + (30 * 60 * 1000));
        payment.setStatus(MilestoneStatus.PENDING);
        payment.setResponsibleParty("payment_team");
        milestones.add(payment);
        
        // Milestone 3: Shipment Creation (2 hours)
        Milestone shipment = new Milestone();
        shipment.setName("shipment_creation");
        shipment.setDescription("Shipment must be created");
        shipment.setSlaMinutes(120);
        shipment.setTargetTimestamp(startTime + (120 * 60 * 1000));
        shipment.setStatus(MilestoneStatus.PENDING);
        shipment.setResponsibleParty("fulfillment_team");
        shipment.setGracePeriodMinutes(15); // 15 minute grace period
        milestones.add(shipment);
        
        // Milestone 4: Order Complete (24 hours)
        Milestone completion = new Milestone();
        completion.setName("order_complete");
        completion.setDescription("Order must be completed");
        completion.setSlaMinutes(24 * 60);
        completion.setTargetTimestamp(startTime + (24 * 60 * 60 * 1000));
        completion.setStatus(MilestoneStatus.PENDING);
        completion.setResponsibleParty("operations_team");
        milestones.add(completion);
        
        return milestones;
    }
}
```

### Saving SLA Configuration

```java
public class WorkflowWithSLA {
    
    public void startWorkflowWithSLA(String caseId, String workflowJson) {
        // 1. Start workflow
        runtimeService.startCase(caseId, workflowJson, null, null);
        
        // 2. Create SLA milestones
        List<Milestone> milestones = SLAConfiguration.createOrderProcessingSLA(caseId);
        
        // 3. Save milestones
        String slaKey = "journey_sla-" + caseId;
        dao.save(slaKey, milestones);
        
        log.info("Started workflow {} with {} SLA milestones", caseId, milestones.size());
    }
}
```

### Loading SLA Configuration

```java
@SuppressWarnings("unchecked")
public List<Milestone> loadSLA(String caseId) {
    String slaKey = "journey_sla-" + caseId;
    
    Object slaData = dao.get(Object.class, slaKey);
    
    if (slaData instanceof List) {
        return (List<Milestone>) slaData;
    }
    
    log.warn("No SLA configuration found for case {}", caseId);
    return new ArrayList<>();
}
```

## SLA Service API

### SLAService Interface

```java
public class SLAService {
    
    private final CommonService dao;
    
    public SLAService(CommonService dao) {
        this.dao = dao;
    }
    
    /**
     * Mark a milestone as achieved
     */
    public void achieveMilestone(String caseId, String milestoneName) {
        List<Milestone> milestones = loadSLA(caseId);
        
        for (Milestone milestone : milestones) {
            if (milestone.getName().equals(milestoneName)) {
                long now = System.currentTimeMillis();
                milestone.setActualTimestamp(now);
                
                // Determine if met or breached
                if (now <= milestone.getTargetTimestamp()) {
                    milestone.setStatus(MilestoneStatus.MET);
                    log.info("Milestone {} met for case {}", milestoneName, caseId);
                } else {
                    milestone.setStatus(MilestoneStatus.BREACHED);
                    long breachDuration = now - milestone.getTargetTimestamp();
                    log.warn("Milestone {} breached for case {} by {}ms", 
                        milestoneName, caseId, breachDuration);
                    
                    // Trigger breach handling
                    handleSLABreach(caseId, milestone, breachDuration);
                }
                
                break;
            }
        }
        
        // Save updated milestones
        saveSLA(caseId, milestones);
    }
    
    /**
     * Check if milestone is at risk (approaching target time)
     */
    public boolean isMilestoneAtRisk(String caseId, String milestoneName, double threshold) {
        List<Milestone> milestones = loadSLA(caseId);
        
        for (Milestone milestone : milestones) {
            if (milestone.getName().equals(milestoneName) && 
                milestone.getStatus() == MilestoneStatus.PENDING) {
                
                long now = System.currentTimeMillis();
                long startTime = milestone.getTargetTimestamp() - (milestone.getSlaMinutes() * 60 * 1000);
                long elapsed = now - startTime;
                long total = milestone.getTargetTimestamp() - startTime;
                
                double percentElapsed = (double) elapsed / total;
                
                return percentElapsed >= threshold; // e.g., 0.8 = 80%
            }
        }
        
        return false;
    }
    
    /**
     * Get all breached milestones
     */
    public List<Milestone> getBreachedMilestones(String caseId) {
        List<Milestone> milestones = loadSLA(caseId);
        
        return milestones.stream()
            .filter(m -> m.getStatus() == MilestoneStatus.BREACHED)
            .collect(Collectors.toList());
    }
    
    /**
     * Get SLA compliance percentage
     */
    public double getSLACompliance(String caseId) {
        List<Milestone> milestones = loadSLA(caseId);
        
        if (milestones.isEmpty()) {
            return 100.0;
        }
        
        long metCount = milestones.stream()
            .filter(m -> m.getStatus() == MilestoneStatus.MET)
            .count();
        
        return (double) metCount / milestones.size() * 100.0;
    }
    
    /**
     * Get time remaining for milestone
     */
    public long getTimeRemaining(String caseId, String milestoneName) {
        List<Milestone> milestones = loadSLA(caseId);
        
        for (Milestone milestone : milestones) {
            if (milestone.getName().equals(milestoneName) && 
                milestone.getStatus() == MilestoneStatus.PENDING) {
                
                long now = System.currentTimeMillis();
                return milestone.getTargetTimestamp() - now;
            }
        }
        
        return 0;
    }
    
    private void handleSLABreach(String caseId, Milestone milestone, long breachDuration) {
        // Implement breach handling (notifications, escalations, etc.)
        // See SLA Breach Handling section
    }
    
    @SuppressWarnings("unchecked")
    private List<Milestone> loadSLA(String caseId) {
        String slaKey = "journey_sla-" + caseId;
        Object slaData = dao.get(Object.class, slaKey);
        return (slaData instanceof List) ? (List<Milestone>) slaData : new ArrayList<>();
    }
    
    private void saveSLA(String caseId, List<Milestone> milestones) {
        String slaKey = "journey_sla-" + caseId;
        dao.save(slaKey, milestones);
    }
}
```

## Milestone States

### Checking Milestone Status

```java
public class MilestoneStatusChecker {
    
    private final SLAService slaService;
    
    public MilestoneReport getMilestoneReport(String caseId) {
        List<Milestone> milestones = slaService.loadSLA(caseId);
        
        MilestoneReport report = new MilestoneReport();
        report.setCaseId(caseId);
        
        for (Milestone milestone : milestones) {
            switch (milestone.getStatus()) {
                case PENDING:
                    if (isPending(milestone)) {
                        report.addPending(milestone);
                        
                        // Check if at risk
                        if (isAtRisk(milestone, 0.8)) {
                            report.addAtRisk(milestone);
                        }
                    }
                    break;
                    
                case MET:
                    report.addMet(milestone);
                    break;
                    
                case BREACHED:
                    report.addBreached(milestone);
                    break;
            }
        }
        
        return report;
    }
    
    private boolean isPending(Milestone milestone) {
        return milestone.getActualTimestamp() == null;
    }
    
    private boolean isAtRisk(Milestone milestone, double threshold) {
        if (milestone.getActualTimestamp() != null) {
            return false; // Already completed
        }
        
        long now = System.currentTimeMillis();
        if (now >= milestone.getTargetTimestamp()) {
            return true; // Already breached
        }
        
        long startTime = milestone.getTargetTimestamp() - (milestone.getSlaMinutes() * 60 * 1000);
        long elapsed = now - startTime;
        long total = milestone.getTargetTimestamp() - startTime;
        
        double percentElapsed = (double) elapsed / total;
        
        return percentElapsed >= threshold;
    }
}

public class MilestoneReport {
    private String caseId;
    private List<Milestone> pending = new ArrayList<>();
    private List<Milestone> atRisk = new ArrayList<>();
    private List<Milestone> met = new ArrayList<>();
    private List<Milestone> breached = new ArrayList<>();
    
    // Getters and setters
    
    public double getCompliancePercentage() {
        int total = met.size() + breached.size();
        if (total == 0) return 100.0;
        return (double) met.size() / total * 100.0;
    }
}
```

### Milestone Achievement in Workflow Steps

```java
public class AchieveMilestoneStep implements InvokableTask {
    
    private final SLAService slaService;
    
    public AchieveMilestoneStep(WorkflowContext context, SLAService slaService) {
        this.context = context;
        this.slaService = slaService;
    }
    
    @Override
    public TaskResponse executeStep() {
        String caseId = context.getCaseId();
        
        try {
            // Perform business logic
            validateOrder();
            
            // Achieve milestone
            slaService.achieveMilestone(caseId, "order_validation");
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (ValidationException e) {
            // Still achieve milestone (even if failed)
            slaService.achieveMilestone(caseId, "order_validation");
            
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "validation_error_queue"
            );
        }
    }
}
```

### Conditional Logic Based on SLA Status

```java
public class SLAAwareRoutingStep implements InvokableRoute {
    
    private final SLAService slaService;
    
    @Override
    public RouteResponse executeRoute() {
        String caseId = context.getCaseId();
        
        // Check if at risk
        boolean atRisk = slaService.isMilestoneAtRisk(
            caseId, 
            "order_complete", 
            0.8  // 80% threshold
        );
        
        List<String> branches = new ArrayList<>();
        
        if (atRisk) {
            // Route to expedited processing
            branches.add("expedited_processing");
            log.info("Routing to expedited processing - SLA at risk");
        } else {
            // Normal processing
            branches.add("standard_processing");
        }
        
        return new RouteResponse(
            StepResponseType.OK_PROCEED,
            branches,
            null
        );
    }
}
```

## Time Calculations

### Calculating Target Times

```java
public class SLATimeCalculator {
    
    /**
     * Calculate target time from now
     */
    public static long calculateTargetTime(int slaMinutes) {
        return System.currentTimeMillis() + (slaMinutes * 60 * 1000L);
    }
    
    /**
     * Calculate target time from specific start time
     */
    public static long calculateTargetTime(long startTime, int slaMinutes) {
        return startTime + (slaMinutes * 60 * 1000L);
    }
    
    /**
     * Calculate time remaining
     */
    public static long calculateTimeRemaining(long targetTime) {
        long remaining = targetTime - System.currentTimeMillis();
        return Math.max(0, remaining); // Don't return negative
    }
    
    /**
     * Calculate percent elapsed
     */
    public static double calculatePercentElapsed(long startTime, long targetTime) {
        long now = System.currentTimeMillis();
        long total = targetTime - startTime;
        long elapsed = now - startTime;
        
        return Math.min(100.0, (double) elapsed / total * 100.0);
    }
    
    /**
     * Calculate breach duration
     */
    public static long calculateBreachDuration(long targetTime, long actualTime) {
        if (actualTime <= targetTime) {
            return 0; // Not breached
        }
        return actualTime - targetTime;
    }
    
    /**
     * Format duration for display
     */
    public static String formatDuration(long milliseconds) {
        if (milliseconds < 0) {
            return "overdue by " + formatDuration(-milliseconds);
        }
        
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
```

### Usage Examples

```java
// Calculate target time for 2-hour SLA
long targetTime = SLATimeCalculator.calculateTargetTime(120);

// Calculate time remaining
long remaining = SLATimeCalculator.calculateTimeRemaining(targetTime);
System.out.println("Time remaining: " + SLATimeCalculator.formatDuration(remaining));

// Check percent elapsed
double percentElapsed = SLATimeCalculator.calculatePercentElapsed(
    startTime,
    targetTime
);
System.out.println("Percent elapsed: " + percentElapsed + "%");

// Calculate breach
long breachDuration = SLATimeCalculator.calculateBreachDuration(
    targetTime,
    actualTime
);
if (breachDuration > 0) {
    System.out.println("Breached by: " + SLATimeCalculator.formatDuration(breachDuration));
}
```

## Business Calendar Integration

### Business Hours Calculator

```java
public class BusinessHoursCalculator {
    
    private final int workdayStartHour = 9;   // 9 AM
    private final int workdayEndHour = 17;    // 5 PM
    private final Set<DayOfWeek> workdays = EnumSet.of(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    );
    private final Set<LocalDate> holidays;
    
    public BusinessHoursCalculator(Set<LocalDate> holidays) {
        this.holidays = holidays;
    }
    
    /**
     * Calculate target time considering business hours
     */
    public long calculateBusinessHoursTarget(long startTime, int businessMinutes) {
        ZonedDateTime start = Instant.ofEpochMilli(startTime)
            .atZone(ZoneId.systemDefault());
        
        int minutesRemaining = businessMinutes;
        ZonedDateTime current = start;
        
        while (minutesRemaining > 0) {
            // Skip to next business day if needed
            while (!isBusinessDay(current.toLocalDate())) {
                current = current.plusDays(1).withHour(workdayStartHour).withMinute(0);
            }
            
            // Move to start of business day if before hours
            if (current.getHour() < workdayStartHour) {
                current = current.withHour(workdayStartHour).withMinute(0);
            }
            
            // Check if we're past business hours
            if (current.getHour() >= workdayEndHour) {
                current = current.plusDays(1).withHour(workdayStartHour).withMinute(0);
                continue;
            }
            
            // Calculate minutes available today
            int minutesUntilEndOfDay = (workdayEndHour - current.getHour()) * 60 
                - current.getMinute();
            
            if (minutesRemaining <= minutesUntilEndOfDay) {
                // Can complete today
                current = current.plusMinutes(minutesRemaining);
                minutesRemaining = 0;
            } else {
                // Need to continue tomorrow
                minutesRemaining -= minutesUntilEndOfDay;
                current = current.plusDays(1).withHour(workdayStartHour).withMinute(0);
            }
        }
        
        return current.toInstant().toEpochMilli();
    }
    
    /**
     * Calculate business minutes between two times
     */
    public int calculateBusinessMinutes(long startTime, long endTime) {
        ZonedDateTime start = Instant.ofEpochMilli(startTime)
            .atZone(ZoneId.systemDefault());
        ZonedDateTime end = Instant.ofEpochMilli(endTime)
            .atZone(ZoneId.systemDefault());
        
        int totalMinutes = 0;
        ZonedDateTime current = start;
        
        while (current.isBefore(end)) {
            LocalDate currentDate = current.toLocalDate();
            
            if (isBusinessDay(currentDate)) {
                // Determine working hours for this day
                int dayStart = Math.max(current.getHour() * 60 + current.getMinute(),
                    workdayStartHour * 60);
                int dayEnd = workdayEndHour * 60;
                
                ZonedDateTime endOfDay = current.withHour(workdayEndHour).withMinute(0);
                if (end.isBefore(endOfDay)) {
                    // End is within this business day
                    dayEnd = end.getHour() * 60 + end.getMinute();
                }
                
                int minutesThisDay = Math.max(0, dayEnd - dayStart);
                totalMinutes += minutesThisDay;
            }
            
            // Move to next day
            current = current.plusDays(1).withHour(workdayStartHour).withMinute(0);
        }
        
        return totalMinutes;
    }
    
    private boolean isBusinessDay(LocalDate date) {
        return workdays.contains(date.getDayOfWeek()) && 
               !holidays.contains(date);
    }
}
```

### Using Business Hours in SLA

```java
public class BusinessHoursSLA {
    
    public static List<Milestone> createWithBusinessHours(
        String caseId,
        BusinessHoursCalculator calculator
    ) {
        List<Milestone> milestones = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        // Milestone: Order Review (2 business hours)
        Milestone review = new Milestone();
        review.setName("order_review");
        review.setDescription("Order must be reviewed");
        review.setSlaMinutes(120); // 2 hours
        review.setTargetTimestamp(
            calculator.calculateBusinessHoursTarget(startTime, 120)
        );
        review.setStatus(MilestoneStatus.PENDING);
        milestones.add(review);
        
        // Milestone: Manager Approval (1 business day = 8 hours)
        Milestone approval = new Milestone();
        approval.setName("manager_approval");
        approval.setDescription("Manager must approve");
        approval.setSlaMinutes(8 * 60); // 8 hours
        approval.setTargetTimestamp(
            calculator.calculateBusinessHoursTarget(startTime, 8 * 60)
        );
        approval.setStatus(MilestoneStatus.PENDING);
        milestones.add(approval);
        
        return milestones;
    }
}
```

### Holiday Calendar

```java
public class HolidayCalendar {
    
    private final Set<LocalDate> holidays = new HashSet<>();
    
    public HolidayCalendar() {
        // Initialize with common holidays
        initializeUSHolidays(2024);
        initializeUSHolidays(2025);
    }
    
    private void initializeUSHolidays(int year) {
        // New Year's Day
        holidays.add(LocalDate.of(year, 1, 1));
        
        // Independence Day
        holidays.add(LocalDate.of(year, 7, 4));
        
        // Christmas
        holidays.add(LocalDate.of(year, 12, 25));
        
        // Memorial Day (last Monday in May)
        holidays.add(getLastMondayOfMonth(year, 5));
        
        // Labor Day (first Monday in September)
        holidays.add(getFirstMondayOfMonth(year, 9));
        
        // Thanksgiving (fourth Thursday in November)
        holidays.add(getNthDayOfMonth(year, 11, DayOfWeek.THURSDAY, 4));
    }
    
    public void addHoliday(LocalDate date) {
        holidays.add(date);
    }
    
    public boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }
    
    public Set<LocalDate> getHolidays() {
        return new HashSet<>(holidays);
    }
    
    private LocalDate getLastMondayOfMonth(int year, int month) {
        LocalDate date = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.minusDays(1);
        }
        return date;
    }
    
    private LocalDate getFirstMondayOfMonth(int year, int month) {
        LocalDate date = LocalDate.of(year, month, 1);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
    
    private LocalDate getNthDayOfMonth(int year, int month, DayOfWeek dayOfWeek, int occurrence) {
        LocalDate date = LocalDate.of(year, month, 1);
        int count = 0;
        
        while (count < occurrence) {
            if (date.getDayOfWeek() == dayOfWeek) {
                count++;
                if (count == occurrence) {
                    return date;
                }
            }
            date = date.plusDays(1);
        }
        
        throw new IllegalArgumentException("Invalid occurrence");
    }
}
```

## Monitoring and Reporting

### SLA Dashboard

```java
public class SLADashboard {
    
    private final SLAService slaService;
    private final CommonService dao;
    
    public SLADashboardData getDashboardData() {
        List<WorkflowInfo> allCases = dao.getAll(WorkflowInfo.class);
        
        SLADashboardData dashboard = new SLADashboardData();
        
        for (WorkflowInfo info : allCases) {
            if (info.getIsComplete()) {
                continue; // Skip completed workflows
            }
            
            String caseId = info.getCaseId();
            List<Milestone> milestones = slaService.loadSLA(caseId);
            
            for (Milestone milestone : milestones) {
                dashboard.incrementTotal();
                
                switch (milestone.getStatus()) {
                    case PENDING:
                        dashboard.incrementPending();
                        
                        if (isAtRisk(milestone, 0.8)) {
                            dashboard.incrementAtRisk();
                            dashboard.addAtRiskCase(caseId, milestone);
                        }
                        break;
                        
                    case MET:
                        dashboard.incrementMet();
                        break;
                        
                    case BREACHED:
                        dashboard.incrementBreached();
                        dashboard.addBreachedCase(caseId, milestone);
                        break;
                }
            }
        }
        
        return dashboard;
    }
    
    private boolean isAtRisk(Milestone milestone, double threshold) {
        if (milestone.getActualTimestamp() != null) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        if (now >= milestone.getTargetTimestamp()) {
            return true;
        }
        
        long startTime = milestone.getTargetTimestamp() - (milestone.getSlaMinutes() * 60 * 1000);
        long elapsed = now - startTime;
        long total = milestone.getTargetTimestamp() - startTime;
        
        return ((double) elapsed / total) >= threshold;
    }
}

public class SLADashboardData {
    private int totalMilestones;
    private int pendingMilestones;
    private int atRiskMilestones;
    private int metMilestones;
    private int breachedMilestones;
    
    private List<CaseMilestone> atRiskCases = new ArrayList<>();
    private List<CaseMilestone> breachedCases = new ArrayList<>();
    
    public void incrementTotal() { totalMilestones++; }
    public void incrementPending() { pendingMilestones++; }
    public void incrementAtRisk() { atRiskMilestones++; }
    public void incrementMet() { metMilestones++; }
    public void incrementBreached() { breachedMilestones++; }
    
    public void addAtRiskCase(String caseId, Milestone milestone) {
        atRiskCases.add(new CaseMilestone(caseId, milestone));
    }
    
    public void addBreachedCase(String caseId, Milestone milestone) {
        breachedCases.add(new CaseMilestone(caseId, milestone));
    }
    
    public double getCompliancePercentage() {
        int completed = metMilestones + breachedMilestones;
        if (completed == 0) return 100.0;
        return (double) metMilestones / completed * 100.0;
    }
    
    // Getters
    public int getTotalMilestones() { return totalMilestones; }
    public int getPendingMilestones() { return pendingMilestones; }
    public int getAtRiskMilestones() { return atRiskMilestones; }
    public int getMetMilestones() { return metMilestones; }
    public int getBreachedMilestones() { return breachedMilestones; }
    public List<CaseMilestone> getAtRiskCases() { return atRiskCases; }
    public List<CaseMilestone> getBreachedCases() { return breachedCases; }
}

public class CaseMilestone {
    private String caseId;
    private Milestone milestone;
    private long timeRemaining;
    private long breachDuration;
    
    public CaseMilestone(String caseId, Milestone milestone) {
        this.caseId = caseId;
        this.milestone = milestone;
        
        long now = System.currentTimeMillis();
        if (milestone.getStatus() == MilestoneStatus.PENDING) {
            this.timeRemaining = milestone.getTargetTimestamp() - now;
        } else if (milestone.getStatus() == MilestoneStatus.BREACHED) {
            this.breachDuration = milestone.getActualTimestamp() - milestone.getTargetTimestamp();
        }
    }
    
    // Getters
}
```

### REST API for Dashboard

```java
@RestController
@RequestMapping("/api/sla")
public class SLADashboardController {
    
    @Autowired
    private SLADashboard dashboard;
    
    @GetMapping("/dashboard")
    public ResponseEntity<SLADashboardData> getDashboard() {
        SLADashboardData data = dashboard.getDashboardData();
        return ResponseEntity.ok(data);
    }
    
    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseSLAReport> getCaseSLA(@PathVariable String caseId) {
        List<Milestone> milestones = slaService.loadSLA(caseId);
        
        CaseSLAReport report = new CaseSLAReport();
        report.setCaseId(caseId);
        report.setMilestones(milestones);
        report.setCompliancePercentage(calculateCompliance(milestones));
        
        return ResponseEntity.ok(report);
    }
    
    @GetMapping("/at-risk")
    public ResponseEntity<List<CaseMilestone>> getAtRiskCases() {
        SLADashboardData data = dashboard.getDashboardData();
        return ResponseEntity.ok(data.getAtRiskCases());
    }
    
    @GetMapping("/breached")
    public ResponseEntity<List<CaseMilestone>> getBreachedCases() {
        SLADashboardData data = dashboard.getDashboardData();
        return ResponseEntity.ok(data.getBreachedCases());
    }
    
    @GetMapping("/compliance")
    public ResponseEntity<Map<String, Object>> getComplianceMetrics() {
        SLADashboardData data = dashboard.getDashboardData();
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("total_milestones", data.getTotalMilestones());
        metrics.put("met_milestones", data.getMetMilestones());
        metrics.put("breached_milestones", data.getBreachedMilestones());
        metrics.put("compliance_percentage", data.getCompliancePercentage());
        
        return ResponseEntity.ok(metrics);
    }
    
    private double calculateCompliance(List<Milestone> milestones) {
        long completed = milestones.stream()
            .filter(m -> m.getActualTimestamp() != null)
            .count();
        
        if (completed == 0) return 100.0;
        
        long met = milestones.stream()
            .filter(m -> m.getStatus() == MilestoneStatus.MET)
            .count();
        
        return (double) met / completed * 100.0;
    }
}
```

### Scheduled SLA Monitoring

```java
@Component
public class SLAMonitor {
    
    @Autowired
    private SLAService slaService;
    
    @Autowired
    private CommonService dao;
    
    @Autowired
    private NotificationService notificationService;
    
    @Scheduled(fixedDelay = 60000) // Every minute
    public void monitorSLAs() {
        List<WorkflowInfo> activeCases = dao.getAll(WorkflowInfo.class).stream()
            .filter(info -> !info.getIsComplete())
            .collect(Collectors.toList());
        
        for (WorkflowInfo info : activeCases) {
            checkCaseSLA(info.getCaseId());
        }
    }
    
    private void checkCaseSLA(String caseId) {
        List<Milestone> milestones = slaService.loadSLA(caseId);
        long now = System.currentTimeMillis();
        
        for (Milestone milestone : milestones) {
            if (milestone.getStatus() != MilestoneStatus.PENDING) {
                continue;
            }
            
            // Check if breached
            if (now > milestone.getTargetTimestamp()) {
                handleSLABreach(caseId, milestone);
            }
            // Check if at risk (80% elapsed)
            else if (isAtRisk(milestone, 0.8)) {
                handleAtRisk(caseId, milestone);
            }
        }
    }
    
    private void handleSLABreach(String caseId, Milestone milestone) {
        log.warn("SLA breached: case={}, milestone={}", caseId, milestone.getName());
        
        // Send notification
        notificationService.sendSLABreachAlert(
            caseId,
            milestone.getName(),
            milestone.getResponsibleParty()
        );
    }
    
    private void handleAtRisk(String caseId, Milestone milestone) {
        log.info("SLA at risk: case={}, milestone={}", caseId, milestone.getName());
        
        // Send warning notification
        notificationService.sendSLAWarning(
            caseId,
            milestone.getName(),
            milestone.getResponsibleParty(),
            calculateTimeRemaining(milestone)
        );
    }
    
    private boolean isAtRisk(Milestone milestone, double threshold) {
        long now = System.currentTimeMillis();
        long startTime = milestone.getTargetTimestamp() - (milestone.getSlaMinutes() * 60 * 1000);
        long elapsed = now - startTime;
        long total = milestone.getTargetTimestamp() - startTime;
        
        return ((double) elapsed / total) >= threshold;
    }
    
    private long calculateTimeRemaining(Milestone milestone) {
        return Math.max(0, milestone.getTargetTimestamp() - System.currentTimeMillis());
    }
}
```

## SLA Breach Handling

### Breach Response Strategies

```java
public class SLABreachHandler {
    
    private final NotificationService notificationService;
    private final EscalationService escalationService;
    private final RuntimeService runtimeService;
    
    /**
     * Handle SLA breach with escalation
     */
    public void handleBreach(String caseId, Milestone milestone) {
        long breachDuration = System.currentTimeMillis() - milestone.getTargetTimestamp();
        
        log.warn("SLA breach detected: case={}, milestone={}, duration={}ms",
            caseId, milestone.getName(), breachDuration);
        
        // 1. Notify responsible party
        notifyResponsibleParty(caseId, milestone);
        
        // 2. Escalate based on severity
        EscalationLevel level = determineEscalationLevel(breachDuration);
        escalate(caseId, milestone, level);
        
        // 3. Trigger workflow intervention if needed
        if (level == EscalationLevel.CRITICAL) {
            triggerWorkflowIntervention(caseId, milestone);
        }
        
        // 4. Record breach for reporting
        recordBreach(caseId, milestone, breachDuration);
    }
    
    private void notifyResponsibleParty(String caseId, Milestone milestone) {
        String responsibleParty = milestone.getResponsibleParty();
        
        if (responsibleParty != null) {
            notificationService.sendBreachNotification(
                responsibleParty,
                caseId,
                milestone.getName(),
                milestone.getDescription()
            );
        }
    }
    
    private EscalationLevel determineEscalationLevel(long breachDuration) {
        long minutes = breachDuration / (60 * 1000);
        
        if (minutes < 15) {
            return EscalationLevel.LOW;
        } else if (minutes < 60) {
            return EscalationLevel.MEDIUM;
        } else if (minutes < 240) {
            return EscalationLevel.HIGH;
        } else {
            return EscalationLevel.CRITICAL;
        }
    }
    
    private void escalate(String caseId, Milestone milestone, EscalationLevel level) {
        switch (level) {
            case LOW:
                // Email to responsible party
                escalationService.escalateToTeam(caseId, milestone.getResponsibleParty());
                break;
                
            case MEDIUM:
                // Email to team lead
                escalationService.escalateToTeamLead(caseId, milestone.getResponsibleParty());
                break;
                
            case HIGH:
                // Email to manager + SMS
                escalationService.escalateToManager(caseId, milestone.getResponsibleParty());
                break;
                
            case CRITICAL:
                // Email to director + SMS + Slack alert
                escalationService.escalateToCritical(caseId, milestone.getResponsibleParty());
                break;
        }
    }
    
    private void triggerWorkflowIntervention(String caseId, Milestone milestone) {
        // Check if workflow is pended
        WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);
        
        if (!info.getPendExecPath().isEmpty()) {
            // Workflow is pended - consider automatic reassignment or escalation
            log.info("Critical SLA breach - workflow is pended, triggering intervention");
            
            // Option 1: Reassign to different work basket
            reassignToEscalatedQueue(caseId);
            
            // Option 2: Force resume with timeout
            // runtimeService.resumeCase(caseId);
        }
    }
    
    private void reassignToEscalatedQueue(String caseId) {
        // Implementation depends on your workflow structure
        WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);
        
        for (ExecPath path : info.getExecPaths()) {
            if (!path.getPendWorkBasket().isEmpty()) {
                String escalatedQueue = path.getPendWorkBasket() + "_escalated";
                path.setPrevPendWorkBasket(path.getPendWorkBasket());
                path.setPendWorkBasket(escalatedQueue);
            }
        }
        
        dao.update("workflow_process_info-" + caseId, info);
        log.info("Reassigned case {} to escalated queue", caseId);
    }
    
    private void recordBreach(String caseId, Milestone milestone, long breachDuration) {
        SLABreachRecord record = new SLABreachRecord();
        record.setCaseId(caseId);
        record.setMilestoneName(milestone.getName());
        record.setTargetTime(milestone.getTargetTimestamp());
        record.setActualTime(System.currentTimeMillis());
        record.setBreachDuration(breachDuration);
        record.setResponsibleParty(milestone.getResponsibleParty());
        
        dao.save("sla_breach-" + caseId + "-" + milestone.getName(), record);
    }
}

public enum EscalationLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

public class SLABreachRecord implements Serializable {
    private String caseId;
    private String milestoneName;
    private Long targetTime;
    private Long actualTime;
    private Long breachDuration;
    private String responsibleParty;
    private Long recordedAt = System.currentTimeMillis();
    
    // Getters and setters
}
```

### Automated Actions on Breach

```java
public class AutomatedBreachActions implements InvokableTask {
    
    private final SLAService slaService;
    
    @Override
    public TaskResponse executeStep() {
        String caseId = context.getCaseId();
        
        // Check for breached milestones
        List<Milestone> breached = slaService.getBreachedMilestones(caseId);
        
        if (!breached.isEmpty()) {
            log.warn("Found {} breached milestones for case {}", breached.size(), caseId);
            
            // Take automated action
            for (Milestone milestone : breached) {
                handleBreachedMilestone(milestone);
            }
            
            // Route to escalation
            return new TaskResponse(
                StepResponseType.OK_PROCEED,
                "",
                "",
                "escalation_handler"  // Ticket to escalation step
            );
        }
        
        // No breaches - continue normal flow
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
    
    private void handleBreachedMilestone(Milestone milestone) {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Mark as breached in process variables
        vars.setValue(
            "milestone_" + milestone.getName() + "_breached",
            WorkflowVariableType.BOOLEAN,
            true
        );
        
        // Store breach duration
        long breachDuration = milestone.getActualTimestamp() - milestone.getTargetTimestamp();
        vars.setValue(
            "milestone_" + milestone.getName() + "_breach_duration",
            WorkflowVariableType.LONG,
            breachDuration
        );
        
        // Increase priority
        Integer currentPriority = vars.getInteger("priority");
        if (currentPriority == null) currentPriority = 0;
        vars.setValue("priority", WorkflowVariableType.INTEGER, currentPriority + 1);
    }
}
```

## Advanced Patterns

### Dynamic SLA Adjustment

```java
public class DynamicSLAAdjuster {
    
    /**
     * Adjust SLA based on case priority
     */
    public List<Milestone> adjustForPriority(
        List<Milestone> baseMilestones,
        PriorityLevel priority
    ) {
        List<Milestone> adjusted = new ArrayList<>();
        
        for (Milestone milestone : baseMilestones) {
            Milestone adjustedMilestone = new Milestone();
            adjustedMilestone.setName(milestone.getName());
            adjustedMilestone.setDescription(milestone.getDescription());
            adjustedMilestone.setResponsibleParty(milestone.getResponsibleParty());
            
            // Adjust SLA based on priority
            int baseSLA = milestone.getSlaMinutes();
            int adjustedSLA = adjustSLAForPriority(baseSLA, priority);
            
            adjustedMilestone.setSlaMinutes(adjustedSLA);
            adjustedMilestone.setTargetTimestamp(
                System.currentTimeMillis() + (adjustedSLA * 60 * 1000L)
            );
            adjustedMilestone.setStatus(MilestoneStatus.PENDING);
            
            adjusted.add(adjustedMilestone);
        }
        
        return adjusted;
    }
    
    private int adjustSLAForPriority(int baseSLA, PriorityLevel priority) {
        switch (priority) {
            case CRITICAL:
                return (int) (baseSLA * 0.5);  // 50% of base
            case HIGH:
                return (int) (baseSLA * 0.75); // 75% of base
            case NORMAL:
                return baseSLA;                 // 100% of base
            case LOW:
                return (int) (baseSLA * 1.5);  // 150% of base
            default:
                return baseSLA;
        }
    }
}

public enum PriorityLevel {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW
}
```

### Cascading Milestones

```java
public class CascadingMilestones {
    
    /**
     * Adjust subsequent milestones when one is delayed
     */
    public void adjustSubsequentMilestones(String caseId, String achievedMilestoneName) {
        List<Milestone> milestones = slaService.loadSLA(caseId);
        
        // Find the achieved milestone
        Milestone achieved = milestones.stream()
            .filter(m -> m.getName().equals(achievedMilestoneName))
            .findFirst()
            .orElse(null);
        
        if (achieved == null || achieved.getActualTimestamp() == null) {
            return;
        }
        
        // Check if it was breached
        if (achieved.getActualTimestamp() > achieved.getTargetTimestamp()) {
            long delay = achieved.getActualTimestamp() - achieved.getTargetTimestamp();
            
            log.info("Milestone {} was delayed by {}ms, adjusting subsequent milestones",
                achievedMilestoneName, delay);
            
            // Adjust all subsequent pending milestones
            boolean foundAchieved = false;
            for (Milestone milestone : milestones) {
                if (milestone.getName().equals(achievedMilestoneName)) {
                    foundAchieved = true;
                    continue;
                }
                
                if (foundAchieved && milestone.getStatus() == MilestoneStatus.PENDING) {
                    // Add the delay to this milestone
                    long newTarget = milestone.getTargetTimestamp() + delay;
                    milestone.setTargetTimestamp(newTarget);
                    
                    log.info("Adjusted milestone {} target by {}ms", 
                        milestone.getName(), delay);
                }
            }
            
            // Save adjusted milestones
            slaService.saveSLA(caseId, milestones);
        }
    }
}
```

### Conditional SLA Based on Data

```java
public class ConditionalSLAFactory {
    
    public List<Milestone> createConditionalSLA(String caseId, WorkflowVariables vars) {
        List<Milestone> milestones = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        // Get order details
        Double orderAmount = vars.getDouble("order_amount");
        Boolean isInternational = vars.getBoolean("is_international");
        String customerTier = vars.getString("customer_tier");
        
        // Milestone 1: Validation
        // Faster SLA for VIP customers
        int validationSLA = "VIP".equals(customerTier) ? 10 : 30;
        milestones.add(createMilestone(
            "validation",
            "Order Validation",
            validationSLA,
            startTime
        ));
        
        // Milestone 2: Processing
        // Longer SLA for international orders
        int processingSLA = Boolean.TRUE.equals(isInternational) ? 120 : 60;
        milestones.add(createMilestone(
            "processing",
            "Order Processing",
            processingSLA,
            startTime
        ));
        
        // Milestone 3: Approval (only for high-value orders)
        if (orderAmount != null && orderAmount > 10000) {
            milestones.add(createMilestone(
                "approval",
                "Manager Approval Required",
                240,  // 4 hours
                startTime
            ));
        }
        
        // Milestone 4: Completion
        int completionSLA = calculateCompletionSLA(orderAmount, isInternational, customerTier);
        milestones.add(createMilestone(
            "completion",
            "Order Completion",
            completionSLA,
            startTime
        ));
        
        return milestones;
    }
    
    private int calculateCompletionSLA(Double amount, Boolean international, String tier) {
        int baseSLA = 24 * 60; // 24 hours
        
        // Adjust for customer tier
        if ("VIP".equals(tier)) {
            baseSLA = (int) (baseSLA * 0.5); // 12 hours for VIP
        }
        
        // Adjust for international
        if (Boolean.TRUE.equals(international)) {
            baseSLA = (int) (baseSLA * 1.5); // Extra time for international
        }
        
        // Adjust for order value
        if (amount != null && amount > 5000) {
            baseSLA += 120; // Add 2 hours for high-value orders (extra checks)
        }
        
        return baseSLA;
    }
    
    private Milestone createMilestone(String name, String description, int slaMinutes, long startTime) {
        Milestone milestone = new Milestone();
        milestone.setName(name);
        milestone.setDescription(description);
        milestone.setSlaMinutes(slaMinutes);
        milestone.setTargetTimestamp(startTime + (slaMinutes * 60 * 1000L));
        milestone.setStatus(MilestoneStatus.PENDING);
        return milestone;
    }
}
```

### Multi-Tier SLA

```java
public class MultiTierSLA {
    
    /**
     * Create SLA with multiple warning levels
     */
    public List<SLATier> createTieredSLA(Milestone milestone) {
        List<SLATier> tiers = new ArrayList<>();
        
        long startTime = milestone.getTargetTimestamp() - (milestone.getSlaMinutes() * 60 * 1000L);
        int totalMinutes = milestone.getSlaMinutes();
        
        // Green tier: 0-70%
        tiers.add(new SLATier(
            "GREEN",
            startTime,
            startTime + (long) (totalMinutes * 0.7 * 60 * 1000),
            "On track"
        ));
        
        // Yellow tier: 70-90%
        tiers.add(new SLATier(
            "YELLOW",
            startTime + (long) (totalMinutes * 0.7 * 60 * 1000),
            startTime + (long) (totalMinutes * 0.9 * 60 * 1000),
            "Approaching deadline"
        ));
        
        // Orange tier: 90-100%
        tiers.add(new SLATier(
            "ORANGE",
            startTime + (long) (totalMinutes * 0.9 * 60 * 1000),
            milestone.getTargetTimestamp(),
            "At risk"
        ));
        
        // Red tier: >100% (breached)
        tiers.add(new SLATier(
            "RED",
            milestone.getTargetTimestamp(),
            Long.MAX_VALUE,
            "Breached"
        ));
        
        return tiers;
    }
    
    public String getCurrentTier(Milestone milestone) {
        if (milestone.getActualTimestamp() != null) {
            return milestone.getStatus() == MilestoneStatus.MET ? "GREEN" : "RED";
        }
        
        long now = System.currentTimeMillis();
        List<SLATier> tiers = createTieredSLA(milestone);
        
        for (SLATier tier : tiers) {
            if (now >= tier.getStartTime() && now < tier.getEndTime()) {
                return tier.getName();
            }
        }
        
        return "UNKNOWN";
    }
}

public class SLATier {
    private String name;
    private long startTime;
    private long endTime;
    private String description;
    
    public SLATier(String name, long startTime, long endTime, String description) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.description = description;
    }
    
    // Getters
    public String getName() { return name; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public String getDescription() { return description; }
}
```

## Best Practices

### 1. Define Meaningful Milestones

✅ **DO**: Create milestones for key business events
```java
// Good milestone names
- "order_validated"
- "payment_received"
- "shipment_dispatched"
- "customer_notified"
- "order_completed"
```

❌ **DON'T**: Create too many granular milestones
```java
// Too granular
- "step_1_complete"
- "step_2_complete"
- "step_3_complete"
```

### 2. Set Realistic SLAs

✅ **DO**: Base SLAs on historical data
```java
// Analyze historical completion times
public int calculateRealisticSLA(String milestoneType) {
    List<Long> historicalTimes = getHistoricalCompletionTimes(milestoneType);
    
    // Use 90th percentile as SLA
    Collections.sort(historicalTimes);
    int index = (int) (historicalTimes.size() * 0.9);
    long p90 = historicalTimes.get(index);
    
    return (int) (p90 / (60 * 1000)); // Convert to minutes
}
```

### 3. Consider Business Hours

✅ **DO**: Use business hours for user-dependent milestones
```java
// Manager approval requires business hours
BusinessHoursCalculator calculator = new BusinessHoursCalculator(holidays);
long targetTime = calculator.calculateBusinessHoursTarget(startTime, 240); // 4 hours
```

❌ **DON'T**: Use business hours for automated processes
```java
// Automated processing can run 24/7
long targetTime = System.currentTimeMillis() + (60 * 60 * 1000); // 1 hour, any time
```

### 4. Implement Grace Periods

✅ **DO**: Add grace periods for critical milestones
```java
milestone.setGracePeriodMinutes(15);

// Check breach considering grace period
boolean isBreached = actualTime > (targetTime + gracePeriod);
```

### 5. Monitor Proactively

✅ **DO**: Alert before breach occurs
```java
// Alert at 80% of SLA
if (percentElapsed >= 0.8 && percentElapsed < 1.0) {
    sendWarningAlert();
}
```

### 6. Document SLA Assumptions

✅ **DO**: Document what's included in SLA
```java
/**
 * Order Processing SLA
 * 
 * Includes:
 * - Order validation (15 min)
 * - Payment processing (30 min)
 * - Inventory allocation (20 min)
 * - Shipment creation (15 min)
 * Total: 80 minutes
 * 
 * Excludes:
 * - Actual shipping time
 * - Customer response time
 * 
 * Business Hours: Mon-Fri, 9 AM - 5 PM EST
 * Holidays: US Federal Holidays excluded
 */
```

### 7. Handle Milestone Dependencies

✅ **DO**: Consider dependencies between milestones
```java
// If payment fails, extend subsequent milestones
if (paymentFailed) {
    extendMilestone("shipment_creation", 60); // Add 1 hour
}
```

### 8. Store Breach Reasons

✅ **DO**: Capture why SLAs were breached
```java
public class EnhancedMilestone extends Milestone {
    private String breachReason;
    private String breachCategory; // SYSTEM, USER, EXTERNAL, PROCESS
    
    public void recordBreach(String reason, String category) {
        this.breachReason = reason;
        this.breachCategory = category;
        this.setStatus(MilestoneStatus.BREACHED);
    }
}
```

### 9. Review and Adjust SLAs

✅ **DO**: Regularly review SLA performance
```java
@Scheduled(cron = "0 0 1 * * MON") // Every Monday at 1 AM
public void reviewSLAPerformance() {
    SLAPerformanceReport report = generateWeeklyReport();
    
    if (report.getComplianceRate() < 0.85) {
        log.warn("SLA compliance below 85%, review needed");
        notifyManagement(report);
    }
}
```

### 10. Test SLA Logic

✅ **DO**: Write tests for SLA calculations
```java
@Test
public void testSLACalculation() {
    long startTime = System.currentTimeMillis();
    int slaMinutes = 120;
    
    Milestone milestone = new Milestone();
    milestone.setSlaMinutes(slaMinutes);
    milestone.setTargetTimestamp(startTime + (slaMinutes * 60 * 1000L));
    
    // Test on-time completion
    milestone.setActualTimestamp(startTime + (100 * 60 * 1000L)); // 100 minutes
    assertEquals(MilestoneStatus.MET, determineStatus(milestone));
    
    // Test breached
    milestone.setActualTimestamp(startTime + (130 * 60 * 1000L)); // 130 minutes
    assertEquals(MilestoneStatus.BREACHED, determineStatus(milestone));
}
```

## Real-World Examples

### Example 1: Customer Support Ticket SLA

```java
public class SupportTicketSLA {
    
    public List<Milestone> createSupportSLA(String caseId, String severity) {
        List<Milestone> milestones = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        // SLA varies by severity
        int firstResponseSLA;
        int resolutionSLA;
        
        switch (severity) {
            case "CRITICAL":
                firstResponseSLA = 15;      // 15 minutes
                resolutionSLA = 4 * 60;     // 4 hours
                break;
            case "HIGH":
                firstResponseSLA =