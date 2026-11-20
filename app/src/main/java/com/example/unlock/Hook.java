package com.example.unlock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "NotifUnlock: ";

    // Packages whose notifications are dropped before they are ever posted.
    // Add more package names here if another one refuses to go away.
    private static final java.util.Set<String> DROP = new java.util.HashSet<>(
            java.util.Arrays.asList("com.motorola.android.provisioning"));

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        boolean sys = lpp.packageName.equals("android");
        boolean settings = lpp.packageName.equals("com.android.settings");
        if (!sys && !settings) return;

        // Both processes: channel lock flags
        Class<?> ch = XposedHelpers.findClassIfExists("android.app.NotificationChannel", lpp.classLoader);
        if (ch != null) {
            constant(ch, "isBlockable", true);
            constant(ch, "isImportanceLockedByCriticalDeviceFunction", false);
            constant(ch, "isImportanceLockedByOEM", false);
        } else {
            XposedBridge.log(TAG + "missing NotificationChannel");
        }

        if (sys) hookDrop(lpp);

        if (!settings) return;

        // Settings UI: the "is this toggle enabled" checks
        String base = "com.android.settings.notification.app.NotificationPreferenceController";
        Class<?> npc = XposedHelpers.findClassIfExists(base, lpp.classLoader);
        if (npc != null) {
            constant(npc, "isChannelBlockable", true);
            constant(npc, "isChannelConfigurable", true);
            constant(npc, "isChannelGroupBlockable", true);
        } else {
            XposedBridge.log(TAG + "missing " + base);
        }

        // App-level row: clear system-app / locked flags
        Class<?> be = XposedHelpers.findClassIfExists(
                "com.android.settings.notification.NotificationBackend", lpp.classLoader);
        if (be != null) {
            try {
                XposedBridge.hookAllMethods(be, "loadAppRow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object row = p.getResult();
                        if (row == null) return;
                        try { XposedHelpers.setBooleanField(row, "systemApp", false); } catch (Throwable ignored) {}
                        try { XposedHelpers.setBooleanField(row, "lockedImportance", false); } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + "loadAppRow: " + t);
            }
        } else {
            XposedBridge.log(TAG + "missing NotificationBackend");
        }
    }

    private void hookDrop(XC_LoadPackage.LoadPackageParam lpp) {
        Class<?> nms = XposedHelpers.findClassIfExists(
                "com.android.server.notification.NotificationManagerService", lpp.classLoader);
        if (nms == null) {
            XposedBridge.log(TAG + "missing NotificationManagerService");
            return;
        }
        try {
            XposedBridge.hookAllMethods(nms, "enqueueNotificationInternal", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] instanceof String
                            && DROP.contains((String) p.args[0])) {
                        p.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "enqueueNotificationInternal: " + t);
        }
    }

    private void constant(Class<?> c, String name, Object v) {
        try {
            XposedBridge.hookAllMethods(c, name, XC_MethodReplacement.returnConstant(v));
        } catch (Throwable t) {
            XposedBridge.log(TAG + "skip " + name + ": " + t);
        }
    }
}
