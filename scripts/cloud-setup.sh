#!/usr/bin/env bash
set -euo pipefail
cd /workspace/medidorWifi
mkdir -p /workspace/tooling /workspace/android-sdk/cmdline-tools
fetch() {
  curl -fL --retry 2 "$1" -o "$2"
  printf '%s  %s\n' "$3" "$2" | sha256sum --check -
}
if [ ! -x /workspace/tooling/jdk-21.0.6+7/bin/javac ]; then
 fetch 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz' /tmp/wifi-jdk.tar.gz a2650fba422283fbed20d936ce5d2a52906a5414ec17b2f7676dddb87201dbae
 tar -xzf /tmp/wifi-jdk.tar.gz -C /workspace/tooling
fi
if [ ! -x /workspace/android-sdk/cmdline-tools/latest/bin/sdkmanager ]; then
 fetch 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip' /tmp/wifi-sdk.zip 2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258
 unzip -q /tmp/wifi-sdk.zip -d /workspace/android-sdk/cmdline-tools
 mv /workspace/android-sdk/cmdline-tools/cmdline-tools /workspace/android-sdk/cmdline-tools/latest
fi
if [ ! -x /workspace/tooling/gradle-8.11.1/bin/gradle ]; then
 fetch 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' /tmp/wifi-gradle.zip f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
 unzip -q /tmp/wifi-gradle.zip -d /workspace/tooling
fi
export JAVA_HOME=/workspace/tooling/jdk-21.0.6+7
export ANDROID_HOME=/workspace/android-sdk
# Use the managed machine's trusted CA set, retaining TLS validation.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
if [ ! -d "$ANDROID_HOME/platforms/android-35" ] || [ ! -d "$ANDROID_HOME/build-tools/35.0.0" ]; then
 set +o pipefail
 yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/tmp/wifi-sdk-licenses.log
 set -o pipefail
 "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
fi
if [ ! -e local.properties ]; then printf 'sdk.dir=/workspace/android-sdk\n' > local.properties; fi
# The shell proxy is not used automatically by the JVM.
proxy_args=()
if [ -n "${HTTPS_PROXY:-}" ]; then
 while IFS= read -r arg; do proxy_args+=("$arg"); done < <(python3 - <<'PYPROXY'
import os,urllib.parse
u=urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
for protocol in ['http','https']:
 print(f'-D{protocol}.proxyHost={u.hostname}')
 print(f'-D{protocol}.proxyPort={u.port or 8080}')
PYPROXY
)
fi
/workspace/tooling/gradle-8.11.1/bin/gradle "${proxy_args[@]}" --no-daemon :app:assembleDebug :app:testDebugUnitTest
