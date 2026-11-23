# JARVIS-Mobile Security Audit — Handover Report

**Audit date:** 23 November 2026  
**Repository:** [sakib-s-projects/JARVIS-Mobile](https://github.com/sakib-s-projects/JARVIS-Mobile)  
**Branch:** `jarvis-branding`  
**Security-relevant source reviewed:** `36f3456d0b73b8d5f7adfee669874e6430fc49d2`  
**Handover report commit:** `3b6733518b2efa4aecf080042e249cede7c9da1d` (report-only change after the reviewed source)  
**Overall result:** Source review substantially completed; **not install-ready / no security clearance granted**.

> This is a source-and-build-pipeline audit, not a proof that the application is free of malware. It does not replace testing on a physical Android 10 device, a full resolved-dependency vulnerability scan, or a clean build and signature check of the exact final APK.

## 1. Executive summary

The reviewed source contains several strong protections: the app disables Android Auto Backup and cleartext traffic; most app components are private; Android-protected system services require their platform binding permissions; provider connections use fixed HTTPS endpoints and avoid redirects; OAuth uses PKCE; provider credentials are encrypted with Android Keystore; extension actions are narrowly allowlisted; routine triggers stop before actions requiring confirmation; and updater/release code verifies an APK hash, package/version and a pinned signing certificate.

No confirmed covert telemetry channel, hidden always-on wakeword loop, remote shell/JavaScript execution facility, or direct call/SMS sending path was identified in the reviewed security-relevant files.

Three concrete local-data / credential hardening changes were made:

1. **Relay bearer secret:** removed the plaintext SharedPreferences fallback. Legacy plaintext values are migrated into Android Keystore once or discarded; the settings save now fails closed if encryption cannot be completed.
2. **Pending-request image retention:** expired/evicted request records now remove unreferenced cached screenshot files, with canonical-path restriction to the app's own pending-attachment directory.
3. **Current-screen retention:** when an expired (30-minute) screen snapshot is next loaded, both its metadata and private thumbnail are deleted, not merely hidden from callers.

A SiteTaskStore ordering/cap edge case was also corrected and tested: equal creation timestamps no longer let a newer insertion be dropped by the item cap.

However, the current branch has not produced a successful post-fix test-and-build run. Workflow #18 on the report commit (`3b6733518b2efa4aecf080042e249cede7c9da1d`) was retried; both attempts failed at the unit-test step because Robolectric's `nativeruntime-dist-compat:1.0.18` dependency could not be retrieved from Maven Central (HTTP 429). Main and test Java compilation completed in the retry, but the workflow skipped `assembleDebug` and artifact upload. An earlier run against the immediately preceding source completed 10,089 tests with 15 failures and 42 skips. The available older APK is from before these fixes and is debug-signed, not signed with the release certificate pinned in source.

**Recommendation: DO NOT install this branch as a trusted daily-use or production build yet.** Treat the audit as a useful source review with fixes applied, not as a completed verification of an installable binary.

## 2. Findings by category

### A. Confirmed safe / good controls observed

| Area | Evidence in reviewed source | Assessment |
|---|---|---|
| Android backup and transport defaults | `app/src/main/AndroidManifest.xml`: `android:allowBackup="false"`, `android:usesCleartextTraffic="false"` | Reduces accidental OS backup and blocks ordinary cleartext app traffic. Does not make the app's own export files encrypted. |
| Component exposure | `AndroidManifest.xml`: internal Activities and receivers are `exported=false`; FileProvider is unexported and grants URI permissions only. | Narrow exported surface. |
| Protected system services | `OrbitVoiceInteractionService` and `OrbitSessionService` require `BIND_VOICE_INTERACTION`; notification listener requires `BIND_NOTIFICATION_LISTENER_SERVICE`; Quick Settings tiles require `BIND_QUICK_SETTINGS_TILE`. | Exported by Android contract, but protected by system signature permissions. |
| External share/selection entry points | `ShareToOrbitActivity`, `ProcessTextToOrbitActivity` validate MIME/input, stage content behind a private one-use token, and only place content in a composer/Vault after explicit user choice. | These are intentional exported doors, not general-purpose app command endpoints. Shared data is not sent until the user submits it. |
| Other exported Activities | `MainActivity` is the launcher/ASSIST surface. `ChatGptSignInReturnActivity` accepts only the app's browsable return scheme and ignores incoming URI data; it only returns to the in-memory screen that initiated sign-in. `OrbitWidgetConfigureActivity` accepts `APPWIDGET_CONFIGURE`, rejects an ID not owned by Orbit via `OrbitWidgets.isOwnedConfiguration()`, and edits that widget's configuration. | These additional exported surfaces were reviewed; no arbitrary command or credential-input bridge was identified in them. |
| File sharing | `app/src/main/res/xml/file_paths.xml` contains narrow cache subpaths; FileProvider is unexported. | No broad filesystem root exposed in reviewed configuration. |
| Provider destinations | `ApiKeyProviderClient.java` uses fixed Anthropic, xAI and OpenRouter HTTPS endpoints; `RelayProvider.java` requires HTTPS and uses `HttpConnectionSecurity.applyNoRedirects`. | No arbitrary provider URL is accepted for the built-in API-key providers. The user-configured relay remains a user trust decision. |
| OAuth and provider credentials | `ChatGptBrowserAuth.java`, `OpenRouterAuth.java`: PKCE S256, random state/verifier, loopback callback and state checks. `SecureStore.java`: AES/GCM with Android Keystore for provider/API credentials. | Stronger than ordinary preference storage; credentials are not included in the portable backup settings snapshot. |
| Provider payload controls | `ProviderRequestMapper.java`: selected provider receives bounded conversation/prompt/context; screen/image/notification data are conditional on the user attaching/asking for them. System prompt marks files, screen content, notifications and quoted messages as untrusted. | Data does leave the phone when the user submits a request to a cloud provider; it is not an automatic upload path. |
| Extensions | `OrbitExtension.java`, `OrbitExtensionV2.java`, `OrbitExtensionActionExecutor.java`: 64 KB manifest cap, 1–20 actions, only `open_url` / `https_request`, HTTPS-only network action, method/time/body/response bounds, local/private address checks, redirect disabled in executor; no shell, JS, arbitrary class loading or arbitrary code execution. | Narrower than a general plugin runtime. A malicious extension can still send information to its declared external endpoint if the user installs/runs it and supplies secrets. |
| Extension secrets | `OrbitExtensionSecretStore.java`: Android Keystore AES-GCM, app-private storage, bounded secret values and clear-on-removal path. | Better at-rest handling; does not guarantee the endpoint is trustworthy. |
| Background routines | `RoutineTriggerExecution.java`: automatic runs stop before an action requiring user confirmation or foreground visibility and issue a continuation notification. `LocationRoutineTriggerReceiver.java` validates expected transition and has a 120-second retrigger guard. Declared extension `https_request` actions are intentionally headless and are the exception: see privacy risk 8 below. | Built-in actions do not bypass confirmation. A user-selected extension HTTPS request can run from a background Routine after its destination/configuration have been reviewed. |
| Voice | `VoiceInputController.java`: runtime microphone permission check; mic starts through explicit interaction or optional user-enabled auto-listen settings, both default false; stop/cancel paths disown active voice turn. | No hidden app-owned always-on wakeword loop was found. System speech recognition may handle audio according to the selected recognition service. |
| Camera and attachments | `AttachmentPickerActivity.java`: camera opened only from an explicit attachment path after CAMERA permission; uses FileProvider temporary output; gallery/documents use Android picker URI grants; cancelled/processed camera temp file is cleaned. | No covert camera capture behavior was identified. CAMERA is also required for flashlight control. |
| Updater integrity checks | `OrbitUpdater.java:445–482, 719–764`; `OrbitLocalInstaller.java:249–280`: HTTPS source, exact expected asset naming, SHA-256, package/version, one signer and pinned release-certificate validation before installer handoff. | Integrity checks are substantial; the redirect-host and repository-origin questions below remain unresolved. |
| Release pipeline controls | `.github/workflows/release.yml`: tag/source-version check, temporary release keystore from Actions secrets, package/version/signer/certificate checks, APK/component SHA-256, generated manifest/checksums, sequential upload, keystore cleanup. | Good release-pipeline controls visible in source; this workflow was not run for the audited final source and its secrets were not independently verified. |

### B. Privacy risks (not evidence of malware)

1. **Notification content is locally retained in readable app-private preferences.**  
   `NotificationStore.java:18–23, 84–103, 121–135, 195–197, 278–286` stores notification title/text/conversation details in `orbit_notification_history`, with a 700-item cap and retention controlled by `Prefs.notificationRetentionDays` (default 7 days, clamped to 1–30). The listener requires the Android Notification Access grant; app blocking and clear-history controls exist. `Prefs.notificationAiEnabled` defaults true, so once Notification Access is granted, the app may include bounded notification context when the user explicitly asks a notification-aware question. Notification bodies are not included in the custom backup snapshot.

2. **Chat history and local memory are app-private, but not encrypted by this app layer.**  
   `ConversationStore.java:21–26, 179–213, 310–325, 1075–1080, 1220–1223` stores conversation JSON in private SharedPreferences (up to 100 chats / 40 messages per chat). `MemoryStore.java:20–24, 177–202, 569–597` stores user memory in private SharedPreferences. These stores are not Android Auto Backup data because `allowBackup=false`, but a compromised/rooted device or an in-process compromise changes the threat model. History can be disabled/cleared and memories deleted/cleared.

3. **Portable backup is plaintext JSON and can contain sensitive user content.**  
   `OrbitBackupManager.java:70–116, 137–175, 185–215` exports chats, memories, routines, commands, reminders, site tasks, extensions, Vault data and attachments into a `.orbitbackup` JSON file. It is user initiated through a selected document URI; that URI may be backed by a cloud document provider. Credentials and provider connection details are excluded, and extension secrets are cleared on successful restore, but the backup file itself is not password-encrypted. Treat it like a sensitive document. No automatic Google Drive API upload/sync implementation was found in the reviewed source.

4. **Screen and attachment content can be sent to cloud AI when the user chooses to include it.**  
   `ProviderRequestMapper.java:93–136, 143–175` serializes selected conversation history, explicit screen text/images, kept context and memory; notification context is included when provided. `RelayProvider.java:96–149` includes task, notification, screen and history fields according to request/settings. This is an intentional feature, but privacy depends on the selected provider/relay and the content the user submits.

5. **Screen-context snapshot is only deleted on access/next invocation.**  
   `LastScreenStore.java:14–15, 42–76, 79–88` now purges an expired 30-minute snapshot when `load()` is next called. It does not schedule a timer to delete data at minute 30 if the app is never opened or the snapshot is never inspected again. It remains in the app-private area until the next cleanup access or next `begin()`.

6. **Failed pending requests retain local screenshot files for retry up to seven days.**  
   `PendingRequestStore.java` retains terminal items for its seven-day retry/retention window and caps history at 100. The new cleanup removes orphan screenshots when records are expired/evicted. Data remains app-private; it is not evidence of network transfer.

7. **Broad but feature-related Android permissions.**  
   The manifest requests contacts, calendar read/write, background/fine/coarse location, camera, microphone, write settings, DND policy, exact alarms, package install/delete and notification access-related capabilities. The source gates these through user-facing features, runtime permission flows or Android system settings. They still increase the impact of a compromised app and should be granted only for features actually used.

8. **A configured extension HTTPS request can run in the background from an automatic Routine.**  
   `RoutineActionCatalog.java:257–278` constructs extension Routine actions with `requiresConfirmation=false`. `RoutineTriggerExecution.java:72–80, 225–237` allows a declared `https_request` through the background-safety gate because it is treated as headless; a saved time/location-triggered Routine can therefore execute the extension's configured GET/POST while Orbit is not visible. The extension execution path is bounded, requires public HTTPS, disables redirects, and only sends the declared request template, setup values/secrets and Routine parameters. The install review in `ExtensionsActivity.java:432–449` displays the actions, network destinations, request details and an isolation notice stating that chats, Memory, screen context, files, other credentials and Android permission state are not exposed to the extension. This is an intentional, explicitly configured outbound capability rather than covert telemetry, but it can disclose the values a user configured for that endpoint without a fresh foreground confirmation each time. Review the host, secret header/setup fields and Routine trigger before enabling unattended execution.

### C. Suspicious / needs explicit clarification

1. **Updater repository differs from this fork.**  
   `app/src/github/java/com/orbit/assistant/OrbitEdition.java` hardcodes `REPOSITORY = "lpnovi/Orbit-Assistant"` for its stable/beta release API and asset download base. The audited repository is `sakib-s-projects/JARVIS-Mobile`; its `release.yml` publishes releases to the current GitHub repository. The current fork had no GitHub Releases returned by the repository releases endpoint during the audit. This may be intentional upstream branding, but it means the updater is not configured to track this fork's release pipeline. Resolve the intended release owner/repository before distribution. No change was made because the correct permanent update origin and signing identity must be decided first.

2. **OpenRouter attribution also names upstream.**  
   `ApiKeyProviderClient.java:35–37` sends the public Referer `https://github.com/lpnovi/Orbit-Assistant` and title `Orbit Assistant`. This is attribution metadata, not a secret, but should be confirmed if this fork is distributed under a separate project identity.

No evidence of malicious behavior was confirmed in these points; they are origin/ownership mismatches requiring an explicit decision.

### D. Unresolved security work / release blockers

1. **Updater redirect destination is not hostname-pinned.**  
   `OrbitUpdater.java:749–765` starts with HTTPS and checks that the final redirected URL is also HTTPS, but `open()` does not turn off redirects and `requireHttpsFinalUrl()` checks only the scheme, not a strict allowlist of final hostnames. The same pattern appears in `OrbitLocalInstaller.java:504–521`. The APK hash and pinned certificate checks are valuable backstops, so this is not a demonstrated signature bypass; however, the expected GitHub release/CDN redirect destinations should be explicitly allowlisted and tested. A redirect to an unexpected HTTPS host could still affect metadata/availability and unnecessarily broaden trust.

2. **Extension DNS rebinding / TOCTOU gap.**  
   `OrbitExtension.java:319–357` resolves the hostname and rejects local/private/non-public addresses; `OrbitExtensionActionExecutor.java:100–127` resolves/revalidates immediately before opening the connection and disables redirects. But the HTTP stack performs its own DNS resolution after validation rather than connecting to the exact validated IP. A DNS change between validation and connect could bypass the intended address check. No exploit was demonstrated. A fix needs a transport that pins the vetted resolved address while preserving TLS hostname verification; a superficial second lookup is not sufficient.

3. **No complete transitive dependency/SBOM or vulnerability-alert result was available.**  
   The repository has no Gradle dependency lockfiles or `gradle/verification-metadata.xml` in the reviewed branch; the GitHub dependency graph/SBOM/Dependabot and code/secret-scanning endpoints were not available through the connected repository access in this audit. Direct versions were identified, but the complete resolved runtime graph was not independently scanned. Therefore this report cannot certify “no known vulnerabilities”.

4. **Workflow actions are referenced by mutable major tags.**  
   `.github/workflows/build-apk.yml`, `release.yml`, and `candidate.yml` reference Actions such as `actions/checkout@v7`, `actions/setup-java@v5`, `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v6`, and `actions/upload-artifact@v7`. Pinning each third-party Action to a reviewed full commit SHA and enabling Dependabot updates would strengthen build supply-chain integrity.

5. **Current-branch test/build is not green.**  
   See section 4. A successful old build must not be used to infer that these code changes compile, test or produce a secure APK.

6. **No signed release APK from the final audited commit was retrieved.**  
   The source includes a release certificate pin, and the release workflow is designed to verify it, but there was no final release artifact from the current branch available for independent inspection. The previously available debug artifact is not a substitute.

## 3. Changes applied on `jarvis-branding`

The following changes were pushed directly to the audited branch. They do not recreate existing features; they tighten the existing storage/retention behavior and correct a tested collection-order edge case.

| Change | Files | Intent / evidence |
|---|---|---|
| Remove relay token plaintext fallback | `app/src/main/java/com/orbit/assistant/SecureStore.java`, `Prefs.java` | Relay bearer token now uses Android Keystore only. Legacy plaintext is migrated once if possible, otherwise discarded. Saving settings returns an error if the secret cannot be encrypted; URL is not committed on that failure. |
| Add relay credential tests | `app/src/test/java/com/orbit/assistant/RelayCredentialSecurityTest.java` | Tests encrypted-only storage, legacy migration/discard, failed-save behavior and backup exclusion. |
| Fix SiteTaskStore ordering at equal timestamps | `SiteTaskStore.java`, `SiteTaskStoreTest.java` | New insertions go to the front when timestamps tie, so the count cap does not evict the newest task. Test forces a timestamp collision. |
| Remove orphaned pending screenshots | `PendingRequestStore.java`, `AttachmentStore.java` | Trim now finds removed expired/evicted terminal requests and deletes only their unreferenced screenshot files. Canonical path check restricts deletion to `filesDir/orbit_attachments/pending/`. |
| Add pending-image retention test | `app/src/test/java/com/orbit/assistant/PendingRequestRetentionTest.java` | Creates a failed request with a private screenshot, expires its retention timestamp and asserts the screenshot and request metadata are removed. |
| Purge expired current-screen snapshot | `LastScreenStore.java` | On next `load()` after the 30-minute age limit, delete the private image and clear text/path/package/label/timestamp preferences. |
| Add current-screen retention tests | `app/src/test/java/com/orbit/assistant/LastScreenStoreRetentionTest.java` | Tests expired thumbnail and metadata deletion plus fresh snapshot availability. |

**Implementation caution:** no changes were made to updater origin, redirect policy or extension DNS transport because those need the release-owner decision / a properly designed and tested transport change. No permission was removed just to make the manifest look smaller; these permissions map to documented features.

## 4. Tests and GitHub Actions evidence

Workflow: [Build Orbit APK](https://github.com/sakib-s-projects/JARVIS-Mobile/actions/workflows/build-apk.yml)

- Earlier successful run: [#2 / 44921368235](https://github.com/sakib-s-projects/JARVIS-Mobile/actions/runs/44921368235), source `0a49f0db9eb561c76116e8ba2f3d56b3deee9026`, artifact `Orbit-Assistant-v0.8.5.0-debug`. This predates the audit hardening and the build workflow at that point did not demonstrate the current test suite. It is not proof that the audited changes work.
- Run [#3 / 45113125998](https://github.com/sakib-s-projects/JARVIS-Mobile/actions/runs/45113125998) exposed a Java effectively-final lambda compile problem in `SiteTaskStoreTest`; it was fixed.
- Run [#8 / 45115503632](https://github.com/sakib-s-projects/JARVIS-Mobile/actions/runs/45115503632), commit `c3153f60907bcfa8bf24f337f7d63a1bde190529`: **10,084 tests completed, 14 failed, 42 skipped**. The SiteTaskStore cap test failure from earlier runs was gone. Remaining failures were in existing AskOrbitProcessText, CalendarActionRouting, ComponentInstallHandoff, LeloEasterEgg, OrbitDialogContract and OrbitNavigationPolicy test suites. They were not treated as security vulnerabilities and were not silenced.
- Run [#9 / 45115515437](https://github.com/sakib-s-projects/JARVIS-Mobile/actions/runs/45115515437), commit `64301d53cf64ae4590c54f8e332e7ecaa59283ce`: failed resolving Robolectric `nativeruntime-dist-compat:1.0.18`; Maven Central returned HTTP 429 Too Many Requests.
- Run [#16 / 45116153558](https://github.com/sakib-s-projects/JARVIS-Mobile/actions/runs/45116153558), commit `582577daef830f3ca3236dad1d536fe6955fc976`: again blocked by Maven Central HTTP 429 on `nativeruntime-dist-compat:1.0.18`. The unit-test step failed before test execution; the APK build step was skipped.
- Run [#17 / 45116165273](https://github.com/sakib-s-projects/JARVIS-Mobile/actions/runs/45116165273), commit `36f3456d0b73b8d5f7adfee669874e6430fc49d2`: completed **10,089 tests, 15 failed, 42 skipped**. Failures included stale JARVIS-vs-Orbit branding expectations (`AskOrbitProcessTextTest`, `LeloEasterEggTest`), plus `CalendarActionRoutingTest`, `ComponentInstallHandoffTest`, `OrbitDialogContractTest`, `OrbitNavigationPolicyTest`, and `OrbitVaultStoreTest`. These were not dismissed or suppressed; they need diagnosis and a clean rerun.
- Run [#18 / 45116827770](https://github.com/sakib-s-projects/JARVIS-Mobile/actions/runs/45116827770), report commit `3b6733518b2efa4aecf080042e249cede7c9da1d`: attempt 1 ran 543 test cases and logged 252 test setup failures at Robolectric `MavenArtifactFetcher.java:129` with dependency-download I/O errors. I explicitly retried failed jobs (attempt 2); it then failed before test execution because Maven Central returned **HTTP 429** while downloading `org.robolectric:nativeruntime-dist-compat:1.0.18`. Both production and unit-test Java compilation succeeded in attempt 2. `assembleDebug` and artifact upload were skipped by the workflow gate. The run's artifacts endpoint returned zero artifacts.

The workflow explicitly executes `./gradlew --no-daemon testDebugUnitTest` before `clean assembleDebug`. A test failure or dependency-resolution failure prevents the APK build and upload. No test filter was added and no test was skipped to force an artifact.

## 5. Dependency review

Direct versions in `app/build.gradle` and `local/build.gradle`:

| Dependency | Declared version | Review note |
|---|---:|---|
| AndroidX Core | 1.16.0 | No direct issue confirmed in this source-only pass. |
| WorkManager | 2.11.2 | Used for queued/background work; transitive graph not scanned. |
| PDFBox Android | 2.0.27.0 | Old release (2 January 2023); no newer release was found in its project listing during review. Apache PDFBox's current security page describes recent CVEs as affecting its `examples` module, not the core library, but that does not substitute for validating this Android wrapper and its transitive dependencies. |
| Play Services ML Kit Text Recognition | 19.0.1 | Behind the current 19.0.2 release listed in Google ML Kit release notes (26 October 2026); update and retest is recommended. |
| MediaPipe Tasks GenAI (`:local` module) | 0.10.35 | Isolated in the optional local component rather than the main app. Transitive/native package scan still required. |
| JUnit / Robolectric | 4.13.2 / 4.16 | Test-only; Robolectric dependency download was rate-limited during this audit. |

The Gradle wrapper is 8.14.8 with a pinned distribution SHA-256 and URL validation. No CVE claim is made for the full dependency tree because it was not resolved and scanned in this audit. Recommended follow-up: generate an SBOM from the exact release variant, run dependency review/Dependabot or equivalent SCA, record exceptions, and enable Gradle dependency verification/locks.

External references:
- [Google ML Kit release notes](https://developers.google.com/ml-kit/release-notes)
- [Apache PDFBox security page](https://pdfbox.apache.org/security.html)
- [PDFBox Android release history](https://github.com/TomRoush/PdfBox-Android/releases)
- [Gradle 8.14.8 release notes](https://docs.gradle.org/8.14.8/release-notes.html)

## 6. APK provenance / signature inspection

The only downloadable artifact inspected was the older **debug** artifact from run #2 (not the final source):

- Workflow artifact: `Orbit-Assistant-v0.8.5.0-debug`, artifact ID `14714480111`.
- ZIP size: 14,632,783 bytes.
- GitHub artifact digest: `sha256:a54386b1350a79aec6ea96d5373c022c7e715a28884bc60482e0485a33cdee48`.
- ZIP integrity: archive test passed (656 entries).
- Included `app-debug.apk`: 28,572,201 bytes; SHA-256 `f8e5b400143069911f6a7e65ac7b2e42495a7524b0b3f34615815a232f843bad`.
- Custom APK v2 signature-block verification checked the content digest and RSA/SHA-256 signature; one signer was present. Certificate DN was `C=US,O=Android,CN=Android Debug`, SHA-256 fingerprint `CF:2A:74:5A:45:01:5D:94:3C:E9:41:5A:0A:7C:DD:9D:F7:D2:88:96:B0:02:29:7E:2E:AD:53:B3:75:F0:4E:B8`.

The source/release workflow pins a different permanent release certificate:
`7D:AD:61:93:85:DF:F1:1E:C7:31:AA:55:5F:2B:44:8A:94:3C:73:91:81:3D:1A:94:DF:1C:B4:23:2E:CD:41:E3`.

Therefore the old artifact was an internally signed **debug** APK, not a release-signed APK matching the pinned production identity. It predates the above changes. It was not the user's original third-party APK, and no conclusion about that separate binary is made here. No final-commit APK was available for package/signature inspection.

## 7. Prioritized next actions before installation

1. Get a successful test-and-build run from the current source. Investigate the 15 assertion failures recorded in run #17, then rerun when Maven Central is serving Robolectric artifacts. Do not bypass tests just to get an APK.
2. Confirm whether the JARVIS fork or `lpnovi/Orbit-Assistant` is the authoritative update/release repository. Update updater API/asset origin and OpenRouter attribution only after the owner confirms the intended identity.
3. Harden updater and Orbit Local redirects with an explicit allowlist of the exact GitHub release/CDN hosts expected for the selected distribution, and add tests for rejected cross-origin HTTPS redirects.
4. Close the extension DNS rebinding gap by pinning the validated IP at the transport layer while retaining TLS hostname verification and SNI; add loopback/private/link-local/IPv6 and DNS-change tests.
5. Run a full dependency/SBOM and secret scan for both `app` and `:local`; add lockfiles/verification metadata and pin GitHub Actions to full commit SHAs.
6. Produce a release/candidate APK from the final audited commit using the intended permanent signing key; independently verify package, version, SHA-256, v2/v3 signature and pinned certificate before installation.
7. Perform physical-device testing on Android 10: microphone permission/revocation, notification access and retention/block/clear, camera cancel and attachment cleanup, background/foreground routines, location permission revocation, updater redirects, and restore failure rollback. The audit did not perform these device tests.

## 8. Scope and limitations

Reviewed the manifest, build configuration, security-relevant Activities/services/receivers/providers (including the exported sign-in-return and widget-configuration Activities), notification store/listener, provider request and network classes, encrypted credential stores, extension parsing/execution/secrets, routine trigger logic, voice and camera controllers, attachment handling, screen-context and pending-request storage, custom backup/restore paths, updater/component installer, edition-specific update code, and GitHub build/release workflows.

Not performed: a physical Android-device test, dynamic instrumentation, full penetration test, reverse-engineering of a final post-fix APK, a complete transitive SCA/SBOM, verification of private Actions secrets, or inspection of an APK that was not made available. The audit cannot certify the code's entire behavior beyond the paths inspected or guarantee the source repository has not changed after the recorded SHA.

**Final decision: NOT INSTALL-READY.** Keep the branch for continued work; do not treat the older debug APK or an unverified artifact as a trusted release. The audit report should travel with any source ZIP or handover archive.
