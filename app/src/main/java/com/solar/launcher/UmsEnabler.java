package com.solar.launcher;

import android.os.IBinder;

/**
 * A standalone Java helper executed out-of-process via app_process as root.
 * Bypasses the MOUNT_UNMOUNT_FILESYSTEMS permission requirement.
 */
public class UmsEnabler {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: UmsEnabler [1|0]");
            System.exit(1);
        }
        boolean enable = "1".equals(args[0]);
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            IBinder service = (IBinder) smClass.getMethod("getService", String.class).invoke(null, "mount");
            Class<?> stub = Class.forName("android.os.storage.IMountService$Stub");
            Object mountService = stub.getMethod("asInterface", IBinder.class).invoke(null, service);
            mountService.getClass().getMethod("setUsbMassStorageEnabled", boolean.class).invoke(mountService, enable);
            System.out.println("UMS enable=" + enable + " executed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
