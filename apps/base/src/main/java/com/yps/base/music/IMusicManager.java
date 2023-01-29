package com.yps.base.music;

import com.yps.compiler.annotation.AIDL;

/*
 * Create by Taylor.Yao
 * on  2023/1/10 - 16:13
 */
@AIDL
public interface IMusicManager {
    void play(int from);

    void pause(int from);

    void playMusic(IMusicInfo iMusicInfo);

    void addPlayListener(IPlayListener listener);
}
