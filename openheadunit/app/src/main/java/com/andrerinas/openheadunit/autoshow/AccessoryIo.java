package com.andrerinas.openheadunit.autoshow;

import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public final class AccessoryIo implements Closeable {
    final InputStream input;
    final OutputStream output;
    final String mode;
    private final ParcelFileDescriptor lease;
    private AccessoryIo(InputStream input, OutputStream output, ParcelFileDescriptor lease, String mode) {
        this.input = Wire.usbInput(input); this.output = output; this.lease = lease; this.mode = mode;
    }
    static AccessoryIo open(UsbManager manager, UsbAccessory accessory) throws Exception {
        if (Build.VERSION.SDK_INT >= 36) {
            Method read = null, write = null;
            try {
                // Public API 36 methods; reflection preserves SDK 35 build compatibility.
                read = UsbManager.class.getMethod("openAccessoryInputStream", UsbAccessory.class);
                write = UsbManager.class.getMethod("openAccessoryOutputStream", UsbAccessory.class);
            } catch (NoSuchMethodException ignored) { /* May be absent on an OEM build. */ }
            if (read != null && write != null) {
                InputStream in = (InputStream) invoke(read, manager, accessory);
                try {
                    OutputStream out = (OutputStream) invoke(write, manager, accessory);
                    return new AccessoryIo(in, out, null, "public Android 16 accessory streams");
                } catch (Exception e) { try { in.close(); } catch (Exception ignored) {} throw e; }
            }
        }
        ParcelFileDescriptor parent = manager.openAccessory(accessory);
        if (parent == null) throw new IOException("USB could not be opened; close Auto+ Show and reconnect");
        ParcelFileDescriptor readFd = null, writeFd = null;
        try {
            readFd = ParcelFileDescriptor.dup(parent.getFileDescriptor());
            writeFd = ParcelFileDescriptor.dup(parent.getFileDescriptor());
            return new AccessoryIo(new ParcelFileDescriptor.AutoCloseInputStream(readFd),
                    new ParcelFileDescriptor.AutoCloseOutputStream(writeFd), parent,
                    "legacy accessory descriptor, original lease retained");
        } catch (Exception e) {
            if (readFd != null) readFd.close();
            if (writeFd != null) writeFd.close();
            parent.close(); throw e;
        }
    }
    private static Object invoke(Method method, UsbManager manager, UsbAccessory accessory) throws Exception {
        try { return method.invoke(manager, accessory); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IOException("Accessory stream API failed", cause);
        }
    }
    @Override public void close() {
        try { output.close(); } catch (Exception ignored) {}
        try { input.close(); } catch (Exception ignored) {}
        try { if (lease != null) lease.close(); } catch (Exception ignored) {}
    }
}
