package dev.rentaldisplay.probe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public final class StreamService extends Service implements UsbSession.Listener, InputManager.InputDeviceListener {
    static final String START = "dev.rentaldisplay.probe.START_STREAM";
    static final String STOP = "dev.rentaldisplay.probe.STOP_STREAM";
    static final String SHOW_GRID = "dev.rentaldisplay.probe.SHOW_GRID";
    static final String SHOW_LOGS = "dev.rentaldisplay.probe.SHOW_LOGS";
    static final String SHARE_LOG = "dev.rentaldisplay.probe.SHARE_LOG";
    private static final String CHANNEL = "usb-display-stream";
    private static final int NOTIFICATION = 10;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SessionHost host;
    private UsbSession session;
    private UsbAccessory accessory;
    private PowerManager.WakeLock wakeLock;
    private InputManager inputManager;
    private boolean foreground, notificationQueued;
    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory detached = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null && accessory.equals(detached)) {
                    host.setReconnect(false); requestStop("USB unplugged");
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                host.gridVisible(false);
                host.log.add("PHONE_SCREEN_OFF: foreground service retains USB/encoder; touch collection requires visible grid");
                host.state("Background stream · phone screen off");
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                host.log.add("PHONE_SCREEN_ON: stream unchanged; grid is not opened automatically");
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                host.log.add("PHONE_UNLOCKED: existing stream retained");
            }
        }
    };
    @Override public void onCreate() {
        super.onCreate(); host = SessionHost.get(this);
        NotificationManager notifications = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL,"USB display streaming",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Controls for the ongoing generated USB display stream"); notifications.createNotificationChannel(channel);
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":UsbStream");
        wakeLock.setReferenceCounted(false);
        inputManager = (InputManager)getSystemService(INPUT_SERVICE);
        if (inputManager != null) inputManager.registerInputDeviceListener(this,main);
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT>=33) registerReceiver(events,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(events,filter);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY; }
        if (STOP.equals(intent.getAction())) { requestStop("Notification Stop"); return START_NOT_STICKY; }
        if (!START.equals(intent.getAction())) { if (session == null) stopSelf(startId); return START_NOT_STICKY; }
        if (session != null) { host.log.add("SERVICE_START_IGNORED: existing stream retained"); return START_NOT_STICKY; }
        try {
            accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            UsbManager usb = (UsbManager)getSystemService(USB_SERVICE);
            if (accessory == null || usb == null || !usb.hasPermission(accessory))
                throw new SecurityException("USB accessory permission is required before starting the stream");
            if (!host.readyToReopen()) throw new IllegalStateException("Previous USB workers are still closing; retry shortly");
            Notification notification = notification();
            if (Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(NOTIFICATION,notification);
            foreground = true;
            if (!host.lifecycle.markRunning()) { finishWithoutSession(); return START_NOT_STICKY; }
            wakeLock.acquire();
            session = new UsbSession(usb,accessory,host.log,this,host.grid,intent.getIntExtra("width",800),
                    intent.getIntExtra("height",480),intent.getIntExtra("dpi",160),intent.getBooleanExtra("request_adb",false),
                    intent.getBooleanExtra("mask_reply",false),intent.getIntExtra("adv_width",0),intent.getIntExtra("adv_height",0));
            host.attach(this,session);
            host.log.add("FOREGROUND_STREAM_STARTED: connectedDevice service owns USB and encoder; wake lock held");
            session.start();
        } catch (Exception e) {
            host.startFailed(e.getClass().getSimpleName()+": "+e.getMessage()); finishWithoutSession();
        }
        return START_NOT_STICKY;
    }
    private PendingIntent activity(String action,int code) {
        Intent intent = new Intent(this,MainActivity.class).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this,code,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        PendingIntent stop = PendingIntent.getService(this,4,new Intent(this,StreamService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        long frames = session == null ? 0 : session.frames();
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Rental Display · USB stream")
                .setContentText(host.status()+" · "+frames+" buffers")
                .setStyle(new Notification.BigTextStyle().bigText(host.status()+"\nGenerated display continues while the phone UI is hidden or off. Open the grid for touch testing."))
                .setContentIntent(activity(SHOW_LOGS,1)).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(android.R.drawable.ic_menu_view,"Open grid",activity(SHOW_GRID,2))
                .addAction(android.R.drawable.ic_menu_share,"Share log",activity(SHARE_LOG,3))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop",stop).build();
    }
    private void updateNotification() {
        main.post(() -> {
            if (!foreground || notificationQueued) return;
            notificationQueued = true;
            main.postDelayed(() -> {
                notificationQueued = false;
                if (foreground) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification());
            },1000);
        });
    }
    void requestStop(String reason) {
        host.lifecycle.requestStop();
        main.post(() -> {
            if (session != null) {
                UsbSession closing = session;
                host.log.add("SERVICE_STOP_REQUEST: "+reason); closing.stop(reason);
                main.postDelayed(() -> {
                    if (session == closing && host.lifecycle.phase()==StreamLifecycle.Phase.STOPPING) {
                        session = null;
                        host.log.add("SERVICE_STOP_DEADLINE: releasing service resources; reopening stays blocked until USB workers finish");
                        host.reconnectRequired(); host.finished(this,closing); releaseForeground(); stopSelf();
                    }
                },3000);
            }
            else finishWithoutSession();
        });
    }
    private void releaseForeground() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
    }
    private void finishWithoutSession() {
        host.finished(this,null); releaseForeground(); stopSelf();
    }
    @Override public void state(String text) { host.state(text); updateNotification(); }
    @Override public void changed() { host.changed(); updateNotification(); }
    @Override public void reconnectRequired() { host.reconnectRequired(); }
    @Override public void inputDiagnostic(String reason) { main.post(() -> InputDiagnostics.capture(this,host.log,reason)); }
    @Override public void ended(UsbSession ended) {
        main.post(() -> {
            if (session != ended) return;
            session = null; host.log.add("FOREGROUND_STREAM_ENDED: releasing wake lock and notification");
            host.finished(this,ended); releaseForeground(); stopSelf();
        });
    }
    @Override public void onInputDeviceAdded(int id) { InputDiagnostics.device(host.log,"INPUT_DEVICE_ADDED",id); }
    @Override public void onInputDeviceChanged(int id) { InputDiagnostics.device(host.log,"INPUT_DEVICE_CHANGED",id); }
    @Override public void onInputDeviceRemoved(int id) { host.log.add("INPUT_DEVICE_REMOVED id="+id); }
    @Override public void onTaskRemoved(Intent rootIntent) { host.log.add("UI_TASK_REMOVED: ongoing service retains the stream"); }
    @Override public void onDestroy() {
        UsbSession current = session; session = null;
        if (current != null) { current.stop("Foreground service destroyed"); host.setReconnect(true); host.finished(this,current); }
        releaseForeground(); main.removeCallbacksAndMessages(null);
        unregisterReceiver(events); if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
