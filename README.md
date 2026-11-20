# Notification Unlock (LSPosed)

Unlocks grayed-out / non-blockable notification toggles system-wide.

Hooks:
- `android` also drops all notifications from packages listed in `DROP` (Hook.java), default `com.motorola.android.provisioning`
- `android` (system_server): NotificationChannel lock flags
- `com.android.settings`: channel/app toggle checks

## Build
Push to GitHub; the `build` workflow produces the APK under Actions -> Artifacts.

## Use
1. Install the APK, enable it in LSPosed.
2. Scope: System Framework + Settings.
3. Reboot.

If a toggle stays gray, check LSPosed -> Logs -> Modules for `NotifUnlock:` lines.
Avoid disabling emergency alerts / critical system warnings.
