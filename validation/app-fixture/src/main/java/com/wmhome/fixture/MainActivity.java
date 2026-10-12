// SPDX-License-Identifier: GPL-3.0-only
package com.wmhome.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.widget.TextView;
public class MainActivity extends Activity {
    private MediaSession session;
    private TextView label;
    private long position = 60000;
    private void state(int value) {
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
                PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO)
            .setState(value, position, value == PlaybackState.STATE_PLAYING ? 1f : 0f, SystemClock.elapsedRealtime()).build());
        label.setText(value == PlaybackState.STATE_PLAYING ? "测试第三方应用 · 正在播放" : "测试第三方应用 · 已暂停");
    }
    private void title(String title) {
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "本地验证播放器")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 240000).build());
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        label = new TextView(this); label.setTextSize(28); label.setGravity(17); setContentView(label);
        session = new MediaSession(this, "wm-fixture");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { state(PlaybackState.STATE_PLAYING); }
            @Override public void onPause() { state(PlaybackState.STATE_PAUSED); }
            @Override public void onSkipToNext() { title("下一首测试"); }
            @Override public void onSkipToPrevious() { title("上一首测试"); }
            @Override public void onSeekTo(long value) { position = value; state(PlaybackState.STATE_PLAYING); }
        });
        session.setActive(true); title("威马桌面媒体测试"); state(PlaybackState.STATE_PLAYING);
    }
    @Override public void onDestroy() { session.release(); super.onDestroy(); }
}
