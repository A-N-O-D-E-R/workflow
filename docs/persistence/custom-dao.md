# Custom DAO Implementation Guide

## Overview

Simple Workflow supports pluggable persistence through the `CommonService` interface. This allows you to implement custom storage backends (RDBMS, NoSQL, file systems, cloud storage, etc.) to fit your application's needs.

## Table of Contents
- [Interface Overview](#interface-overview)
- [Implementation Patterns](#implementation-patterns)
- [File-Based Implementation](#file-based-implementation)
- [RDBMS Implementation](#rdbms-implementation)
- [NoSQL Implementation](#nosql-implementation)
- [Cloud Storage Implementation](#cloud-storage-implementation)
- [Testing Your DAO](#testing-your-dao)
- [Best Practices](#best-practices)
- [Performance Considerations](#performance-considerations)

## Interface Overview

### CommonService Interface

```java
public interface CommonService {
    // Basic CRUD operations
    void save(Serializable id, Object object);
    void update(Serializable id, Object object);
    void saveOrUpdate(Serializable id, Object object);
    void delete(Serializable id);
    <T> T get(Class<T> objectClass, Serializable id);
    
    // Batch operations
    void saveCollection(Collection objects);
    void saveOrUpdateCollection(Collection objects);
    
    // Query operations
    <T> List<T> getAll(Class<T> type);
    <T> T getUniqueItem(Class<T> type, String uniqueKeyName, String uniqueKeyValue);
    
    // Locking
    <T> T getLocked(Class<T> objectClass, Serializable id);
    
    // Counter management
    long incrCounter(String counterName);
    
    // Advanced operations
    Map<Serializable, Serializable> makeClone(Object object, IdFactory idFactory);
    Serializable getMinimalId(Comparator<Serializable> comparator);
}
```

### Data Types Stored

Workflow persists these types of objects:

| Type | Class | Key Format | Example Key |
|------|-------|------------|-------------|
| Workflow Definition | `WorkflowDefinition` | `journey-{caseId}` | `journey-order-123` |
| Workflow Info | `WorkflowInfo` | `workflow_process_info-{caseId}` | `workflow_process_info-order-123` |
| SLA Configuration | `List<Milestone>` | `journey_sla-{caseId}` | `journey_sla-order-123` |
| Audit Log | `Document` | `audit_log-{caseId}_{seq}_{step}` | `audit_log-order-123_00001_step_2` |

**Note**: The separator character (`-` in examples above) is configurable via `WorkflowService.init()`.

## Implementation Patterns

### Pattern 1: Document-Based Storage

Store objects as JSON documents:

```java
public class DocumentBasedDAO implements CommonService {
    
    @Override
    public void save(Serializable id, Object object) {
        // Convert object to JSON
        String json = toJson(object);
        
        // Store in your backend
        storage.put(id.toString(), json);
    }
    
    @Override
    public <T> T get(Class<T> objectClass, Serializable id) {
        // Retrieve JSON
        String json = storage.get(id.toString());
        if (json == null) return null;
        // Deserialize to object
        return fromJson(json, objectClass);
    }
    
    private String toJson(Object object) {
        // Use your JSON library
        return objectMapper.writeValueAsString(object);
    }
    
    private <T> T fromJson(String json, Class<T> clazz) {
        return objectMapper.readValue(json, clazz);
    }
}
```

### Pattern 2: Relational Mapping

Map workflow entities to database tables:

```java
public class JpaDAO implements CommonService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    @Transactional
    public void save(Serializable id, Object object) {
        // Workflow objects are already JPA entities
        entityManager.persist(object);
    }
    
    @Override
    @Transactional(readOnly = true)
    public <T> T get(Class<T> objectClass, Serializable id) {
        return entityManager.find(objectClass, id);
    }
}
```

### Pattern 3: Hybrid Approach

Combine document and relational storage:

```java
public class HybridDAO implements CommonService {
    
    private DocumentStore documentStore; // For workflow definitions
    private RelationalDB relationalDB;   // For structured queries
    
    @Override
    public void save(Serializable id, Object object) {
        if (object instanceof WorkflowDefinition) {
            // Store as document
            documentStore.save(id, object);
        } else if (object instanceof WorkflowInfo) {
            // Store in both for querying
            documentStore.save(id, object);
            relationalDB.saveMetadata(id, object);
        } else {
            documentStore.save(id, object);
        }
    }
}
```

## File-Based Implementation

The built-in `FileDao` implementation provides a reference:

### Directory Structure

```
workflow-data/
├── journey-order-123.json              # Workflow definition
├── workflow_process_info-order-123.json # Workflow state
├── journey_sla-order-123.json          # SLA config
├── audit_log-order-123_00001_step_1.json
├── audit_log-order-123_00002_step_2.json
└── counters.json                       # Counter storage
```

### Key Implementation Details

```java
public class FileDao implements CommonService {
    
    private final String storageDir;
    private final ObjectMapper mapper;
    
    public FileDao(String storageDir) {
        this.storageDir = storageDir;
        this.mapper = new ObjectMapper();
        createDirectoryIfNeeded();
    }
    
    @Override
    public void save(Serializable id, Object object) {
        File file = new File(storageDir, id.toString() + ".json");
        
        try {
            // Convert to JSON
            String json = mapper.writerWithDefaultPrettyPrinter()
                               .writeValueAsString(object);
            
            // Write atomically
            File tempFile = new File(file.getAbsolutePath() + ".tmp");
            Files.write(tempFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
            
            // Atomic rename
            Files.move(tempFile.toPath(), file.toPath(), 
                      StandardCopyOption.REPLACE_EXISTING,
                      StandardCopyOption.ATOMIC_MOVE);
                      
        } catch (IOException e) {
            throw new RuntimeException("Failed to save " + id, e);
        }
    }
    
    @Override
    public <T> T get(Class<T> objectClass, Serializable id) {
        File file = new File(storageDir, id.toString() + ".json");
        
        if (!file.exists()) {
            return null;
        }
        
        try {
            String json = new String(
                Files.readAllBytes(file.toPath()), 
                StandardCharsets.UTF_8
            );
            return mapper.readValue(json, objectClass);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + id, e);
        }
    }
    
    @Override
    public void delete(Serializable id) {
        File file = new File(storageDir, id.toString() + ".json");
        if (file.exists()) {
            file.delete();
        }
    }
    
    @Override
    public <T> List<T> getAll(Class<T> type) {
        File dir = new File(storageDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        
        if (files == null) {
            return Collections.emptyList();
        }
        
        List<T> results = new ArrayList<>();
        for (File file : files) {
            try {
                String json = new String(
                    Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8
                );
                T obj = mapper.readValue(json, type);
                results.add(obj);
            } catch (Exception e) {
                // Skip incompatible files
                log.warn("Cannot deserialize {} to {}", file.getName(), type);
            }
        }
        
        return results;
    }
    
    @Override
    public synchronized long incrCounter(String counterName) {
        File counterFile = new File(storageDir, "counters.json");
        Map<String, Long> counters = loadCounters(counterFile);
        
        Long current = counters.getOrDefault(counterName, 0L);
        Long next = current + 1;
        counters.put(counterName, next);
        
        saveCounters(counterFile, counters);
        return next;
    }
}
```

### Pros and Cons

**Pros**:
- Simple to understand and debug
- No database setup required
- Human-readable JSON files
- Easy backup (copy directory)

**Cons**:
- Not suitable for high concurrency
- No transactional guarantees
- Limited query capabilities
- File system I/O overhead

## RDBMS Implementation

### Using JPA/Hibernate

The provided `PostgresCommonDAO` shows how to implement with JPA:

```java
public class PostgresCommonDAO implements CommonService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    @Transactional
    public void save(Serializable id, Object object) {
        entityManager.persist(object);
        entityManager.flush(); // Ensure immediate persistence for crash recovery
    }
    
    @Override
    @Transactional
    public void update(Serializable id, Object object) {
        entityManager.merge(object);
        entityManager.flush();
    }
    
    @Override
    @Transactional
    public void saveOrUpdate(Serializable id, Object object) {
        if (entityManager.contains(object)) {
            entityManager.merge(object);
        } else {
            Object existing = entityManager.find(object.getClass(), id);
            if (existing != null) {
                entityManager.merge(object);
            } else {
                entityManager.persist(object);
            }
        }
        entityManager.flush();
    }
    
    @Override
    @Transactional
    public void delete(Serializable id) {
        // Note: Need to know the type - this is a limitation
        // Implementation would need type information
        Object obj = entityManager.find(Object.class, id);
        if (obj != null) {
            entityManager.remove(obj);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public <T> T get(Class<T> objectClass, Serializable id) {
        return entityManager.find(objectClass, id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public <T> T getLocked(Class<T> objectClass, Serializable id) {
        return entityManager.find(
            objectClass, 
            id, 
            LockModeType.PESSIMISTIC_WRITE
        );
    }
    
    @Override
    @Transactional
    public void saveCollection(Collection objects) {
        for (Object obj : objects) {
            entityManager.persist(obj);
        }
        entityManager.flush();
    }
    
    @Override
    @Transactional(readOnly = true)
    public <T> List<T> getAll(Class<T> type) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(type);
        Root<T> root = query.from(type);
        query.select(root);
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public <T> T getUniqueItem(Class<T> type, String keyName, String keyValue) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(type);
        Root<T> root = query.from(type);
        
        query.select(root)
             .where(cb.equal(root.get(keyName), keyValue));
        
        try {
            return entityManager.createQuery(query).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
    
    @Override
    @Transactional
    public synchronized long incrCounter(String counterName) {
        // Use database sequence or dedicated counter table
        String sql = "INSERT INTO workflow_counters (name, value) " +
                     "VALUES (:name, 1) " +
                     "ON CONFLICT (name) DO UPDATE " +
                     "SET value = workflow_counters.value + 1 " +
                     "RETURNING value";
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("name", counterName);
        
        return ((Number) query.getSingleResult()).longValue();
    }
}
```

### Database Schema

```sql
-- Counter table
CREATE TABLE workflow_counters (
    name VARCHAR(100) PRIMARY KEY,
    value BIGINT NOT NULL DEFAULT 0
);

-- Workflow entities use JPA annotations
-- The entity classes already have @Entity, @Table, etc.
```

### Configuration

```java
// persistence.xml
<persistence-unit name="workflow">
    <class>com.anode.workflow.entities.WorkflowDefinition</class>
    <class>com.anode.workflow.entities.WorkflowInfo</class>
    <class>com.anode.workflow.entities.ExecPath</class>
    <!-- ... other entities ... -->
    
    <properties>
        <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect"/>
        <property name="hibernate.hbm2ddl.auto" value="update"/>
        <property name="hibernate.show_sql" value="false"/>
    </properties>
</persistence-unit>
```

### Pros and Cons

**Pros**:
- ACID transactions
- Efficient querying
- Concurrent access support
- Battle-tested reliability
- Backup/restore tools

**Cons**:
- Requires database setup
- More complex configuration
- Schema management needed
- Potential performance overhead for simple cases

## NoSQL Implementation

### MongoDB Example

```java
public class MongoDAO implements CommonService {
    
    private final MongoDatabase database;
    private final String collectionName = "workflow_objects";
    
    public MongoDAO(MongoClient client, String dbName) {
        this.database = client.getDatabase(dbName);
    }
    
    @Override
    public void save(Serializable id, Object object) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        
        // Convert object to BSON document
        Document doc = new Document()
            .append("_id", id.toString())
            .append("_type", object.getClass().getName())
            .append("_data", toBson(object));
        
        collection.insertOne(doc);
    }
    
    @Override
    public void update(Serializable id, Object object) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        
        Document doc = new Document()
            .append("_type", object.getClass().getName())
            .append("_data", toBson(object));
        
        collection.updateOne(
            Filters.eq("_id", id.toString()),
            new Document("$set", doc)
        );
    }
    
    @Override
    public void saveOrUpdate(Serializable id, Object object) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        
        Document doc = new Document()
            .append("_id", id.toString())
            .append("_type", object.getClass().getName())
            .append("_data", toBson(object));
        
        collection.replaceOne(
            Filters.eq("_id", id.toString()),
            doc,
            new ReplaceOptions().upsert(true)
        );
    }
    
    @Override
    public <T> T get(Class<T> objectClass, Serializable id) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        
        Document doc = collection.find(Filters.eq("_id", id.toString())).first();
        if (doc == null) {
            return null;
        }
        
        return fromBson(doc.get("_data", Document.class), objectClass);
    }
    
    @Override
    public void delete(Serializable id) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        collection.deleteOne(Filters.eq("_id", id.toString()));
    }
    
    @Override
    public <T> List<T> getAll(Class<T> type) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        
        List<T> results = new ArrayList<>();
        
        collection.find(Filters.eq("_type", type.getName()))
                  .forEach(doc -> {
                      T obj = fromBson(doc.get("_data", Document.class), type);
                      results.add(obj);
                  });
        
        return results;
    }
    
    @Override
    public <T> T getUniqueItem(Class<T> type, String keyName, String keyValue) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        
        Document doc = collection.find(
            Filters.and(
                Filters.eq("_type", type.getName()),
                Filters.eq("_data." + keyName, keyValue)
            )
        ).first();
        
        if (doc == null) {
            return null;
        }
        
        return fromBson(doc.get("_data", Document.class), type);
    }
    
    @Override
    public synchronized long incrCounter(String counterName) {
        MongoCollection<Document> collection = database.getCollection("counters");
        
        Document result = collection.findOneAndUpdate(
            Filters.eq("_id", counterName),
            new Document("$inc", new Document("value", 1L)),
            new FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER)
        );
        
        return result.getLong("value");
    }
    
    @Override
    public <T> T getLocked(Class<T> objectClass, Serializable id) {
        // MongoDB doesn't have traditional row-level locking
        // Use optimistic locking with version field or
        // implement distributed lock
        return get(objectClass, id);
    }
    
    private Document toBson(Object object) {
        // Serialize to BSON
        String json = objectMapper.writeValueAsString(object);
        return Document.parse(json);
    }
    
    private <T> T fromBson(Document doc, Class<T> clazz) {
        String json = doc.toJson();
        return objectMapper.readValue(json, clazz);
    }
}
```

### Indexes

```javascript
// Create indexes for efficient queries
db.workflow_objects.createIndex({ "_id": 1 });
db.workflow_objects.createIndex({ "_type": 1 });
db.workflow_objects.createIndex({ "_type": 1, "_data.caseId": 1 });
db.counters.createIndex({ "_id": 1 });
```

### Pros and Cons

**Pros**:
- Flexible schema
- Horizontal scalability
- Good for document-heavy workflows
- Fast reads/writes

**Cons**:
- Eventual consistency (in some setups)
- Limited transaction support (varies by DB)
- Locking challenges
- Query complexity for relational patterns

## Cloud Storage Implementation

### AWS DynamoDB Example

```java
public class DynamoDBDAO implements CommonService {
    
    private final DynamoDbClient dynamoDb;
    private final String tableName = "workflow_objects";
    
    public DynamoDBDAO(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }
    
    @Override
    public void save(Serializable id, Object object) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id.toString()).build());
        item.put("type", AttributeValue.builder().s(object.getClass().getName()).build());
        item.put("data", AttributeValue.builder().s(toJson(object)).build());
        item.put("timestamp", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());
        
        PutItemRequest request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build();
        
        dynamoDb.putItem(request);
    }
    
    @Override
    public void saveOrUpdate(Serializable id, Object object) {
        // DynamoDB put is always upsert
        save(id, object);
    }
    
    @Override
    public <T> T get(Class<T> objectClass, Serializable id) {
        GetItemRequest request = GetItemRequest.builder()
            .tableName(tableName)
            .key(Map.of("id", AttributeValue.builder().s(id.toString()).build()))
            .build();
        
        GetItemResponse response = dynamoDb.getItem(request);
        
        if (!response.hasItem()) {
            return null;
        }
        
        String json = response.item().get("data").s();
        return fromJson(json, objectClass);
    }
    
    @Override
    public void delete(Serializable id) {
        DeleteItemRequest request = DeleteItemRequest.builder()
            .tableName(tableName)
            .key(Map.of("id", AttributeValue.builder().s(id.toString()).build()))
            .build();
        
        dynamoDb.deleteItem(request);
    }
    
    @Override
    public <T> List<T> getAll(Class<T> type) {
        // Use Query or Scan with filter
        ScanRequest request = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression("#type = :typeValue")
            .expressionAttributeNames(Map.of("#type", "type"))
            .expressionAttributeValues(Map.of(
                ":typeValue", AttributeValue.builder().s(type.getName()).build()
            ))
            .build();
        
        ScanResponse response = dynamoDb.scan(request);
        
        List<T> results = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            String json = item.get("data").s();
            results.add(fromJson(json, type));
        }
        
        return results;
    }
    
    @Override
    public synchronized long incrCounter(String counterName) {
        UpdateItemRequest request = UpdateItemRequest.builder()
            .tableName("workflow_counters")
            .key(Map.of("name", AttributeValue.builder().s(counterName).build()))
            .updateExpression("ADD #val :inc")
            .expressionAttributeNames(Map.of("#val", "value"))
            .expressionAttributeValues(Map.of(
                ":inc", AttributeValue.builder().n("1").build()
            ))
            .returnValues(ReturnValue.UPDATED_NEW)
            .build();
        
        UpdateItemResponse response = dynamoDb.updateItem(request);
        return Long.parseLong(response.attributes().get("value").n());
    }
}
```

### Table Schema

```json
{
  "TableName": "workflow_objects",
  "KeySchema": [
    { "AttributeName": "id", "KeyType": "HASH" }
  ],
  "AttributeDefinitions": [
    { "AttributeName": "id", "AttributeType": "S" },
    { "AttributeName": "type", "AttributeType": "S" }
  ],
  "GlobalSecondaryIndexes": [
    {
      "IndexName": "type-index",
      "KeySchema": [
        { "AttributeName": "type", "KeyType": "HASH" }
      ],
      "Projection": { "ProjectionType": "ALL" }
    }
  ]
}
```

## Testing Your DAO

### Unit Tests

```java
@Test
public void testSaveAndGet() {
    CommonService dao = new YourCustomDAO();
    
    // Create test workflow info
    WorkflowInfo info = new WorkflowInfo();
    info.setCaseId("test-123");
    info.setIsComplete(false);
    
    String id = "workflow_process_info-test-123";
    
    // Save
    dao.save(id, info);
    
    // Retrieve
    WorkflowInfo retrieved = dao.get(WorkflowInfo.class, id);
    
    assertNotNull(retrieved);
    assertEquals("test-123", retrieved.getCaseId());
    assertFalse(retrieved.getIsComplete());
}

@Test
public void testSaveOrUpdate() {
    CommonService dao = new YourCustomDAO();
    String id = "workflow_process_info-test-456";
    
    // First save
    WorkflowInfo info1 = new WorkflowInfo();
    info1.setCaseId("test-456");
    info1.setIsComplete(false);
    dao.saveOrUpdate(id, info1);
    
    // Update
    WorkflowInfo info2 = new WorkflowInfo();
    info2.setCaseId("test-456");
    info2.setIsComplete(true);
    dao.saveOrUpdate(id, info2);
    
    // Verify update
    WorkflowInfo retrieved = dao.get(WorkflowInfo.class, id);
    assertTrue(retrieved.getIsComplete());
}

@Test
public void testCounterIncrement() {
    CommonService dao = new YourCustomDAO();
    
    long val1 = dao.incrCounter("test_counter");
    long val2 = dao.incrCounter("test_counter");
    long val3 = dao.incrCounter("test_counter");
    
    assertEquals(val1 + 1, val2);
    assertEquals(val2 + 1, val3);
}

@Test
public void testConcurrentAccess() throws InterruptedException {
    CommonService dao = new YourCustomDAO();
    int threadCount = 10;
    CountDownLatch latch = new CountDownLatch(threadCount);
    
    for (int i = 0; i < threadCount; i++) {
        final int index = i;
        new Thread(() -> {
            try {
                WorkflowInfo info = new WorkflowInfo();
                info.setCaseId("concurrent-" + index);
                dao.save("workflow_process_info-concurrent-" + index, info);
            } finally {
                latch.countDown();
            }
        }).start();
    }
    
    latch.await(10, TimeUnit.SECONDS);
    
    // Verify all saves succeeded
    for (int i = 0; i < threadCount; i++) {
        WorkflowInfo retrieved = dao.get(
            WorkflowInfo.class,
            "workflow_process_info-concurrent-" + i
        );
        assertNotNull(retrieved);
    }
}
```

### Integration Tests

```java
@Test
public void testWithActualWorkflow() {
    // Initialize workflow with custom DAO
    WorkflowService.init(10, 30000, "-");
    YourCustomDAO dao = new YourCustomDAO();
    
    WorkflowComponantFactory factory = new TestComponentFactory();
    EventHandler handler = new TestEventHandler();
    
    RuntimeService rts = WorkflowService.instance()
        .getRunTimeService(dao, factory, handler, null);
    
    // Load workflow definition
    String json = loadTestWorkflow();
    
    // Start case
    rts.startCase("integration-test-1", json, null, null);
    
    // Verify workflow info persisted
    WorkflowInfo info = dao.get(
        WorkflowInfo.class,
        "workflow_process_info-integration-test-1"
    );
    
    assertNotNull(info);
    assertEquals("integration-test-1", info.getCaseId());
}

@Test
public void testCrashRecovery() {
    YourCustomDAO dao = new YourCustomDAO();
    
    // Start workflow
    RuntimeService rts = createRuntimeService(dao);
    rts.startCase("crash-test-1", workflowJson, null, null);
    
    // Simulate crash - don't complete workflow
    // Just abandon the runtime service
    
    // Create new runtime service (simulating restart)
    RuntimeService newRts = createRuntimeService(dao);
    
    // Resume should work
    newRts.resumeCase("crash-test-1");
    
    // Verify recovery
    WorkflowInfo info = dao.get(
        WorkflowInfo.class,
        "workflow_process_info-crash-test-1"
    );
    assertNotNull(info);
}
```

## Best Practices

### 1. Atomic Writes

```java
// ✅ Good: Atomic write
public void save(Serializable id, Object object) {
    File temp = new File(path + ".tmp");
    writeToFile(temp, object);
    Files.move(temp.toPath(), new File(path).toPath(), ATOMIC_MOVE);
}

// ❌ Bad: Non-atomic write
public void save(Serializable id, Object object) {
    File file = new File(path);
    writeToFile(file, object); // Can be corrupted if crash happens during write
}
```

### 2. Handle Serialization Errors

```java
@Override
public void save(Serializable id, Object object) {
    try {
        String json = mapper.writeValueAsString(object);
        storage.put(id.toString(), json);
    } catch (JsonProcessingException e) {
        throw new WorkflowRuntimeException(
            "Cannot serialize object with id: " + id,
            e
        );
    }
}
```

### 3. Implement Proper Locking

```java
@Override
public <T> T getLocked(Class<T> objectClass, Serializable id) {
    // Use database locking
    return entityManager.find(objectClass, id, LockModeType.PESSIMISTIC_WRITE);
    
    // Or implement distributed lock
    Lock lock = lockService.acquire(id.toString());
    try {
        return get(objectClass, id);
    } finally {
        lock.release();
    }
}
```

### 4. Optimize for Workflow Access Patterns

```java
// Workflow primarily does:
// 1. Get workflow info by case ID (frequent)
// 2. Get workflow definition by case ID (frequent)
// 3. List all cases (rare, admin only)
// 4. Get by unique key (occasional)

// Optimize for #1 and #2
@Override
public <T> T get(Class<T> objectClass, Serializable id) {
    // Use cache for frequent reads
    String cacheKey = objectClass.getName() + ":" + id;
    T cached = cache.get(cacheKey);
    if (cached != null) {
        return cached;
    }
    
    T object = readFromStorage(objectClass, id);
    cache.put(cacheKey, object);
    return object;
}
```

### 5. Flush Immediately for Crash Recovery

```java
@Override
@Transactional
public void save(Serializable id, Object object) {
    entityManager.persist(object);
    entityManager.flush(); // Critical for crash recovery!
}
```

### 6. Version Your Data Format

```java
// Include version in saved data
class SerializedData {
    String version = "1.0";
    Object data;
}

// Handle migrations
@Override
public <T> T get(Class<T> objectClass, Serializable id) {
    SerializedData serialized = readFromStorage(id);
    
    if ("1.0".equals(serialized.version)) {
        return deserializeV1(serialized.data, objectClass);
    } else if ("2.0".equals(serialized.version)) {
        return deserializeV2(serialized.data, objectClass);
    }
    
    throw new IllegalStateException("Unknown version: " + serialized.version);
}
```

## Performance Considerations

### Benchmark Your Implementation

```java
public class DAOBenchmark {
    
    @Test
    public void benchmarkSave() {
        CommonService dao = new YourCustomDAO();
        int iterations = 1000;
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < iterations; i++) {
            WorkflowInfo info = createTestWorkflowInfo(i);
            dao.save("workflow_process_info-bench-" + i, info);
        }
        
        long duration = System.currentTimeMillis() - start;
        double opsPerSecond = (iterations * 1000.0) / duration;
        
        System.out.printf("Save: %.2f ops/sec%n", opsPerSecond);
    }
    
    @Test
    public void benchmarkGet() {
        // Similar benchmark for get operations
    }
}
```

### Expected Performance Targets

| Operation | Target | Notes |
|-----------|--------|-------|
| Save | > 100/sec | For aggressive persistence mode |
| Get | > 500/sec | Frequent during workflow execution |
| SaveOrUpdate | > 100/sec | Similar to save |
| Counter Increment | > 1000/sec | Used for audit log sequencing |

### Optimization Techniques

```java
// 1. Batch operations
@Override
public void saveCollection(Collection objects) {
    // Batch instead of individual saves
    executeBatch(objects);
}

// 2. Connection pooling
HikariConfig config = new HikariConfig();
config.setMaximumPoolSize(20);
config.setMinimumIdle(5);
DataSource dataSource = new HikariDataSource(config);

// 3. Async writes (if appropriate)
private ExecutorService asyncWriter = Executors.newSingleThreadExecutor();

@Override
public void save(Serializable id, Object object) {
    // Synchronous for crash recovery
    if (isWorkflowInfo(object)) {
        saveSync(id, object);
    } else {
        // Async for audit logs
        asyncWriter.submit(() -> saveSync(id, object));
    }
}
```

## Related Documentation

- [Crash Recovery](./crash-recovery.md)
- [PostgreSQL Setup](./postgres-setup.md)
- [Performance Tuning](../operations/performance.md)

## FAQ

**Q: Can I use multiple DAOs in the same application?**  
A: No, workflow uses a single DAO instance per RuntimeService.

**Q: Does my DAO need to support transactions?**  
A: Not required, but recommended for ACID guarantees.

**Q: Can I change DAO implementations after deployment?**  
A: Yes, but you need to migrate data to the new storage format.

**Q: How do I handle schema changes?**  
A: Implement versioning in your serialization/deserialization logic and provide migration paths.

**Q: Is caching safe with workflow?**  
A: Be careful with caching workflow info - it changes frequently. Cache only workflow definitions (immutable).

**Q: What happens if DAO operations fail?**  
A: Workflow throws `WorkflowRuntimeException`. The operation should be retried or case should be marked for manual intervention.

**Q: Can I use read replicas?**  
A: Not for writes. Reads can use replicas if replication lag is acceptable (usually not for active workflows).

## Troubleshooting

### Issue: Data Not Persisting

**Symptoms**: Workflow state lost after restart

**Causes**:
- Forget to call `flush()` in JPA
- Not using atomic writes in file-based storage
- Transaction not committed

**Solutions**:
```java
// Ensure flush in JPA
@Override
public void save(Serializable id, Object object) {
    entityManager.persist(object);
    entityManager.flush(); // Add this!
}

// Ensure commit in JDBC
@Override
public void save(Serializable id, Object object) {
    // ... save logic
    connection.commit(); // Add this!
}

// Use atomic writes for files
Files.move(tempFile, targetFile, ATOMIC_MOVE);
```

### Issue: Corrupted Data After Crash

**Symptoms**: Cannot deserialize workflow info, JSON parse errors

**Causes**:
- Non-atomic writes
- Partial writes during crash
- Concurrent modifications

**Solutions**:
```java
// Use write-ahead log pattern
public void save(Serializable id, Object object) {
    // 1. Write to temporary location
    File tempFile = new File(path + ".tmp");
    writeObject(tempFile, object);
    
    // 2. Fsync to ensure on disk
    FileChannel channel = new FileOutputStream(tempFile).getChannel();
    channel.force(true);
    
    // 3. Atomic rename
    Files.move(tempFile.toPath(), new File(path).toPath(), ATOMIC_MOVE);
}
```

### Issue: Poor Performance Under Load

**Symptoms**: Workflow execution slows down significantly

**Diagnosis**:
```java
// Add timing logs
@Override
public void save(Serializable id, Object object) {
    long start = System.currentTimeMillis();
    try {
        actualSave(id, object);
    } finally {
        long duration = System.currentTimeMillis() - start;
        if (duration > 100) {
            log.warn("Slow save for {}: {}ms", id, duration);
        }
    }
}
```

**Solutions**:
- Add connection pooling
- Use batch operations where possible
- Add caching for read-heavy operations
- Optimize indexes in database
- Consider async writes for audit logs

### Issue: Locking Conflicts

**Symptoms**: Deadlocks, timeout exceptions

**Causes**:
- Incorrect lock ordering
- Holding locks too long
- No lock timeout configured

**Solutions**:
```java
// Set lock timeout
@Override
public <T> T getLocked(Class<T> objectClass, Serializable id) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("javax.persistence.lock.timeout", 5000); // 5 seconds
    
    return entityManager.find(
        objectClass, 
        id, 
        LockModeType.PESSIMISTIC_WRITE,
        properties
    );
}

// Use consistent lock ordering
public void lockMultiple(List<String> ids) {
    // Always lock in sorted order to prevent deadlocks
    Collections.sort(ids);
    for (String id : ids) {
        getLocked(WorkflowInfo.class, id);
    }
}
```

## Example: Complete Redis Implementation

Here's a complete example using Redis for high-performance caching:

```java
public class RedisDAO implements CommonService {
    
    private final RedissonClient redisson;
    private final ObjectMapper mapper;
    
    public RedisDAO(String redisUrl) {
        Config config = new Config();
        config.useSingleServer().setAddress(redisUrl);
        this.redisson = Redisson.create(config);
        this.mapper = new ObjectMapper();
    }
    
    @Override
    public void save(Serializable id, Object object) {
        RBucket<String> bucket = redisson.getBucket(id.toString());
        try {
            String json = mapper.writeValueAsString(object);
            bucket.set(json);
        } catch (JsonProcessingException e) {
            throw new WorkflowRuntimeException("Cannot serialize: " + id, e);
        }
    }
    
    @Override
    public void update(Serializable id, Object object) {
        save(id, object); // Redis SET is always upsert
    }
    
    @Override
    public void saveOrUpdate(Serializable id, Object object) {
        save(id, object);
    }
    
    @Override
    public void delete(Serializable id) {
        RBucket<String> bucket = redisson.getBucket(id.toString());
        bucket.delete();
    }
    
    @Override
    public <T> T get(Class<T> objectClass, Serializable id) {
        RBucket<String> bucket = redisson.getBucket(id.toString());
        String json = bucket.get();
        
        if (json == null) {
            return null;
        }
        
        try {
            return mapper.readValue(json, objectClass);
        } catch (JsonProcessingException e) {
            throw new WorkflowRuntimeException("Cannot deserialize: " + id, e);
        }
    }
    
    @Override
    public <T> T getLocked(Class<T> objectClass, Serializable id) {
        RLock lock = redisson.getLock("lock:" + id);
        
        try {
            // Try to acquire lock with timeout
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new WorkflowRuntimeException("Cannot acquire lock: " + id);
            }
            
            return get(objectClass, id);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkflowRuntimeException("Lock interrupted: " + id, e);
        }
        // Note: Lock must be released by caller
    }
    
    @Override
    public void saveCollection(Collection objects) {
        RBatch batch = redisson.createBatch();
        
        for (Object obj : objects) {
            try {
                String id = extractId(obj);
                String json = mapper.writeValueAsString(obj);
                batch.getBucket(id).setAsync(json);
            } catch (Exception e) {
                throw new WorkflowRuntimeException("Batch save failed", e);
            }
        }
        
        batch.execute();
    }
    
    @Override
    public void saveOrUpdateCollection(Collection objects) {
        saveCollection(objects); // Same as save for Redis
    }
    
    @Override
    public <T> List<T> getAll(Class<T> type) {
        // Use Redis SCAN to avoid blocking
        List<T> results = new ArrayList<>();
        RKeys keys = redisson.getKeys();
        
        // This is expensive - consider maintaining an index
        Iterable<String> allKeys = keys.getKeysByPattern("*");
        
        for (String key : allKeys) {
            try {
                T obj = get(type, key);
                if (obj != null) {
                    results.add(obj);
                }
            } catch (Exception e) {
                // Skip incompatible keys
                log.debug("Cannot deserialize {} to {}", key, type.getName());
            }
        }
        
        return results;
    }
    
    @Override
    public <T> T getUniqueItem(Class<T> type, String keyName, String keyValue) {
        // Redis doesn't have secondary indexes by default
        // Options:
        // 1. Maintain separate index manually
        // 2. Use RedisSearch module
        // 3. Scan all keys (slow)
        
        // Example: Manual index
        String indexKey = "index:" + type.getName() + ":" + keyName + ":" + keyValue;
        RBucket<String> indexBucket = redisson.getBucket(indexKey);
        String objectId = indexBucket.get();
        
        if (objectId == null) {
            return null;
        }
        
        return get(type, objectId);
    }
    
    @Override
    public long incrCounter(String counterName) {
        RAtomicLong counter = redisson.getAtomicLong("counter:" + counterName);
        return counter.incrementAndGet();
    }
    
    @Override
    public Map<Serializable, Serializable> makeClone(Object object, IdFactory idFactory) {
        // Default implementation - override if needed
        throw new UnsupportedOperationException("Clone not implemented for Redis DAO");
    }
    
    @Override
    public Serializable getMinimalId(Comparator<Serializable> comparator) {
        // Default implementation - override if needed
        throw new UnsupportedOperationException("getMinimalId not implemented for Redis DAO");
    }
    
    // Helper method to extract ID from object
    private String extractId(Object obj) throws Exception {
        // Use reflection or known patterns
        if (obj instanceof WorkflowInfo) {
            return "workflow_process_info-" + ((WorkflowInfo) obj).getCaseId();
        } else if (obj instanceof WorkflowDefinition) {
            return "journey-" + ((WorkflowDefinition) obj).getCaseId();
        }
        throw new IllegalArgumentException("Cannot extract ID from: " + obj.getClass());
    }
    
    // Cleanup
    public void shutdown() {
        redisson.shutdown();
    }
}
```

### Using the Redis DAO

```java
// Initialize
RedisDAO dao = new RedisDAO("redis://localhost:6379");

WorkflowService.init(10, 30000, "-");
RuntimeService rts = WorkflowService.instance()
    .getRunTimeService(dao, factory, handler, null);

// Use normally
rts.startCase("test-123", workflowJson, null, null);

// Cleanup on shutdown
dao.shutdown();
```

## Migration Guide

### Migrating Between DAOs

```java
public class DAOMigration {
    
    public static void migrate(
        CommonService sourceDAO,
        CommonService targetDAO,
        List<String> caseIds
    ) {
        log.info("Starting migration of {} cases", caseIds.size());
        
        for (String caseId : caseIds) {
            try {
                migrateCaseData(caseId, sourceDAO, targetDAO);
                log.info("Migrated case: {}", caseId);
            } catch (Exception e) {
                log.error("Failed to migrate case {}: {}", caseId, e.getMessage());
            }
        }
        
        log.info("Migration complete");
    }
    
    private static void migrateCaseData(
        String caseId,
        CommonService source,
        CommonService target
    ) {
        // 1. Migrate workflow definition
        String defKey = "journey-" + caseId;
        WorkflowDefinition def = source.get(WorkflowDefinition.class, defKey);
        if (def != null) {
            target.save(defKey, def);
        }
        
        // 2. Migrate workflow info
        String infoKey = "workflow_process_info-" + caseId;
        WorkflowInfo info = source.get(WorkflowInfo.class, infoKey);
        if (info != null) {
            target.save(infoKey, info);
        }
        
        // 3. Migrate SLA config
        String slaKey = "journey_sla-" + caseId;
        Object sla = source.get(Object.class, slaKey);
        if (sla != null) {
            target.save(slaKey, sla);
        }
        
        // 4. Migrate audit logs (optional - can be large)
        // migrateAuditLogs(caseId, source, target);
    }
    
    @SuppressWarnings("unchecked")
    private static void migrateAuditLogs(
        String caseId,
        CommonService source,
        CommonService target
    ) {
        // Audit logs use pattern: audit_log-{caseId}_{seq}_{step}
        // This requires listing all keys - implementation depends on DAO
        
        // Example for file-based source:
        if (source instanceof FileDao) {
            File dir = new File(((FileDao) source).getStorageDir());
            File[] files = dir.listFiles((d, name) -> 
                name.startsWith("audit_log-" + caseId)
            );
            
            if (files != null) {
                for (File file : files) {
                    String key = file.getName().replace(".json", "");
                    Object log = source.get(Object.class, key);
                    target.save(key, log);
                }
            }
        }
    }
}
```

### Usage Example

```java
// Migrate from file to PostgreSQL
FileDao fileDao = new FileDao("./workflow-data");
PostgresCommonDAO postgresDao = new PostgresCommonDAO(entityManager);

List<String> caseIds = Arrays.asList("order-123", "order-456", "order-789");
DAOMigration.migrate(fileDao, postgresDao, caseIds);
```

## Summary

Implementing a custom DAO allows you to:

✅ Use your preferred storage technology  
✅ Integrate with existing infrastructure  
✅ Optimize for your specific use case  
✅ Scale according to your needs  

**Key Requirements**:
1. Implement all `CommonService` methods
2. Ensure atomic writes for crash recovery
3. Support concurrent access
4. Provide adequate performance
5. Handle serialization robustly

**Recommended Implementations by Use Case**:

| Use Case | Recommended DAO | Why |
|----------|----------------|-----|
| Getting started | FileDao | Simple, no setup |
| Production single-node | PostgreSQL + JPA | ACID, reliable |
| High throughput | Redis + PostgreSQL | Fast + durable |
| Multi-region | DynamoDB/Cosmos DB | Global distribution |
| Existing MongoDB | MongoDB DAO | Leverage existing infrastructure |
| Microservices | HTTP/REST DAO | Service-oriented |

Choose the implementation that best fits your requirements and infrastructure.
