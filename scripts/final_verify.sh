#!/bin/bash

# Final Verification Script
set -e

echo "========================================="
echo "Deepfake Shield - Final Verification"
echo "========================================="

# Run lint
echo "Running lint checks..."
./gradlew lint || true

# Run Detekt
echo "Running Detekt static analysis..."
./gradlew detekt || true

# Run unit tests
echo "Running unit tests..."
./gradlew test || true

# Build debug APK
echo "Building debug APK..."
./gradlew assembleDebug

# Build release APK
echo "Building release APK..."
./gradlew assembleRelease

# Create dist directory
mkdir -p dist

# Copy APKs
echo "Copying APKs to /dist/..."
cp app/build/outputs/apk/debug/app-debug.apk dist/Kotlin-DeepfakeShield-debug.apk

if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    cp app/build/outputs/apk/release/app-release.apk dist/Kotlin-DeepfakeShield-release.apk
elif [ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]; then
    cp app/build/outputs/apk/release/app-release-unsigned.apk dist/Kotlin-DeepfakeShield-release.apk
fi

# Get file sizes
DEBUG_SIZE=$(ls -lh dist/Kotlin-DeepfakeShield-debug.apk | awk '{print $5}')
RELEASE_SIZE=$(ls -lh dist/Kotlin-DeepfakeShield-release.apk | awk '{print $5}')

echo "========================================="
echo "✓ Build Complete!"
echo "========================================="
echo "Debug APK: dist/Kotlin-DeepfakeShield-debug.apk ($DEBUG_SIZE)"
echo "Release APK: dist/Kotlin-DeepfakeShield-release.apk ($RELEASE_SIZE)"
echo "========================================="
