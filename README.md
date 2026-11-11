# Music – a personal offline music player (Android)

Offline player for local MP3s with an Apple-Music-style layout (original design, no Apple assets).
Kotlin + Jetpack Compose + Media3. Built for low-end phones (tested target: OPPO A54, Android 11).

Features: Library / Albums / Artists / Songs / Search tabs, playlists, mini player, full Now Playing
screen with big album art (from MP3 tags), favorites, custom playlist and song cover images from your
gallery, queue ("Playing Next"), shuffle, repeat, sleep timer (any time 1–60 min),
background playback with notification + lock-screen controls, automatic dark/light mode.

> Personal use only. Not affiliated with Apple.

## Option A – let GitHub build the APK (no Android tools needed)
1. Push this folder to a GitHub repo (branch `main`).
2. Repo → **Actions** → *Build APK* → open the latest run → download the **melody-apk** artifact (zip).
3. Unzip it, then install: `adb install -r app-release.apk`

## Option B – build on your Mac
```bash
# one-time setup
brew install --cask temurin@17
brew install gradle
brew install --cask android-commandlinetools

export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME="$HOME/Library/Android/sdk"
mkdir -p "$ANDROID_HOME"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-34" "build-tools;34.0.0"

# generate the Gradle wrapper (in an empty folder, then copy it in)
mkdir /tmp/gw && cd /tmp/gw && gradle wrapper --gradle-version 8.7 && cd -
cp -R /tmp/gw/gradlew /tmp/gw/gradle ./

# build
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`

## Install + add music
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb push ~/Music/MyMP3s/. /sdcard/Music/
```
Open Music → allow access → tap **Rescan** (Library tab) if songs don't show up.
