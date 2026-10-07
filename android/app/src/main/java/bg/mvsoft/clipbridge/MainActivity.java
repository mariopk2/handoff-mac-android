package bg.mvsoft.clipbridge;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Екран с настройки: адрес на Mac, код, тест и добавяне на плочките. */
public class MainActivity extends Activity {

    private EditText hostField;
    private EditText tokenField;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(32), pad, pad);

        TextView title = new TextView(this);
        title.setText("ClipBridge");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        root.addView(title);

        root.addView(label("Код от менюто на Mac (иконата горе вдясно)"));
        tokenField = new EditText(this);
        tokenField.setHint("напр. K7M2QX");
        tokenField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        tokenField.setText(Config.token(this));
        root.addView(tokenField);

        root.addView(label("Адрес на Mac (попълва се автоматично)"));
        hostField = new EditText(this);
        hostField.setHint("напр. 192.168.1.20");
        hostField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hostField.setText(Config.host(this));
        root.addView(hostField);

        root.addView(button("Намери Mac автоматично", this::discover));
        root.addView(button("Запази и тествай", this::saveAndTest));

        status = new TextView(this);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status);

        if (Build.VERSION.SDK_INT >= 33) {
            root.addView(label("Плочки в Quick Settings"));
            root.addView(button("Добави плочка „Към Mac“",
                    () -> addTile(SendTile.class, "Към Mac", R.drawable.ic_up)));
            root.addView(button("Добави плочка „От Mac“",
                    () -> addTile(ReceiveTile.class, "От Mac", R.drawable.ic_down)));
        }

        TextView help = new TextView(this);
        help.setPadding(0, dp(16), 0, 0);
        help.setText("Как се ползва:\n"
                + "• Копирай текст на телефона → дръпни панела надолу → „Към Mac“.\n"
                + "• Копирай текст на Mac → на телефона натисни „От Mac“.\n"
                + "• Или маркирай текст → Сподели → ClipBridge.\n\n"
                + "Двете устройства трябва да са в една Wi‑Fi мрежа.");
        root.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void saveFields() {
        Config.setToken(this, tokenField.getText().toString());
        Config.setHost(this, hostField.getText().toString());
        tokenField.setText(Config.token(this));
        hostField.setText(Config.host(this));
    }

    private void discover() {
        status.setText("Търся Mac в мрежата…");
        new Thread(() -> {
            String found = Discovery.find(getApplicationContext(), 6000);
            runOnUiThread(() -> {
                if (found == null) {
                    status.setText("Не намерих Mac. Пуснато ли е ClipBridge на Mac-а и в една мрежа ли сте?");
                } else {
                    hostField.setText(found);
                    Config.setHost(this, found);
                    status.setText("Намерен: " + found);
                }
            });
        }).start();
    }

    private void saveAndTest() {
        saveFields();
        status.setText("Проверявам…");
        new Thread(() -> {
            String msg;
            try {
                Net.ping(getApplicationContext());
                msg = "✓ Връзката работи (" + Config.host(getApplicationContext()) + ")";
            } catch (Net.AuthException e) {
                msg = "✗ Грешен или липсващ код";
            } catch (Exception e) {
                msg = "✗ Mac не отговаря";
            }
            String finalMsg = msg;
            runOnUiThread(() -> {
                status.setText(finalMsg);
                hostField.setText(Config.host(this));
            });
        }).start();
    }

    private void addTile(Class<?> cls, String label, int icon) {
        if (Build.VERSION.SDK_INT < 33) return;
        StatusBarManager sbm = getSystemService(StatusBarManager.class);
        sbm.requestAddTileService(new ComponentName(this, cls), label,
                Icon.createWithResource(this, icon), getMainExecutor(),
                result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        Toast.makeText(this, "Плочката вече е добавена", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(18), 0, dp(4));
        return t;
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
