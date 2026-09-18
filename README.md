# Offline Pay

**Send money, check your balance, and pay by QR — even with no internet.**

Offline Pay lets you make UPI payments over your mobile network using India's `*99#` service, so you can pay when you have no data and no Wi-Fi — on a patchy signal, while travelling, or after you've run out of data. It puts a clean, modern screen in front of the old `*99#` dialer menus and fills them in for you.

> **Beta software.** This is an early release. It automates real money transfers, so mistakes can cost you. Please read the [Before you start](#before-you-start) section — it matters.

---

## What you can do

- **Send money** to a mobile number or a UPI ID
- **Request money** from someone
- **Check your bank balance**
- **Scan a QR code** to pay
- **See your history** of past transactions
- **Save people you pay often** as favorites
- **Lock the app** with your fingerprint or face
- Use it on **either SIM** if you have two
- Pick a **light, dark, or automatic** theme

---

## Before you start

Offline Pay is an independent, open-source app. **It is not made by, or connected to, your bank, NPCI, RBI, or your mobile operator.** It simply automates the `*99#` menu that already exists on your phone.

A few honest things to know:

- **You use it at your own risk.** Payments go through your bank's real `*99#` service, and a failed or wrong transfer is possible — as it is with any payment method.
- **Standard USSD charges may apply** from your telecom operator for each `*99#` session.
- **Your bank's rules still apply.** Make sure you're allowed to use `*99#` on your account.
- Because of how it works (see [How it works](#how-it-works)), the app is meant to be **installed directly (sideloaded)**, not from the Play Store.

The full legal text is in the [Disclaimer](DISCLAIMER.md), [Terms of Service](TERMS_OF_SERVICE.md), and [Privacy Policy](PRIVACY_POLICY.md).

---

## What you need

- An **Android phone running Android 8.0 or newer**
- A **SIM card** with a mobile-network signal (this is what carries the payment — you do *not* need internet)
- A **bank account linked to `*99#` / UPI** (if you've used `*99#` before, you're set)
- Your **UPI PIN**

---

## Getting started

### 1. Install the app

1. Download the latest `OfflinePay-*.apk` from the [**Releases page**](https://github.com/maroufwani/OfflinePay/releases/latest).
2. Open the file on your phone. Android will ask to allow installing from this source — say yes.
3. Tap **Install**.

The app can also tell you when a newer version is out and offer to update itself.

### 2. First-time setup

When you open the app the first time, it walks you through a short setup:

- Confirms your phone number (used to place the `*99#` call)
- Asks for the permissions it needs (explained below)
- Lets you set up a fingerprint/face lock if you want one

Once that's done, you're on the home screen and ready to pay.

### 3. Make your first payment

1. Tap **Send Money**.
2. Enter a mobile number or UPI ID, or **scan a QR code**.
3. Enter the amount.
4. Confirm — the app dials `*99#` and steps through the menu for you.
5. Enter your **UPI PIN** when asked. The money moves over the mobile network.

Your balance check and money requests work the same way: pick the action, and the app handles the `*99#` menus.

---

## Your privacy

**Everything stays on your phone.** Offline Pay has no server and sends none of your information anywhere. Your transaction history, saved recipients, and settings live only on your device.

What's stored locally, just for the app to work:

- Your phone number (to place the `*99#` call)
- Contacts you choose to pay
- Your transaction history and saved recipients
- Your app settings (theme, SIM choice, lock)

Uninstalling the app removes this data. Details are in the [Privacy Policy](PRIVACY_POLICY.md).

---

## Permissions, in plain words

The app only asks for what it needs to place a payment call and read the `*99#` menu:

| It asks for | So it can |
|---|---|
| **Phone calls** | Dial the `*99#` payment code |
| **Phone/SIM state** | Know which SIM and network to use |
| **Accessibility** | Read and tap through the `*99#` menu screens for you |
| **Camera** *(optional)* | Scan QR codes to pay |
| **Contacts** *(optional)* | Let you pick a recipient from your contacts |

You can skip the optional ones and still send money.

---

## How it works

Android has no normal way for an app to drive the `*99#` menu, so Offline Pay uses an **Accessibility Service** to read the `*99#` dialog boxes from your phone's dialer and tap through them automatically. The payment itself travels over your **mobile network**, which is why it works with no internet.

Because using the Accessibility Service this way doesn't fit Google Play's rules for that permission, the app is built for **direct install (sideloading)** rather than the Play Store.

---

## Need help or found a bug?

- Something not working? [**Open an issue**](https://github.com/maroufwani/OfflinePay/issues) and tell us what happened, your phone model, and your Android version.
- Found a security problem? Please follow the [Security Policy](SECURITY.md) to report it privately.

---

## For developers

Offline Pay is open source under the [Apache License 2.0](LICENSE). Contributions are welcome — see [Contributing](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).

**Build it yourself:**

```bash
git clone https://github.com/maroufwani/OfflinePay.git
cd OfflinePay
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

- **Requirements:** Android Studio (Ladybug or later), JDK 11+, Android SDK API 36
- **Minimum device:** Android 8.0 (API 26)

More project documents: [Changelog](CHANGELOG.md) · [Terms of Service](TERMS_OF_SERVICE.md) · [Regulatory notes](#regulatory-note)

---

## Regulatory note

Offline Pay interacts with India's `*99#` USSD service, which is governed by NPCI and RBI. **It does not hold any payment-system license and is not a payment service provider, aggregator, or intermediary** under Indian law. You are responsible for using it in line with your bank's terms, RBI and NPCI guidelines, and applicable law (including the PSS Act 2007, IT Act 2000 and SPDI Rules, DPDP Act 2023, and Consumer Protection Act 2019). See the [Disclaimer](DISCLAIMER.md) and [Terms of Service](TERMS_OF_SERVICE.md) for the complete text.
