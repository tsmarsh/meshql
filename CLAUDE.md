# MeshQL Development Guide

## Build Commands
- Full build: `mvn clean install`
- Run all tests: `mvn test`
- Run single test: `mvn test -Dtest=ClassName`
- Run specific method: `mvn test -Dtest=ClassName#methodName`

## Code Style Guidelines
- **Java Version**: Java 17
- **Naming**: 
  - Classes: PascalCase (e.g., `CrudHandler`)
  - Methods: camelCase (e.g., `getAuthTokens`)
  - Interfaces: Noun-based (e.g., `Repository`)
- **Formatting**:
  - 4-space indentation
  - Opening braces on same line
- **Architecture**:
  - Interface-based design
  - Composition over inheritance
  - Immutable objects when possible
  - Functional programming style with UnderBar utilities
- **Error Handling**:
  - Use specific exceptions with clear messages
  - Log errors with SLF4J
  - Return appropriate HTTP status codes
  - Format errors consistently