package com.example.workflow.orderprocessing;

import com.anode.workflow.service.WorkflowService;
import com.anode.workflow.service.RuntimeService;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.storage.CommonService;
import com.anode.workflow.storage.file.FileDao;
import com.anode.workflow.entities.ProcessEntity;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Example application demonstrating order processing workflow.
 */
public class OrderProcessingApp {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Order Processing Workflow Example ===\n");

        // 1. Initialize workflow service
        System.out.println("Initializing workflow service...");
        WorkflowService.init(
            10,      // Thread pool size
            30000,   // Step timeout (30 seconds)
            "-"      // Execution path delimiter
        );

        // 2. Set up persistence
        CommonService dao = new FileDao("./workflow-data");

        // 3. Create component factory and event handler
        WorkflowComponantFactory factory = new OrderComponentFactory();
        EventHandler handler = new OrderEventHandler();

        // 4. Get runtime service
        RuntimeService runtimeService = WorkflowService.instance()
            .getRunTimeService(dao, factory, handler, null);

        // 5. Load workflow definition
        System.out.println("Loading workflow definition...");
        String workflowJson = new String(
            Files.readAllBytes(Paths.get("workflow-definition.json"))
        );

        // 6. Prepare order data
        String caseId = "CASE-" + System.currentTimeMillis();
        String orderId = "ORD-12345";

        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("orderId", orderId);
        processVariables.put("customerId", "CUST-001");
        processVariables.put("customerEmail", "customer@example.com");

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("customerId", "CUST-001");
        orderData.put("items", List.of(
            Map.of("sku", "ITEM-001", "name", "Widget", "quantity", 2, "price", 99.99),
            Map.of("sku", "ITEM-002", "name", "Gadget", "quantity", 1, "price", 149.99)
        ));
        orderData.put("totalAmount", 349.97);
        orderData.put("shippingAddress", Map.of(
            "street", "123 Main St",
            "city", "Springfield",
            "state", "IL",
            "zip", "62701"
        ));

        processVariables.put("orderData", orderData);

        // 7. Start workflow
        System.out.println("\nStarting order processing workflow...");
        System.out.println("Case ID: " + caseId);
        System.out.println("Order ID: " + orderId);
        System.out.println();

        runtimeService.startCase(
            caseId,
            workflowJson,
            processVariables,
            null  // No custom SLA config
        );

        // 8. Wait for completion
        System.out.println("Waiting for workflow to complete...\n");
        Thread.sleep(5000);  // Wait 5 seconds

        // 9. Check final status
        ProcessEntity process = runtimeService.getProcess(caseId);
        System.out.println("\n=== Workflow Complete ===");
        System.out.println("Final status: " + process.getStatus());
        System.out.println("Total steps: " + process.getTotalSteps());
        System.out.println("Completed steps: " + process.getCompletedSteps());

        // 10. Display results
        Map<String, Object> results = process.getProcessVariables();
        System.out.println("\n=== Order Results ===");
        System.out.println("Order validated: " + results.get("orderValid"));
        System.out.println("In stock: " + results.get("inStock"));
        System.out.println("Payment ID: " + results.get("paymentId"));
        System.out.println("Tracking number: " + results.get("trackingNumber"));
        System.out.println("Notification sent: " + results.get("notificationSent"));

        System.out.println("\nExample completed successfully!");
    }
}
