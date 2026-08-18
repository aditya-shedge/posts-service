## CODING GUIDELINES (MANDATORY)

### Goal
Ensure code is *extremely readable*, composed of *very small and focused* methods and classes, and **avoids all code smells**.


### General Principles
- Code is for **humans first**, computers second
- **Express intent clearly** - if it's not obvious, rewrite it. Variables, method names, Class names to be self-explanatory
- Prefer **self-documenting code**; comments as the last resort
- **Small is beautiful** - Prefer **small**, focused methods and classes.
- **Duplication is a bad sign** - extract and reuse
- **Keep it Simple Stupid (KISS)** - simpler is always better, reduce complexity as much as possible
- **You aren't gonna need it (YAGNI)** - Avoid over-engineering
- **Boy Scout Rule (BSR)** - leave the campground cleaner than you found it
- **Tell, Don't Ask** - to promote loose coupling between objects
- **Be Consistent** - if you do something a certain way, do all similar things in the same way. Follow conventions that you see in the codebase
- **Encapsulate Boundary Conditions** - boundary conditions are hard to keep track of, put the processing for them in one place
- **Avoid logical dependency** - don't write methods which work correctly depending on something else in the same class
- Avoid negative conditionals
- Keep configurable data at high levels
- Separate multithreading code; document thread-safety assumptions
- Prevent over-configurability
- Use dependency injection
- Externalise messages, regex patterns and constants
- Standardize special characters

### SOLID Design Principles
- **SRP (Single Responsibility Principle)** - Each method/class does one thing only
- **OCP (Open/Close Principle)** - Open for extension, closed for modification
- **LSP (Liskov Substitution Rule)** - Subtypes substitutable for base types
- **ISP (Interface Segregation Principle)** - Small, focused interfaces
- **DIP (Dependency Inversion Principle)** - Depend on abstractions, not concretions

### Code Smells to Identify and remove
- Long Method
- Large Class
- Primitive Obsession
- Long Parameters List: **maximum**: 3
- Data Clumps
- Alternative classes with Different Interfaces
- Switch Statements
- Temporary Field
- Refused Bequest
- Divergent Change
- Parallel Inheritance Hierarchies
- Shotgun Surgery
- Comments
- Data Class
- Lazy Class
- Duplicated code
- Dead Code
- Speculative Generality
- Feature Envy
- Incomplete Library Class
- Middle Man (excessive delegation without adding value)
- Inappropriate Intimacy
- Message Chains - (e.g. `a.getB().getC().getD()` in backend, `props.user.address.street.name` in frontend)
- Magic numbers (also magic literals): Replace magic numbers with named constants

### Guidelines for Comments
- Always try to explain yourself in code instead of comments
- Comments explain *why*, not *what* (if code is clear)
- Document assumptions, invariants, edge cases.
- Don't be redundant
- Don't add obvious noise
- Don't use closing brace comments
- Don't comment out code. Just remove when unused
- Use as an explanation of intent
- Use as clarification of code
- Use as a warning of consequences

### Test Design
- Small, specific, isolated
- Fast
- Independent
- Repeatable
- Readable
- Always write unit tests for new public behavior.
- Stick to fewer assertions per test. **Maximum**: 3
- Adhere to _Arrange-Act-Assert_ format
- **Avoid duplicated code** - extract commonly used data at class level
- Avoid `lenient()` unless retrofitting legacy tests; prefer explicit mocks.
- For new APIs, add integration test against controller + service + repository slice.
- Remove tests that only verify trivial getters or Spring annotations.
- Validate mandatory request fields added during changes.
- At least one negative test per API.

---
## Cross-Functional Requirements (XFRs)

### Error Handling Guidelines
- **Exception and Error Localization**: Use domain specific custom exceptions
- Externalise all user facing messages
- Handle exceptions gracefully
- Maintain an ErrorCode mapping: internal log message + translation key
- Avoid swallowing exceptions; propagate or log appropriately
- Special characters validated for FrontEnd constraints

### Logging Guidelines
- Log all Business events, not technical noise
- Well-structured logs
- Write meaningful log-entries
- Use appropriate log levels (INFO for lifecycle, WARN for recoverable anomalies, ERROR for failures)
- No secrets or sensitive information in logs
- Centralise log message constants for reusability and consistency

### Security Guidelines
- No hardcoded secrets, URLs, profiles or any sensitive information
- All sensitive config resolved via environment or config server.
- Test for potential data leakage in code
- PII Protection: Implement data masking, tokenization for personally identifiable information
- When performing any upgrades on dependencies or libraries, ensure only minor or patch versions are upgraded. Do not upgrade to major versions
- Do not perform any commands that can result in deleting files/directories or killing process on the system

### Dependency and Version Management
- Remove unused dependencies promptly.
- Get explicit confirmation from user before making any upgrades. When upgrading any dependency/library, strictly limit to minor or patch version upgrades only.
- Add CVE remediation notes in changelogs.

### Performance Guidelines
- **IMP**: Avoid premature micro-optimizations unless profiled.
- Analyze complexity; refactor nested loops into indexed maps or grouping (convert to `O(n+m)`)
- Batch deletes / updates in a single transaction

### Internationalization (i18n) & Localization
- Use translation keys in exceptions & validations; FrontEnd resolves localized text.
- Normalize characters where system limitations exist.
- Keep a consolidated `messages.properties` and language-specific overrides (backend)
- Handle special characters (German umlauts replaced with `ae`, `oe`, `ue`)
- Ensure any updates to English (en) language also updates across all other existing locales as well

### Architectural Smells to identify and remove
- **Rigidity** The software is difficult to change. A small change causes a cascade of subsequent changes.
- **Fragility** The software breaks in many places due to a single change.
- **Immobility** You cannot reuse parts of the code in other projects because of the involved risks and high effort.

### Technology-Specific Guidelines (Mandatory)
In addition to these core standards, you MUST follow the relevant technology-specific guideline(s) depending on the codebase you are working in:

- React: [react.md](react.md)
- React JavaScript: [react-javascript.md](react-javascript.md)
- Java / Spring Boot: [java-spring-boot.md](java-spring-boot.md)
- TypeScript: [typescript.md](typescript.md)
