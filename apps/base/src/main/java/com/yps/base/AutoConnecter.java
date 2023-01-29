package com.yps.base;
/*
 * Create by Taylor.Yao
 * on  2023/1/9 - 10:15
 */

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.util.Log;


import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.List;

public class AutoConnecter<T> implements ServiceConnection {
    private static final String TAG = "AutoConnecter";
    private String implName;
    private String pkgName;
    private String action;
    private Context context;
    private OnConnectListener<T> listener;
    Class<T> tClass;

    public AutoConnecter(Class<T> tClass, Context context) {
        this.tClass = tClass;
        this.context = context;
        try {
            parseAction();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseAction() throws Exception {
        String simpleName = tClass.getSimpleName();
        InputStream inputStream = context.getAssets().open("service/" + simpleName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        JSONObject object = new JSONObject(reader.readLine());
        reader.close();
        this.implName = object.getString("proxyName");
        this.action = object.getString("serviceName");
        this.pkgName = object.getString("pkgName");
        Log.d(TAG, "parseAction: implName=" + implName + "  action= " + action+ "  pkgName= " + pkgName);
    }

    public void connect(OnConnectListener<T> listener) {
        this.listener = listener;
        Intent intent = new Intent();
        intent.setAction(this.action);
        intent.setPackage(this.pkgName);
        boolean service = context.getApplicationContext().bindService(intent, this, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "service is exist " + service + "  connect: " + action + "   implName " + implName);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "onServiceConnected: " + name.getPackageName());
        Class<?> aClass = null;
        try {
            aClass = Class.forName(implName);
            Method asInterface = aClass.getDeclaredMethod("asInterface", IBinder.class);
            T cast1 = tClass.cast(asInterface.invoke(null, service));
            if (listener != null) {
                listener.onConnect(cast1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected: " + name.getPackageName());
    }

    public interface OnConnectListener<T> {
        void onConnect(T t);
    }

    public static Intent createExplicitFromImplicitIntent(Context context, Intent implicitIntent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(implicitIntent, 0);
        if (resolveInfo == null || resolveInfo.size() != 1) {
            return null;
        }
        ResolveInfo serviceInfo = resolveInfo.get(0);
        String packageName = serviceInfo.serviceInfo.packageName;
        String className = serviceInfo.serviceInfo.name;
        ComponentName component = new ComponentName(packageName, className);
        Intent explicitIntent = new Intent(implicitIntent);
        explicitIntent.setComponent(component);

        return explicitIntent;
    }
}
