package com.yps.voiceassistant;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.yps.voice.R;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity---";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.bind).setOnClickListener(v -> {
            bindService();
        });
    }

    private void bindService() {
        Intent intent = new Intent();
        intent.setAction("com.yps.base.music.IMusicManagerProxyService");
        String packageName = getPackageName();
        intent.setPackage("com.yps.voice");
        boolean bb = bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected: " + name);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected: " + name);
            }
        }, BIND_AUTO_CREATE);
        Log.d(TAG, "bindService: " + packageName + bb);
    }
}
