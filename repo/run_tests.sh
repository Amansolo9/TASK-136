#!/usr/bin/env bash
set -euo pipefail

echo "=== Running Task-136 shared module unit tests ==="
echo ""

# Detect whether we are already inside the Docker container
# by checking for the Android SDK that the Dockerfile installs.
if [ -d "/opt/android-sdk" ]; then
    # Inside container – run Gradle directly
    if [ -f "./gradlew" ]; then
        GRADLE="./gradlew"
        chmod +x "$GRADLE" 2>/dev/null || true
    else
        GRADLE="gradle"
    fi

    $GRADLE :shared:testDebugUnitTest --no-daemon --project-cache-dir /tmp/gradle-test-cache "$@"
elif [ -f "./gradlew" ]; then
    chmod +x ./gradlew 2>/dev/null || true
    ./gradlew :shared:testDebugUnitTest --no-daemon "$@"
elif [ -f "./gradlew.bat" ]; then
    ./gradlew.bat :shared:testDebugUnitTest --no-daemon "$@"
else
    # Stop the app service first — it shares /workspace and its Gradle
    # locks conflict with the test run.
    docker compose stop app 2>/dev/null || true

    # Run tests inside the container with an isolated GRADLE_USER_HOME
    docker compose run --rm --no-deps \
        -e GRADLE_USER_HOME=/tmp/gradle-test \
        app bash -lc \
        "chmod +x ./gradlew 2>/dev/null || true; ./gradlew :shared:testDebugUnitTest --no-daemon $*"
fi

echo ""
echo "=== All tests passed ==="
