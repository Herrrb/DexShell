package com.herbwen.unshell;

import android.app.Application;
import android.util.Log;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("Herrrb", "source apk onCreate:"+this);
    }
}
