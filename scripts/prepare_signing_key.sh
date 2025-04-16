#!/bin/bash
# This script helps encode your Android signing keystore for GitHub Actions secrets

# Check if a keystore file path is provided
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <path-to-keystore>"
    exit 1
fi

KEYSTORE_PATH="$1"

# Check if the keystore file exists
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "Error: Keystore file not found at $KEYSTORE_PATH"
    exit 1
fi

# Encode the keystore file to base64
BASE64_KEYSTORE=$(base64 -i "$KEYSTORE_PATH")

# Print the encoded value
echo ""
echo "Add the following value to your GitHub repository secrets as SIGNING_KEY:"
echo ""
echo "$BASE64_KEYSTORE"
echo ""
echo "You will also need to add these secrets:"
echo "- KEY_ALIAS: Your keystore alias"
echo "- KEY_STORE_PASSWORD: Your keystore password"
echo "- KEY_PASSWORD: Your key password"
echo ""
echo "These values can be added at: https://github.com/YOUR-USERNAME/SimpleLogUpload/settings/secrets/actions"
