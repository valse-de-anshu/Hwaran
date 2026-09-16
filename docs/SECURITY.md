# Security Policy & Vault Protection

## 🛡️ Privacy & Offline Security Model

Hwaran is strictly a **local-first, offline media vault**. 

- **Zero Network Telemetry**: The app does not include any analytics libraries, ad SDKs, tracking pixels, or remote diagnostic beacons.
- **Local Storage Isolation**: Media stored inside the internal vault (`manga_vault/`) is shielded by `.nomedia` markers, preventing discovery by third-party gallery scanners.
- **Biometric & PIN Shield**: Sensitive media categories or specific folders can be flagged as locked (`isLocked = true`), requiring device biometric authentication (Fingerprint / Face Unlock) or application PIN entry to unmask.
- **Scoped Storage & SAF**: External media linking is mediated strictly through Android's Storage Access Framework (SAF), ensuring no broad filesystem permissions are abused.

---

## 🚨 Reporting a Vulnerability

If you discover a security issue or vulnerability in Hwaran:

1. **Do not disclose publicly** via GitHub issues.
2. Submit a private vulnerability report via GitHub Security Advisories or contact the maintainers directly.
3. Include:
   - A detailed description of the vulnerability.
   - Steps or proof-of-concept code to reproduce.
   - Potential impact on local file confidentiality or PIN authentication bypass.

We appreciate responsible disclosure and will work to address security issues promptly.
