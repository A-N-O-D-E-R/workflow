# Work Baskets

Work baskets are queues that hold workflow steps requiring human intervention or manual processing. They enable human task management, workload distribution, and assignment-based workflow routing within the Simple Workflow engine.

## Table of Contents
1. [Overview](#overview)
2. [Core Concepts](#core-concepts)
3. [Creating Work Items](#creating-work-items)
4. [Claiming and Completing Work](#claiming-and-completing-work)
5. [Work Basket Routing](#work-basket-routing)
6. [Advanced Patterns](#advanced-patterns)
7. [Best Practices](#best-practices)
8. [Common Use Cases](#common-use-cases)

## Overview

### What Are Work Baskets?

Work baskets (also called work queues or task lists) are collections of work items that:
- **Represent human tasks**: Steps that require manual intervention
- **Enable workload distribution**: Assign work to teams or individuals
- **Support prioritization**: Order work by priority, due date, or other criteria
- **Track assignments**: Know who is working on what
- **Integrate with workflows**: Seamlessly pause and resume workflow execution

### Key Characteristics

```java
✅ Queue-based: Work items are held in queues
✅ Assignable: Work can be assigned to users or groups
✅ Claimable: Users can claim work from shared queues
✅ Time-tracked: Track how long work items remain in baskets
✅ Integrated: Part of the workflow engine, not external
```

## Core Concepts

### Work Item

A work item represents a single unit of work that requires human intervention:

```java
WorkItem {
    String workItemId;           // Unique identifier
    String caseId;               // Associated workflow instance
    String stepId;               // Step awaiting completion
    String basketName;           // Queue containing this item
    String assignedTo;           // User/team assigned (if any)
    String claimedBy;            // User who claimed it (if any)
    Date createdAt;              // When item was created
    Date claimedAt;              // When item was claimed
    String priority;             // Priority level
    Map<String, Object> data;    // Additional context data
}
```

### Work Basket

A named collection of work items:

```java
WorkBasket {
    String basketName;           // Unique basket identifier
    int itemCount;               // Number of items in basket
    List<WorkItem> items;        // Work items in this basket
}
```

### Work Management Service

The service that manages work baskets and items:

```java
WorkManagementService wms = WorkflowService.instance()
    .getWorkManagementService(dao);
```

## Creating Work Items

### Basic Work Item Creation

Create a work item when a step needs manual processing:

```java
public class ApprovalStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        String caseId = context.getCaseId();
        String orderId = context.getProcessVariable("orderId");
        Double amount = context.getProcessVariable("orderAmount");

        // Get work management service
        WorkManagementService wms = WorkflowService.instance()
            .getWorkManagementService(context.getDao());

        // Create work item in "approvals" basket
        WorkItem item = wms.createWorkItem(
            caseId,
            "approve-order",      // Step ID
            "approvals"           // Basket name
        );

        // Add context data for the approver
        Map<String, Object> itemData = new HashMap<>();
        itemData.put("orderId", orderId);
        itemData.put("amount", amount);
        itemData.put("requiresReview", amount > 1000);

        item.setData(itemData);
        wms.updateWorkItem(item);

        System.out.println("Created work item " + item.getWorkItemId() +
            " in basket 'approvals'");

        // Step remains in PENDING state until work item is completed
    }
}
```

### Work Item with Priority

```java
public class PriorityWorkItemStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        WorkManagementService wms = getWorkManagementService(context);

        Double amount = context.getProcessVariable("orderAmount");

        // Determine priority based on business rules
        String priority;
        if (amount > 10000) {
            priority = "HIGH";
        } else if (amount > 1000) {
            priority = "MEDIUM";
        } else {
            priority = "LOW";
        }

        WorkItem item = wms.createWorkItem(
            context.getCaseId(),
            context.getCurrentStepId(),
            "approvals"
        );

        item.setPriority(priority);
        wms.updateWorkItem(item);
    }
}
```

### Pre-Assigned Work Item

Assign work directly to a specific user:

```java
public class AssignedWorkItemStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        WorkManagementService wms = getWorkManagementService(context);

        // Determine assignee based on business rules
        String assignee = determineApprover(context);

        WorkItem item = wms.createWorkItem(
            context.getCaseId(),
            context.getCurrentStepId(),
            "personal-tasks"
        );

        // Pre-assign to specific user
        item.setAssignedTo(assignee);
        wms.updateWorkItem(item);

        System.out.println("Work item assigned to: " + assignee);
    }

    private String determineApprover(WorkflowContext context) {
        Double amount = context.getProcessVariable("orderAmount");

        if (amount > 5000) {
            return "senior-manager";
        } else if (amount > 1000) {
            return "manager";
        } else {
            return "supervisor";
        }
    }
}
```

## Claiming and Completing Work

### Listing Available Work

Users can query work baskets to see available work:

```java
public class WorkBasketUI {

    public void displayWorkBasket(String basketName, String userId) {
        WorkManagementService wms = getWorkManagementService();

        // Get all items in basket
        List<WorkItem> items = wms.getWorkItemsByBasket(basketName);

        System.out.println("Work Basket: " + basketName);
        System.out.println("Items: " + items.size());

        for (WorkItem item : items) {
            System.out.println("\n  Item: " + item.getWorkItemId());
            System.out.println("  Case: " + item.getCaseId());
            System.out.println("  Priority: " + item.getPriority());
            System.out.println("  Created: " + item.getCreatedAt());
            System.out.println("  Assigned: " +
                (item.getAssignedTo() != null ? item.getAssignedTo() : "Unassigned"));
            System.out.println("  Status: " +
                (item.getClaimedBy() != null ? "Claimed by " + item.getClaimedBy() : "Available"));
        }
    }

    public void displayMyWork(String userId) {
        WorkManagementService wms = getWorkManagementService();

        // Get items assigned to or claimed by user
        List<WorkItem> myItems = wms.getWorkItemsByUser(userId);

        System.out.println("My Work Items: " + myItems.size());
        for (WorkItem item : myItems) {
            displayWorkItemDetails(item);
        }
    }
}
```

### Claiming Work

Users claim work items from shared baskets:

```java
public class WorkClaimHandler {

    public boolean claimWork(String workItemId, String userId) {
        WorkManagementService wms = getWorkManagementService();

        try {
            // Get work item
            WorkItem item = wms.getWorkItem(workItemId);

            // Check if already claimed
            if (item.getClaimedBy() != null) {
                System.out.println("Work item already claimed by: " +
                    item.getClaimedBy());
                return false;
            }

            // Claim the work
            item.setClaimedBy(userId);
            item.setClaimedAt(new Date());
            wms.updateWorkItem(item);

            System.out.println("Work item " + workItemId + " claimed by " + userId);
            return true;

        } catch (Exception e) {
            System.err.println("Failed to claim work: " + e.getMessage());
            return false;
        }
    }
}
```

### Completing Work

Complete a work item to resume workflow execution:

```java
public class WorkCompletionHandler {

    public void completeWork(String workItemId, String userId,
                            Map<String, Object> completionData) {
        WorkManagementService wms = getWorkManagementService();
        RuntimeService rts = getRuntimeService();

        try {
            // Get work item
            WorkItem item = wms.getWorkItem(workItemId);

            // Verify ownership
            if (!userId.equals(item.getClaimedBy()) &&
                !userId.equals(item.getAssignedTo())) {
                throw new SecurityException(
                    "User " + userId + " not authorized to complete this work");
            }

            // Store completion data in process variables
            String caseId = item.getCaseId();
            WorkflowContext context = rts.getContext(caseId);

            for (Map.Entry<String, Object> entry : completionData.entrySet()) {
                context.setProcessVariable(entry.getKey(), entry.getValue());
            }

            // Mark work item complete
            wms.completeWorkItem(workItemId);

            // Signal workflow to continue
            rts.signalStep(caseId, item.getStepId());

            System.out.println("Work item " + workItemId + " completed by " + userId);

        } catch (Exception e) {
            System.err.println("Failed to complete work: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void completeApproval(String workItemId, String userId,
                                boolean approved, String comments) {
        Map<String, Object> completionData = new HashMap<>();
        completionData.put("approved", approved);
        completionData.put("approvedBy", userId);
        completionData.put("approvalComments", comments);
        completionData.put("approvalTimestamp", System.currentTimeMillis());

        completeWork(workItemId, userId, completionData);
    }
}
```

### Releasing Work

Release a claimed work item back to the basket:

```java
public class WorkReleaseHandler {

    public void releaseWork(String workItemId, String userId) {
        WorkManagementService wms = getWorkManagementService();

        try {
            WorkItem item = wms.getWorkItem(workItemId);

            // Verify user owns the work
            if (!userId.equals(item.getClaimedBy())) {
                throw new SecurityException(
                    "User " + userId + " does not own this work item");
            }

            // Release back to basket
            item.setClaimedBy(null);
            item.setClaimedAt(null);
            wms.updateWorkItem(item);

            System.out.println("Work item " + workItemId +
                " released back to basket");

        } catch (Exception e) {
            System.err.println("Failed to release work: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
```

## Work Basket Routing

### Conditional Basket Assignment

Route work to different baskets based on conditions:

```java
public class ConditionalRoutingStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        WorkManagementService wms = getWorkManagementService(context);

        Double amount = context.getProcessVariable("orderAmount");
        String customerTier = context.getProcessVariable("customerTier");

        // Determine basket based on business rules
        String basketName;
        if (amount > 10000 || "PLATINUM".equals(customerTier)) {
            basketName = "vip-approvals";
        } else if (amount > 1000) {
            basketName = "manager-approvals";
        } else {
            basketName = "standard-approvals";
        }

        WorkItem item = wms.createWorkItem(
            context.getCaseId(),
            context.getCurrentStepId(),
            basketName
        );

        context.setProcessVariable("assignedBasket", basketName);
    }
}
```

### Multi-Level Approval Routing

Implement escalation workflows:

```java
public class MultiLevelApprovalStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        WorkManagementService wms = getWorkManagementService(context);

        // Check current approval level
        Integer approvalLevel = context.getProcessVariable("approvalLevel");
        if (approvalLevel == null) {
            approvalLevel = 1;
        }

        // Get previous approval result
        Boolean previousApproved = context.getProcessVariable("approved");

        if (approvalLevel == 1) {
            // First level approval
            createApprovalWorkItem(context, wms, "supervisor-approvals", 1);

        } else if (approvalLevel == 2 && Boolean.TRUE.equals(previousApproved)) {
            // Supervisor approved, escalate to manager
            createApprovalWorkItem(context, wms, "manager-approvals", 2);

        } else if (approvalLevel == 3 && Boolean.TRUE.equals(previousApproved)) {
            // Manager approved, escalate to director
            createApprovalWorkItem(context, wms, "director-approvals", 3);

        } else if (Boolean.FALSE.equals(previousApproved)) {
            // Rejected at any level
            context.setProcessVariable("finalApproval", false);
            context.setProcessVariable("rejectedAt", "Level " + approvalLevel);

        } else {
            // All approvals complete
            context.setProcessVariable("finalApproval", true);
            context.setProcessVariable("approvalLevelsCompleted", approvalLevel);
        }
    }

    private void createApprovalWorkItem(WorkflowContext context,
                                       WorkManagementService wms,
                                       String basketName,
                                       int level) {
        WorkItem item = wms.createWorkItem(
            context.getCaseId(),
            context.getCurrentStepId() + "-level" + level,
            basketName
        );

        context.setProcessVariable("approvalLevel", level);
        context.setProcessVariable("currentBasket", basketName);
    }
}
```

### Team-Based Routing

Assign work based on team capacity or skills:

```java
public class TeamBasedRoutingStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        WorkManagementService wms = getWorkManagementService(context);

        String taskType = context.getProcessVariable("taskType");
        String region = context.getProcessVariable("region");

        // Determine team based on task type and region
        String basketName = determineTeamBasket(taskType, region);

        // Check team capacity
        List<WorkItem> existingItems = wms.getWorkItemsByBasket(basketName);
        int currentLoad = existingItems.size();

        // If team is overloaded, route to backup team
        if (currentLoad > 50) {
            basketName = getBackupTeam(basketName);
        }

        WorkItem item = wms.createWorkItem(
            context.getCaseId(),
            context.getCurrentStepId(),
            basketName
        );

        // Add routing metadata
        Map<String, Object> itemData = new HashMap<>();
        itemData.put("taskType", taskType);
        itemData.put("region", region);
        itemData.put("routedAt", System.currentTimeMillis());
        itemData.put("teamLoad", currentLoad);

        item.setData(itemData);
        wms.updateWorkItem(item);
    }

    private String determineTeamBasket(String taskType, String region) {
        return taskType + "-" + region + "-team";
    }

    private String getBackupTeam(String primaryTeam) {
        return primaryTeam + "-backup";
    }
}
```

## Advanced Patterns

### Work Item Expiration

Automatically escalate or reassign expired work items:

```java
public class WorkItemExpirationMonitor {

    private static final long EXPIRATION_THRESHOLD = 24 * 60 * 60 * 1000; // 24 hours

    public void checkExpiredItems() {
        WorkManagementService wms = getWorkManagementService();

        List<WorkItem> allItems = wms.getAllWorkItems();
        long now = System.currentTimeMillis();

        for (WorkItem item : allItems) {
            long age = now - item.getCreatedAt().getTime();

            if (age > EXPIRATION_THRESHOLD && item.getClaimedBy() == null) {
                // Item expired without being claimed
                escalateWorkItem(item);
            }
        }
    }

    private void escalateWorkItem(WorkItem item) {
        WorkManagementService wms = getWorkManagementService();

        // Move to escalation basket
        String originalBasket = item.getBasketName();
        String escalationBasket = originalBasket + "-escalated";

        item.setBasketName(escalationBasket);
        item.setPriority("HIGH");

        Map<String, Object> data = item.getData();
        data.put("escalated", true);
        data.put("originalBasket", originalBasket);
        data.put("escalatedAt", System.currentTimeMillis());
        item.setData(data);

        wms.updateWorkItem(item);

        System.out.println("Escalated work item " + item.getWorkItemId() +
            " from " + originalBasket + " to " + escalationBasket);
    }
}
```

### Load Balancing

Distribute work evenly across workers:

```java
public class LoadBalancingRoutingStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        WorkManagementService wms = getWorkManagementService(context);

        // Get available baskets for this task type
        List<String> availableBaskets = Arrays.asList(
            "team-a-basket",
            "team-b-basket",
            "team-c-basket"
        );

        // Find basket with least load
        String targetBasket = findLeastLoadedBasket(wms, availableBaskets);

        WorkItem item = wms.createWorkItem(
            context.getCaseId(),
            context.getCurrentStepId(),
            targetBasket
        );

        System.out.println("Routed to least loaded basket: " + targetBasket);
    }

    private String findLeastLoadedBasket(WorkManagementService wms,
                                        List<String> baskets) {
        String leastLoaded = baskets.get(0);
        int minLoad = Integer.MAX_VALUE;

        for (String basket : baskets) {
            List<WorkItem> items = wms.getWorkItemsByBasket(basket);
            int load = items.size();

            if (load < minLoad) {
                minLoad = load;
                leastLoaded = basket;
            }
        }

        return leastLoaded;
    }
}
```

### Work Item Delegation

Allow users to delegate work to others:

```java
public class WorkDelegationHandler {

    public void delegateWork(String workItemId, String fromUser, String toUser) {
        WorkManagementService wms = getWorkManagementService();

        try {
            WorkItem item = wms.getWorkItem(workItemId);

            // Verify user owns the work
            if (!fromUser.equals(item.getClaimedBy()) &&
                !fromUser.equals(item.getAssignedTo())) {
                throw new SecurityException(
                    "User " + fromUser + " does not own this work item");
            }

            // Record delegation
            Map<String, Object> data = item.getData();
            data.put("delegatedFrom", fromUser);
            data.put("delegatedTo", toUser);
            data.put("delegatedAt", System.currentTimeMillis());

            // Update assignment
            item.setAssignedTo(toUser);
            item.setClaimedBy(null);  // Recipient must claim
            item.setData(data);

            wms.updateWorkItem(item);

            System.out.println("Work item " + workItemId +
                " delegated from " + fromUser + " to " + toUser);

        } catch (Exception e) {
            System.err.println("Failed to delegate work: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
```

## Best Practices

### 1. Use Descriptive Basket Names

```java
// ❌ Bad
wms.createWorkItem(caseId, stepId, "basket1");
wms.createWorkItem(caseId, stepId, "queue");

// ✅ Good
wms.createWorkItem(caseId, stepId, "order-approvals");
wms.createWorkItem(caseId, stepId, "customer-service-escalations");
wms.createWorkItem(caseId, stepId, "finance-team-reviews");
```

### 2. Always Set Priority

```java
// ✅ Set priority based on business rules
WorkItem item = wms.createWorkItem(caseId, stepId, basket);

String priority = calculatePriority(context);
item.setPriority(priority);
wms.updateWorkItem(item);
```

### 3. Include Context Data

```java
// ✅ Provide context for workers
WorkItem item = wms.createWorkItem(caseId, stepId, basket);

Map<String, Object> itemData = new HashMap<>();
itemData.put("orderId", orderId);
itemData.put("customerName", customerName);
itemData.put("amount", amount);
itemData.put("dueDate", dueDate);
itemData.put("notes", notes);

item.setData(itemData);
wms.updateWorkItem(item);
```

### 4. Implement Time Limits

```java
// ✅ Track time and escalate if needed
public void monitorWorkItemAge() {
    WorkManagementService wms = getWorkManagementService();
    List<WorkItem> items = wms.getAllWorkItems();

    for (WorkItem item : items) {
        long ageHours = (System.currentTimeMillis() -
            item.getCreatedAt().getTime()) / (1000 * 60 * 60);

        if (ageHours > 24 && item.getPriority().equals("LOW")) {
            item.setPriority("MEDIUM");
            wms.updateWorkItem(item);
        } else if (ageHours > 48) {
            escalateWorkItem(item);
        }
    }
}
```

### 5. Validate Completion Data

```java
// ✅ Validate before completing
public void completeWork(String workItemId, String userId,
                        Map<String, Object> completionData) {
    // Validate required fields
    if (!completionData.containsKey("approved")) {
        throw new IllegalArgumentException("Missing required field: approved");
    }

    if (!completionData.containsKey("comments")) {
        throw new IllegalArgumentException("Missing required field: comments");
    }

    // Proceed with completion
    WorkManagementService wms = getWorkManagementService();
    wms.completeWorkItem(workItemId);
}
```

### 6. Log Work Item Actions

```java
// ✅ Maintain audit trail
public void auditWorkItemAction(String workItemId, String userId,
                               String action, String details) {
    AuditLog log = new AuditLog();
    log.setWorkItemId(workItemId);
    log.setUserId(userId);
    log.setAction(action);  // CREATED, CLAIMED, COMPLETED, RELEASED, etc.
    log.setDetails(details);
    log.setTimestamp(System.currentTimeMillis());

    auditService.log(log);
}
```

## Common Use Cases

### 1. Approval Workflows

```java
// Manager approval required for large orders
if (orderAmount > 1000) {
    WorkItem item = wms.createWorkItem(caseId, "manager-approval", "approvals");
    item.setPriority(orderAmount > 5000 ? "HIGH" : "MEDIUM");
    wms.updateWorkItem(item);
}
```

### 2. Customer Service Escalations

```java
// Escalate customer complaints to senior staff
if ("COMPLAINT".equals(ticketType)) {
    WorkItem item = wms.createWorkItem(caseId, "handle-complaint",
        "customer-service-escalations");
    item.setPriority("HIGH");
    item.setAssignedTo("senior-agent");
    wms.updateWorkItem(item);
}
```

### 3. Document Review Workflows

```java
// Multiple reviewers for document approval
for (String reviewer : reviewerList) {
    WorkItem item = wms.createWorkItem(caseId, "review-document",
        "reviewer-" + reviewer);
    item.setAssignedTo(reviewer);
    wms.updateWorkItem(item);
}
```

### 4. Manual Data Entry

```java
// Data entry tasks for scanning operations
WorkItem item = wms.createWorkItem(caseId, "enter-scanned-data",
    "data-entry-team");
Map<String, Object> data = new HashMap<>();
data.put("documentType", "invoice");
data.put("scanImageUrl", imageUrl);
item.setData(data);
wms.updateWorkItem(item);
```

## Related Documentation

- [Execution Paths](./execution-paths.md) - How work baskets interact with workflow execution
- [Process Variables](./process-variables.md) - Storing completion data
- [SLA Management](./sla-management.md) - Tracking work item deadlines
- [Error Handling](../patterns/error-handling.md) - Handling work item failures

## Summary

Work baskets enable human task management within workflows:
- Use descriptive basket names for clarity
- Set priority based on business rules
- Include context data for workers
- Implement time limits and escalation
- Validate completion data
- Maintain audit trails
- Monitor basket loads for balancing

With work baskets, you can seamlessly integrate human decision-making into automated workflows.
