# Offline Pay

An Android app that enables payments without internet using the `*99#` USSD service. The app provides a modern UI for managing USSD-based payment flows via mobile network (no data connection required).

## Disclaimer

> **This application is NOT affiliated with, endorsed by, or approved by the National Payments Corporation of India (NPCI), the Reserve Bank of India (RBI), or any bank or telecom operator.**
>
> This is an independent, community-developed open-source project. It automates interaction with the publicly available `*99#` USSD service. **Use at your own risk.**
>
> This application is **NOT** a payment system, payment aggregator, or payment gateway under the Payment and Settlement Systems Act, 2007. No license under the PSS Act is required or held.
>
> The developers of this application assume no liability for:
> - Failed, incorrect, or duplicate transactions
> - Account suspension or blocking by your bank or telecom operator
> - Any financial loss incurred through use of this application
> - Violations of your bank's terms of service or applicable regulations
>
> **By using this application, you acknowledge that you understand the risks involved in automating USSD-based financial transactions.**
>
> See [DISCLAIMER.md](DISCLAIMER.md) for the full legal disclaimer.

## How It Works

The app uses Android's `CALL_PHONE` permission to dial USSD codes (`*99#`) and an Accessibility Service to navigate the USSD menu dialogs automatically. All processing happens locally on your device — **no data is sent to any server**.

### Accessibility Service Usage

This app uses Android's Accessibility Service to read and interact with USSD dialog windows from the system phone app (`com.android.phone`). This is required because Android does not provide a standard API for USSD menu navigation.

**Important:** This usage pattern may not comply with Google Play Store policies for Accessibility Service usage. This app is intended for **sideloading only** and is not suitable for distribution on the Google Play Store without architectural changes.

## Features

- Send money via mobile number or payee address
- Request money
- Check account balance
- Scan QR codes for payments
- Transaction history
- Favorite recipients
- Biometric app lock
- Dark/Light/System theme support
- Dual SIM support

## Permissions

| Permission | Purpose |
|---|---|
| `CALL_PHONE` | Required to dial USSD codes (`*99#`) |
| `READ_PHONE_STATE` | Required to detect SIM card and network state |
| `READ_CONTACTS` | Optional — used to select payment recipients from contacts |
| `CAMERA` | Optional — used for scanning QR codes |
| `ACCESSIBILITY_SERVICE` | Required to navigate USSD menu dialogs |

## Building

### Prerequisites
- Android Studio Ladybug or later
- JDK 11+
- Android SDK with API level 36

### Build
```bash
git clone <repo-url>
cd OfflineUPI
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Privacy

All data is stored **locally on your device only**. No data is transmitted to any server. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details.

### Data stored locally:
- Phone number (for USSD dialing)
- Contact information (names and numbers, for recipient selection)
- Transaction history (amounts, recipients, timestamps)
- Recipient list (names, identifiers)
- App preferences (theme, SIM slot, lock settings)

## Legal Documents

| Document | Description |
|---|---|
| [Privacy Policy](PRIVACY_POLICY.md) | Data handling, DPDPA 2023, IT Act, SPDI Rules compliance |
| [Terms of Service](TERMS_OF_SERVICE.md) | Usage terms, liability, regulatory compliance, dispute resolution |
| [Disclaimer](DISCLAIMER.md) | Risk acknowledgment, warranty disclaimer, limitation of liability |
| [License](LICENSE) | Apache License, Version 2.0 |
| [Changelog](CHANGELOG.md) | Version history and release notes |
| [Security Policy](SECURITY.md) | Vulnerability reporting and security measures |
| [Contributing](CONTRIBUTING.md) | Contribution guidelines |
| [Code of Conduct](CODE_OF_CONDUCT.md) | Community standards |

## Regulatory Notice

This application interacts with India's `*99#` USSD payment service, which is regulated by NPCI and RBI. Users are responsible for ensuring their use of this application complies with:

- Their bank's terms of service
- **RBI** regulations on digital payments
- **NPCI** guidelines for `*99#` service usage
- **Payment and Settlement Systems Act, 2007**
- **Information Technology Act, 2000** and SPDI Rules, 2011
- **Digital Personal Data Protection Act, 2023**
- **Prevention of Money Laundering Act, 2002**
- **Indian Telegraph Act, 1885** (for USSD/telecom usage)
- **Consumer Protection Act, 2019**
- Applicable bank-specific and telecom operator terms

This application **does not hold any payment system license** and is **not a payment service provider, payment aggregator, or intermediary** under any Indian law.

## Contributing

Contributions are welcome! Please read the [Contributing Guidelines](CONTRIBUTING.md) before getting started.

All participants are expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full text.
