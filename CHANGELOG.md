# Changelog

All notable changes to the Simple Workflow project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial comprehensive documentation structure
- Getting started guide with complete example
- Core concepts documentation (process variables, work baskets, execution paths, crash recovery, SLA management)
- Architecture documentation with detailed component descriptions
- API reference documentation
- Best practices guide
- Testing guide with examples
- Operations guide for production deployment
- Performance tuning guide
- Example workflows (order processing)
- Contributing guidelines
- Documentation indexes for easy navigation

### Changed
- Fixed typo in getting-started filename (gettings-started.md → getting-started.md)
- Updated README references to corrected documentation paths

### Fixed
- Empty getting-started.md file now contains comprehensive guide

## [0.0.1] - 2023-11-02

### Added
- Initial release of Simple Workflow engine
- Core workflow execution engine
- Support for sequential and parallel execution paths
- File-based persistence (FileDao)
- Crash recovery mechanism
- Process variable management
- Work basket implementation for human tasks
- SLA management and milestone tracking
- Event handler system for workflow lifecycle events
- Component factory pattern for steps and routes
- Thread pool-based concurrent execution
- JSON-based workflow definitions
- Basic documentation (README, existing concept docs)

### Features
- **Workflow Execution**
  - Sequential step execution
  - Parallel execution paths
  - Nested execution paths with delimiter-based naming
  - Automatic step scheduling and coordination

- **Persistence**
  - File-based DAO for development
  - CommonService interface for custom implementations
  - Automatic state persistence after each step
  - Support for process, step, and variable storage

- **Crash Recovery**
  - Automatic detection of incomplete processes
  - Recovery from last persisted state
  - No data loss on application crash
  - Graceful continuation of interrupted workflows

- **Work Management**
  - Work basket creation and management
  - Work item lifecycle (claim, release, complete)
  - User assignment tracking
  - Basket-based routing

- **SLA Management**
  - Milestone definition and tracking
  - Breach detection
  - Time-based SLA monitoring
  - Historical SLA reporting

- **Process Variables**
  - Key-value storage per workflow instance
  - Support for complex object serialization
  - Shared across execution paths
  - Persistent across crashes

- **Event System**
  - Process lifecycle events (start, complete, error)
  - Step lifecycle events (start, complete, error)
  - Custom event handler implementation
  - Asynchronous event notification

### Technical Details
- Java 17+ requirement
- Maven-based build system
- Minimal external dependencies (JPA/Hibernate optional)
- Thread-safe concurrent execution
- Configurable thread pool size
- Configurable step execution timeout

### Known Limitations
- File-based persistence not recommended for production at scale
- No built-in workflow designer UI
- No REST API for workflow management
- Limited monitoring and metrics out of the box
- No built-in retry mechanisms (must be implemented in steps)

## [0.0.1-alpha] - 2023-10-01

### Added
- Initial proof of concept
- Basic workflow execution
- Simple file persistence
- Core entity models

## Version History

### Versioning Scheme

We use [Semantic Versioning](https://semver.org/):
- **MAJOR**: Incompatible API changes
- **MINOR**: New functionality (backward compatible)
- **PATCH**: Bug fixes (backward compatible)

### Release Cadence

- **Major releases**: As needed for breaking changes
- **Minor releases**: Quarterly (every 3 months)
- **Patch releases**: As needed for critical bug fixes

### Support Policy

- **Current version**: Full support (bug fixes and new features)
- **Previous minor version**: Bug fixes only (6 months)
- **Older versions**: No official support (community support available)

## Upgrade Guides

### Upgrading from 0.0.1-alpha to 0.0.1

No breaking changes. Simply update your dependency version:

```xml
<dependency>
    <groupId>com.anode</groupId>
    <artifactId>workflow</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Migration Notes

### Future Breaking Changes

We strive to maintain backward compatibility, but the following may change in future major versions:

- **CommonService interface**: May add new methods
- **Entity models**: May add new fields
- **Configuration**: May change initialization parameters

We will provide detailed migration guides for all breaking changes.

## Deprecation Notices

Currently, there are no deprecated features.

When features are deprecated, they will be:
1. Marked as `@Deprecated` in code
2. Documented in this changelog
3. Supported for at least one major version
4. Removed in the next major version

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for details on:
- How to report bugs
- How to request features
- How to submit pull requests
- Coding standards and guidelines

## Security Vulnerabilities

To report security vulnerabilities, please email security@openevolve.org instead of using the public issue tracker.

We take security seriously and will respond to reports within 48 hours.

## License

See [LICENSE](./LICENSE) for license information.

---

**Legend:**
- `Added` - New features
- `Changed` - Changes to existing functionality
- `Deprecated` - Features marked for removal
- `Removed` - Removed features
- `Fixed` - Bug fixes
- `Security` - Security fixes

[0.0.1]: https://github.com/OpenEvolve/workflow/releases/tag/v0.0.1
