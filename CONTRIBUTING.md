# Contributing to Simple Workflow

Thank you for your interest in contributing to Simple Workflow! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Setup](#development-setup)
4. [How to Contribute](#how-to-contribute)
5. [Coding Standards](#coding-standards)
6. [Testing Guidelines](#testing-guidelines)
7. [Documentation](#documentation)
8. [Pull Request Process](#pull-request-process)
9. [Issue Guidelines](#issue-guidelines)

## Code of Conduct

### Our Pledge

We are committed to providing a welcoming and inclusive environment for all contributors, regardless of experience level, background, or identity.

### Expected Behavior

- Be respectful and considerate in all interactions
- Welcome newcomers and help them get started
- Accept constructive criticism gracefully
- Focus on what is best for the project and community

### Unacceptable Behavior

- Harassment, discrimination, or offensive comments
- Personal attacks or trolling
- Publishing others' private information
- Any conduct that would be inappropriate in a professional setting

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Git
- Your favorite IDE (IntelliJ IDEA, Eclipse, VS Code)

### First Time Contributors

If you're new to open source, here are some good first issues:
- Documentation improvements
- Adding examples
- Writing tests
- Fixing typos

Look for issues labeled `good-first-issue` or `help-wanted`.

## Development Setup

### 1. Fork the Repository

Click the "Fork" button on GitHub to create your own copy of the repository.

### 2. Clone Your Fork

```bash
git clone https://github.com/YOUR-USERNAME/workflow.git
cd workflow
```

### 3. Add Upstream Remote

```bash
git remote add upstream https://github.com/A-N-O-D-E-R/workflow.git
```

### 4. Build the Project

```bash
mvn clean install
```

### 5. Run Tests

```bash
mvn test
```

### 6. Create a Branch

```bash
git checkout -b feature/my-new-feature
```

## How to Contribute

### Types of Contributions

We welcome various types of contributions:

#### 🐛 Bug Reports
- Search existing issues first
- Provide clear reproduction steps
- Include version information
- Add relevant logs or error messages

#### ✨ Feature Requests
- Explain the use case
- Describe the proposed solution
- Consider alternatives
- Be open to discussion

#### 📝 Documentation
- Fix typos or unclear sections
- Add examples
- Improve API documentation
- Write tutorials or guides

#### 💻 Code Contributions
- Bug fixes
- New features
- Performance improvements
- Refactoring

#### 🧪 Testing
- Add missing tests
- Improve test coverage
- Add integration tests
- Performance benchmarks

## Coding Standards

### Java Style Guide

We follow standard Java conventions with some specific guidelines:

#### Formatting

```java
// Use 4 spaces for indentation (no tabs)
public class MyClass {

    // One blank line between methods
    public void method1() {
        // Implementation
    }

    public void method2() {
        // Implementation
    }
}
```

#### Naming Conventions

```java
// Classes: PascalCase
public class OrderProcessingStep implements WorkflowStep { }

// Methods and variables: camelCase
public void processOrder(String orderId) {
    int orderCount = 0;
}

// Constants: UPPER_SNAKE_CASE
public static final int MAX_RETRY_COUNT = 3;

// Package names: lowercase
package com.anode.workflow.service;
```

#### Code Organization

```java
public class MyClass {
    // 1. Constants
    private static final String DEFAULT_VALUE = "default";

    // 2. Static fields
    private static int instanceCount = 0;

    // 3. Instance fields
    private String name;
    private int value;

    // 4. Constructors
    public MyClass() {
    }

    // 5. Public methods
    public void publicMethod() {
    }

    // 6. Protected methods
    protected void protectedMethod() {
    }

    // 7. Private methods
    private void privateMethod() {
    }

    // 8. Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

#### Documentation

```java
/**
 * Executes a workflow step.
 *
 * <p>This method is called by the workflow engine when the step is ready
 * to execute. The implementation should perform the business logic and
 * update process variables as needed.
 *
 * @param context the workflow context providing access to variables and services
 * @throws Exception if the step execution fails
 */
@Override
public void execute(WorkflowContext context) throws Exception {
    // Implementation
}
```

### Best Practices

#### Error Handling

```java
// ✅ Good: Specific exception types
public void processOrder(String orderId) throws OrderNotFoundException {
    Order order = orderService.findById(orderId);
    if (order == null) {
        throw new OrderNotFoundException("Order not found: " + orderId);
    }
}

// ❌ Bad: Generic exception
public void processOrder(String orderId) throws Exception {
    // ...
}
```

#### Null Safety

```java
// ✅ Good: Check for null
String value = context.getProcessVariable("key");
if (value != null) {
    process(value);
}

// ✅ Good: Use Optional
Optional<String> value = Optional.ofNullable(
    context.getProcessVariable("key"));
value.ifPresent(this::process);

// ❌ Bad: No null check
String value = context.getProcessVariable("key");
process(value);  // NullPointerException risk!
```

#### Resource Management

```java
// ✅ Good: Try-with-resources
try (InputStream is = new FileInputStream("file.txt")) {
    // Use resource
}

// ❌ Bad: Manual close
InputStream is = new FileInputStream("file.txt");
try {
    // Use resource
} finally {
    is.close();  // May throw exception
}
```

## Testing Guidelines

### Test Coverage

- Aim for 80%+ code coverage
- All new features must include tests
- Bug fixes should include regression tests

### Test Structure

```java
@Test
public void testMethodName_Scenario_ExpectedResult() {
    // Arrange
    OrderStep step = new OrderStep();
    WorkflowContext context = mock(WorkflowContext.class);
    when(context.getProcessVariable("orderId")).thenReturn("ORD-123");

    // Act
    step.execute(context);

    // Assert
    verify(context).setProcessVariable("status", "PROCESSED");
}
```

### Test Categories

```java
// Unit tests - fast, no external dependencies
@Test
public void testValidation() {
    // Test logic in isolation
}

// Integration tests - test with real dependencies
@SpringBootTest
@Test
public void testWorkflowExecution() {
    // Test complete workflow
}

// Performance tests - benchmark performance
@Test
public void testThroughput() {
    // Measure performance
}
```

### Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=OrderStepTest

# Specific test method
mvn test -Dtest=OrderStepTest#testValidOrder

# With coverage
mvn test jacoco:report
```

## Documentation

### Code Documentation

- All public classes and methods must have Javadoc
- Include examples for complex APIs
- Document exceptions and edge cases

### README Updates

- Update README.md for new features
- Keep examples current
- Update version numbers

### Documentation Files

- Add new docs to `docs/` directory
- Follow existing structure
- Include code examples
- Link related documentation

### Example Format

```markdown
# Feature Name

Brief description of the feature.

## Overview

Detailed explanation.

## Example

\`\`\`java
// Code example
\`\`\`

## Related Documentation

- [Link to related docs]
```

## Pull Request Process

### Before Submitting

- [ ] Code follows style guidelines
- [ ] Tests pass locally
- [ ] New tests added for new features
- [ ] Documentation updated
- [ ] Commit messages are clear
- [ ] No merge conflicts

### Creating a Pull Request

1. **Push to Your Fork**
   ```bash
   git push origin feature/my-new-feature
   ```

2. **Create PR on GitHub**
   - Go to the original repository
   - Click "New Pull Request"
   - Select your fork and branch
   - Fill out the PR template

3. **PR Title Format**
   ```
   [Type] Brief description

   Types:
   - feat: New feature
   - fix: Bug fix
   - docs: Documentation
   - test: Tests
   - refactor: Code refactoring
   - perf: Performance improvement
   - chore: Maintenance

   Examples:
   - feat: Add support for MongoDB persistence
   - fix: Correct crash recovery for nested paths
   - docs: Improve getting started guide
   ```

4. **PR Description Template**
   ```markdown
   ## Description
   Brief description of changes

   ## Type of Change
   - [ ] Bug fix
   - [ ] New feature
   - [ ] Documentation
   - [ ] Performance improvement

   ## Testing
   - Describe how you tested the changes
   - List any new tests added

   ## Checklist
   - [ ] Tests pass locally
   - [ ] Code follows style guidelines
   - [ ] Documentation updated
   - [ ] No breaking changes (or documented)

   ## Related Issues
   Fixes #123
   ```

### Review Process

1. **Automated Checks**
   - CI/CD pipeline runs tests
   - Code coverage is checked
   - Style checks are performed

2. **Code Review**
   - Maintainers review the code
   - Feedback provided as comments
   - Address feedback and push updates

3. **Approval and Merge**
   - At least one maintainer approval required
   - Squash and merge for clean history
   - Automatic deployment to staging

### After Merge

- Delete your branch (optional)
- Pull latest changes from upstream
- Celebrate your contribution! 🎉

## Issue Guidelines

### Reporting Bugs

Use this template:

```markdown
**Description**
Clear description of the bug

**To Reproduce**
1. Step 1
2. Step 2
3. See error

**Expected Behavior**
What should happen

**Actual Behavior**
What actually happens

**Environment**
- Java version:
- Workflow version:
- OS:

**Additional Context**
- Logs
- Screenshots
- Code snippets
```

### Requesting Features

Use this template:

```markdown
**Problem Statement**
What problem does this solve?

**Proposed Solution**
How should it work?

**Alternatives Considered**
What other approaches did you consider?

**Additional Context**
Examples, mockups, related issues
```

## Getting Help

### Resources

- 📚 [Documentation](./docs/README.md)
- 💬 [Discussions](https://github.com/A-N-O-D-E-R/workflow/discussions)
- 🐛 [Issues](https://github.com/A-N-O-D-E-R/workflow/issues)

### Contact

- Email: dev@openevolve.org
- GitHub: @OpenEvolve

## Recognition

Contributors are recognized in:
- Release notes
- Contributors page
- Project README

Thank you for contributing to Simple Workflow! Your efforts help make this project better for everyone.
