# Development Scripts

This directory contains helpful scripts for development and release management.

## prepare_signing_key.sh

This script helps you prepare your Android signing keystore for use with GitHub Actions by encoding it to Base64 format.

### Usage

```bash
# Make the script executable
chmod +x prepare_signing_key.sh

# Run the script with your keystore file path
./prepare_signing_key.sh /path/to/your/keystore.jks
```

The script will output the Base64-encoded keystore which you should add as a GitHub secret named `SIGNING_KEY`.

### Required GitHub Secrets

To enable automatic signing and releasing of APKs, add the following secrets to your GitHub repository:

1. `SIGNING_KEY`: The Base64-encoded keystore file (output from this script)
2. `KEY_ALIAS`: The alias used in your keystore
3. `KEY_STORE_PASSWORD`: The password for your keystore
4. `KEY_PASSWORD`: The password for your key

Add these secrets at: https://github.com/YOUR-USERNAME/SimpleLogUpload/settings/secrets/actions
