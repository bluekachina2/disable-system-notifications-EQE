package com.example.unlock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "NotifUnlock: ";

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

    private void constant(Class<?> c, String name, Object v) {
        try {
            XposedBridge.hookAllMethods(c, name, XC_MethodReplacement.returnConstant(v));
        } catch (Throwable t) {
            XposedBridge.log(TAG + "skip " + name + ": " + t);
        }
    }
}
