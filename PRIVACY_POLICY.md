# Privacy Policy — Offline Pay

**Last updated:** April 9, 2026

**Applicable jurisdiction:** India

---

## 1. Overview

Offline Pay ("the App") is an open-source Android application that enables USSD-based payments via the `*99#` service. This privacy policy explains what data the App processes, how it is stored, your rights under applicable Indian law, and the security measures in place.

This policy is drafted in compliance with:
- **The Digital Personal Data Protection Act, 2023** (DPDPA)
- **The Information Technology Act, 2000** (IT Act)
- **The Information Technology (Reasonable Security Practices and Procedures and Sensitive Personal Data or Information) Rules, 2011** (SPDI Rules)

---

## 2. Data Fiduciary Information (DPDPA, Section 5)

Under the DPDPA 2023, a "Data Fiduciary" is any person who determines the purpose and means of processing personal data. Because Offline Pay is open-source software distributed without a central service, **no centralized Data Fiduciary exists**. The individual who installs and uses the App on their own device acts as both the operator and the sole custodian of their data.

No entity collects, receives, or has access to any personal data processed by this App.

---

## 3. Data Collection and Processing

Offline Pay does **not** collect, transmit, upload, or share any personal data with external servers. **All data remains on your device at all times.**

- No analytics, tracking, crash reporting, advertising, or telemetry of any kind is used.
- No data is shared with any third party, Data Processor, or Data Fiduciary.
- No server-side infrastructure exists for this App.

---

## 4. Personal Data Stored Locally

The following data is stored **only on your device** in encrypted storage:

| Data | Category (SPDI Rules) | Purpose |
|---|---|---|
| Phone number | Sensitive Personal Data | Used to identify your account for USSD dialing |
| Contact names and numbers | Personal Data | Used to select payment recipients from your contacts |
| Transaction history | Financial Information (Sensitive) | Records of payments you initiate (amount, recipient, timestamp, status) |
| Recipient / payee list | Personal Data | Saved payees for quick access |
| Account balance | Financial Information (Sensitive) | Last queried balance via `*99#` |
| App preferences | Non-personal Data | Theme, SIM slot selection, biometric/lock settings |

### Lawful Basis for Processing (DPDPA, Section 4)

All data processing is performed locally on the user's device solely at the user's initiation and for the user's legitimate purpose of making USSD payments. No consent mechanism is required for purely on-device processing where no Data Fiduciary exists. The user retains full control and can delete all data at any time.

---

## 5. Sensitive Personal Data or Information (SPDI Rules)

Under the IT Act's SPDI Rules, financial information and phone numbers constitute Sensitive Personal Data or Information (SPDI). The App implements **reasonable security practices** as required under **Section 43A of the IT Act** and **Rule 8 of the SPDI Rules**, including:

- **Encryption at rest** using industry-standard cryptographic measures (see Section 6).
- **No network transmission** of any SPDI — data never leaves the device.
- **No third-party access** — no entity other than the device owner can access the data.
- **Access control** via optional biometric/PIN app lock.

---

## 6. Data Encryption and Security Measures

| Measure | Implementation |
|---|---|
| Sensitive preferences | Stored using Android's `EncryptedSharedPreferences` backed by the Android Keystore |
| Transaction database | Encrypted using SQLCipher with AES-256 |
| Encryption key management | Managed by the Android Keystore system; keys never leave the device hardware |
| App access protection | Optional biometric authentication or device PIN lock |
| Backup prevention | `android:allowBackup="false"` prevents cloud backup of app data |

These measures constitute **reasonable security practices and procedures** as contemplated under Section 43A of the IT Act, 2000, and Rule 8 of the SPDI Rules, 2011.

---

## 7. Permissions

| Permission | Purpose | Mandatory? |
|---|---|---|
| `CALL_PHONE` | To dial USSD codes (`*99#`) for processing payments | Yes |
| `READ_PHONE_STATE` | To detect SIM card presence and network status | Yes |
| `READ_CONTACTS` | To allow selection of payment recipients from your contacts | No (optional) |
| `CAMERA` | To scan QR codes for payment | No (optional) |
| `ACCESSIBILITY_SERVICE` | To read and navigate USSD dialog menus automatically | Yes |

All permissions are used solely for the stated purposes. No data obtained through these permissions is transmitted off the device.

---

## 8. Accessibility Service Disclosure

The App uses Android's Accessibility Service **solely** to interact with USSD dialog windows from the system phone app (`com.android.phone`). It does **not**:
- Read content from any other application
- Log keystrokes, screen content, or browsing activity
- Transmit any data externally
- Access notifications, messages, or any non-USSD content
- Overlay content on other apps

---

## 9. Third-Party Libraries

| Library | Purpose | Data Processing |
|---|---|---|
| Google ML Kit (Barcode Scanning) | QR code reading | Entirely on-device; no image data sent to Google servers |

No other third-party services, SDKs, or analytics tools are integrated. The App contains no advertisements.

---

## 10. Data Retention and Deletion

All data remains on your device until you take one of the following actions:
- **Clear the app's data** via Android Settings
- **Uninstall the app** (all data is permanently deleted)
- **Manually delete records** within the app

There is no server-side data to delete because no data is ever transmitted off your device.

### Right to Erasure (DPDPA, Section 12)

Since all data is stored locally and no Data Fiduciary holds your data, you can exercise your right to erasure at any time by clearing app data or uninstalling the App. No request to any entity is required.

---

## 11. Data Breach Notification (DPDPA, Section 8)

Since the App does not transmit, store, or process data on any server or with any Data Fiduciary, the data breach notification provisions of Section 8 of the DPDPA are not applicable. The security of locally stored data depends on the security of your physical device.

---

## 12. Cross-Border Data Transfer

The App does **not** transfer any personal data outside your device. No cross-border data transfer occurs. The provisions of **Section 16 of the DPDPA** regarding restriction on transfer of personal data outside India are not applicable.

---

## 13. Children's Privacy (DPDPA, Section 9)

This App is not intended for use by anyone under **18 years of age**. It involves financial transactions that require a valid bank account linked to a mobile number. The App does not knowingly process data of children or persons with disabilities acting through lawful guardians.

In compliance with Section 9 of the DPDPA 2023, no data processing that could be detrimental to children is performed.

---

## 14. Grievance Redressal

As no personal data is collected by any entity, formal grievance officer appointment under the DPDPA is not applicable. However, for any privacy-related concerns or questions regarding this policy, you may:

- Open an issue on the project's GitHub repository
- Contact the project maintainers through the repository's communication channels

---

## 15. Changes to This Policy

Changes to this privacy policy will be reflected in the repository with an updated "Last updated" date. Users are encouraged to review this policy periodically. Continued use of the App after changes constitutes acceptance of the updated policy.

---

## 16. Governing Law

This Privacy Policy shall be governed by and construed in accordance with the laws of India, including but not limited to the Information Technology Act, 2000, the Digital Personal Data Protection Act, 2023, and rules framed thereunder. Any disputes shall be subject to the exclusive jurisdiction of the courts in India.

---

## 17. Definitions

- **Personal Data**: Any data about an individual who is identifiable by or in relation to such data (DPDPA, Section 2(t)).
- **Sensitive Personal Data or Information (SPDI)**: As defined in Rule 3 of the SPDI Rules, 2011 — includes financial information, passwords, and phone numbers.
- **Data Fiduciary**: Any person who alone or in conjunction with other persons determines the purpose and means of processing of personal data (DPDPA, Section 2(i)).
- **Data Principal**: The individual to whom the personal data relates (DPDPA, Section 2(j)).
