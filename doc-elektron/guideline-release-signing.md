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

Bash:

```
keytool -genkeypair -v -keystore elektron-release.jks -alias elektron-release \
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 50040
```

PowerShell (the line continuation is a backtick, not a backslash):

```
keytool -genkeypair -v -keystore elektron-release.jks -alias elektron-release `
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 50040
```

Both work as one single line as well.

- `-validity 50040` is 137 years, valid until 2163 (a symbolic number, Android only needs the certificate to outlive the app). The keystore password and the key password MUST be long and unique. Store them in a password manager.
- Keep at least two offline backups of `elektron-release.jks` in different places. **If this file is lost, no update can be signed any more, and every user has to uninstall and reinstall.**
- The key MUST NOT be committed, pasted into chats or stored anywhere except the offline backups and the repository secrets.
- Print the certificate fingerprint, you need it for Google and for the release notes:

Bash:

```
keytool -list -v -keystore elektron-release.jks -alias elektron-release | grep SHA256
```

PowerShell:

```
keytool -list -v -keystore elektron-release.jks -alias elektron-release | Select-String SHA256
```

The `SHA256:` line under "Certificate fingerprints" is the fingerprint.

## 3. Repository secrets

Settings, Secrets and variables, Actions, "New repository secret":

| Secret | Value |
|---|---|
| `ELEKTRON_KEYSTORE_BASE64` | the Base64 text of the keystore file, see below |
| `ELEKTRON_KEYSTORE_PASSWORD` | keystore password |
| `ELEKTRON_KEY_ALIAS` | `elektron-release` |
| `ELEKTRON_KEY_PASSWORD` | key password (optional, defaults to the keystore password) |
| `ELEKTRON_RELEASE_CERT_SHA256` | optional: the expected certificate SHA-256, then every release run fails if the APK carries another certificate |

The keystore value is one very long line, so the easiest way is to write it to a text file and copy it from there.

Windows (PowerShell, not `cmd`), run in the folder that holds the keystore:

```powershell
cd C:\path\to\folder
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\elektron-release.jks).Path)) | Out-File -Encoding ascii -NoNewline keystore-base64.txt
notepad keystore-base64.txt
```

Linux and macOS:

```bash
base64 -w0 elektron-release.jks > keystore-base64.txt
```

Open `keystore-base64.txt`, select everything (Ctrl+A), copy it (Ctrl+C) and paste it into the secret field `ELEKTRON_KEYSTORE_BASE64`. The file must be one single line with no spaces and no line breaks, and it does not need to be saved again. When the secret is stored, delete `keystore-base64.txt`, because it contains your signing key in readable form.

`Resolve-Path` is needed because .NET resolves a bare file name against its own working directory, which can differ from the folder shown by PowerShell, and then reports a missing file. To copy to the clipboard without a file, replace `Out-File ...` with `Set-Clipboard`.

## 4. Run a release

1. Merge the code and set the wallet version in `app/mobile-version.txt` if needed.
2. Tag the commit `v<node version>-m<wallet version>`, for example `v4.0.6-m0.1.1`, and push the tag. Or start the `release` workflow manually and enter the same version.
3. The workflow builds the node from the matching `elektron-net` release tag, builds both APKs, checks the certificate with `apksigner verify --print-certs`, and publishes the APKs, `SHA256SUMS.txt` and notes with the certificate fingerprint.
4. Compare the printed fingerprint with the one you noted in §2.

Switching a device from a test build to a release build is a fresh install, because the signing keys and application IDs differ. Export the wallet file or keep the recovery phrase first.

## 5. Android Developer Verification

Google is introducing a verification of developers for apps installed on certified Android devices, also for apps outside Google Play. Check the current rules and dates in the Android Developer Console documentation before you register, they change. The registration is described on [Register Android package names](https://support.google.com/googleplay/android-developer/answer/16761053) (the German page is machine translated, read the English one when in doubt).

- A personal account is enough. Distribution through Google Play is not required, so the restriction of Play on wallet apps for organizations does not apply.
- Register the package names `org.elektronnet.mobile` (mainnet) and `org.elektronnet.mobiletestnet`, one after the other, each with the SHA-256 fingerprint of the release certificate from §2. The console accepts the fingerprint with colons, as `keytool` prints it.
- Do not register the public test key and do not register the `.nightly` or `.debug` application IDs.
- Keep the release key unchanged for the lifetime of the app. A key change requires Android key rotation and a new registration.
- Keep the account and its identity documents up to date. A lapsed verification can block installs on certified devices.

### 5.1 Steps in the Play Console

1. Open "Identity verification for Android developers", tab "Packages", and choose "Register package name".
2. Enter the package name and a friendly name, then continue.
3. A package name that was never used on Android needs only the public certificate: choose "Add key", enter the SHA-256 fingerprint and confirm.
4. A package name that already has installations shows a list of permitted fingerprints. Choose the release fingerprint, or ask Google for an exception with a reason if it is not listed.
5. The console then asks for a proof of ownership: an APK signed with the private release key that contains the identifier of the account (the snippet). The uploaded APK is only used to check the key, it is not published.

### 5.2 Proof of ownership with the release APK

- The snippet is stored in `app/src/main/assets/adi-registration.properties` of this repository. The file name is fixed, and the content is the snippet that the console shows. Both flavors share the `assets` folder, so one file covers both package names when the console shows the same snippet for both. If the snippets differ, keep the mainnet snippet in `app/src/main/assets/` and put the testnet snippet in `app/src/tnet/assets/adi-registration.properties`, which replaces it for the testnet build.
- Any release APK built by the `release` workflow contains the file and is signed with the release key, so the APK of a normal release can be uploaded in the console under "Upload". Android checks the signature and registers the ownership.
- The snippet identifies the account and is not a secret. Whether the file may be removed again after the registration is not stated on the Google page, so it stays in the repository.
- When the console shows an upload dialog for the second package name, upload the testnet APK of the same release.

## 6. Checklist

- [ ] Release keystore created offline (§2), two backups stored
- [ ] Certificate fingerprint noted and added to README and website
- [ ] Repository secrets set (§3)
- [ ] Package names registered with the fingerprint (§5.1), proof of ownership uploaded when the console asks for it (§5.2)
- [ ] First release run green, fingerprint in the notes matches
- [ ] Update from the first release to the next one tested on a device
