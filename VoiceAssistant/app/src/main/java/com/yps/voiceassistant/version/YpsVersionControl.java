package com.yps.voiceassistant.version;
/*
 * Create by Taylor.Yao
 * on  2023/1/28 - 9:37
 */


import com.yps.base.version.IVersionControl;
import com.yps.base.version.IVersionInfoListener;
import com.yps.compiler.annotation.TargetService;

@TargetService(name = IVersionControl.class)
public class YpsVersionControl implements IVersionControl {

    int vCode = 2023;
    String vName = "NewVersion2023";
    IVersionInfoListener listener;

    @Override
    public int getVersionCode() {
        return vCode;
    }

    @Override
    public String getVersionName() {
        return vName;
    }

    @Override
    public void checkUpdate(IVersionInfoListener listener) {
        this.listener = listener;
        if (listener != null) {
            listener.onNewVersion(vName, vCode);
        }
    }

    @Override
    public void upDate() {
        if (listener != null) {
            listener.onLastVersion();
        }
    }
}
