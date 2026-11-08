package dev.rentaldisplay.probe;

import android.app.Activity;
import android.app.PendingIntent;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.net.Uri;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class MainActivity extends Activity implements UsbSession.Listener, InputManager.InputDeviceListener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private SessionHost host;
    private GridState grid;
    private UsbManager usb;
    private InputManager inputs;
    private ProbeLog log;
    private UsbAccessory pending;
    private TextView status, logView;
    private Button connect, stop;
    private CheckBox adbSetup;
    private TouchGridView gridView;
    private boolean visible, uiQueued, permissionInFlight, needsReconnect, gridActive, carPhase;
    private boolean pendingGridConnect;
    private boolean requestAdbForTest;
    private boolean logBusy;
    private boolean retryQueued;
    private int retryGeneration;
    private UsbAccessory notificationAccessory;
    private File pendingSaveSnapshot;
    private long lastMoveLog, lastWindowMotionLog;
    private int phoneWidth, phoneHeight;
    private String permissionAction;
    private static final int EXPORT_LOG = 101;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (permissionAction.equals(intent.getAction())) {
                UsbAccessory a = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                UsbAccessory wanted = pending; pending = null; permissionInFlight = false;
                if (visible && gridActive && wanted != null && wanted.equals(a) && usb.hasPermission(wanted)) open(wanted);
                else { log.add("USB permission denied or request no longer active"); state("USB permission not granted"); }
                refresh();
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(intent.getAction())) {
                UsbAccessory a = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (matches(a)) { setNeedsReconnect(false); disconnect("USB unplugged"); }
            }
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        usb = (UsbManager) getSystemService(USB_SERVICE);
        inputs = (InputManager) getSystemService(INPUT_SERVICE);
        host = SessionHost.get(this); grid = host.grid; log = host.log;
        carPhase = grid.snapshot().phase.startsWith("CAR");
        if (saved != null && saved.getString("save_snapshot") != null) {
            try { pendingSaveSnapshot = ExportFiles.resolve(new File(getCacheDir(),"log-exports"),saved.getString("save_snapshot")); }
            catch (java.io.IOException ignored) {}
        }
        needsReconnect = host.needsReconnect();
        permissionAction = getPackageName() + ".USB_PERMISSION";
        buildUi();
        IntentFilter filter = new IntentFilter(permissionAction);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        if (inputs != null) inputs.registerInputDeviceListener(this, main);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> { if (gridActive) leaveGrid(); else finish(); });
        log.add("=== Rental Display Probe 0.3.4; Android API " + Build.VERSION.SDK_INT + " ===");
        log.add("PHONE_MODEL: " + Build.MANUFACTURER + " " + Build.MODEL);
        log.add("Stationary touch diagnostic. USB path based on the displayed 0.1.1 test; no Bluetooth-name exchange.");
        log.add("Input metadata is evidence, not proof of car origin. During CAR TEST touch only the car screen.");
        logDebugState("APP_START");
        refresh();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18); root.setPadding(pad, pad, pad, pad); root.setBackgroundColor(Color.rgb(15,25,37));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(),
                    pad + insets.getSystemWindowInsetRight(), pad + insets.getSystemWindowInsetBottom()); return insets;
        });
        root.addView(label("Rental Display · logs & tests", 24));
        root.addView(label("The USB stream continues with other apps open or the phone screen off. Open the grid for touch tests while parked.", 14));
        status = label("Ready", 17); status.setTextColor(Color.rgb(100,230,204)); root.addView(status);
        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Copy all logs", v -> copyLogs()),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(button("Share full log", v -> shareLog()),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(button("Save full log", v -> exportLog()),new LinearLayout.LayoutParams(0,-2,1));
        root.addView(actions);
        root.addView(label("These actions include the full retained log, even when only recent events appear below.",12));
        root.addView(button("Phone input check (no USB)", v -> startGrid(false)));
        adbSetup = new CheckBox(this); adbSetup.setText("Advanced: ask car to set up ADB touch");
        adbSetup.setTextColor(Color.WHITE); root.addView(adbSetup);
        root.addView(label("Off by default. Requires USB debugging already enabled. Authorizing a rental car for debugging gives it broader access to your phone; avoid persistent authorization.",12));
        connect = button("Start car touch grid", v -> startGrid(true)); root.addView(connect);
        root.addView(button("Retry connection (keep cable plugged in)", v -> retryConnection()));
        root.addView(button("Keep streaming in background",v -> backgroundStream()));
        stop = button("Stop connection", v -> disconnect("Stopped by user")); root.addView(stop);
        root.addView(button("Record: I touched the car screen only", v -> {
            GridState.Snapshot s = grid.snapshot();
            if (!carPhase || s.downs == 0) { state("No touch-down events recorded in the car test"); return; }
            log.add("USER_CAR_ORIGIN_CONFIRMATION: user reports touching only car; downs=" + s.downs + " targets=" + s.complete + "/5");
            state("Car-only report recorded separately from input metadata"); refresh();
        }));
        root.addView(button("Inspect input · 20 seconds", v -> inspectInput()));
        root.addView(button("Phone-only inspection help", v -> inspectionHelp()));
        root.addView(label("Inspection records input-device details, bounded accessory payloads and local input-command results in the shared log. Payloads may contain device details. Android may deny system-level checks.",12));
        root.addView(label("Recent events",17));
        logView = label("", 12); logView.setTypeface(android.graphics.Typeface.MONOSPACE); logView.setTextIsSelectable(true);
        root.addView(logView);
        ScrollView page = new ScrollView(this); page.setFillViewport(true); page.setBackgroundColor(Color.rgb(15,25,37));
        page.addView(root); setContentView(page);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(Color.WHITE);
        t.setPadding(0,dp(4),0,dp(4)); return t;
    }
    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(listener); return b;
    }
    private boolean matches(UsbAccessory a) {
        return a != null && "Garmin".equals(a.getManufacturer()) && "Garmin USB Link Accessory".equals(a.getModel());
    }

    private void startGrid(boolean car) {
        if (permissionInFlight || logBusy || retryQueued) return;
        if (host.active()) {
            if (!car) { state("Stop the existing stream before starting a phone-only baseline"); return; }
            carPhase = true; gridActive = true; pendingGridConnect = false;
            log.add("UI_GRID_RESUMED: existing service session and calibration retained"); showGrid(); return;
        }
        carPhase = car; grid.reset(car); gridActive = true; pendingGridConnect = car; lastMoveLog = 0;
        requestAdbForTest = car && adbSetup.isChecked();
        logDebugState(car ? "CAR_TEST_START" : "PHONE_BASELINE_START");
        log.add("INPUT_SETUP_MODE: " + (requestAdbForTest ? "user-selected ADB setup request" : "passive input observation"));
        log.add("TOUCH_TEST_BEGIN: phase=" + (car ? "CAR" : "PHONE") + "; normalised full-view coordinates; Back exits grid");
        showGrid();
    }
    private void showGrid() {
        host.gridVisible(true);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        gridView = new TouchGridView(this, grid, this::touch);
        FrameLayout screen = new FrameLayout(this);
        screen.addView(gridView,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout tools = new LinearLayout(this);
        tools.setBackgroundColor(Color.rgb(15,25,37)); tools.setElevation(dp(8));
        tools.addView(gridControl("Copy logs",v -> copyLogs()));
        tools.addView(gridControl("Share logs",v -> shareLog()));
        tools.addView(gridControl("Logs",v -> leaveGrid()));
        tools.addView(gridControl("Background",v -> backgroundStream()));
        tools.addView(gridControl("Retry",v -> retryConnection()));
        tools.addView(gridControl("Inspect",v -> inspectInput()));
        FrameLayout.LayoutParams location = new FrameLayout.LayoutParams(-2,dp(40),Gravity.TOP|Gravity.END);
        location.topMargin = location.rightMargin = dp(8); screen.addView(tools,location);
        screen.setOnApplyWindowInsetsListener((view,insets) -> {
            int top = insets.getSystemWindowInsetTop(), right = insets.getSystemWindowInsetRight();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                top = Math.max(top,insets.getDisplayCutout().getSafeInsetTop());
                right = Math.max(right,insets.getDisplayCutout().getSafeInsetRight());
            }
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams)tools.getLayoutParams();
            if (p.topMargin != dp(8)+top || p.rightMargin != dp(8)+right) {
                p.topMargin = dp(8)+top; p.rightMargin = dp(8)+right; tools.setLayoutParams(p);
            }
            return insets;
        });
        setContentView(screen); immersive();
        TouchGridView current = gridView;
        gridView.post(() -> waitForLandscape(current, 0));
    }
    private Button gridControl(String text,View.OnClickListener listener) {
        Button b = button(text,listener); b.setTextSize(12); b.setSingleLine(true);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(3),0,dp(3),0);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(78),-1)); return b;
    }
    private void waitForLandscape(TouchGridView current, int attempt) {
        if (!gridActive || gridView != current || !visible) return;
        if (current.getWidth() > current.getHeight() && current.getHeight() > 0) {
            phoneWidth = current.getWidth(); phoneHeight = current.getHeight();
            log.add("PHONE_VIEW: " + phoneWidth + "x" + phoneHeight + "; grid fills whole view without letterboxing");
            for (int id : InputDevice.getDeviceIds()) logDevice("INPUT_DEVICE_PRESENT", id);
            if (pendingGridConnect) { pendingGridConnect = false; connect(); }
        } else if (attempt < 25) main.postDelayed(() -> waitForLandscape(current, attempt + 1), 100);
        else state("Landscape view not ready · use Back and retry");
    }
    private void immersive() {
        if (!gridActive) return;
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams p = getWindow().getAttributes();
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(p);
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void leaveGrid() {
        if (!gridActive) return;
        GridState.Snapshot s = grid.snapshot();
        log.add((host.active() ? "TOUCH_GRID_HIDDEN" : "TOUCH_TEST_END")+": phase=" + (carPhase ? "CAR" : "PHONE") + "; events=" + s.events
                + "; downs=" + s.downs + "; targets=" + s.complete + "/5; origin not automatically confirmed");
        gridActive = false; pendingGridConnect = false; host.gridVisible(false);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        buildUi(); status.setText(host.active() ? "Stream continues · use Stop connection to end it" : "Grid ended · " + s.downs + " downs, " + s.complete + "/5 targets"); refresh();
    }
    private void inspectInput() {
        if (!gridActive) {
            if (!host.active()) { state("Start the phone baseline or car grid first, then tap Inspect"); return; }
            carPhase=true; gridActive=true; pendingGridConnect=false; showGrid();
        }
        if (!host.inspection.start(host::changed)) {
            Toast.makeText(this,"Inspection already running; wait for the 20-second window to finish",Toast.LENGTH_LONG).show(); return;
        }
        Toast.makeText(this,"For 20 seconds touch ONLY the car. Keep this phone unlocked. Then Share logs.",Toast.LENGTH_LONG).show();
        refresh();
    }
    private void inspectionHelp() {
        new android.app.AlertDialog.Builder(this).setTitle("Phone-only input inspection")
            .setMessage("While parked: enable Developer options → Pointer location and Show taps. Verify them with one phone tap. Start the car grid and wait for video. Tap Inspect, then touch only the car for 20 seconds while watching the phone. Return to Logs and share the full log.\n\nInspect observes our accessory stream and app-visible input. It attempts getevent and dumpsys as this app; Android may deny them. It cannot intercept separate HID/ADB traffic or grant itself shell access. No output does not prove the car sends nothing.\n\nFor deeper phone-only checks, pair a trusted on-device wireless ADB client through Wireless debugging. Copy commands below and run them there, not in this app. Pairing does not grant this app access. Device IDs in our logs are not event-node numbers. getevent may still be denied. The live command watches all accessible nodes; avoid typing private information and stop it with Ctrl+C. ADB-injected input may not appear in kernel events.")
            .setPositiveButton("Copy ADB commands",(dialog,which) -> {
                ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if (clipboard!=null) clipboard.setPrimaryClip(ClipData.newPlainText("On-device ADB inspection",
                    "dumpsys input\ngetevent -lp\ngetevent -lt\n"));
                Toast.makeText(this,"Commands copied for a paired ADB shell client",Toast.LENGTH_LONG).show();
            }).setNeutralButton("Developer options",(dialog,which) -> {
                try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
                catch (Exception e) { Toast.makeText(this,"Open Developer options in phone Settings",Toast.LENGTH_LONG).show(); }
            }).setNegativeButton("Close",null).show();
    }
    private void backgroundStream() {
        if (!host.active()) { state("Start a car stream first"); return; }
        log.add("UI_BACKGROUND_REQUESTED: service retains USB, encoder and logs");
        moveTaskToBack(true);
    }
    @Override public void onBackPressed() { if (gridActive) leaveGrid(); else super.onBackPressed(); }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); if (gridActive) immersive(); }

    private void observeWindowInput(MotionEvent event, String path) {
        if (log == null || !visible || !host.active()) return;
        InputDevice device = event.getDevice();
        // Do not filter on isExternal(): input injected by a head-unit ADB helper is commonly attributed to an
        // existing touchscreen or to no device, so dropping those events would hide the path being tested.
        String origin = InputDiagnostics.origin(device);
        int action = event.getActionMasked();
        long now = SystemClock.elapsedRealtime();
        if ((action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE)
                && now-lastWindowMotionLog < 100) return;
        lastWindowMotionLog = now;
        log.add("WINDOW_INPUT origin="+origin+" ("+InputOrigin.meaning(origin)+") path="+path+" action="+MotionEvent.actionToString(action)
                +" deviceId="+event.getDeviceId()+" source=0x"+Integer.toHexString(event.getSource())
                +" x="+event.getX()+" y="+event.getY()+" gridVisible="+gridActive
                +" windowFocus="+hasWindowFocus()+"; observed at activity dispatch before view routing");
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        observeWindowInput(event,"touch"); return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        observeWindowInput(event,"generic"); return super.dispatchGenericMotionEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) immersive();
        if (log != null) {
            View decor = getWindow().getDecorView();
            log.add("WINDOW_FOCUS focused="+focused+" gridVisible="+gridActive+" displayId="
                    +(decor.getDisplay()==null ? -1 : decor.getDisplay().getDisplayId()));
        }
    }
    private void touch(MotionEvent event, int width, int height, boolean generic) {
        if (!visible || !gridActive || width <= 0 || height <= 0) return;
        int action = event.getActionMasked();
        boolean moving = generic || action == MotionEvent.ACTION_MOVE;
        long now = SystemClock.uptimeMillis();
        boolean writeLog = !moving || now - lastMoveLog >= 100;
        if (moving && writeLog) lastMoveLog = now;
        InputDevice device = event.getDevice();
        String deviceInfo = deviceInfo(device);
        String kind = (generic ? "GENERIC_" : "") + MotionEvent.actionToString(action);
        int count = event.getPointerCount();
        for (int i = 0; i < count; i++) {
            double nx = event.getX(i) / width, ny = event.getY(i) / height;
            String summary = String.format(Locale.US, "%s p%d cell %d  x=%.3f y=%.3f  dev=%d",
                    kind, event.getPointerId(i), GridState.cell(nx,ny), nx, ny, event.getDeviceId());
            GridState.Hit hit = grid.touch(nx, ny, event.getPointerId(i), !generic && action == MotionEvent.ACTION_DOWN && i == 0,
                    i == 0, summary);
            if (writeLog) {
                GridState.Snapshot s = grid.snapshot();
                float rawX = Build.VERSION.SDK_INT >= 29 ? event.getRawX(i) : event.getRawX() + event.getX(i) - event.getX();
                float rawY = Build.VERSION.SDK_INT >= 29 ? event.getRawY(i) : event.getRawY() + event.getY(i) - event.getY();
                log.add(String.format(Locale.US,
                        "TOUCH phase=%s action=%s actionIndex=%d pointer=%d count=%d view=%.1f,%.1f raw=%.1f,%.1f norm=%.4f,%.4f inferredVideo=%.1f,%.1f cell=%d expected=%d hit=%s deltaNorm=%.4f,%.4f source=0x%x tool=%d pressure=%.2f deviceId=%d %s",
                        carPhase ? "CAR" : "PHONE", kind, event.getActionIndex(), event.getPointerId(i), count, event.getX(i), event.getY(i), rawX, rawY,
                        nx, ny, nx * s.videoWidth, ny * s.videoHeight, hit.cell, hit.expected, hit.correct, hit.dx, hit.dy,
                        event.getSource(), event.getToolType(i), event.getPressure(i), event.getDeviceId(), deviceInfo));
            }
        }
    }
    private String deviceInfo(InputDevice device) {
        return InputDiagnostics.describe(device);
    }
    private void logDevice(String event, int id) {
        InputDiagnostics.device(log,event,id);
    }
    @Override public void onInputDeviceAdded(int id) { if (!host.active()) logDevice("INPUT_DEVICE_ADDED", id); }
    @Override public void onInputDeviceChanged(int id) { if (!host.active()) logDevice("INPUT_DEVICE_CHANGED", id); }
    @Override public void onInputDeviceRemoved(int id) { if (!host.active()) log.add("INPUT_DEVICE_REMOVED id=" + id); }
    private int adbEnabled() {
        return InputDiagnostics.adbEnabled(this);
    }
    private void logDebugState(String reason) {
        InputDiagnostics.capture(this,log,reason);
    }
    @Override public void inputDiagnostic(String reason) {
        main.post(() -> {
            if (isDestroyed()) return;
            logDebugState(reason);
        });
    }

    private void connect() {
        if (host.active() || permissionInFlight || retryQueued || !visible || !gridActive) return;
        if (usb == null) { state("USB accessory mode unavailable"); return; }
        UsbAccessory[] accessories = usb.getAccessoryList();
        if (accessories == null || accessories.length == 0) {
            setNeedsReconnect(false); log.add("NO_ACCESSORY: select Auto+ Show and use the car's data USB port");
            state("No USB accessory · local grid still works"); return;
        }
        if (!host.readyToReopen()) {
            state("Previous USB workers are closing · use Retry connection");
            log.add("RETRY_WAIT: old USB/encoder workers must finish before reopening"); return;
        }
        if (needsReconnect) log.add("SOFT_RETRY: accessory still attached; trying a fresh session without cable reset");
        for (UsbAccessory a : accessories) if (matches(a)) {
            log.add("MATCH: Garmin USB Link Accessory (serial omitted)");
            if (usb.hasPermission(a)) open(a);
            else {
                pending = a; permissionInFlight = true;
                PendingIntent pi = PendingIntent.getBroadcast(this,1,new Intent(permissionAction).setPackage(getPackageName()),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                try { usb.requestPermission(a,pi); state("Allow USB access in Android's prompt"); }
                catch (Exception e) { pending = null; permissionInFlight = false; state("USB permission failed"); log.add("Permission error: " + e.getClass().getSimpleName()); }
            }
            refresh(); return;
        }
        state("Unsupported USB accessory"); log.add("UNSUPPORTED_ACCESSORY: Garmin USB Link not found");
    }
    private void open(UsbAccessory a) {
        if (host.active() || !visible || !gridActive) return;
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("notification_prompted",false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_prompted",true).apply();
            notificationAccessory = a;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},104); return;
        }
        launchStream(a);
    }
    private void launchStream(UsbAccessory a) {
        if (host.active() || !visible || !gridActive) return;
        if (!host.readyToReopen()) { state("Previous connection is still closing · use Retry connection"); return; }
        boolean request = requestAdbForTest && adbEnabled() == 1;
        if (requestAdbForTest && !request) {
            log.add("ADB_SETUP_SKIPPED: phone debugging is disabled or unknown; app will not enable it");
            state("ADB setup skipped · USB debugging is off or unknown");
        }
        if (!host.lifecycle.requestStart()) return;
        try {
            Intent service = new Intent(this,StreamService.class).setAction(StreamService.START)
                    .putExtra(UsbManager.EXTRA_ACCESSORY,a).putExtra("width",phoneWidth).putExtra("height",phoneHeight)
                    .putExtra("dpi",getResources().getDisplayMetrics().densityDpi).putExtra("request_adb",request);
            startForegroundService(service);
            if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                log.add("NOTIFICATIONS_HIDDEN: stream may run; use app Stop connection or Android active-app controls to stop it");
        } catch (Exception e) { host.startFailed(e.getClass().getSimpleName()+": "+e.getMessage()); }
        refresh();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request != 104) return;
        UsbAccessory a = notificationAccessory; notificationAccessory = null;
        log.add("NOTIFICATION_PERMISSION: "+(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED ? "granted" : "not granted"));
        if (a != null && gridActive && usb.hasPermission(a)) {
            if (visible) launchStream(a); else notificationAccessory = a;
        }
    }
    private void disconnect(String reason) {
        retryQueued = false; retryGeneration++;
        pending = null; permissionInFlight = false;
        host.stop(reason);
        state("Stop requested · logs remain available"); refresh();
    }
    private void retryConnection() {
        if (!visible || permissionInFlight || logBusy || retryQueued) return;
        retryQueued = true;
        final int generation = ++retryGeneration;
        final boolean requestAdb = gridActive ? requestAdbForTest : adbSetup.isChecked();
        log.add("SOFT_RETRY_REQUESTED: one user-requested session restart; no USB reset or automatic loop");
        if (host.active()) host.stop("User requested a fresh session without unplugging");
        state("Closing old connection · retrying with cable attached");
        awaitRetry(generation,requestAdb,SystemClock.elapsedRealtime());
    }
    private void awaitRetry(int generation,boolean requestAdb,long began) {
        if (!retryQueued || generation != retryGeneration || !visible || isDestroyed()) return;
        if (host.active() || !host.readyToReopen()) {
            if (SystemClock.elapsedRealtime()-began > 8000) {
                retryQueued = false;
                log.add("SOFT_RETRY_BLOCKED: old resources have not finished closing; no overlapping USB session opened");
                state("USB still closing · try again shortly; if stuck, reconnect cable"); return;
            }
            main.postDelayed(() -> awaitRetry(generation,requestAdb,began),250); return;
        }
        retryQueued = false;
        log.add("SOFT_RETRY_OPEN: old workers finished; starting one new handshake on attached accessory");
        if (!grid.snapshot().phase.startsWith("CAR")) grid.reset(true);
        carPhase = true; gridActive = true; pendingGridConnect = true; requestAdbForTest = requestAdb;
        showGrid();
    }
    @Override public void state(String text) {
        grid.status(text);
        main.post(() -> { if (!isDestroyed() && status != null) status.setText(text); });
    }
    private void setNeedsReconnect(boolean required) {
        needsReconnect = required; host.setReconnect(required);
    }
    @Override public void reconnectRequired() { main.post(() -> setNeedsReconnect(true)); }
    @Override public void changed() {
        main.post(() -> {
            if (isDestroyed() || uiQueued || gridActive) return;
            uiQueued = true; main.postDelayed(() -> { uiQueued = false; if (!isDestroyed()) refresh(); },150);
        });
    }
    @Override public void ended(UsbSession ended) {
        main.post(() -> {
            if (!isDestroyed()) refresh();
        });
    }
    private void refresh() {
        if (logView == null) return;
        logView.setText(log.text()); connect.setText(host.active() ? "Resume car touch grid" : "Start car touch grid");
        connect.setEnabled(!permissionInFlight && !logBusy); stop.setEnabled(host.active());
        adbSetup.setEnabled(!host.active() && !permissionInFlight && !logBusy);
    }
    private boolean beginLogAction() {
        if (logBusy) { Toast.makeText(this,"Preparing full log…",Toast.LENGTH_SHORT).show(); return false; }
        logBusy = true; refresh(); return true;
    }
    private void copyLogs() {
        if (!beginLogAction()) return;
        log.add("LOG_ACTION: copy full retained log; running test is preserved");
        new Thread(() -> {
            try {
                String full = log.fullText();
                main.post(() -> {
                    if (isDestroyed()) return;
                    try {
                        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
                        clipboard.setPrimaryClip(ClipData.newPlainText("Full Rental Display log",full));
                        state(gridActive ? "Full log copied · test continues" : "Full retained log copied");
                        Toast.makeText(this,"Full log copied",Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { logActionError("Clipboard could not accept the log. Use Share full log or Save full log."); }
                    finally { logBusy = false; refresh(); }
                });
            } catch (Exception e) { main.post(() -> logActionError(e.getMessage())); }
        },"copy-full-log").start();
    }
    private void logActionError(String message) {
        logBusy = false;
        if (isDestroyed()) return;
        String text = message == null ? "Log action failed" : message;
        state(text); Toast.makeText(this,text,Toast.LENGTH_LONG).show(); refresh();
    }
    private void prepareLogExport(Runnable action) {
        if (gridActive) leaveGrid();
        log.add("LOG_EXPORT: existing stream retained; snapshot captures records available now");
        action.run();
    }
    private void exportLog() {
        if (!beginLogAction()) return;
        prepareLogExport(() -> prepareLogSnapshot(false));
    }
    private void shareLog() {
        if (!beginLogAction()) return;
        prepareLogExport(() -> prepareLogSnapshot(true));
    }
    private void prepareLogSnapshot(boolean share) {
        log.add("LOG_ACTION: " + (share ? "share" : "save") + " full retained log snapshot");
        new Thread(() -> {
            try {
                File snapshot = log.snapshot();
                String fullText = share && snapshot.length() <= 64*1024 ? LogStore.readText(snapshot,64*1024) : null;
                main.post(() -> {
                    if (isDestroyed()) return;
                    try {
                        if (share) {
                            Uri uri = new Uri.Builder().scheme("content").authority(getPackageName()+".logs")
                                    .appendPath("exports").appendPath(snapshot.getName()).build();
                            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT,"Full Rental Display log").putExtra(Intent.EXTRA_STREAM,uri)
                                    .putExtra(Intent.EXTRA_TEXT,fullText == null ? "Full retained log attached ("+snapshot.length()+" bytes)." : fullText)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            send.setClipData(ClipData.newUri(getContentResolver(),"Full log",uri));
                            Intent chooser = Intent.createChooser(send,"Share full log").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(chooser);
                        } else {
                            pendingSaveSnapshot = snapshot;
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TITLE,"rental-display-full-log.txt");
                            startActivityForResult(intent,EXPORT_LOG);
                        }
                        state("Full log snapshot ready");
                    } catch (Exception e) { logActionError("No suitable app available. Try another log action."); }
                    finally { logBusy = false; refresh(); }
                });
            } catch (Exception e) { main.post(() -> logActionError(e.getMessage())); }
        },"full-log-snapshot").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request != EXPORT_LOG) return;
        File snapshot = pendingSaveSnapshot; pendingSaveSnapshot = null;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (snapshot == null || !snapshot.isFile()) { logActionError("Saved snapshot unavailable. Use Save full log again."); return; }
        new Thread(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData(),"wt"); InputStream in = new java.io.FileInputStream(snapshot)) {
                if (out == null) throw new java.io.IOException("Document not writable");
                byte[] buffer = new byte[8192]; int count; while ((count=in.read(buffer)) != -1) out.write(buffer,0,count);
                state("Full log saved");
            } catch (Exception e) { state("Save failed: " + e.getClass().getSimpleName()); }
        },"export-log").start();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (pendingSaveSnapshot != null) out.putString("save_snapshot",pendingSaveSnapshot.getName());
        super.onSaveInstanceState(out);
    }
    @Override protected void onStart() {
        super.onStart(); visible = true;
        host.add(this); needsReconnect = host.needsReconnect();
        host.gridVisible(gridActive); carPhase = grid.snapshot().phase.startsWith("CAR");
        log.add("UI_FOREGROUND: serviceActive="+host.active()+" gridVisible="+gridActive);
        handleUiIntent();
        if (notificationAccessory != null && gridActive && !host.active() && usb.hasPermission(notificationAccessory)) {
            UsbAccessory a = notificationAccessory; notificationAccessory = null; launchStream(a);
        }
        refresh();
    }
    private void handleUiIntent() {
        String action = getIntent().getAction();
        if (StreamService.SHOW_GRID.equals(action)) {
            getIntent().setAction(Intent.ACTION_MAIN);
            if (host.active()) startGrid(true); else state("No active stream · start a new connection");
        } else if (StreamService.SHOW_LOGS.equals(action) || StreamService.SHARE_LOG.equals(action)) {
            getIntent().setAction(Intent.ACTION_MAIN);
            if (gridActive) leaveGrid();
            if (StreamService.SHARE_LOG.equals(action)) main.post(this::shareLog);
        }
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(getIntent().getAction())) {
            setNeedsReconnect(false); getIntent().setAction(Intent.ACTION_MAIN); startGrid(true);
        }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (visible && UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(intent.getAction())) {
            setNeedsReconnect(false); intent.setAction(Intent.ACTION_MAIN); startGrid(true);
        } else if (visible) handleUiIntent();
    }
    @Override protected void onStop() {
        if (retryQueued) log.add("SOFT_RETRY_CANCELLED: phone UI hidden before reopen; tap Retry when ready");
        retryQueued = false; retryGeneration++;
        visible = false; pendingGridConnect = false;
        host.gridVisible(false); host.remove(this);
        log.add("UI_BACKGROUND: serviceActive="+host.active()+"; no activity-owned disconnect"); super.onStop();
    }
    @Override protected void onDestroy() {
        host.remove(this); unregisterReceiver(receiver); if (inputs != null) inputs.unregisterInputDeviceListener(this);
        main.removeCallbacksAndMessages(null); super.onDestroy();
    }
}
