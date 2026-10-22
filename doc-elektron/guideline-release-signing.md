# Elektron Net: `elektron-net-mobile` Release Signing and Android Developer Verification

- **Version:** 0.1 (draft)
- **Date:** October 22, 2026
- **Audience:** Maintainers who create the release key and run the release workflow
- **Authors:** Elektron Net Developers, Ali Kutlusoy ([@kutlusoy](https://x.com/kutlusoy))
- **Reference implementation:** `.github/workflows/release.yml`, `.github/workflows/prerelease.yml`, `app/build.gradle.kts` (signing configs) in this repository
- **See also:** [`guideline-distribution-and-signing.md`](./guideline-distribution-and-signing.md) (§5 Signing, §6.1 releases, §7 Android Developer Verification)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Two workflows, two keys

| Workflow | Start | Signing key | Result |
|---|---|---|---|
| `prerelease` | manual only ("Run workflow") | public test key `app/test-signing/elektron-test.jks` | GitHub pre-release, application ID with the `.nightly` suffix, never replaces a release install |
| `release` | push of a tag `v*`, or manual | the release keystore from the repository secrets | GitHub release (pre-release only when the version has a suffix such as `-rc1`) |

The `release` workflow stops with an error when a secret is missing, and it stops when an APK carries the public test certificate. It cannot produce a test-signed release by accident. Use `prerelease` for everything that is only a test.

**Pre-releases and releases never update each other.** The signatures differ and the application IDs differ (`.nightly` suffix), so a release cannot update a pre-release and a pre-release cannot update a release. Switching means a fresh install and a new setup with the recovery phrase or an exported wallet file. A pre-release is updated by a newer pre-release, a release by a newer release. The title and notes of a pre-release build say so (title: "Elektron Mobile v<version> (test build, no updates to or from releases)").

A PGP key is not needed. Android only checks the APK signature, and the Android Developer Verification of Google asks for the APK signing certificate, not for PGP. Users verify a download with the SHA-256 of the file and the signing certificate fingerprint printed in the release notes.

## 2. Create the release keystore

Do this once, offline, on a machine you trust.

```
keytool -genkeypair -v -keystore elektron-release.jks -alias elektron-release \
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 36500
```

- The keystore password and the key password MUST be long and unique. Store them in a password manager.
- Keep at least two offline backups of `elektron-release.jks` in different places. **If this file is lost, no update can be signed any more, and every user has to uninstall and reinstall.**
- The key MUST NOT be committed, pasted into chats or stored anywhere except the offline backups and the repository secrets.
- Print the certificate fingerprint, you need it for Google and for the release notes:

```
keytool -list -v -keystore elektron-release.jks -alias elektron-release | grep SHA256
```

## 3. Repository secrets

Settings, Secrets and variables, Actions, "New repository secret":

| Secret | Value |
|---|---|
| `ELEKTRON_KEYSTORE_BASE64` | `base64 -w0 elektron-release.jks` |
| `ELEKTRON_KEYSTORE_PASSWORD` | keystore password |
| `ELEKTRON_KEY_ALIAS` | `elektron-release` |
| `ELEKTRON_KEY_PASSWORD` | key password (optional, defaults to the keystore password) |
| `ELEKTRON_RELEASE_CERT_SHA256` | optional: the expected certificate SHA-256, then every release run fails if the APK carries another certificate |

## 4. Run a release

1. Merge the code and set the wallet version in `app/mobile-version.txt` if needed.
2. Tag the commit `v<node version>-m<wallet version>`, for example `v4.0.6-m0.1.1`, and push the tag. Or start the `release` workflow manually and enter the same version.
3. The workflow builds the node from the matching `elektron-net` release tag, builds both APKs, checks the certificate with `apksigner verify --print-certs`, and publishes the APKs, `SHA256SUMS.txt` and notes with the certificate fingerprint.
4. Compare the printed fingerprint with the one you noted in §2.

Switching a device from a test build to a release build is a fresh install, because the signing keys and application IDs differ. Export the wallet file or keep the recovery phrase first.

## 5. Android Developer Verification

Google is introducing a verification of developers for apps installed on certified Android devices, also for apps outside Google Play. Check the current rules and dates in the Android Developer Console documentation before you register, they change.

- A personal account is enough. Distribution through Google Play is not required, so the restriction of Play on wallet apps for organizations does not apply.
- Register the package names `org.elektronnet.mobile` (mainnet) and the testnet variant, each with the SHA-256 fingerprint of the release certificate from §2.
- Do not register the public test key and do not register the `.nightly` application ID of the pre-releases.
- Keep the release key unchanged for the lifetime of the app. A key change requires Android key rotation and a new registration.
- Keep the account and its identity documents up to date. A lapsed verification can block installs on certified devices.

## 6. Checklist

- [ ] Release keystore created offline (§2), two backups stored
- [ ] Certificate fingerprint noted and added to README and website
- [ ] Repository secrets set (§3)
- [ ] Package names registered with the fingerprint (§5)
- [ ] First release run green, fingerprint in the notes matches
- [ ] Update from the first release to the next one tested on a device
