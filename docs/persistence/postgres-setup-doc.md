# PostgreSQL Setup Guide

## Overview

This guide covers setting up PostgreSQL as the persistence layer for Simple Workflow using the provided `PostgresCommonDAO` implementation.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Database Setup](#database-setup)
- [Schema Configuration](#schema-configuration)
- [JPA Configuration](#jpa-configuration)
- [Connection Pooling](#connection-pooling)
- [Initialization](#initialization)
- [Performance Tuning](#performance-tuning)
- [Backup and Recovery](#backup-and-recovery)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Software Requirements

- PostgreSQL 12+ (recommended: 14 or later)
- Java 17+
- Maven or Gradle build tool

### Dependencies

Add to your `pom.xml`:

```xml
<dependencies>
    <!-- Simple Workflow -->
    <dependency>
        <groupId>com.anode</groupId>
        <artifactId>workflow</artifactId>
        <version>0.0.1</version>
    </dependency>
    
    <!-- JPA / Hibernate -->
    <dependency>
        <groupId>org.hibernate</groupId>
        <artifactId>hibernate-core</artifactId>
        <version>6.2.7.Final</version>
    </dependency>
    
    <!-- PostgreSQL JDBC Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.6.0</version>
    </dependency>
    
    <!-- Connection Pooling (HikariCP) -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>5.0.1</version>
    </dependency>
    
    <!-- JPA API -->
    <dependency>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
        <version>3.1.0</version>
    </dependency>
    
    <!-- Transaction API -->
    <dependency>
        <groupId>jakarta.transaction</groupId>
        <artifactId>jakarta.transaction-api</artifactId>
        <version>2.0.1</version>
    </dependency>
</dependencies>
```

Or for Gradle (`build.gradle`):

```gradle
dependencies {
    implementation 'com.anode:workflow:0.0.1'
    implementation 'org.hibernate:hibernate-core:6.2.7.Final'
    implementation 'org.postgresql:postgresql:42.6.0'
    implementation 'com.zaxxer:HikariCP:5.0.1'
    implementation 'jakarta.persistence:jakarta.persistence-api:3.1.0'
    implementation 'jakarta.transaction:jakarta.transaction-api:2.0.1'
}
```

## Database Setup

### 1. Install PostgreSQL

**Ubuntu/Debian**:
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
```

**macOS** (using Homebrew):
```bash
brew install postgresql
brew services start postgresql
```

**Windows**:
Download installer from [postgresql.org](https://www.postgresql.org/download/windows/)

### 2. Create Database and User

```bash
# Connect as postgres user
sudo -u postgres psql

# Or on macOS/Windows
psql -U postgres
```

```sql
-- Create database
CREATE DATABASE workflow_db;

-- Create user
CREATE USER workflow_user WITH PASSWORD 'your_secure_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE workflow_db TO workflow_user;

-- Connect to the database
\c workflow_db

-- Grant schema privileges (PostgreSQL 15+)
GRANT ALL ON SCHEMA public TO workflow_user;
GRANT ALL ON ALL TABLES IN SCHEMA public TO workflow_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO workflow_user;

-- Set default privileges for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
GRANT ALL ON TABLES TO workflow_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public 
GRANT ALL ON SEQUENCES TO workflow_user;

-- Exit
\q
```

### 3. Verify Connection

```bash
psql -h localhost -U workflow_user -d workflow_db
```

## Schema Configuration

### Option 1: Automatic Schema Generation (Development)

Hibernate can automatically create the schema from JPA entities.

**persistence.xml**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence
             http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd"
             version="2.2">
    
    <persistence-unit name="workflow-pu" transaction-type="RESOURCE_LOCAL">
        <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
        
        <!-- Workflow entity classes -->
        <class>com.anode.workflow.entities.WorkflowDefinition</class>
        <class>com.anode.workflow.entities.WorkflowInfo</class>
        <class>com.anode.workflow.entities.ExecPath</class>
        <class>com.anode.workflow.entities.WorkflowVariables</class>
        <class>com.anode.workflow.entities.ProcessVariableValue</class>
        <class>com.anode.workflow.entities.Step</class>
        <class>com.anode.workflow.entities.Route</class>
        <class>com.anode.workflow.entities.RouteBranch</class>
        <class>com.anode.workflow.entities.Milestone</class>
        
        <properties>
            <!-- Database connection -->
            <property name="jakarta.persistence.jdbc.url" 
                      value="jdbc:postgresql://localhost:5432/workflow_db"/>
            <property name="jakarta.persistence.jdbc.user" 
                      value="workflow_user"/>
            <property name="jakarta.persistence.jdbc.password" 
                      value="your_secure_password"/>
            <property name="jakarta.persistence.jdbc.driver" 
                      value="org.postgresql.Driver"/>
            
            <!-- Hibernate settings -->
            <property name="hibernate.dialect" 
                      value="org.hibernate.dialect.PostgreSQLDialect"/>
            <property name="hibernate.hbm2ddl.auto" 
                      value="update"/>  <!-- create, update, validate, none -->
            <property name="hibernate.show_sql" 
                      value="false"/>
            <property name="hibernate.format_sql" 
                      value="true"/>
            
            <!-- Performance settings -->
            <property name="hibernate.jdbc.batch_size" 
                      value="20"/>
            <property name="hibernate.order_inserts" 
                      value="true"/>
            <property name="hibernate.order_updates" 
                      value="true"/>
            
            <!-- Cache settings -->
            <property name="hibernate.cache.use_second_level_cache" 
                      value="false"/>
            <property name="hibernate.cache.use_query_cache" 
                      value="false"/>
        </properties>
    </persistence-unit>
</persistence>
```

### Option 2: Manual Schema Creation (Production)

For production, create schema explicitly:

```sql
-- workflow_definition table
CREATE TABLE workflow_definition (
    hibid VARCHAR(255) PRIMARY KEY,
    case_id VARCHAR(255) NOT NULL,
    ts BIGINT,
    def_name VARCHAR(255),
    def_version VARCHAR(50),
    flow_json TEXT
);

CREATE INDEX idx_wf_def_case_id ON workflow_definition(case_id);

-- workflow_info table
CREATE TABLE workflow_info (
    hibid VARCHAR(255) PRIMARY KEY,
    case_id VARCHAR(255) NOT NULL UNIQUE,
    ts BIGINT,
    is_complete BOOLEAN DEFAULT FALSE,
    pend_exec_path VARCHAR(500),
    last_executed_step VARCHAR(255),
    last_executed_comp_name VARCHAR(255),
    ticket VARCHAR(255)
);

CREATE INDEX idx_wf_info_case_id ON workflow_info(case_id);
CREATE INDEX idx_wf_info_complete ON workflow_info(is_complete);
CREATE INDEX idx_wf_info_ts ON workflow_info(ts);

-- exec_path table
CREATE TABLE exec_path (
    id BIGSERIAL PRIMARY KEY,
    workflow_info_hibid VARCHAR(255) REFERENCES workflow_info(hibid),
    name VARCHAR(500) NOT NULL,
    status VARCHAR(50),
    step VARCHAR(255),
    unit_response_type VARCHAR(50),
    pend_workbasket VARCHAR(255),
    prev_pend_workbasket VARCHAR(255),
    error_code INTEGER,
    error_desc TEXT
);

CREATE INDEX idx_exec_path_wf_info ON exec_path(workflow_info_hibid);
CREATE INDEX idx_exec_path_status ON exec_path(status);

-- process_variable_value table
CREATE TABLE process_variable_value (
    id BIGSERIAL PRIMARY KEY,
    workflow_variables_id BIGINT,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    value_string TEXT,
    value_long BIGINT,
    value_boolean BOOLEAN,
    value_double DOUBLE PRECISION
);

CREATE INDEX idx_pv_wf_vars ON process_variable_value(workflow_variables_id);
CREATE INDEX idx_pv_name ON process_variable_value(name);

-- workflow_variables table
CREATE TABLE workflow_variables (
    id BIGSERIAL PRIMARY KEY,
    workflow_info_hibid VARCHAR(255) REFERENCES workflow_info(hibid)
);

-- step table
CREATE TABLE step (
    id BIGSERIAL PRIMARY KEY,
    workflow_definition_hibid VARCHAR(255) REFERENCES workflow_definition(hibid),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    next_step VARCHAR(255),
    component_name VARCHAR(255)
);

-- route table
CREATE TABLE route (
    id BIGSERIAL PRIMARY KEY,
    workflow_definition_hibid VARCHAR(255) REFERENCES workflow_definition(hibid),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    component_name VARCHAR(255),
    join_name VARCHAR(255)
);

-- route_branch table
CREATE TABLE route_branch (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT REFERENCES route(id),
    name VARCHAR(255) NOT NULL,
    next_step VARCHAR(255)
);

-- milestone table (SLA)
CREATE TABLE milestone (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    target_ts BIGINT,
    actual_ts BIGINT,
    status VARCHAR(50),
    sla_minutes INTEGER
);

CREATE INDEX idx_milestone_case_id ON milestone(case_id);
CREATE INDEX idx_milestone_status ON milestone(status);

-- counters table
CREATE TABLE workflow_counters (
    name VARCHAR(100) PRIMARY KEY,
    value BIGINT NOT NULL DEFAULT 0
);

-- audit log can be stored separately or in JSON format
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(255) NOT NULL,
    sequence INTEGER NOT NULL,
    step_name VARCHAR(255),
    ts BIGINT,
    log_data JSONB,
    UNIQUE(case_id, sequence, step_name)
);

CREATE INDEX idx_audit_case_id ON audit_log(case_id);
CREATE INDEX idx_audit_log_data ON audit_log USING GIN (log_data);
```

## JPA Configuration

### Create EntityManagerFactory

```java
public class JPAConfig {
    
    private static EntityManagerFactory emf;
    
    public static EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            emf = Persistence.createEntityManagerFactory("workflow-pu");
        }
        return emf;
    }
    
    public static EntityManager createEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }
    
    public static void closeEntityManagerFactory() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
```

### With HikariCP Connection Pooling

```java
public class JPAConfigWithPooling {
    
    private static EntityManagerFactory emf;
    
    public static EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            // Configure HikariCP
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:postgresql://localhost:5432/workflow_db");
            hikariConfig.setUsername("workflow_user");
            hikariConfig.setPassword("your_secure_password");
            hikariConfig.setDriverClassName("org.postgresql.Driver");
            
            // Pool settings
            hikariConfig.setMaximumPoolSize(20);
            hikariConfig.setMinimumIdle(5);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            
            // PostgreSQL optimizations
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            
            HikariDataSource dataSource = new HikariDataSource(hikariConfig);
            
            // Create EntityManagerFactory with HikariCP
            Map<String, Object> properties = new HashMap<>();
            properties.put("hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
            properties.put("hibernate.hikari.dataSource", dataSource);
            properties.put("hibernate.dialect", 
                "org.hibernate.dialect.PostgreSQLDialect");
            properties.put("hibernate.show_sql", "false");
            properties.put("hibernate.hbm2ddl.auto", "update");
            
            emf = Persistence.createEntityManagerFactory("workflow-pu", properties);
        }
        return emf;
    }
}
```

## Initialization

### Complete Setup Example

```java
public class WorkflowPostgresSetup {
    
    private EntityManagerFactory emf;
    private RuntimeService runtimeService;
    
    public void initialize() {
        // 1. Initialize JPA
        emf = JPAConfigWithPooling.getEntityManagerFactory();
        
        // 2. Initialize Workflow Service
        WorkflowService.init(
            10,      // Max threads for parallel processing
            30000,   // Thread join timeout (ms)
            "-"      // Key separator character
        );
        
        // 3. Create DAO
        EntityManager em = emf.createEntityManager();
        PostgresCommonDAO dao = new PostgresCommonDAO();
        dao.setEntityManager(em);
        
        // 4. Create component factory
        WorkflowComponantFactory factory = new MyComponentFactory();
        
        // 5. Create event handler
        EventHandler eventHandler = new MyEventHandler();
        
        // 6. Get RuntimeService
        runtimeService = WorkflowService.instance()
            .getRunTimeService(dao, factory, eventHandler, null);
        
        System.out.println("Workflow initialized with PostgreSQL");
    }
    
    public RuntimeService getRuntimeService() {
        return runtimeService;
    }
    
    public void shutdown() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
    
    public static void main(String[] args) {
        WorkflowPostgresSetup setup = new WorkflowPostgresSetup();
        setup.initialize();
        
        // Use runtime service
        RuntimeService rts = setup.getRuntimeService();
        
        // Start a workflow
        String workflowJson = loadWorkflowDefinition();
        rts.startCase("order-123", workflowJson, null, null);
        
        // Shutdown on exit
        Runtime.getRuntime().addShutdownHook(new Thread(setup::shutdown));
    }
}
```

### Spring Boot Integration

```java
@Configuration
public class WorkflowConfig {
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource dataSource
    ) {
        LocalContainerEntityManagerFactoryBean em = 
            new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.anode.workflow.entities");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", 
            "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        em.setJpaProperties(properties);
        
        return em;
    }
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/workflow_db");
        config.setUsername("workflow_user");
        config.setPassword("your_secure_password");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        
        return new HikariDataSource(config);
    }
    
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(emf);
        return transactionManager;
    }
    
    @Bean
    public CommonService workflowDAO(EntityManager entityManager) {
        PostgresCommonDAO dao = new PostgresCommonDAO();
        dao.setEntityManager(entityManager);
        return dao;
    }
    
    @Bean
    public RuntimeService runtimeService(
        CommonService dao,
        WorkflowComponantFactory factory,
        EventHandler eventHandler
    ) {
        WorkflowService.init(10, 30000, "-");
        return WorkflowService.instance()
            .getRunTimeService(dao, factory, eventHandler, null);
    }
}
```

```java
@Service
public class WorkflowService {
    
    private final RuntimeService runtimeService;
    
    @Autowired
    public WorkflowService(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }
    
    public void startWorkflow(String caseId, String workflowJson) {
        runtimeService.startCase(caseId, workflowJson, null, null);
    }
    
    public void resumeWorkflow(String caseId) {
        runtimeService.resumeCase(caseId);
    }
}
```

## Connection Pooling

### Optimal HikariCP Settings

```java
HikariConfig config = new HikariConfig();

// Basic settings
config.setJdbcUrl("jdbc:postgresql://localhost:5432/workflow_db");
config.setUsername("workflow_user");
config.setPassword("your_secure_password");
config.setDriverClassName("org.postgresql.Driver");

// Pool sizing
config.setMaximumPoolSize(20);  // Max connections in pool
config.setMinimumIdle(5);       // Min idle connections
config.setConnectionTimeout(30000);  // 30 seconds
config.setIdleTimeout(600000);       // 10 minutes
config.setMaxLifetime(1800000);      // 30 minutes

// Connection testing
config.setConnectionTestQuery("SELECT 1");
config.setValidationTimeout(5000);

// Leak detection (development only)
config.setLeakDetectionThreshold(60000); // 60 seconds

// PostgreSQL specific optimizations
config.addDataSourceProperty("cachePrepStmts", "true");
config.addDataSourceProperty("prepStmtCacheSize", "250");
config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
config.addDataSourceProperty("useServerPrepStmts", "true");
config.addDataSourceProperty("useLocalSessionState", "true");
config.addDataSourceProperty("rewriteBatchedStatements", "true");
config.addDataSourceProperty("cacheResultSetMetadata", "true");
config.addDataSourceProperty("cacheServerConfiguration", "true");
config.addDataSourceProperty("elideSetAutoCommits", "true");
config.addDataSourceProperty("maintainTimeStats", "false");

HikariDataSource dataSource = new HikariDataSource(config);
```

### Pool Size Calculation

**Formula**: `connections = ((core_count * 2) + effective_spindle_count)`

For a 4-core server with SSD:
- `connections = (4 * 2) + 1 = 9`
- Round up to 10-20 for workflow (accounts for parallel processing)

**Monitoring Pool Health**:

```java
HikariPoolMXBean poolBean = dataSource.getHikariPoolMXBean();

System.out.println("Active connections: " + poolBean.getActiveConnections());
System.out.println("Idle connections: " + poolBean.getIdleConnections());
System.out.println("Total connections: " + poolBean.getTotalConnections());
System.out.println("Threads awaiting: " + poolBean.getThreadsAwaitingConnection());
```

## Performance Tuning

### PostgreSQL Configuration

Edit `postgresql.conf`:

```conf
# Memory settings
shared_buffers = 256MB              # 25% of RAM for dedicated server
effective_cache_size = 1GB          # 50-75% of RAM
work_mem = 16MB                     # Per sort operation
maintenance_work_mem = 128MB        # For VACUUM, CREATE INDEX

# Checkpoint settings
checkpoint_completion_target = 0.9
wal_buffers = 16MB
max_wal_size = 2GB
min_wal_size = 1GB

# Query planning
random_page_cost = 1.1              # For SSD (4.0 for HDD)
effective_io_concurrency = 200      # For SSD (2 for HDD)

# Logging (development)
log_statement = 'none'              # 'all' for debugging
log_min_duration_statement = 1000   # Log queries > 1 second
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '

# Connections
max_connections = 100               # Adjust based on pool size

# Autovacuum (important for workflow tables)
autovacuum = on
autovacuum_max_workers = 3
autovacuum_naptime = 1min
```

Restart PostgreSQL:
```bash
sudo systemctl restart postgresql
```

### Index Optimization

```sql
-- Analyze table statistics
ANALYZE workflow_info;
ANALYZE exec_path;
ANALYZE audit_log;

-- Check index usage
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan ASC;

-- Add missing indexes based on query patterns
CREATE INDEX CONCURRENTLY idx_wf_info_ts_incomplete 
ON workflow_info(ts) 
WHERE is_complete = FALSE;

CREATE INDEX CONCURRENTLY idx_exec_path_workbasket 
ON exec_path(pend_workbasket) 
WHERE pend_workbasket IS NOT NULL;

-- Partial index for active cases
CREATE INDEX CONCURRENTLY idx_wf_info_active 
ON workflow_info(case_id, ts) 
WHERE is_complete = FALSE;
```

### Query Optimization

```java
// Enable query logging in JPA
properties.put("hibernate.show_sql", "true");
properties.put("hibernate.format_sql", "true");
properties.put("hibernate.use_sql_comments", "true");

// Use EXPLAIN ANALYZE to check query plans
EntityManager em = emf.createEntityManager();
Query query = em.createNativeQuery(
    "EXPLAIN ANALYZE SELECT * FROM workflow_info WHERE is_complete = false"
);
List<Object[]> results = query.getResultList();
results.forEach(row -> System.out.println(Arrays.toString(row)));
```

### Batch Operations

```java
// Configure batch processing in persistence.xml
properties.put("hibernate.jdbc.batch_size", "20");
properties.put("hibernate.order_inserts", "true");
properties.put("hibernate.order_updates", "true");
properties.put("hibernate.jdbc.batch_versioned_data", "true");

// Use batch operations in DAO
@Override
@Transactional
public void saveCollection(Collection objects) {
    int batchSize = 20;
    int count = 0;
    
    for (Object obj : objects) {
        entityManager.persist(obj);
        count++;
        
        if (count % batchSize == 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }
    
    // Flush remaining
    entityManager.flush();
    entityManager.clear();
}
```

### Monitoring Queries

```sql
-- Long running queries
SELECT 
    pid,
    now() - pg_stat_activity.query_start AS duration,
    query,
    state
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '5 seconds'
AND state != 'idle'
ORDER BY duration DESC;

-- Table sizes
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
    pg_total_relation_size(schemaname||'.'||tablename) AS bytes
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY bytes DESC;

-- Index bloat
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY pg_relation_size(indexrelid) DESC;
```

## Backup and Recovery

### Automated Backup Script

```bash
#!/bin/bash
# backup-workflow-db.sh

BACKUP_DIR="/var/backups/postgresql"
DB_NAME="workflow_db"
DB_USER="workflow_user"
DATE=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_${DATE}.sql.gz"

# Create backup directory if not exists
mkdir -p $BACKUP_DIR

# Perform backup
pg_dump -U $DB_USER -h localhost $DB_NAME | gzip > $BACKUP_FILE

# Keep only last 7 days of backups
find $BACKUP_DIR -name "${DB_NAME}_*.sql.gz" -mtime +7 -delete

echo "Backup completed: $BACKUP_FILE"
```

Make executable and schedule with cron:
```bash
chmod +x backup-workflow-db.sh

# Add to crontab (daily at 2 AM)
crontab -e
# Add line:
0 2 * * * /path/to/backup-workflow-db.sh >> /var/log/workflow-backup.log 2>&1
```

### Point-in-Time Recovery

Enable WAL archiving in `postgresql.conf`:

```conf
wal_level = replica
archive_mode = on
archive_command = 'cp %p /var/lib/postgresql/wal_archive/%f'
archive_timeout = 300  # 5 minutes
```

Create base backup:
```bash
pg_basebackup -U workflow_user -h localhost -D /var/backups/base -Ft -z -P
```

### Restore from Backup

```bash
# Stop application
systemctl stop your-workflow-app

# Drop and recreate database
psql -U postgres -c "DROP DATABASE workflow_db;"
psql -U postgres -c "CREATE DATABASE workflow_db OWNER workflow_user;"

# Restore
gunzip -c /var/backups/postgresql/workflow_db_20240101_020000.sql.gz | \
    psql -U workflow_user -d workflow_db

# Restart application
systemctl start your-workflow-app
```

### Disaster Recovery Testing

```java
public class DisasterRecoveryTest {
    
    @Test
    public void testDatabaseFailover() {
        // Start workflow
        runtimeService.startCase("dr-test-1", workflowJson, null, null);
        
        // Wait for persistence
        Thread.sleep(1000);
        
        // Simulate database failure
        closeAllConnections();
        
        // Wait for connection pool to detect failure
        Thread.sleep(5000);
        
        // Restore database connection
        restoreConnections();
        
        // Resume workflow should work
        runtimeService.resumeCase("dr-test-1");
        
        // Verify workflow completed
        WorkflowInfo info = dao.get(WorkflowInfo.class, 
            "workflow_process_info-dr-test-1");
        assertTrue(info.getIsComplete());
    }
}
```

## Troubleshooting

### Common Issues

#### Issue 1: Connection Pool Exhausted

**Symptoms**:
```
java.sql.SQLTransientConnectionException: HikariPool-1 - 
Connection is not available, request timed out after 30000ms
```

**Solutions**:
```java
// 1. Increase pool size
config.setMaximumPoolSize(30);

// 2. Reduce connection timeout
config.setConnectionTimeout(20000);

// 3. Check for connection leaks
config.setLeakDetectionThreshold(10000);

// 4. Ensure proper transaction management
@Transactional
public void operation() {
    // Always use transactions
}
```

#### Issue 2: Slow Queries

**Diagnosis**:
```sql
-- Enable slow query logging
ALTER DATABASE workflow_db SET log_min_duration_statement = 100;

-- Check slow queries
SELECT query, calls, total_time, mean_time 
FROM pg_stat_statements 
ORDER BY mean_time DESC 
LIMIT 10;
```

**Solutions**:
```sql
-- Add missing indexes
CREATE INDEX idx_custom ON table_name(column);

-- Update statistics
ANALYZE table_name;

-- Vacuum if bloated
VACUUM FULL table_name;
```

#### Issue 3: Database Locks

**Symptoms**: Workflows hang, timeout exceptions

**Diagnosis**:
```sql
-- Check locks
SELECT 
    locktype,
    database,
    pid,
    mode,
    granted
FROM pg_locks
WHERE NOT granted;

-- Check blocking queries
SELECT 
    blocked_locks.pid AS blocked_pid,
    blocking_locks.pid AS blocking_pid,
    blocked_activity.query AS blocked_statement,
    blocking_activity.query AS blocking_statement
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
```

**Solutions**:
```java
// 1. Set lock timeout
properties.put("javax.persistence.lock.timeout", "5000");

// 2. Use optimistic locking where possible
@Version
private Long version;

// 3. Kill blocking query (emergency)
// SELECT pg_terminate_backend(blocking_pid);
```

#### Issue 4: Out of Memory

**Symptoms**: `OutOfMemoryError` when loading large workflows

**Solutions**:
```java
// 1. Use pagination for large queries
TypedQuery<WorkflowInfo> query = em.createQuery(
    "SELECT w FROM WorkflowInfo w WHERE w.isComplete = false",
    WorkflowInfo.class
);
query.setMaxResults(100); // Limit results

// 2. Clear persistence context regularly
if (count % 100 == 0) {
    em.flush();
    em.clear();
}

// 3. Use projection queries
TypedQuery<String> query = em.createQuery(
    "SELECT w.caseId FROM WorkflowInfo w WHERE w.isComplete = false",
    String.class
);

// 4. Increase JVM heap
// java -Xmx2g -Xms1g ...
```

#### Issue 5: "Table doesn't exist" Error

**Symptoms**:
```
org.postgresql.util.PSQLException: ERROR: relation "workflow_info" does not exist
```

**Solutions**:
```bash
# 1. Verify schema creation
psql -U workflow_user -d workflow_db -c "\dt"

# 2. Check hibernate auto-ddl setting
# In persistence.xml:
<property name="hibernate.hbm2ddl.auto" value="update"/>

# 3. Manually create schema
psql -U workflow_user -d workflow_db < schema.sql
```

### Health Check Endpoint

```java
@RestController
@RequestMapping("/health")
public class HealthCheckController {
    
    @Autowired
    private EntityManager entityManager;
    
    @GetMapping("/database")
    public ResponseEntity<Map<String, Object>> checkDatabase() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test query
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            
            // Get connection pool stats
            HikariDataSource ds = (HikariDataSource) 
                entityManager.getEntityManagerFactory()
                .getProperties().get("hibernate.hikari.dataSource");
            
            health.put("status", "UP");
            health.put("activeConnections", 
                ds.getHikariPoolMXBean().getActiveConnections());
            health.put("idleConnections", 
                ds.getHikariPoolMXBean().getIdleConnections());
            health.put("totalConnections", 
                ds.getHikariPoolMXBean().getTotalConnections());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }
}
```

## Security Best Practices

### 1. Use SSL Connections

```java
config.setJdbcUrl(
    "jdbc:postgresql://localhost:5432/workflow_db?" +
    "ssl=true&" +
    "sslmode=require&" +
    "sslcert=/path/to/client-cert.pem&" +
    "sslkey=/path/to/client-key.pem&" +
    "sslrootcert=/path/to/ca-cert.pem"
);
```

### 2. Restrict Database User Permissions

```sql
-- Create read-only user for reporting
CREATE USER workflow_readonly WITH PASSWORD 'readonly_password';
GRANT CONNECT ON DATABASE workflow_db TO workflow_readonly;
GRANT USAGE ON SCHEMA public TO workflow_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO workflow_readonly;

-- Revoke unnecessary permissions from app user
REVOKE CREATE ON SCHEMA public FROM workflow_user;
```

### 3. Encrypt Sensitive Data

```java
// Use PostgreSQL pgcrypto extension
CREATE EXTENSION IF NOT EXISTS pgcrypto;

// Encrypt sensitive process variables
INSERT INTO process_variable_value (name, value_string)
VALUES ('ssn', pgp_sym_encrypt('123-45-6789', 'encryption_key'));

// Decrypt when reading
SELECT pgp_sym_decrypt(value_string::bytea, 'encryption_key') 
FROM process_variable_value 
WHERE name = 'ssn';
```

### 4. Secure Configuration

```java
// Load from environment variables or secrets manager
config.setUsername(System.getenv("DB_USERNAME"));
config.setPassword(System.getenv("DB_PASSWORD"));

// Or use AWS Secrets Manager, HashiCorp Vault, etc.
String password = secretsManager.getSecret("workflow/db/password");
config.setPassword(password);
```

## Migration from File-Based Storage

```java
public class FileToPostgresMigration {
    
    public static void migrate(String fileDir) {
        // 1. Initialize file DAO
        FileDao fileDao = new FileDao(fileDir);
        
        // 2. Initialize PostgreSQL DAO
        EntityManager em = JPAConfig.createEntityManager();
        PostgresCommonDAO pgDao = new PostgresCommonDAO();
        pgDao.setEntityManager(em);
        
        // 3. Get all workflow info from files
        File dir = new File(fileDir);
        File[] files = dir.listFiles(
            (d, name) -> name.startsWith("workflow_process_info-")
        );
        
        if (files == null) {
            System.out.println("No files to migrate");
            return;
        }
        
        em.getTransaction().begin();
        
        try {
            for (File file : files) {
                String filename = file.getName().replace(".json", "");
                
                // Load from file
                WorkflowInfo info = fileDao.get(WorkflowInfo.class, filename);
                
                if (info != null) {
                    // Save to PostgreSQL
                    pgDao.save(filename, info);
                    System.out.println("Migrated: " + filename);
                }
            }
            
            em.getTransaction().commit();
            System.out.println("Migration complete!");
            
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            em.close();
        }
    }
    
    public static void main(String[] args) {
        migrate("./workflow-data");
    }
}
```

## Monitoring and Metrics

### Prometheus Metrics

```java
@Component
public class WorkflowMetrics {
    
    private final Counter casesStarted;
    private final Counter casesCompleted;
    private final Gauge activeCases;
    private final Timer caseExecutionTime;
    
    public WorkflowMetrics(MeterRegistry registry) {
        this.casesStarted = Counter.builder("workflow.cases.started")
            .description("Total workflow cases started")
            .register(registry);
            
        this.casesCompleted = Counter.builder("workflow.cases.completed")
            .description("Total workflow cases completed")
            .register(registry);
            
        this.activeCases = Gauge.builder("workflow.cases.active", this, 
            WorkflowMetrics::countActiveCases)
            .description("Number of active workflow cases")
            .register(registry);
            
        this.caseExecutionTime = Timer.builder("workflow.case.execution.time")
            .description("Time taken to complete workflow case")
            .register(registry);
    }
    
    private double countActiveCases() {
        // Query database for active cases
        Query query = em.createQuery(
            "SELECT COUNT(w) FROM WorkflowInfo w WHERE w.isComplete = false"
        );
        return ((Long) query.getSingleResult()).doubleValue();
    }
}
```

## Related Documentation

- [Custom DAO Implementation](./custom-dao.md)
- [Crash Recovery](./crash-recovery.md)
- [Performance Tuning](../operations/performance.md)
- [Monitoring Guide](../operations/monitoring.md)

## Summary

PostgreSQL provides a robust, ACID-compliant storage backend for Simple Workflow with:

✅ **Reliability**: ACID transactions, crash recovery  
✅ **Performance**: Efficient indexing, query optimization  
✅ **Scalability**: Connection pooling, read replicas  
✅ **Monitoring**: Built-in statistics, logging  
✅ **Backup**: Point-in-time recovery, automated backups  

**Quick Setup Checklist**:
- [ ] Install PostgreSQL
- [ ] Create database and user
- [ ] Configure persistence.xml
- [ ] Set up connection pooling
- [ ] Create indexes
- [ ] Configure automated backups
- [ ] Set up monitoring
- [ ] Test disaster recovery

Your workflow is now ready for production with PostgreSQL!
