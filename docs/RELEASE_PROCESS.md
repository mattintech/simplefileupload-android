# Release Process for SimpleLogUpload

This document describes the process of creating new releases for the SimpleLogUpload Android application using GitHub Actions.

## Versioning Scheme

We use a `vYY.MM.PATCH` versioning format:
- `YY`: Year (last two digits, e.g., 25 for 2025)
- `MM`: Month (two digits, e.g., 04 for April)
- `PATCH`: Incremental patch number within the month (starting at 1)

Example: `v25.04.1` represents the first release in April 2025.

## Prerequisites

Before you can use the automated release process, you need to set up the signing configuration:

1. **Create a keystore** (if you don't already have one):
   ```bash
   keytool -genkey -v -keystore simplelogupload.keystore -alias upload_key -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Encode your keystore file**:
   ```bash
   # Make the script executable
   chmod +x scripts/prepare_signing_key.sh
   
   # Run the script with your keystore file
   ./scripts/prepare_signing_key.sh /path/to/your/simplelogupload.keystore
   ```

3. **Add the required secrets to your GitHub repository**:
   - `SIGNING_KEY`: The Base64-encoded keystore file (output from step 2)
   - `KEY_ALIAS`: The alias of your key (e.g., `upload_key`)
   - `KEY_STORE_PASSWORD`: The password for your keystore
   - `KEY_PASSWORD`: The password for your key

   Add these at: https://github.com/YOUR-USERNAME/SimpleLogUpload/settings/secrets/actions

## Creating a New Release

1. **Update your code**:
   - Make all necessary code changes
   - Update version numbers in app/build.gradle if needed
   - Commit and push your changes to the main branch

2. **Create and push a tag**:
   ```bash
   # Get the latest changes
   git pull origin main
   
   # Create a new tag
   git tag v25.04.1
   
   # Push the tag to GitHub
   git push origin v25.04.1
   ```

3. **Monitor the GitHub Actions workflow**:
   - Go to the "Actions" tab in your repository
   - You should see a workflow named "Android Release Build" running
   - When complete, it will create a new release with the APK attached

4. **Verify the release**:
   - Check the "Releases" section of your repository
   - Ensure the APK file is attached
   - Download and test the APK on a device

## Troubleshooting

If the automatic release process fails:

1. **Check the GitHub Actions logs**:
   - Go to the "Actions" tab and click on the failed workflow
   - Examine the logs for errors

2. **Common issues**:
   - Missing GitHub secrets
   - Incorrect keystore information
   - Build errors in the codebase

3. **Manual release**:
   If needed, you can build and release manually:
   ```bash
   # Build the release APK
   ./gradlew assembleRelease
   
   # Sign the APK using your keystore
   # (Use apksigner from the Android SDK)
   ```

## Post-Release Steps

1. **Announce the release** to users or stakeholders
2. **Monitor for issues** after the release
3. **Plan the next release** and begin work on new features
