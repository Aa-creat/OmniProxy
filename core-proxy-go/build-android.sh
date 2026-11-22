#!/bin/bash
set -e

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
TARGET_DIR="../app/src/main/jniLibs"

if [ -z "$ANDROID_NDK_HOME" ] && [ -n "$ANDROID_NDK_ROOT" ]; then
    ANDROID_NDK_HOME="$ANDROID_NDK_ROOT"
fi

if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "Error: ANDROID_NDK_HOME is not set."
    exit 1
fi

HOST_OS="linux-x86_64"
if [[ "$OSTYPE" == "darwin"* ]]; then
    HOST_OS="darwin-x86_64"
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_OS"
API=26

for ABI in "${ABIS[@]}"; do
    echo "=== Compiling for $ABI ==="
    OUTPUT_DIR="$TARGET_DIR/$ABI"
    mkdir -p "$OUTPUT_DIR"

    case $ABI in
        "arm64-v8a")
            TARGET_ARCH="aarch64-linux-android"
            GOARCH="arm64"
            GOARM=""
            ;;
        "armeabi-v7a")
            TARGET_ARCH="armv7a-linux-androideabi"
            GOARCH="arm"
            GOARM="7"
            ;;
        "x86_64")
            TARGET_ARCH="x86_64-linux-android"
            GOARCH="amd64"
            GOARM=""
            ;;
    esac

    CC="$TOOLCHAIN/bin/${TARGET_ARCH}${API}-clang"

    CGO_ENABLED=1 \
    GOOS=android \
    GOARCH=$GOARCH \
    GOARM=$GOARM \
    CC=$CC \
    CGO_CFLAGS="-I$TOOLCHAIN/sysroot/usr/include" \
    go build -v -buildmode=c-shared -ldflags="-s -w" -o "$OUTPUT_DIR/libomniproxy.so" cmd/jni/main.go

    echo "Generated: $OUTPUT_DIR/libomniproxy.so"
done

echo "Android native builds completed successfully!"
