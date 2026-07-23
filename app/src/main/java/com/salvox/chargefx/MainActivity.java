package com.salvox.chargefx;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int TAB_HOME = 0;
    private static final int TAB_PERMISSIONS = 1;
    private static final int TAB_TERMINAL = 2;
    private static final int TAB_SETTINGS = 3;

    private static final int COLOR_BG = Color.parseColor("#0A0A12");
    private static final int COLOR_CARD = Color.parseColor("#15151F");
    private static final int COLOR_ACCENT = Color.parseColor("#85B7EB");
    private static final int COLOR_ACCENT2 = Color.parseColor("#AFA9EC");
    private static final int COLOR_OK = Color.parseColor("#5DCAA5");
    private static final int COLOR_BAD = Color.parseColor("#F09595");
    private static final int COLOR_MUTED = Color.parseColor("#5F5E5A");
    private static final int COLOR_TEXT = Color.parseColor("#D3D1C7");

    private FrameLayout content;
    private int currentTab = TAB_HOME;
    private TextView terminalOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        content = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(content, contentParams);

        root.addView(buildBottomNav());
        setContentView(root);

        showTab(TAB_HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showTab(currentTab);
    }

    private void showTab(int tab) {
        currentTab = tab;
        content.removeAllViews();
        switch (tab) {
            case TAB_HOME: content.addView(buildHomeTab()); break;
            case TAB_PERMISSIONS: content.addView(buildPermissionsTab()); break;
            case TAB_TERMINAL: content.addView(buildTerminalTab()); break;
            case TAB_SETTINGS: content.addView(buildSettingsTab()); break;
        }
    }

    // ---------- INICIO ----------

    private View buildHomeTab() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = padded(new LinearLayout(this));
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = textView("Salvox ChargeFX", 20, Color.WHITE);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        LinearLayout overlayCard = card();
        overlayCard.addView(textView("Overlay", 16, COLOR_ACCENT));
        overlayCard.addView(spacer(8));
        overlayCard.addView(textView("Foreground service: " + (isForegroundReady() ? "listo" : "pendiente"), 12, COLOR_TEXT));
        overlayCard.addView(textView("Daemon root: " + (daemonLogExists() ? "detectado" : "no detectado"), 12, COLOR_TEXT));
        overlayCard.addView(spacer(12));
        Button previewBtn = new Button(this);
        previewBtn.setText("▶  Ver preview de la animación");
        previewBtn.setOnClickListener(v -> launchOverlayPreview());
        overlayCard.addView(previewBtn);
        layout.addView(overlayCard);
        layout.addView(spacer(12));

        LinearLayout permCard = card();
        permCard.addView(textView("Permisos", 16, COLOR_ACCENT));
        permCard.addView(spacer(8));
        permCard.addView(permRow("Mostrar sobre otras apps", hasOverlayPermission()));
        permCard.addView(permRow("Sin restricción de batería", isIgnoringBatteryOptimizations()));
        layout.addView(permCard);
        layout.addView(spacer(12));

        if (!hasOverlayPermission() || !isIgnoringBatteryOptimizations()) {
            Button resolveBtn = new Button(this);
            resolveBtn.setText("⚡  Resolver permiso pendiente");
            resolveBtn.setOnClickListener(v -> showTab(TAB_PERMISSIONS));
            layout.addView(resolveBtn);
        }

        scroll.addView(layout);
        return scroll;
    }

    private void launchOverlayPreview() {
        Intent serviceIntent = new Intent(this, ChargeOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private boolean isForegroundReady() {
        return true; // el servicio siempre puede arrancar en foreground si se le llama
    }

    private boolean daemonLogExists() {
        String out = RootShell.runBlocking("[ -f /data/local/tmp/salvox_charge_daemon.log ] && echo yes || echo no");
        return out.contains("yes");
    }

    // ---------- PERMISOS ----------

    private View buildPermissionsTab() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = padded(new LinearLayout(this));
        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(textView("Permisos", 20, Color.WHITE));
        layout.addView(spacer(16));

        layout.addView(permissionActionRow(
                "Mostrar sobre otras apps",
                hasOverlayPermission(),
                "Es imprescindible para poder dibujar la animación encima de todo. Sin él, el overlay nunca aparecerá.",
                this::requestOverlayPermission));

        layout.addView(spacer(12));

        layout.addView(permissionActionRow(
                "Sin restricción de batería",
                isIgnoringBatteryOptimizations(),
                "Evita que el sistema mate la app en segundo plano antes de que pueda reaccionar al enchufar el cargador.",
                this::requestIgnoreBatteryOptimizations));

        layout.addView(spacer(12));

        layout.addView(permissionActionRow(
                "Notificaciones",
                areNotificationsEnabled(),
                "El overlay se muestra como un servicio en primer plano, que en Android necesita una notificación (silenciosa) para poder arrancar desde segundo plano.",
                this::openNotificationSettings));

        scroll.addView(layout);
        return scroll;
    }

    private LinearLayout permissionActionRow(String label, boolean granted, String explanation, Runnable action) {
        LinearLayout row = card();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = textView(granted ? "✓" : "✕", 16, granted ? COLOR_OK : COLOR_BAD);
        icon.setPadding(0, 0, 16, 0);
        header.addView(icon);
        header.addView(textView(label, 14, Color.WHITE));
        row.addView(header);
        row.addView(spacer(6));
        row.addView(textView(explanation, 11, COLOR_MUTED));
        if (!granted) {
            row.addView(spacer(10));
            Button btn = new Button(this);
            btn.setText("Conceder");
            btn.setOnClickListener(v -> action.run());
            row.addView(btn);
        }
        return row;
    }

    private View permRow(String label, boolean granted) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 6, 0, 6);
        TextView icon = textView(granted ? "✓" : "✕", 14, granted ? COLOR_OK : COLOR_BAD);
        icon.setPadding(0, 0, 12, 0);
        row.addView(icon);
        row.addView(textView(label, 12, COLOR_TEXT));
        return row;
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestIgnoreBatteryOptimizations() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private boolean areNotificationsEnabled() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    // ---------- TERMINAL (root) ----------

    private View buildTerminalTab() {
        LinearLayout layout = padded(new LinearLayout(this));
        layout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(textView("Terminal 🔒", 20, Color.WHITE));
        layout.addView(header);
        layout.addView(spacer(10));

        terminalOutput = new TextView(this);
        terminalOutput.setTextColor(COLOR_OK);
        terminalOutput.setTextSize(10);
        terminalOutput.setTypeface(android.graphics.Typeface.MONOSPACE);
        terminalOutput.setText("Cargando log del daemon...");
        ScrollView outputScroll = new ScrollView(this);
        outputScroll.setBackgroundColor(Color.BLACK);
        outputScroll.setPadding(12, 12, 12, 12);
        outputScroll.addView(terminalOutput);
        LinearLayout.LayoutParams outputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 400);
        layout.addView(outputScroll, outputParams);
        layout.addView(spacer(10));

        refreshTerminalLog();

        EditText input = new EditText(this);
        input.setHint("comando root…");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(Color.WHITE);
        layout.addView(input);
        layout.addView(spacer(6));

        Button runBtn = new Button(this);
        runBtn.setText("Ejecutar");
        runBtn.setOnClickListener(v -> {
            String cmd = input.getText().toString();
            if (!cmd.trim().isEmpty()) {
                terminalOutput.setText("Ejecutando: " + cmd + "\n...");
                RootShell.run(cmd, output -> terminalOutput.setText(output));
            }
        });
        layout.addView(runBtn);
        layout.addView(spacer(16));

        layout.addView(textView("Accesos rápidos", 14, COLOR_ACCENT));
        layout.addView(spacer(8));
        layout.addView(quickActionButton("Ver log del daemon", "cat /data/local/tmp/salvox_charge_daemon.log"));
        layout.addView(quickActionButton("Comprobar proceso daemon", "ps -A | grep service.sh"));
        layout.addView(quickActionButton("dumpsys battery", "dumpsys battery"));
        layout.addView(quickActionButton("Ver estado keyguard", "dumpsys window | grep -i keyguard"));

        return layout;
    }

    private void refreshTerminalLog() {
        RootShell.run("tail -30 /data/local/tmp/salvox_charge_daemon.log 2>/dev/null || echo '(sin log todavia)'",
                output -> terminalOutput.setText(output));
    }

    private Button quickActionButton(String label, String command) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setOnClickListener(v -> {
            terminalOutput.setText("Ejecutando: " + label + "\n...");
            RootShell.run(command, output -> terminalOutput.setText(output));
        });
        return btn;
    }

    // ---------- AJUSTES ----------

    private View buildSettingsTab() {
        LinearLayout layout = padded(new LinearLayout(this));
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(textView("Ajustes", 20, Color.WHITE));
        layout.addView(spacer(16));
        layout.addView(textView("Salvox ChargeFX v1.0", 13, COLOR_TEXT));
        layout.addView(spacer(6));
        layout.addView(textView("Muestra la animación de carga de Salvox al enchufar el cargador con el móvil bloqueado.", 12, COLOR_MUTED));
        return layout;
    }

    // ---------- NAV INFERIOR ----------

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(COLOR_BG);
        nav.setPadding(0, 24, 0, 24);
        nav.setWeightSum(4);

        nav.addView(navItem("Inicio", TAB_HOME));
        nav.addView(navItem("Permisos", TAB_PERMISSIONS));
        nav.addView(navItem("Terminal", TAB_TERMINAL));
        nav.addView(navItem("Ajustes", TAB_SETTINGS));

        return nav;
    }

    private View navItem(String label, int tab) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(currentTab == tab ? COLOR_ACCENT : COLOR_MUTED);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tv.setOnClickListener(v -> showTab(tab));
        return tv;
    }

    // ---------- helpers de UI ----------

    private LinearLayout padded(LinearLayout l) {
        l.setPadding(32, 32, 32, 32);
        return l;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(COLOR_CARD);
        card.setPadding(28, 28, 28, 28);
        return card;
    }

    private TextView textView(String text, float sizeSp, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        return tv;
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (heightDp * getResources().getDisplayMetrics().density)));
        return v;
    }
}
