package bg.mvsoft.clipbridge;

import android.service.quicksettings.TileService;

/** Плочка "Към Mac": изпраща клипборда на телефона към Mac. */
public class SendTile extends TileService {
    @Override public void onStartListening() { TileLauncher.markReady(this); }
    @Override public void onClick() { TileLauncher.launch(this, ClipActivity.MODE_SEND); }
}
