package bg.mvsoft.clipbridge;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTP към Mac-а. Блокира — викай от фонова нишка. */
final class Net {

    static final class AuthException extends IOException {
        AuthException() { super("bad token"); }
    }

    static void sendToMac(Context c, String text) throws IOException {
        call(c, "POST", "/clip", text);
    }

    static String fetchFromMac(Context c) throws IOException {
        return call(c, "GET", "/clip", null);
    }

    static void ping(Context c) throws IOException {
        call(c, "GET", "/ping", null);
    }

    /** Ако Mac-ът не отговаря (сменено IP), търси го отново и опитва още веднъж. */
    private static String call(Context c, String method, String path, String body) throws IOException {
        if (Config.token(c).isEmpty()) throw new AuthException();
        String host = Config.host(c);
        if (!host.isEmpty()) {
            try {
                return request(host, Config.token(c), method, path, body);
            } catch (AuthException e) {
                throw e;
            } catch (IOException ignored) {
                // продължаваме към търсене
            }
        }
        String found = Discovery.find(c, 5000);
        if (found == null) throw new IOException("Mac not found");
        Config.setHost(c, found);
        return request(found, Config.token(c), method, path, body);
    }

    private static String request(String host, String token, String method, String path, String body)
            throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL("http://" + host + path).openConnection();
        try {
            con.setConnectTimeout(2500);
            con.setReadTimeout(5000);
            con.setRequestMethod(method);
            con.setRequestProperty("X-Token", token);
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                con.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int code = con.getResponseCode();
            if (code == 401) throw new AuthException();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            con.disconnect();
        }
    }
}
