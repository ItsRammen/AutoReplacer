# Making a release

A release is a tag. Pushing a tag builds the APK on GitHub and attaches it to a release.

## Each release

1. Bump `versionName` in `openheadunit/app/build.gradle.kts` (for example `0.7.18`).
2. Add a `### 0.7.18` section at the top of `openheadunit/CHANGELOG-AUTOREPLACER.md`. Its bullet lines become the release notes.
3. Merge to `main`. The workflow file must be on the commit you tag.
4. Tag it and push the tag. The tag **must** be `v` plus the version, or the build stops with a clear error:

   ```sh
   git checkout main && git pull
   git tag v0.7.18
   git push origin v0.7.18
   ```

   Or use GitHub: **Releases → Draft a new release → Choose a tag → create `v0.7.18` on publish → target `main`**. The workflow then adds the APK to that release.

5. Watch **Actions → Build APK**. When it is green, the release has `AutoReplacer-v0.7.18.apk` and its `.sha256`. It is marked **pre-release** until you change that.

To build without releasing: **Actions → Build APK → Run workflow**. The APK is attached to the run as an artifact.

## Signing (do this once)

Without secrets, every build is signed with a **temporary debug key** that differs per run, so phones must uninstall before each update (and lose pairing and calibration). To sign releases with one stable key:

1. Make a keystore on your own machine and keep it safe. **Do not commit it. If you lose it you cannot update installed apps.**

   ```sh
   keytool -genkeypair -v -keystore autoreplacer-release.jks -alias autoreplacer \
     -keyalg RSA -keysize 4096 -validity 36500
   base64 -w0 autoreplacer-release.jks   # on macOS: base64 -i autoreplacer-release.jks
   ```

2. In the repository: **Settings → Secrets and variables → Actions → New repository secret**, add:

   | Secret | Value |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | the base64 text from above |
   | `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
   | `ANDROID_KEY_ALIAS` | `autoreplacer` (or the alias you used) |
   | `ANDROID_KEY_PASSWORD` | leave unset. `keytool`'s default (PKCS12) keystore uses the keystore password for the key too; set this only if you made an old-style `.jks` with a different key password |

3. The first release signed with this key needs a one-time uninstall of any earlier debug-signed build.

## What the build is

The workflow builds the **debug** variant of the `github` flavour (`:app:assembleGithubDebug`), the one the project is developed and tested with, then re-signs it with your key. The minified `release` build type has not been tested on a car and is not used.
