package com.reynaldo.optimizador;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    // Paquetes de Xiaomi/Google que suelen sobrar. Solo se muestran los que existen en el teléfono.
    private static final String[] BLOAT = {
            "com.miui.analytics",
            "com.miui.msa.global",
            "com.xiaomi.mipicks",
            "com.miui.bugreport",
            "com.miui.hybrid",
            "com.miui.hybrid.accessory",
            "com.mi.globalminusscreen",
            "com.miui.videoplayer",
            "com.miui.player",
            "com.miui.yellowpage",
            "com.miui.android.fashiongallery",
            "com.xiaomi.glgm",
            "com.google.android.videos",
            "com.google.android.apps.tachyon"
    };

    private static final String[] AUTO_PREFIX = {
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.google.android.webview"
    };

    private static class App {
        String pkg;
        String label;
    }

    private interface Result {
        void ok(boolean[] checked);
    }

    private SharedPreferences sp;
    private PackageManager pm;
    private TextView stats, shizukuInfo, log;
    private Button ecoBtn;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private boolean busy = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateStats();
            h.postDelayed(this, 2000);
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permL =
            (code, res) -> runOnUiThread(this::updateShizukuInfo);

    // ---------------------------------------------------------------- UI

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("p", 0);
        pm = getPackageManager();
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF101010));

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        sv.addView(root);

        TextView title = text("Optimizador", 28, 0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        stats = text("", 15, 0xFFB0E0B0);
        stats.setPadding(0, dp(12), 0, 0);
        root.addView(stats);

        shizukuInfo = text("", 13, 0xFF9E9E9E);
        shizukuInfo.setPadding(0, dp(4), 0, dp(8));
        root.addView(shizukuInfo);

        root.addView(button("1  Optimizar apps (compilar)", 0xFF1F1F1F, v -> doCompile()));
        root.addView(button("2  Cerrar apps en segundo plano", 0xFF1F1F1F, v -> doKill()));
        root.addView(button("3  Limpiar caché de todo", 0xFF1F1F1F, v -> doCache()));
        root.addView(button("4  Bloquear segundo plano por app", 0xFF1F1F1F, v -> pickBlock()));
        root.addView(button("5  Desactivar bloatware", 0xFF1F1F1F, v -> pickBloat()));
        ecoBtn = button("", 0xFF2E5E2E, v -> toggleEco());
        root.addView(ecoBtn);
        root.addView(button("Apps protegidas (nunca se tocan)", 0xFF1F1F1F, v -> pickProtected()));

        log = text("", 12, 0xFFCCCCCC);
        log.setTypeface(Typeface.MONOSPACE);
        log.setPadding(0, dp(16), 0, 0);
        root.addView(log);

        setContentView(sv);
        Shizuku.addRequestPermissionResultListener(permL);
        updateEcoBtn();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateShizukuInfo();
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(permL);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private Button button(String label, int bg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void setLog(String s) {
        log.setText(s.length() > 1500 ? s.substring(s.length() - 1500) : s);
    }

    // ---------------------------------------------------------------- Monitor (función 7)

    private void updateStats() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        float temp = 0;
        int lvl = -1;
        if (bat != null) {
            temp = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            lvl = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        }
        stats.setText("RAM libre: " + (mi.availMem >> 20) + " / " + (mi.totalMem >> 20) + " MB\n"
                + "Temperatura: " + temp + " °C    Batería: " + lvl + "%");
    }

    private void updateShizukuInfo() {
        String s;
        try {
            if (!Shizuku.pingBinder()) s = "Shizuku: no está activo (ábrelo e inícialo)";
            else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                s = "Shizuku: activo, falta dar permiso (toca cualquier botón)";
            else s = "Shizuku: listo ✔";
        } catch (Throwable t) {
            s = "Shizuku: no disponible";
        }
        shizukuInfo.setText(s);
    }

    private void updateEcoBtn() {
        ecoBtn.setText(sp.getBoolean("eco", false)
                ? "6  Ahorro extremo: ACTIVO (tocar para revertir)"
                : "6  Activar ahorro extremo");
    }

    // ---------------------------------------------------------------- Ejecución

    private boolean ready() {
        try {
            if (!Shizuku.pingBinder()) {
                toast("Shizuku no está activo. Ábrelo e inícialo.");
                return false;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(100);
                toast("Acepta el permiso de Shizuku y vuelve a tocar el botón.");
                return false;
            }
            return true;
        } catch (Throwable t) {
            toast("Shizuku no disponible.");
            return false;
        }
    }

    private void runTask(String title, Supplier<String> job) {
        if (busy) {
            toast("Espera, hay una tarea en curso.");
            return;
        }
        if (!ready()) return;
        busy = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setLog(title + "…");
        ex.execute(() -> {
            String r;
            try {
                r = job.get();
            } catch (Throwable t) {
                r = "Error: " + t;
            }
            final String out = r;
            runOnUiThread(() -> {
                busy = false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setLog(title + "\n" + out);
                updateEcoBtn();
                updateStats();
            });
        });
    }

    // ---------------------------------------------------------------- Listas de apps

    private boolean safe(String p) {
        return p != null && p.matches("[A-Za-z0-9._]+");
    }

    private boolean startsAuto(String p) {
        for (String x : AUTO_PREFIX) if (p.startsWith(x)) return true;
        return false;
    }

    /** Apps que nunca se tocan (propias, launcher, teclado, Shizuku, ashell, Google Play...). */
    private Set<String> autoSet() {
        Set<String> s = new HashSet<>();
        s.add(getPackageName());
        s.add("moe.shizuku.privileged.api");
        s.add("in.sunilpaulmathew.ashell");
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo r : pm.queryIntentActivities(home, 0)) s.add(r.activityInfo.packageName);
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null && ime.contains("/")) s.add(ime.substring(0, ime.indexOf('/')));
        return s;
    }

    private Set<String> userProtected() {
        return new HashSet<>(sp.getStringSet("protected", new HashSet<>()));
    }

    private Set<String> blockedSet() {
        return new HashSet<>(sp.getStringSet("blocked", new HashSet<>()));
    }

    private List<App> apps(boolean excludeUserProtected) {
        Set<String> auto = autoSet();
        Set<String> prot = excludeUserProtected ? userProtected() : new HashSet<>();
        List<App> r = new ArrayList<>();
        for (ApplicationInfo a : pm.getInstalledApplications(0)) {
            boolean sys = (a.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean upd = (a.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (sys && !upd) continue;
            if (!a.enabled) continue;
            if (!safe(a.packageName)) continue;
            if (auto.contains(a.packageName) || startsAuto(a.packageName)) continue;
            if (prot.contains(a.packageName)) continue;
            App x = new App();
            x.pkg = a.packageName;
            x.label = String.valueOf(pm.getApplicationLabel(a));
            r.add(x);
        }
        Collections.sort(r, (p, q) -> p.label.compareToIgnoreCase(q.label));
        return r;
    }

    private void multi(String title, String[] labels, boolean[] checked, Result r) {
        final boolean[] c = checked.clone();
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(title)
                .setMultiChoiceItems(labels, c, (d, i, on) -> c[i] = on)
                .setPositiveButton("Aplicar", (d, w) -> r.ok(c))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------------------------------------------------------- 1. Compilar

    private void doCompile() {
        runTask("Optimizando apps (tarda varios minutos, no cierres la app)", () -> {
            String o = Sh.run("cmd package compile -m speed-profile -a");
            if (o.contains("Success")) return "Listo ✔ Todas las apps compiladas.";
            return o.length() > 400 ? o.substring(o.length() - 400) : o;
        });
    }

    // ---------------------------------------------------------------- 2. Cerrar apps

    private void doKill() {
        runTask("Cerrando apps", () -> {
            Set<String> prot = userProtected();
            StringBuilder c = new StringBuilder();
            int n = 0;
            for (App a : apps(true)) {
                if (prot.contains(a.pkg)) continue;
                c.append("am force-stop ").append(a.pkg).append("; ");
                n++;
            }
            c.append("am kill-all");
            Sh.run(c.toString());
            return "Listo ✔ " + n + " apps cerradas.";
        });
    }

    // ---------------------------------------------------------------- 3. Caché

    private long freeBytes() {
        StatFs s = new StatFs(Environment.getDataDirectory().getPath());
        return s.getAvailableBytes();
    }

    private void doCache() {
        runTask("Limpiando caché", () -> {
            long before = freeBytes();
            Sh.run("pm trim-caches 999G");
            long freed = Math.max(0, freeBytes() - before) >> 20;
            return "Listo ✔ Espacio liberado: " + freed + " MB";
        });
    }

    // ---------------------------------------------------------------- 4. Bloquear segundo plano

    private String blockCmd(String p, boolean block) {
        if (block) {
            return "cmd appops set " + p + " RUN_IN_BACKGROUND ignore; "
                    + "cmd appops set " + p + " RUN_ANY_IN_BACKGROUND ignore; "
                    + "cmd appops set " + p + " 10008 ignore; "
                    + "am set-standby-bucket " + p + " restricted; "
                    + "am force-stop " + p + "; ";
        }
        return "cmd appops set " + p + " RUN_IN_BACKGROUND allow; "
                + "cmd appops set " + p + " RUN_ANY_IN_BACKGROUND allow; "
                + "cmd appops set " + p + " 10008 allow; "
                + "am set-standby-bucket " + p + " active; ";
    }

    private void pickBlock() {
        final List<App> list = apps(true);
        if (list.isEmpty()) {
            toast("No hay apps para mostrar.");
            return;
        }
        final Set<String> blocked = blockedSet();
        String[] labels = new String[list.size()];
        boolean[] checked = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            labels[i] = list.get(i).label;
            checked[i] = blocked.contains(list.get(i).pkg);
        }
        multi("Marcadas = sin segundo plano ni autoinicio", labels, checked, c -> {
            final Set<String> now = new HashSet<>(blocked);
            final StringBuilder cmd = new StringBuilder();
            int add = 0, rem = 0;
            for (int i = 0; i < list.size(); i++) {
                String p = list.get(i).pkg;
                if (c[i] && !blocked.contains(p)) {
                    cmd.append(blockCmd(p, true));
                    now.add(p);
                    add++;
                } else if (!c[i] && blocked.contains(p)) {
                    cmd.append(blockCmd(p, false));
                    now.remove(p);
                    rem++;
                }
            }
            if (cmd.length() == 0) return;
            final String msg = "Listo ✔ Bloqueadas: " + add + "  Liberadas: " + rem;
            runTask("Aplicando bloqueo", () -> {
                Sh.run(cmd.toString());
                sp.edit().putStringSet("blocked", now).apply();
                return msg;
            });
        });
    }

    // ---------------------------------------------------------------- 5. Bloatware

    private void pickBloat() {
        final List<String> pk = new ArrayList<>();
        final List<String> lb = new ArrayList<>();
        final List<Boolean> wasOff = new ArrayList<>();
        for (String p : BLOAT) {
            try {
                ApplicationInfo a = pm.getApplicationInfo(p, 0);
                pk.add(p);
                lb.add(pm.getApplicationLabel(a) + "  (" + p + ")");
                wasOff.add(!a.enabled);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        if (pk.isEmpty()) {
            toast("No se encontró bloatware conocido.");
            return;
        }
        boolean[] checked = new boolean[pk.size()];
        for (int i = 0; i < pk.size(); i++) checked[i] = wasOff.get(i);
        multi("Marcadas = desactivadas (se pueden reactivar)", lb.toArray(new String[0]), checked, c -> {
            StringBuilder cmd = new StringBuilder();
            int off = 0, on = 0;
            for (int i = 0; i < pk.size(); i++) {
                String p = pk.get(i);
                if (c[i] && !wasOff.get(i)) {
                    cmd.append("pm disable-user --user 0 ").append(p).append("; ");
                    off++;
                } else if (!c[i] && wasOff.get(i)) {
                    cmd.append("pm enable ").append(p).append("; ");
                    on++;
                }
            }
            if (cmd.length() == 0) return;
            final String s = cmd.toString();
            final String msg = "Listo ✔ Desactivadas: " + off + "  Reactivadas: " + on;
            runTask("Aplicando cambios", () -> {
                Sh.run(s);
                return msg;
            });
        });
    }

    // ---------------------------------------------------------------- 6. Ahorro extremo

    private void toggleEco() {
        if (!sp.getBoolean("eco", false)) ecoOn();
        else ecoOff();
    }

    private void ecoOn() {
        runTask("Activando ahorro extremo", () -> {
            try {
                sp.edit()
                        .putInt("b", Settings.System.getInt(getContentResolver(), "screen_brightness", 100))
                        .putInt("bm", Settings.System.getInt(getContentResolver(), "screen_brightness_mode", 0))
                        .apply();
            } catch (Throwable ignored) {
            }
            Set<String> blocked = blockedSet();
            Set<String> eco = new HashSet<>();
            StringBuilder c = new StringBuilder();
            c.append("settings put global low_power 1; ");
            c.append("cmd netpolicy set restrict-background true; ");
            c.append("settings put system screen_brightness_mode 0; ");
            c.append("settings put system screen_brightness 30; ");
            for (App a : apps(true)) {
                c.append("am force-stop ").append(a.pkg).append("; ");
                if (!blocked.contains(a.pkg)) {
                    c.append("cmd appops set ").append(a.pkg).append(" RUN_ANY_IN_BACKGROUND ignore; ");
                    c.append("am set-standby-bucket ").append(a.pkg).append(" restricted; ");
                    eco.add(a.pkg);
                }
            }
            c.append("am kill-all");
            Sh.run(c.toString());
            sp.edit().putBoolean("eco", true).putStringSet("eco_apps", eco).apply();
            return "Ahorro extremo ACTIVO ✔\nAhorro de batería, ahorro de datos, brillo bajo, apps cerradas y restringidas.";
        });
    }

    private void ecoOff() {
        runTask("Revirtiendo ahorro extremo", () -> {
            Set<String> eco = new HashSet<>(sp.getStringSet("eco_apps", new HashSet<>()));
            StringBuilder c = new StringBuilder();
            c.append("settings put global low_power 0; ");
            c.append("cmd netpolicy set restrict-background false; ");
            c.append("settings put system screen_brightness_mode ").append(sp.getInt("bm", 0)).append("; ");
            c.append("settings put system screen_brightness ").append(sp.getInt("b", 100)).append("; ");
            for (String p : eco) {
                if (!safe(p)) continue;
                c.append("cmd appops set ").append(p).append(" RUN_ANY_IN_BACKGROUND allow; ");
                c.append("am set-standby-bucket ").append(p).append(" active; ");
            }
            Sh.run(c.toString());
            sp.edit().putBoolean("eco", false).remove("eco_apps").apply();
            return "Ahorro extremo desactivado ✔ Todo vuelve como estaba.";
        });
    }

    // ---------------------------------------------------------------- Apps protegidas

    private void pickProtected() {
        final List<App> list = apps(false);
        if (list.isEmpty()) {
            toast("No hay apps para mostrar.");
            return;
        }
        final Set<String> prot = userProtected();
        String[] labels = new String[list.size()];
        boolean[] checked = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            labels[i] = list.get(i).label;
            checked[i] = prot.contains(list.get(i).pkg);
        }
        multi("Marcadas = protegidas (no se cierran ni restringen)", labels, checked, c -> {
            Set<String> now = new HashSet<>();
            for (int i = 0; i < list.size(); i++) if (c[i]) now.add(list.get(i).pkg);
            sp.edit().putStringSet("protected", now).apply();
            setLog("Apps protegidas guardadas: " + now.size());
        });
    }
}
