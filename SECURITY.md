# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

Instead, please report vulnerabilities by opening a **private security advisory** on this repository:
1. Go to the **Security** tab of this repository
2. Click **Report a vulnerability**
3. Provide a detailed description of the issue

### What to include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response timeline

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 7 days
- **Fix or mitigation**: Dependent on severity

## Security Considerations

This application handles financial transactions via USSD. The following security measures are in place:

### Data Protection

- All sensitive data stored using **EncryptedSharedPreferences** (AES-256-GCM)
- Database encrypted with **SQLCipher** (AES-256)
- Encryption keys managed by **Android Keystore** (hardware-backed when available)
- App backup disabled (`android:allowBackup="false"`)

### Transaction Security

- PINs and OTPs are **never stored** — passed directly through the Accessibility Service
- Payment credentials handled entirely by the system phone app and NPCI
- No data transmitted to external servers

### Accessibility Service Scope

- Strictly limited to `com.android.phone` package
- Only monitors `WindowStateChanged` and `WindowContentChanged` events
- Does not read notifications, perform keylogging, or overlay other apps

## Supported Versions

| Version | Supported |
|---|---|
| 0.1.x-beta | Yes |

## Scope

The following are considered in-scope for security reports:

- Data leaks (PINs, OTPs, account numbers, transaction data)
- Encryption weaknesses or bypass
- Accessibility Service scope violations
- Privilege escalation
- Unauthorized data transmission

Out of scope:

- Vulnerabilities in the `*99#` USSD service itself (report to NPCI/your bank)
- Android OS or system phone app vulnerabilities (report to the device manufacturer)
- Social engineering attacks
