# Deployment Guide

Complete guide for deploying Simple Workflow engine to various environments.

## Table of Contents

1. [Pre-Deployment Checklist](#pre-deployment-checklist)
2. [Environment Setup](#environment-setup)
3. [Standalone Deployment](#standalone-deployment)
4. [Spring Boot Deployment](#spring-boot-deployment)
5. [Docker Deployment](#docker-deployment)
6. [Kubernetes Deployment](#kubernetes-deployment)
7. [Cloud Platform Deployments](#cloud-platform-deployments)
8. [Database Setup](#database-setup)
9. [Configuration Management](#configuration-management)
10. [Security Hardening](#security-hardening)
11. [Post-Deployment Verification](#post-deployment-verification)

## Pre-Deployment Checklist

Before deploying to any environment:

- [ ] All tests passing locally
- [ ] Performance benchmarks completed
- [ ] Security review completed
- [ ] Database schema ready
- [ ] Backup strategy defined
- [ ] Monitoring configured
- [ ] Logging configured
- [ ] Documentation updated
- [ ] Rollback plan prepared
- [ ] Team trained on operations

## Environment Setup

### Development Environment

```yaml
# Configuration for development
workflow:
  thread-pool-size: 5
  step-timeout: 30000
  persistence: file

logging:
  level:
    com.anode.workflow: DEBUG
```

### Staging Environment

```yaml
# Configuration for staging (production-like)
workflow:
  thread-pool-size: 10
  step-timeout: 45000
  persistence: database

database:
  url: jdbc:postgresql://staging-db:5432/workflow
  pool-size: 20

logging:
  level:
    com.anode.workflow: INFO
```

### Production Environment

```yaml
# Configuration for production
workflow:
  thread-pool-size: 20
  step-timeout: 60000
  persistence: database

database:
  url: jdbc:postgresql://prod-db:5432/workflow
  pool-size: 50
  connection-timeout: 30000

logging:
  level:
    com.anode.workflow: WARN

monitoring:
  enabled: true
  metrics-export: true
```

## Standalone Deployment

### Traditional Java Application

#### 1. Package Your Application

```bash
mvn clean package
```

This creates: `target/my-workflow-app.jar`

#### 2. Create Startup Script

Create `start-workflow.sh`:

```bash
#!/bin/bash

# Configuration
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC"
APP_OPTS="--spring.profiles.active=production"
JAR_FILE="my-workflow-app.jar"
PID_FILE="workflow.pid"
LOG_FILE="logs/workflow.log"

# Start application
java $JAVA_OPTS -jar $JAR_FILE $APP_OPTS > $LOG_FILE 2>&1 &

# Save PID
echo $! > $PID_FILE

echo "Workflow application started with PID $(cat $PID_FILE)"
```

#### 3. Create Stop Script

Create `stop-workflow.sh`:

```bash
#!/bin/bash

PID_FILE="workflow.pid"

if [ -f $PID_FILE ]; then
    PID=$(cat $PID_FILE)
    echo "Stopping workflow application (PID: $PID)"
    kill $PID
    rm $PID_FILE
    echo "Workflow application stopped"
else
    echo "PID file not found. Application may not be running."
fi
```

#### 4. Make Scripts Executable

```bash
chmod +x start-workflow.sh stop-workflow.sh
```

#### 5. Run Application

```bash
./start-workflow.sh
```

### Systemd Service (Linux)

Create `/etc/systemd/system/workflow.service`:

```ini
[Unit]
Description=Simple Workflow Service
After=network.target postgresql.service

[Service]
Type=simple
User=workflow
Group=workflow
WorkingDirectory=/opt/workflow
ExecStart=/usr/bin/java -Xms2g -Xmx4g -jar /opt/workflow/my-workflow-app.jar
ExecStop=/bin/kill -SIGTERM $MAINPID
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

**Enable and start:**

```bash
sudo systemctl daemon-reload
sudo systemctl enable workflow
sudo systemctl start workflow

# Check status
sudo systemctl status workflow

# View logs
sudo journalctl -u workflow -f
```

## Spring Boot Deployment

### 1. Create Spring Boot Application

```java
@SpringBootApplication
public class WorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
```

### 2. Configuration Class

```java
@Configuration
public class WorkflowConfig {

    @Value("${workflow.thread-pool-size:10}")
    private int threadPoolSize;

    @Value("${workflow.step-timeout:30000}")
    private long stepTimeout;

    @Bean
    public WorkflowService workflowService() {
        WorkflowService.init(threadPoolSize, stepTimeout, "-");
        return WorkflowService.instance();
    }

    @Bean
    public RuntimeService runtimeService(
            CommonService dao,
            WorkflowComponantFactory factory,
            EventHandler eventHandler) {
        return workflowService().getRunTimeService(
            dao, factory, eventHandler, null);
    }

    @Bean
    public CommonService dao(DataSource dataSource) {
        return new PostgresDao(dataSource);
    }

    @Bean
    public WorkflowComponantFactory componentFactory() {
        return new MyComponentFactory();
    }

    @Bean
    public EventHandler eventHandler() {
        return new MyEventHandler();
    }
}
```

### 3. Application Properties

`src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: workflow-service

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/workflow}
    username: ${DB_USER:workflow}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

workflow:
  thread-pool-size: ${WORKFLOW_THREADS:10}
  step-timeout: ${WORKFLOW_TIMEOUT:30000}

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  metrics:
    export:
      prometheus:
        enabled: true

logging:
  level:
    com.anode.workflow: INFO
    com.example: DEBUG
  file:
    name: logs/workflow.log
```

### 4. Build and Run

```bash
# Build
mvn clean package -DskipTests

# Run with profile
java -jar target/workflow-service.jar --spring.profiles.active=production

# Or with environment variables
export DB_URL=jdbc:postgresql://prod-db:5432/workflow
export DB_PASSWORD=secretpassword
export WORKFLOW_THREADS=20
java -jar target/workflow-service.jar
```

## Docker Deployment

### 1. Create Dockerfile

```dockerfile
# Multi-stage build
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 workflow && \
    adduser -D -u 1001 -G workflow workflow

# Copy application
COPY --from=build /app/target/*.jar app.jar

# Create directories
RUN mkdir -p /app/workflow-data /app/logs && \
    chown -R workflow:workflow /app

# Switch to non-root user
USER workflow

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

### 2. Build Docker Image

```bash
docker build -t workflow-service:1.0.0 .
```

### 3. Run Container

```bash
docker run -d \
  --name workflow-service \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://db:5432/workflow \
  -e DB_USER=workflow \
  -e DB_PASSWORD=secretpassword \
  -e WORKFLOW_THREADS=10 \
  -v workflow-data:/app/workflow-data \
  -v workflow-logs:/app/logs \
  workflow-service:1.0.0
```

### 4. Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:14-alpine
    environment:
      POSTGRES_DB: workflow
      POSTGRES_USER: workflow
      POSTGRES_PASSWORD: workflow123
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U workflow"]
      interval: 10s
      timeout: 5s
      retries: 5

  workflow-service:
    build: .
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/workflow
      DB_USER: workflow
      DB_PASSWORD: workflow123
      WORKFLOW_THREADS: 10
      JAVA_OPTS: -Xms512m -Xmx1g
    ports:
      - "8080:8080"
    volumes:
      - workflow-data:/app/workflow-data
      - workflow-logs:/app/logs
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped

volumes:
  postgres-data:
  workflow-data:
  workflow-logs:
```

**Run with Docker Compose:**

```bash
docker-compose up -d

# View logs
docker-compose logs -f workflow-service

# Stop
docker-compose down
```

## Kubernetes Deployment

### 1. Create ConfigMap

`k8s/configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: workflow-config
  namespace: workflow
data:
  application.yml: |
    workflow:
      thread-pool-size: 20
      step-timeout: 60000

    spring:
      datasource:
        hikari:
          maximum-pool-size: 50
          minimum-idle: 10

    logging:
      level:
        com.anode.workflow: INFO
```

### 2. Create Secret

```bash
kubectl create secret generic workflow-secrets \
  --from-literal=db-password=yourpassword \
  --namespace=workflow
```

### 3. Create Deployment

`k8s/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: workflow-service
  namespace: workflow
  labels:
    app: workflow-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: workflow-service
  template:
    metadata:
      labels:
        app: workflow-service
        version: "1.0.0"
    spec:
      containers:
      - name: workflow-service
        image: your-registry/workflow-service:1.0.0
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: DB_URL
          value: "jdbc:postgresql://postgres-service:5432/workflow"
        - name: DB_USER
          value: "workflow"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: workflow-secrets
              key: db-password
        - name: WORKFLOW_THREADS
          value: "20"
        - name: JAVA_OPTS
          value: "-Xms1g -Xmx2g -XX:+UseContainerSupport"

        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"

        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3

        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3

        volumeMounts:
        - name: config
          mountPath: /app/config
        - name: logs
          mountPath: /app/logs

      volumes:
      - name: config
        configMap:
          name: workflow-config
      - name: logs
        emptyDir: {}
```

### 4. Create Service

`k8s/service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: workflow-service
  namespace: workflow
  labels:
    app: workflow-service
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: workflow-service
```

### 5. Create Ingress

`k8s/ingress.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: workflow-ingress
  namespace: workflow
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - workflow.example.com
    secretName: workflow-tls
  rules:
  - host: workflow.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: workflow-service
            port:
              number: 8080
```

### 6. Create HorizontalPodAutoscaler

`k8s/hpa.yaml`:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: workflow-hpa
  namespace: workflow
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: workflow-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 2
        periodSeconds: 30
      selectPolicy: Max
```

### 7. Deploy to Kubernetes

```bash
# Create namespace
kubectl create namespace workflow

# Apply all manifests
kubectl apply -f k8s/

# Check deployment
kubectl get pods -n workflow
kubectl get svc -n workflow
kubectl get ingress -n workflow

# View logs
kubectl logs -f deployment/workflow-service -n workflow

# Scale manually
kubectl scale deployment workflow-service --replicas=5 -n workflow
```

## Cloud Platform Deployments

### AWS Elastic Beanstalk

```bash
# Install EB CLI
pip install awsebcli

# Initialize
eb init -p docker workflow-service

# Create environment
eb create workflow-prod --database.engine postgres

# Deploy
eb deploy

# Check status
eb status
eb health

# View logs
eb logs
```

### Google Cloud Run

```bash
# Build and push to GCR
gcloud builds submit --tag gcr.io/PROJECT_ID/workflow-service

# Deploy
gcloud run deploy workflow-service \
  --image gcr.io/PROJECT_ID/workflow-service \
  --platform managed \
  --region us-central1 \
  --memory 2Gi \
  --cpu 2 \
  --min-instances 1 \
  --max-instances 10 \
  --set-env-vars DB_URL=... \
  --set-secrets DB_PASSWORD=workflow-db-password:latest

# Get URL
gcloud run services describe workflow-service --region us-central1
```

### Azure App Service

```bash
# Create resource group
az group create --name workflow-rg --location eastus

# Create App Service plan
az appservice plan create \
  --name workflow-plan \
  --resource-group workflow-rg \
  --sku B2 \
  --is-linux

# Create web app
az webapp create \
  --resource-group workflow-rg \
  --plan workflow-plan \
  --name workflow-service \
  --deployment-container-image-name your-registry/workflow-service:1.0.0

# Configure environment
az webapp config appsettings set \
  --resource-group workflow-rg \
  --name workflow-service \
  --settings DB_URL=... WORKFLOW_THREADS=20
```

## Database Setup

### PostgreSQL

```sql
-- Create database
CREATE DATABASE workflow;

-- Create user
CREATE USER workflow WITH PASSWORD 'your_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE workflow TO workflow;

-- Connect to database
\c workflow

-- Create schema (if using custom DAO)
CREATE TABLE processes (
    case_id VARCHAR(255) PRIMARY KEY,
    process_id VARCHAR(255),
    status VARCHAR(50),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    -- Add other fields as needed
);

CREATE TABLE steps (
    step_id VARCHAR(255) PRIMARY KEY,
    case_id VARCHAR(255) REFERENCES processes(case_id),
    step_type VARCHAR(255),
    status VARCHAR(50),
    execution_path VARCHAR(255),
    -- Add other fields
);

CREATE INDEX idx_processes_status ON processes(status);
CREATE INDEX idx_steps_case_id ON steps(case_id);
```

## Configuration Management

### Environment-Specific Configs

Use profiles for different environments:

```yaml
# application.yml (common)
workflow:
  step-timeout: 30000

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev

workflow:
  thread-pool-size: 5

---
# application-prod.yml
spring:
  config:
    activate:
      on-profile: prod

workflow:
  thread-pool-size: 20
```

### External Configuration

Use configuration servers or vault:

```yaml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      name: workflow-service
      profile: ${ENVIRONMENT}
```

## Security Hardening

### 1. Use HTTPS Only

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

### 2. Secure Database Credentials

Never hardcode credentials. Use environment variables or secrets:

```bash
export DB_PASSWORD=$(aws secretsmanager get-secret-value \
  --secret-id workflow-db-password \
  --query SecretString \
  --output text)
```

### 3. Enable Security Headers

```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.headers()
            .contentSecurityPolicy("default-src 'self'")
            .and()
            .frameOptions().deny()
            .and()
            .xssProtection().enable();
        return http.build();
    }
}
```

## Post-Deployment Verification

### 1. Health Check

```bash
curl http://your-app/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### 2. Smoke Test

```bash
# Start a test workflow
curl -X POST http://your-app/api/workflows \
  -H "Content-Type: application/json" \
  -d '{"orderId": "TEST-001"}'

# Check status
curl http://your-app/api/workflows/TEST-001
```

### 3. Load Test

```bash
ab -n 1000 -c 10 http://your-app/api/workflows
```

### 4. Monitor Metrics

```bash
curl http://your-app/actuator/metrics
curl http://your-app/actuator/prometheus
```

## Troubleshooting Deployments

### Application Won't Start

Check logs:
```bash
# Docker
docker logs workflow-service

# Kubernetes
kubectl logs deployment/workflow-service -n workflow

# Systemd
journalctl -u workflow -n 100
```

Common causes:
- Database connection failure
- Missing environment variables
- Port already in use
- Insufficient memory

### Database Connection Issues

Test connection:
```bash
psql -h your-db-host -U workflow -d workflow
```

### Performance Issues

Check resources:
```bash
# Kubernetes
kubectl top pods -n workflow

# Docker
docker stats workflow-service
```

## Related Documentation

- [Operations Guide](../operations/README.md) - Day-to-day operations
- [Performance Tuning](../operations/performance.md) - Optimization
- [Monitoring Setup](../operations/README.md#monitoring) - Monitoring configuration
- [Security Best Practices](../best-practices/README.md#security) - Security guidelines

---

**Ready to deploy?** Start with the [Pre-Deployment Checklist](#pre-deployment-checklist) and choose the deployment method that fits your infrastructure!
