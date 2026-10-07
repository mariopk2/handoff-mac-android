package bg.mvsoft.clipbridge;

import android.service.quicksettings.TileService;

/** Плочка "От Mac": взима клипборда на Mac и го слага в телефона. */
public class ReceiveTile extends TileService {
    @Override public void onStartListening() { TileLauncher.markReady(this); }
    @Override public void onClick() { TileLauncher.launch(this, ClipActivity.MODE_RECEIVE); }
}
