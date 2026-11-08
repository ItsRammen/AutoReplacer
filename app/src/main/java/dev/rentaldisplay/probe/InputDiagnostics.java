package dev.rentaldisplay.probe;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.MotionEvent;

final class InputDiagnostics {
    static int adbEnabled(Context context) {
        try { return Settings.Global.getInt(context.getContentResolver(),Settings.Global.ADB_ENABLED,-1); }
        catch (Exception e) { return -1; }
    }
    static String describe(InputDevice device) {
        if (device == null) return "device=unknown";
        String name = device.getName().replace('\n',' ').replace('\r',' ');
        if (name.length()>80) name = name.substring(0,80);
        return "device=\""+name+"\" external="+device.isExternal()+" virtual="+device.isVirtual();
    }
    static void device(ProbeLog log,String event,int id) {
        InputDevice d = InputDevice.getDevice(id);
        if (d != null && (d.isExternal() || d.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)
                || d.supportsSource(InputDevice.SOURCE_MOUSE) || d.supportsSource(InputDevice.SOURCE_TOUCHPAD))) {
            log.add(event+" id="+id+" sources=0x"+Integer.toHexString(d.getSources())+" "+describe(d));
            if (d.isExternal()) {
                log.add("EXTERNAL_INPUT_CAPABILITIES id="+id+" enabled="+d.isEnabled()
                        +" vendor="+d.getVendorId()+" product="+d.getProductId()+" axes="+d.getMotionRanges().size());
                for (InputDevice.MotionRange range : d.getMotionRanges())
                    log.add("EXTERNAL_INPUT_AXIS id="+id+" axis="+MotionEvent.axisToString(range.getAxis())
                            +" source=0x"+Integer.toHexString(range.getSource())+" min="+range.getMin()
                            +" max="+range.getMax()+" resolution="+range.getResolution());
            }
        }
    }
    private static String flag(Intent state,String key) {
        return state.hasExtra(key) ? Boolean.toString(state.getBooleanExtra(key,false)) : "unknown";
    }
    static void capture(Context context,ProbeLog log,String reason) {
        log.add("PHONE_DEBUG_STATE reason="+reason+" adbSetting="+adbEnabled(context)+" (1=enabled,0=disabled,-1=unknown; authorization not established)");
        try {
            Intent state = context.registerReceiver(null,new IntentFilter("android.hardware.usb.action.USB_STATE"));
            if (state == null) log.add("PHONE_USB_STATE unavailable (no sticky state)");
            else log.add("PHONE_USB_STATE reason="+reason+" connected="+flag(state,"connected")
                    +" configured="+flag(state,"configured")+" accessory="+flag(state,"accessory")+" adb="+flag(state,"adb"));
        } catch (Exception e) { log.add("PHONE_USB_STATE unavailable: "+e.getClass().getSimpleName()); }
        for (int id : InputDevice.getDeviceIds()) device(log,"INPUT_DEVICE_SNAPSHOT",id);
    }
}
