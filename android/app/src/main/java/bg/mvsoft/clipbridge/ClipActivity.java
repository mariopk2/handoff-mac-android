package bg.mvsoft.clipbridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

/**
 * Невидим екран, който се отваря от плочките или от "Сподели".
 * Android 10+ позволява четене на клипборда само на приложение на преден план,
 * затова четем едва когато прозорецът получи фокус.
 */
public class ClipActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final String MODE_SEND = "send";
    static final String MODE_RECEIVE = "receive";

    private boolean started = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new View(this));

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            started = true;
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text == null || text.length() == 0) {
                done("Няма текст за изпращане");
            } else {
                send(text.toString());
            }
        } else if (MODE_RECEIVE.equals(intent.getStringExtra(EXTRA_MODE))) {
            started = true;
            receive();
        }
        // MODE_SEND чака onWindowFocusChanged
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || started) return;
        started = true;

        ClipboardManager cm = getSystemService(ClipboardManager.class);
        ClipData clip = cm.getPrimaryClip();
        String text = null;
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence cs = clip.getItemAt(0).coerceToText(this);
            if (cs != null) text = cs.toString();
        }
        if (text == null || text.isEmpty()) {
            done("Клипбордът е празен");
        } else {
            send(text);
        }
    }

    private void send(String text) {
        new Thread(() -> {
            try {
                Net.sendToMac(getApplicationContext(), text);
                done("Изпратено към Mac ✓");
            } catch (Net.AuthException e) {
                done("Грешен код — отвори ClipBridge и го въведи");
            } catch (Exception e) {
                done("Mac не е намерен в мрежата");
            }
        }).start();
    }

    private void receive() {
        new Thread(() -> {
            try {
                String text = Net.fetchFromMac(getApplicationContext());
                runOnUiThread(() -> {
                    if (text.isEmpty()) {
                        done("Клипбордът на Mac е празен");
                        return;
                    }
                    ClipboardManager cm = getSystemService(ClipboardManager.class);
                    cm.setPrimaryClip(ClipData.newPlainText("ClipBridge", text));
                    done("Взето от Mac ✓");
                });
            } catch (Net.AuthException e) {
                done("Грешен код — отвори ClipBridge и го въведи");
            } catch (Exception e) {
                done("Mac не е намерен в мрежата");
            }
        }).start();
    }

    private void done(String message) {
        runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
