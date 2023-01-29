package com.yps.base.voice;
/*
 * Create by Taylor.Yao
 * on  2023/1/10 - 10:42
 */

import com.yps.compiler.annotation.LISTENER;

@LISTENER
public interface IVoiceProcess {
    void onRecognizer(String text);

    void onMvw(String word);
}
