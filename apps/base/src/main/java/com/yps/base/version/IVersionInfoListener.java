package com.yps.base.version;
/*
 * Create by Taylor.Yao
 * on  2023/1/28 - 9:34
 */

import com.yps.compiler.annotation.LISTENER;

@LISTENER
public interface IVersionInfoListener {
    void onNewVersion(String vName, int vCode);

    void onLastVersion();
}
