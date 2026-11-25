package com.remap.buttons;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/**
 * Esta caja tiene 2 GB de RAM: al abrir una app desde el launcher se cierran en segundo plano las demás apps instaladas
 * (Xuper, Xuper Tv NF, Paramount+…), así solo queda la que se está usando. No toca el sistema ni a este launcher; si
 * una app está al frente Android no la cierra.
 */
final class Memory {
    private Memory() { }

    /** Cierra el segundo plano de todas las apps de terceros menos keep (la que se va a abrir). */
    static void freeFor(Context c, String keep) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            PackageManager pm = c.getPackageManager();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                boolean system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                if (system || ai.packageName.equals(keep) || ai.packageName.equals(c.getPackageName())) continue;
                am.killBackgroundProcesses(ai.packageName);
            }
        } catch (Exception ignored) { }
    }
}
