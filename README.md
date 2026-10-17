# workflow — embeddable Java orchestration engine

Lightweight, embeddable workflow engine: JSON-defined journeys of tasks, singular and parallel
routes, joins, pauses and tickets, with persisted state, crash recovery, work baskets and SLA
milestones. No server to run.

- Java 25. Runtime dependencies: Jackson, the Jakarta Persistence API (annotations only), SLF4J,
  and the document/service classes of [`tool`](https://github.com/A-N-O-D-E-R/tools).
- Spring Boot integration: [`workflow-spring-boot-starter`](https://github.com/A-N-O-D-E-R/workflow-springboot-starter).
- Derived from American Express [Flowret](https://github.com/americanexpress/unify-flowret) — see [NOTICE](NOTICE).

## Installation

```xml
<dependency>
    <groupId>io.github.a-n-o-d-e-r</groupId>
    <artifactId>workflow</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quick start

```java
WorkflowService.init(10, 30000, "-");           // worker threads, idle timeout (ms), id separator

CommonService dao = ...;                         // persistence: your DAO (file, memory, JDBC, JPA via the starter)
WorkflowComponantFactory factory = ctx -> ...;   // returns the InvokableTask / InvokableRoute for a step
EventHandler handler = (event, ctx) -> {};       // lifecycle events (start, pend, complete...)

WorkflowDefinition journey =
        WorkflowDefinitionMapper.toEntity(new JDocument(Files.readString(Path.of("order.json"))));

RuntimeService rts = WorkflowService.instance().getRunTimeService(dao, factory, handler, null);
rts.startCase("order-123", journey, null, null); // runs until the journey pends or ends
rts.resumeCase("order-123");                      // after the pending work is done
```

Full guides: [docs/](docs/README.md).

## Thread safety

- A `RuntimeService` carries the state of the case it is operating on. Its public operations are
  `synchronized`: a shared instance is safe but runs cases one at a time. For concurrency, take one
  instance per concurrent operation — `getRunTimeService` creates a new, cheap instance each call.
- Concurrent operations on the **same** case id must be serialized by the caller (e.g. a database
  row lock); parallel branches *inside* a case run on the engine's thread pool.

## Persistence of variables

Variables are typed (`STRING`, `BOOLEAN`, `INTEGER`, `LONG`, lists of those, `OBJECT`). With JPA,
scalars are stored as plain text; lists and objects as Base64 Java serialization, read back
through an `ObjectInputFilter` that only admits `java.base` classes unless you configure
`jdk.serialFilter`.

## Build

```bash
mvn verify          # JDK 25
```

Releases: push a `v<version>` tag; GitHub Actions publishes to Maven Central.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
