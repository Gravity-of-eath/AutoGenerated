package com.yps.base.music;
/*
 * Create by Taylor.Yao
 * on  2023/1/10 - 16:15
 */

import com.yps.compiler.annotation.LISTENER;

@LISTENER
public interface IPlayListener {
    void onPlayStatus(int status);
}
