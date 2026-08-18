---
description: TypeScript guidelines covering core standards
globs: "**/*.ts, **/*.tsx"
---

## Code Style & Structure

- Write concise, technical TypeScript code.
- Use functional and declarative patterns; avoid classes.
- Prefer iteration and modularization over duplication.
- Use descriptive variable names with auxiliary verbs (e.g., `isLoading`, `hasError`).
- Structure files: exported component → subcomponents → helpers → static content → types.

## Naming Conventions

### Files & Directories
- Lowercase with dashes for directories and files (e.g., `components/auth-wizard/auth-wizard.tsx`).
- Component files match the component name (e.g., `UserCard.tsx` exports `UserCard`).
- Utility/helper files use lowercase with dashes (e.g., `format-date.ts`).
- Test files mirror the source file name with `.test.ts(x)` suffix (e.g., `auth-wizard.test.tsx`).

### Variables & Functions
- `camelCase` for variables, functions, and method names.
- `SCREAMING_SNAKE_CASE` for module-level constants and environment variables.
- Boolean variables should use positive auxiliary verb prefixes: `isLoading`, `hasError`, `canSubmit`, `shouldRefetch`.
- Event handler functions prefixed with `handle`: `handleSubmit`, `handleChange`.
- Async functions suffixed with context, not `Async`: prefer `fetchUser` over `fetchUserAsync`.

### Types & Interfaces
- `PascalCase` for interfaces, types, and enums.
- Prefix interfaces for React component props with the component name: `UserCardProps`, `ModalProps`.
- Avoid `I` prefix for interfaces (e.g., use `User`, not `IUser`).
- Suffix context types with `Context` (e.g., `AuthContext`); reducers with `Action` (e.g., `AuthAction`).

### Components & Hooks
- `PascalCase` for all React and React Native components.
- Custom hooks prefixed with `use`: `useAuth`, `useDebounce`, `useFormState`.
- Favor named exports for components; avoid default exports except for screen/page components.

### Generics
- Use descriptive single-letter or short names: `T` for general, `TData`, `TError`, `TKey` when context helps clarity.
- Avoid meaningless names like `T1`, `T2`.

## TypeScript Usage

- Use TypeScript for all code; prefer `interface` over `type`.
- Avoid enums; use const maps instead.
- Use functional components with TypeScript interfaces.
- Enable strict mode (`"strict": true` in `tsconfig.json`).

## Syntax & Formatting

- Use the `function` keyword for pure functions.
- Omit unnecessary curly braces in conditionals; use concise syntax for simple statements.
- Use declarative TSX.
- Use Prettier for consistent formatting.

---

## Cross Fruntional Guidelines

### Accessibility

- Set `accessible`, `accessibilityLabel`, and `accessibilityHint` on all interactive elements.
- Use `accessibilityRole` to convey element purpose (e.g., `button`, `header`, `link`).
- Use `accessibilityState` for dynamic states (`disabled`, `selected`, `checked`).
- Group related content with `accessible={true}` on the container to reduce noise for screen readers.
- Ensure minimum touch targets of 44×44 pt.
- Support text scaling — avoid fixed font sizes; use `allowFontScaling` (default `true`).
- Test with VoiceOver (iOS) and TalkBack (Android).
- Use `AccessibilityInfo.isScreenReaderEnabled()` to adapt UI when a screen reader is active.


### Error Handling & Validation

- Log errors with Sentry or equivalent.
- Handle errors at the top of functions; use early returns to avoid deep nesting.
- Use `expo-error-reporter` for production error reporting.


### Testing

- Unit tests with Jest and Typescript Testing Library.
- Integration tests for critical flows with Detox.
- Snapshot tests for UI consistency.

### Security

- Sanitize user inputs to prevent XSS.
- Enforce HTTPS for all API communication.
- Follow [Expo Security guidelines](https://docs.expo.dev/guides/security/).

### Internationalization (i18n)

- Use `expo-localization` for localization.
- Support RTL layouts and multiple languages.
- Ensure text scaling for accessibility.
