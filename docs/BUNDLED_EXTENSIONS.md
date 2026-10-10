# Bundled extension packages

Android APK builds include the extension packages listed in the SpotiFLAC registry configured in `scripts/bundle_extensions.py` (by default, the registry URL supplied for this project).

Before Flutter asset compilation, the build workflow runs:

```sh
python3 scripts/bundle_extensions.py
```

The script downloads the registry and each listed `.sflx` package over HTTPS, checks the package's SHA-256 digest against the registry, then writes the verified packages and a generated manifest to `assets/bundled_extensions/`. A digest mismatch or failed download stops the build instead of embedding an unverified package.

At app startup, `ExtensionNotifier` reads that manifest, checks each embedded package digest again, and installs packages that are missing from the user's installed-extension directory. Existing installed extensions are left unchanged. The extension store is configured to use the same registry by default.

This is build-time bundling: the source archive contains the installer script and registry snapshot, while the APK build workflow fetches the binary `.sflx` packages before packaging. Provider credentials, login, and mandatory verification are not included or bypassed.
