# Contributing to Offline Pay

Thank you for your interest in contributing! This document provides guidelines and information for contributors.

## How to Contribute

### Reporting Bugs

1. Check existing [issues](../../issues) to avoid duplicates
2. Use the **Bug Report** issue template
3. Include:
   - Device model and Android version
   - SIM carrier and `*99#` service status
   - Steps to reproduce the issue
   - Expected vs. actual behavior
   - Relevant logs (with sensitive info redacted)

### Suggesting Features

1. Open an issue using the **Feature Request** template
2. Describe the use case and expected behavior
3. Explain why this would benefit other users

### Submitting Code

1. **Open an issue first** to discuss proposed changes
2. Fork the repository
3. Create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. Make your changes following the guidelines below
5. Test on a physical device (USSD features cannot be tested on emulators)
6. Submit a pull request

## Development Setup

### Prerequisites

- Android Studio Ladybug or later
- JDK 11+
- Android SDK with API level 36
- A physical Android device with an active SIM card (for USSD testing)

### Building

```bash
git clone <repo-url>
cd OfflineUPI
./gradlew assembleDebug
```

## Code Guidelines

### Language & Frameworks

- **Kotlin** — all new code must be in Kotlin
- **Jetpack Compose** — for all UI work
- **MVVM architecture** — ViewModels for business logic, Compose for UI

### Style

- Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions focused and concise

### Security

This app handles financial transactions. Security is critical:

- **Never** log or store PINs, OTPs, or passwords
- **Never** transmit data to external servers
- Use `EncryptedSharedPreferences` for sensitive preferences
- Use SQLCipher for database encryption
- All encryption keys must be managed by Android Keystore

### Commit Messages

Use clear, descriptive commit messages:

```
feat: add dual SIM selection in settings
fix: handle USSD timeout on slow networks
docs: update permission table in README
```

Prefixes: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `build`

## Testing

- **USSD flows** require a physical device with an active SIM and `*99#` service enabled
- **UI tests** can run on emulators
- **Unit tests** for validators, parsers, and repositories can run on JVM

## Important Notes

- This app uses an Accessibility Service that is scoped to `com.android.phone` only. Any changes to the service must maintain this strict scope.
- The app is intended for sideloading only; it does not comply with Google Play's Accessibility Service policies.
- All data must remain on-device. Do not add analytics, crash reporting, or any network calls.

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to uphold this code.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
