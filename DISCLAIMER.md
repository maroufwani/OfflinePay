# Legal Disclaimer — Offline Pay

**Last updated:** April 9, 2026

**Applicable jurisdiction:** India

---

## 1. No Affiliation or Endorsement

Offline Pay ("the App") is **NOT** affiliated with, endorsed by, approved by, or in any way officially connected with:

- **National Payments Corporation of India (NPCI)**
- **Reserve Bank of India (RBI)**
- **Any bank or financial institution** in India or elsewhere
- **Any telecom operator** in India or elsewhere
- **Google LLC** or the Android Open Source Project

This is an **independent, community-developed, open-source project**. Any use of the names "NPCI", "RBI", "UPI", "`*99#`", or any bank/operator names is for **descriptive and informational purposes only** and does not imply any affiliation or endorsement.

---

## 2. Not a Payment System or Financial Service

This App is **NOT**:
- A **payment system** as defined under the Payment and Settlement Systems Act, 2007 (PSS Act)
- A **payment aggregator** as defined under the RBI Guidelines on Regulation of Payment Aggregators and Payment Gateways (2020)
- A **payment gateway**, prepaid payment instrument, or banking correspondent
- A **Non-Banking Financial Company (NBFC)** or any regulated financial entity
- A **financial advisor** or provider of financial advice

The App is merely a **user interface** that dials the publicly available `*99#` USSD code and automates navigation of USSD menus. All actual payment processing is performed entirely by NPCI, the user's bank, and the telecom operator.

**No license or authorization under the PSS Act, 2007, is required or held** by the developers of this App.

---

## 3. Risk Acknowledgment

By using this App, you acknowledge and accept the following risks:

### 3.1 Transaction Risks
- **Failed transactions**: USSD sessions may time out, be interrupted, or fail without completing the transaction.
- **Incorrect transactions**: Automation of USSD menus involves parsing dynamic dialog content. Errors in parsing could result in incorrect amounts, wrong recipients, or unintended transactions.
- **Duplicate transactions**: Network issues or USSD session interruptions may cause transactions to be processed multiple times.
- **Irreversible transactions**: Once a transaction is processed by the banking system, it **cannot be reversed** through the App.

### 3.2 Account and Service Risks
- **Account suspension**: Your bank may suspend or restrict your account for automated USSD access, as this may violate their terms of service.
- **Service blocking**: Your telecom operator may block or restrict USSD access from your number.
- **SIM blocking**: Excessive USSD dialing may trigger anti-fraud mechanisms by your telecom operator.
- **`*99#` service changes**: NPCI or banks may modify the `*99#` USSD menu structure at any time, which could break the App's functionality without warning.

### 3.3 Device and Security Risks
- **Device compromise**: If your device is compromised (malware, unauthorized physical access), locally stored data including transaction history could be accessed.
- **Accessibility Service abuse**: While the App's Accessibility Service only accesses USSD dialogs, a modified version of the App could potentially access other on-screen content. Only use the official, unmodified App.
- **No guarantee of encryption**: While the App implements encryption, the security of locally stored data ultimately depends on the integrity of the Android OS and hardware on your device.

---

## 4. No Warranty

THE APP IS PROVIDED **"AS IS"** AND **"AS AVAILABLE"** WITHOUT WARRANTY OF ANY KIND.

THE DEVELOPERS **EXPRESSLY DISCLAIM** ALL WARRANTIES, WHETHER EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE, INCLUDING BUT NOT LIMITED TO IMPLIED WARRANTIES OF:
- **MERCHANTABILITY**
- **FITNESS FOR A PARTICULAR PURPOSE**
- **NON-INFRINGEMENT**
- **ACCURACY OR RELIABILITY**

---

## 5. Limitation of Liability

TO THE FULLEST EXTENT PERMITTED BY APPLICABLE LAW IN INDIA, INCLUDING THE **INDIAN CONTRACT ACT, 1872**, AND THE **CONSUMER PROTECTION ACT, 2019**:

IN NO EVENT SHALL THE DEVELOPERS, CONTRIBUTORS, OR MAINTAINERS BE LIABLE FOR:

| Category | Examples |
|---|---|
| Financial loss | Failed, incorrect, or duplicate transactions; unauthorized transactions |
| Account issues | Bank account suspension, restriction, or closure |
| Service disruption | USSD service unavailability, telecom issues |
| Data loss | Loss of locally stored data due to device failure, theft, or OS updates |
| Indirect damages | Loss of profits, business interruption, loss of goodwill |
| Consequential damages | Any downstream effects of any of the above |

**THE TOTAL LIABILITY OF THE DEVELOPERS SHALL NOT EXCEED INR 0 (ZERO)**, as the App is distributed free of charge without any commercial consideration.

---

## 6. Regulatory and Legal Compliance — User Responsibility

Users are **solely responsible** for ensuring their use of the App complies with:

| Regulation | Authority | Relevance |
|---|---|---|
| `*99#` Service Terms | NPCI / Banks | Governs use of the USSD payment service |
| Digital Payment Guidelines | RBI | Regulates digital payment practices in India |
| Payment and Settlement Systems Act, 2007 | Parliament of India | Framework for payment system regulation |
| Information Technology Act, 2000 | Parliament of India | Cybersecurity and electronic transactions |
| Indian Telegraph Act, 1885 | Parliament of India | Governs use of telecom services including USSD |
| Prevention of Money Laundering Act, 2002 | Parliament of India | AML/CFT compliance |
| Foreign Exchange Management Act, 1999 | Parliament of India / RBI | Cross-border transaction regulations |
| Consumer Protection Act, 2019 | Parliament of India | Consumer rights and e-commerce rules |
| Aadhaar Act, 2016 | Parliament of India | If Aadhaar-linked services are used |
| Income Tax Act, 1961 | Parliament of India | Tax reporting obligations for transactions |
| Bank-specific Terms of Service | Individual banks | Each bank's specific rules for `*99#` |
| Telecom Operator Terms | Individual operators | USSD usage policies |

---

## 7. Accessibility Service Disclaimer

The App uses Android's Accessibility Service to automate interaction with USSD dialogs. This approach:

- **May not comply with Google Play Store policies** for Accessibility Service usage. The App is intended for **sideloading only**.
- **May behave differently** across different Android versions, device manufacturers, and telecom operators.
- **May break** if the system USSD dialog format changes (OEM-specific or Android version updates).
- **Is not guaranteed** to work on all devices or with all banks.

The developers are **not responsible** for any issues arising from the use of the Accessibility Service.

---

## 8. Open-Source Disclaimer

This App is open-source software distributed under the Apache License, Version 2.0.

- **No commercial support** is provided.
- **No service-level agreement (SLA)** exists.
- **Community contributions** are voluntary and not guaranteed.
- **Forked or modified versions** of the App are the responsibility of their respective distributors.

If you obtained this App from a source other than the official repository, the developers are **not responsible** for any modifications, malware, or issues introduced by third parties.

---

## 9. Indemnification

You agree to indemnify and hold harmless the developers, contributors, and maintainers from any and all claims, damages, losses, and expenses (including legal fees) arising from:

- Your use of the App
- Any financial transactions you initiate
- Violation of these terms or any applicable law
- Any third-party claims related to your use

---

## 10. Governing Law and Jurisdiction

This Disclaimer shall be governed by and construed in accordance with the **laws of India**. Any disputes shall be subject to the exclusive jurisdiction of the **courts in India**, subject to the arbitration provisions in the [Terms of Service](TERMS_OF_SERVICE.md).

---

## 11. Acknowledgment

**BY INSTALLING OR USING THIS APP, YOU ACKNOWLEDGE THAT:**

1. You have read and understood this Disclaimer, the [Terms of Service](TERMS_OF_SERVICE.md), and the [Privacy Policy](PRIVACY_POLICY.md).
2. You understand the **risks** of automating USSD-based financial transactions.
3. You accept **full responsibility** for all transactions initiated through the App.
4. You will **not hold the developers liable** for any financial loss, account issues, or other damages.
5. You are of **legal age (18+)** and have a **valid Indian bank account** with `*99#` service enabled.
6. You understand this is **community-developed open-source software** and not a commercial product.

---

## 12. Contact

For questions about this Disclaimer, please open an issue on the project's GitHub repository.
