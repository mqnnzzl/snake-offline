package com.mintarcade.snake;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;

/** Decode asynchronously; no synthesis or audio work in the game frame callback. */
final class GameMusic {
    private final AudioManager audio;
    private MediaPlayer player;
    private AudioFocusRequest focusRequest;
    private boolean ready, wanted, focused, released;
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (released || player == null) return;
        if (change == AudioManager.AUDIOFOCUS_GAIN) {
            focused = true;
            if (wanted && ready) player.start();
        } else {
            focused = false;
            if (ready && player.isPlaying()) player.pause();
        }
    };

    GameMusic(Context context) {
        audio = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        if (Build.VERSION.SDK_INT >= 26) focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener).setWillPauseWhenDucked(true).build();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attributes);
            player.setVolume(.55f, .55f);
            player.setLooping(true);
            player.setOnPreparedListener(p -> { ready = true; if (wanted) play(); });
            player.setOnErrorListener((p, what, extra) -> { release(); return true; });
            try (AssetFileDescriptor asset = context.getResources().openRawResourceFd(R.raw.neon_orchard)) {
                player.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
            }
            player.prepareAsync();
        } catch (Exception failure) {
            release(); // Audio failure must not prevent offline gameplay.
        }
    }
    void setActive(boolean active) {
        if (released || wanted == active) return;
        wanted = active;
        if (active) { if (ready) play(); }
        else {
            if (ready && player.isPlaying()) player.pause();
            abandon();
        }
    }
    private void play() {
        if (released || !wanted || !ready) return;
        int result = Build.VERSION.SDK_INT >= 26 ? audio.requestAudioFocus(focusRequest)
            : audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        focused = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (focused) player.start();
    }
    private void abandon() {
        if (audio == null) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        else audio.abandonAudioFocus(focusListener);
        focused = false;
    }
    void release() {
        if (released) return;
        released = true; wanted = false;
        abandon();
        if (player != null) { player.release(); player = null; }
    }
}
