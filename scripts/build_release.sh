#!/bin/bash

# Build Release APK Script
set -e

echo "========================================="
echo "Building Deepfake Shield - Release APK"
echo "========================================="

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Build release APK
echo "Building release APK..."
# Note: For actual signing, you need to set up keystore in local.properties
# For now, this will build an unsigned release APK
./gradlew assembleRelease

# Create dist directory
mkdir -p dist

# Copy APK to dist
echo "Copying APK to /dist/..."
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    cp app/build/outputs/apk/release/app-release.apk dist/Kotlin-DeepfakeShield-release.apk
elif [ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]; then
    cp app/build/outputs/apk/release/app-release-unsigned.apk dist/Kotlin-DeepfakeShield-release.apk
fi

echo "========================================="
echo "✓ Release APK built successfully!"
echo "Location: dist/Kotlin-DeepfakeShield-release.apk"
echo "Note: For production, configure signing in local.properties"
echo "========================================="
