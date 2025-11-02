# Order Processing Example

Complete example of an order processing workflow with Simple Workflow engine.

## Overview

This example demonstrates:
- Sequential workflow execution
- Process variable usage
- Error handling
- Event handling
- File-based persistence

## Workflow Steps

1. **Validate Order**: Check order data is complete and valid
2. **Check Inventory**: Verify items are in stock
3. **Process Payment**: Charge customer's payment method
4. **Ship Order**: Create shipment and generate tracking number
5. **Send Notification**: Email customer with order confirmation

## Running the Example

### Prerequisites

- Java 17+
- Maven 3.6+

### Build

```bash
mvn clean package
```

### Run

```bash
java -jar target/order-processing-example.jar
```

### Expected Output

```
Initializing workflow service...
Starting order processing workflow...

Workflow started: CASE-20231102-001
Step started: validate (validateOrder)
Order ORD-12345 validated: true
Step completed: validate

Step started: inventory (checkInventory)
Inventory check for order ORD-12345: true
Step completed: inventory

Step started: payment (processPayment)
Payment for order ORD-12345: SUCCESS
Step completed: payment

Step started: ship (shipOrder)
Order ORD-12345 shipped. Tracking: TRACK-1698932123456
Step completed: ship

Step started: notify (sendNotification)
Notification sent to customer@example.com
Step completed: notify

Workflow completed: CASE-20231102-001
Final status: COMPLETED
```

## Files

- `workflow-definition.json` - Workflow definition
- `OrderProcessingApp.java` - Main application
- `steps/` - Step implementations
  - `ValidateOrderStep.java`
  - `CheckInventoryStep.java`
  - `ProcessPaymentStep.java`
  - `ShipOrderStep.java`
  - `SendNotificationStep.java`
- `OrderComponentFactory.java` - Component factory
- `OrderEventHandler.java` - Event handler
- `pom.xml` - Maven configuration

## Customization

### Modify Workflow

Edit `workflow-definition.json` to change the workflow structure:

```json
{
  "steps": [
    // Add, remove, or reorder steps
  ]
}
```

### Add New Steps

1. Create step class implementing `WorkflowStep`
2. Add to `OrderComponentFactory.getStepInstance()`
3. Add step to workflow definition JSON

### Change Persistence

Replace FileDao with your own implementation:

```java
// Use database instead
CommonService dao = new PostgresDao(dataSource);
```

## Testing

Run tests:

```bash
mvn test
```

## Related Examples

- [Parallel Processing](../parallel-processing/) - Parallel execution demo
- [Approval Workflow](../approval-workflow/) - Human tasks example
- [Saga Pattern](../saga-pattern/) - Distributed transactions
