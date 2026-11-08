package dev.rentaldisplay.probe;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/** Application-scoped state; never owns an activity. StreamService owns the USB session. */
final class SessionHost {
    private static SessionHost instance;
    static synchronized SessionHost get(Context context) {
        if (instance == null) instance = new SessionHost(context.getApplicationContext());
        return instance;
    }
    final GridState grid = new GridState();
    final ProbeLog log;
    final PhoneInspection inspection;
    final StreamLifecycle lifecycle = new StreamLifecycle();
    private final SharedPreferences preferences;
    private final CopyOnWriteArrayList<WeakReference<UsbSession.Listener>> observers = new CopyOnWriteArrayList<>();
    private volatile StreamService owner;
    private volatile UsbSession session;
    private volatile UsbSession previousSession;
    private volatile long closedAt;
    private volatile String status = "Ready";
    private SessionHost(Context context) {
        log = new ProbeLog(context);
        inspection = new PhoneInspection(context,log);
        preferences = context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE);
    }
    boolean active() { return lifecycle.active(); }
    UsbSession session() { return session; }
    String status() { return status; }
    void add(UsbSession.Listener observer) {
        remove(observer); observers.add(new WeakReference<>(observer));
        if (active()) observer.state(status);
    }
    void remove(UsbSession.Listener observer) {
        for (WeakReference<UsbSession.Listener> reference : observers)
            if (reference.get() == null || reference.get() == observer) observers.remove(reference);
    }
    interface Call { void invoke(UsbSession.Listener observer); }
    private void notifyObservers(Call call) {
        for (WeakReference<UsbSession.Listener> reference : observers) {
            UsbSession.Listener observer = reference.get();
            if (observer == null) observers.remove(reference); else call.invoke(observer);
        }
    }
    void attach(StreamService owner,UsbSession session) {
        this.owner = owner; this.session = session; previousSession = session; changed();
    }
    boolean readyToReopen() {
        UsbSession previous = previousSession;
        return previous == null || (previous.resourcesReleased()
                && android.os.SystemClock.elapsedRealtime()-closedAt >= 750);
    }
    synchronized boolean finished(StreamService owner,UsbSession ended) {
        if (this.owner != null && this.owner != owner) return false;
        if (session != null && session != ended) return false;
        this.owner = null; session = null;
        if (ended != null) closedAt = android.os.SystemClock.elapsedRealtime();
        lifecycle.finished();
        notifyObservers(o -> o.ended(ended)); changed(); return true;
    }
    void startFailed(String message) {
        owner = null; session = null; lifecycle.finished();
        log.add("SERVICE_START_FAILED: " + message); state("Stream could not start · see log"); changed();
    }
    void stop(String reason) {
        lifecycle.requestStop();
        StreamService service = owner;
        if (service != null) service.requestStop(reason);
        else changed(); // A pending start sees STOPPING and cancels rather than opening USB.
    }
    void state(String text) { status = text; grid.status(text); notifyObservers(o -> o.state(text)); }
    void changed() { notifyObservers(UsbSession.Listener::changed); }
    boolean needsReconnect() { return preferences.getBoolean("needs_usb_reconnect",false); }
    void setReconnect(boolean required) { preferences.edit().putBoolean("needs_usb_reconnect",required).apply(); }
    void reconnectRequired() { setReconnect(true); notifyObservers(UsbSession.Listener::reconnectRequired); }
    void gridVisible(boolean visible) { grid.gridVisible(visible); }
}
