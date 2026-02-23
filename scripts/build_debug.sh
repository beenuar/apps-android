#!/bin/bash

# Build Debug APK Script
set -e

# Set JAVA_HOME
export JAVA_HOME=/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home

echo "========================================="
echo "Building Deepfake Shield - Debug APK"
echo "========================================="

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Build debug APK
echo "Building debug APK..."
./gradlew assembleDebug

# Create dist directory
mkdir -p dist

# Copy APK to dist
echo "Copying APK to /dist/..."
cp app/build/outputs/apk/debug/app-debug.apk dist/Kotlin-DeepfakeShield-debug.apk

echo "========================================="
echo "✓ Debug APK built successfully!"
echo "Location: dist/Kotlin-DeepfakeShield-debug.apk"
echo "========================================="
