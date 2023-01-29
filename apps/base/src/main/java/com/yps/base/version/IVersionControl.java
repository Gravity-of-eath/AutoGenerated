package com.yps.base.version;
/*
 * Create by Taylor.Yao
 * on  2023/1/28 - 9:31
 */

import com.yps.compiler.annotation.AIDL;

@AIDL
public interface IVersionControl {

    int getVersionCode();

    String getVersionName();

    void checkUpdate(IVersionInfoListener listener);

    void upDate();
}
