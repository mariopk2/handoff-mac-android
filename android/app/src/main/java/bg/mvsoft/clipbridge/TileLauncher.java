package bg.mvsoft.clipbridge;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

final class TileLauncher {

    static void launch(TileService service, String mode) {
        Runnable run = () -> {
            Intent intent = new Intent(service, ClipActivity.class)
                    .putExtra(ClipActivity.EXTRA_MODE, mode)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pi = PendingIntent.getActivity(service, mode.hashCode(), intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                service.startActivityAndCollapse(pi);
            } else {
                service.startActivityAndCollapse(intent);
            }
        };
        if (service.isLocked()) service.unlockAndRun(run); else run.run();
    }

    static void markReady(TileService service) {
        Tile tile = service.getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
