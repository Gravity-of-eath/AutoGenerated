package com.yps.voiceassistant.music;
/*
 * Create by Taylor.Yao
 * on  2023/1/10 - 16:17
 */

import android.util.Log;

import com.yps.base.music.IMusicInfo;
import com.yps.base.music.IMusicManager;
import com.yps.base.music.IPlayListener;
import com.yps.compiler.annotation.TargetService;

@TargetService(name = IMusicManager.class)
public class YPSMusicManager implements IMusicManager {
    private static final String TAG = "YPSMusicManager";
    private IPlayListener listener;

    @Override
    public void play(int from) {
        Log.d(TAG, "play: ");
        listener.onPlayStatus(0);
    }

    @Override
    public void pause(int from) {
        Log.d(TAG, "pause: ");
        listener.onPlayStatus(2);
    }

    @Override
    public void playMusic(IMusicInfo iMusicInfo) {
        Log.d(TAG, "play: " + iMusicInfo);
        listener.onPlayStatus(1);
    }

    @Override
    public void addPlayListener(IPlayListener listener) {
        this.listener = listener;
    }
}
