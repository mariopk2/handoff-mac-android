package bg.mvsoft.clipbridge;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Намира Mac-а в локалната мрежа чрез Bonjour (mDNS). Блокира — викай от фонова нишка. */
final class Discovery {
    private static final String SERVICE_TYPE = "_clipbridge._tcp";

    /** Връща "ip:port" или null. */
    static String find(Context context, long timeoutMs) {
        NsdManager nsd = context.getSystemService(NsdManager.class);
        if (nsd == null) return null;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();

        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onServiceFound(NsdServiceInfo info) {
                nsd.resolveService(info, new NsdManager.ResolveListener() {
                    @Override public void onServiceResolved(NsdServiceInfo resolved) {
                        String ip = pickIPv4(resolved);
                        if (ip != null && result.compareAndSet(null, ip + ":" + resolved.getPort())) {
                            latch.countDown();
                        }
                    }
                    @Override public void onResolveFailed(NsdServiceInfo i, int errorCode) { }
                });
            }
            @Override public void onStartDiscoveryFailed(String type, int errorCode) { latch.countDown(); }
            @Override public void onStopDiscoveryFailed(String type, int errorCode) { }
            @Override public void onDiscoveryStarted(String type) { }
            @Override public void onDiscoveryStopped(String type) { }
            @Override public void onServiceLost(NsdServiceInfo info) { }
        };

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener);
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        } finally {
            try { nsd.stopServiceDiscovery(listener); } catch (Exception ignored) { }
        }
        return result.get();
    }

    private static String pickIPv4(NsdServiceInfo info) {
        List<InetAddress> addrs = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 34) {
            addrs.addAll(info.getHostAddresses());
        } else if (info.getHost() != null) {
            addrs.add(info.getHost());
        }
        for (InetAddress a : addrs) {
            if (a instanceof Inet4Address) return a.getHostAddress();
        }
        return null;
    }
}
