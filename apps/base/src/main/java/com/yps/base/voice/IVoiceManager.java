package com.yps.base.voice;
/*
 * Create by Taylor.Yao
 * on  2023/1/10 - 10:40
 */

import com.yps.compiler.annotation.AIDL;
import com.yps.compiler.annotation.IInterface;

@AIDL
public interface IVoiceManager {
    void startListen(int what);

    void stopListen(int what);

    void pauseListen(int why);

    void resumeListen(int why);

    void registerProcess(IVoiceProcess process);
}
