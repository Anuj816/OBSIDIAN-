# Provider session persistence and verification behavior

This project keeps the official SpotiFLAC Mobile UI and its native Rust extension backend. Provider authentication is extension-owned: the app can preserve session data, but it cannot make an unsupported provider session persistent or skip a verification challenge that the provider requires.

## What persists

- Each extension receives a private data directory under the app's application-support `extension_data` directory. This directory survives ordinary app restarts and is prepared before the extension system starts.
- Extensions can use the built-in `storage` API for non-secret settings and `credentials` API for secret values. Both are scoped to the extension; credentials are stored through the backend's protected storage implementation.
- Extensions that declare the signed-session capability can use `session.status()`, `session.completeGrant(grant)`, and `session.signedFetch(...)`. Signed-session records are written atomically under the extension data root and restored when the extension system starts again. Expiry and refresh policy are still controlled by the provider's signed-session configuration.
- Removing an extension's data intentionally removes its saved state. Clearing a session or changing its signed-session namespace/base URL/app version/platform also invalidates the old session by design.

## Verification behavior

The app now treats only explicit verification markers (`VERIFY_REQUIRED`, `verification_required`, or equivalent challenge wording) as a request to launch the verification flow. Generic errors such as `signed session expired` or `session is not authenticated` no longer automatically open a browser challenge when the provider did not actually supply one. Those errors should be surfaced as authentication/session errors, while explicit challenges continue through the existing verification and one-time retry flow.

This is not a verification bypass. If a provider requires a CAPTCHA, interactive login, fresh grant, or other verification, the app still requires that step. The change prevents false/repeated verification UI when there is no active challenge to complete.

## Extension author guidance

1. Prefer the signed-session API when the provider supports its protocol and declares the corresponding capability.
2. For provider-specific sessions, persist only the minimum required state using `credentials` for secrets and `storage` for non-secret preferences; do not keep access tokens only in module globals.
3. Respect provider expiry and refresh semantics. If a session expires or is revoked, request a new provider-supported login/verification flow rather than reusing stale credentials.
4. Never log access tokens, cookies, grants, or session secrets.

## Scope note: Python module

The Python `SpotiFLAC-Module-Version` project is a separate runtime and extension system; it is not a drop-in Flutter/Rust library. This source archive preserves the official Mobile UI and improves its verification/session behavior. It does not claim that the Python module itself has been embedded into the Android app. Doing that requires a separately designed and tested bridge or a port of the module's runtime and extensions.
