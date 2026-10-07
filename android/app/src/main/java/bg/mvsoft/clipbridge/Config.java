package bg.mvsoft.clipbridge;

import android.content.Context;
import android.content.SharedPreferences;

final class Config {
    static final int DEFAULT_PORT = 8765;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("clipbridge", Context.MODE_PRIVATE);
    }

    /** Връща "ip:port" или празен низ. */
    static String host(Context c) {
        return prefs(c).getString("host", "");
    }

    static void setHost(Context c, String host) {
        host = host == null ? "" : host.trim();
        if (!host.isEmpty() && !host.contains(":")) host = host + ":" + DEFAULT_PORT;
        prefs(c).edit().putString("host", host).apply();
    }

    static String token(Context c) {
        return prefs(c).getString("token", "");
    }

    static void setToken(Context c, String token) {
        prefs(c).edit().putString("token", token == null ? "" : token.trim().toUpperCase()).apply();
    }
}
