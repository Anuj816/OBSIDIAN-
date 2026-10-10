# Build OBSIDIAN APK from an Android phone

This repository includes `.github/workflows/android-apk.yml`, which builds a debug APK on GitHub-hosted runners. No PC is needed.

1. Push the project to the `main` branch of your GitHub repository.
2. Open **Actions → Build OBSIDIAN APK**.
3. If using manual run, select **Run workflow**. A push to `main` touching app/build files also triggers it.
4. Wait for the workflow to complete successfully.
5. Download `obsidian-debug-apk` from the run's **Artifacts** section and extract the APK on your phone.

The artifact is a debug build, not a Play Store release. The workflow has not been run in this editing environment, so a successful build is not guaranteed until GitHub Actions completes. If it fails, share the failed step's log (never include tokens or secrets).
