package com.herbwen.reforceapk;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import dalvik.system.DexClassLoader;

public class ProxyApplication extends Application{
    private static final String appkey = "APPLICATION_CLASS_NAME";
    private String apkFileName;
    private String odexPath;
    private String libPath;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            File odex = this.getDir("payload_odex", MODE_PRIVATE);
            File libs = this.getDir("payload_lib", MODE_PRIVATE);
            odexPath = odex.getAbsolutePath();
            libPath = libs.getAbsolutePath();
            apkFileName = odex.getAbsolutePath() + "/payload.apk";
            File dexFile = new File(apkFileName);
            Log.i("demo", "apk size:"+dexFile.length());
            if (!dexFile.exists())
            {
                dexFile.createNewFile();
                byte[] dexdata = this.readDexFileFromApk();
                this.splitPayLoadFromDex(dexdata);
            }
            Object currentActivityThread = RefInvoke.invokeStaticMethod(
                    "android.app.ActivityThread", "currentActivityThread",
                    new Class[] {}, new Object[] {});
            String packageName = this.getPackageName();

            ArrayMap mPackages = (ArrayMap) RefInvoke.getFieldObject(
                    "android.app.ActivityThread", currentActivityThread,
                    "mPackages");
            WeakReference wr = (WeakReference) mPackages.get(packageName);

            DexClassLoader dLoader = new DexClassLoader(apkFileName, odexPath,
                    libPath, (ClassLoader) RefInvoke.getFieldObject(
                    "android.app.LoadedApk", wr.get(), "mClassLoader"));

            RefInvoke.setFieldObject("android.app.LoadedApk", "mClassLoader",
                    wr.get(), dLoader);

            Log.i("demo","classloader:"+dLoader);

            try{
                Object actObj = dLoader.loadClass("com.herbwen.unshell.MainActivity");
                Log.i("demo", "actObj:"+actObj);
            }catch(Exception e){
                Log.i("demo", "activity:"+Log.getStackTraceString(e));
            }
        } catch (Exception e) {
            Log.i("demo", "error:"+Log.getStackTraceString(e));
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        {
            loadResources(apkFileName);

            Log.i("demo", "onCreate");

            String appClassName = null;
            try {
                ApplicationInfo ai = this.getPackageManager()
                        .getApplicationInfo(this.getPackageName(),
                                PackageManager.GET_META_DATA);
                Bundle bundle = ai.metaData;
                if (bundle != null && bundle.containsKey("APPLICATION_CLASS_NAME")) {
                    appClassName = bundle.getString("APPLICATION_CLASS_NAME");
                } else {
                    Log.i("demo", "have no application class name");
                    return;
                }
            } catch (NameNotFoundException e) {
                Log.i("demo", "error:"+Log.getStackTraceString(e));
                e.printStackTrace();
            }

            Object currentActivityThread = RefInvoke.invokeStaticMethod(
                    "android.app.ActivityThread", "currentActivityThread",
                    new Class[] {}, new Object[] {});
            Log.i("Herrrb", "currentActivityThread:"+currentActivityThread);
            Object mBoundApplication = RefInvoke.getFieldObject(
                    "android.app.ActivityThread", currentActivityThread,
                    "mBoundApplication");
            Log.i("Herrrb", "mBoundApplication:"+mBoundApplication);
            Object loadedApkInfo = RefInvoke.getFieldObject(
                    "android.app.ActivityThread$AppBindData",
                    mBoundApplication, "info");
            Log.i("Herrrb", "loadedApkinfo:"+loadedApkInfo);
            RefInvoke.setFieldObject("android.app.LoadedApk", "mApplication",
                    loadedApkInfo, null);
            Object oldApplication = RefInvoke.getFieldObject(
                    "android.app.ActivityThread", currentActivityThread,
                    "mInitialApplication");

            ArrayList<Application> mAllApplications = (ArrayList<Application>) RefInvoke
                    .getFieldObject("android.app.ActivityThread",
                            currentActivityThread, "mAllApplications");
            mAllApplications.remove(oldApplication);

            ApplicationInfo appinfo_In_LoadedApk = (ApplicationInfo) RefInvoke
                    .getFieldObject("android.app.LoadedApk", loadedApkInfo,
                            "mApplicationInfo");
            ApplicationInfo appinfo_In_AppBindData = (ApplicationInfo) RefInvoke
                    .getFieldObject("android.app.ActivityThread$AppBindData",
                            mBoundApplication, "appInfo");
            appinfo_In_LoadedApk.className = appClassName;
            appinfo_In_AppBindData.className = appClassName;
            Log.i("Herrrb", "appClassName:"+appClassName);
            Application app = (Application) RefInvoke.invokeMethod(
                    "android.app.LoadedApk", "makeApplication", loadedApkInfo,
                    new Class[] { boolean.class, Instrumentation.class },
                    new Object[] { false, null });
            Log.i("Herrrb", "app:"+app);
            RefInvoke.setFieldObject("android.app.ActivityThread",
                    "mInitialApplication", currentActivityThread, app);

            ArrayMap mProviderMap = (ArrayMap) RefInvoke.getFieldObject(
                    "android.app.ActivityThread", currentActivityThread,
                    "mProviderMap");
            Iterator it = mProviderMap.values().iterator();
            while (it.hasNext()) {
                Object providerClientRecord = it.next();
                Object localProvider = RefInvoke.getFieldObject(
                        "android.app.ActivityThread$ProviderClientRecord",
                        providerClientRecord, "mLocalProvider");
                RefInvoke.setFieldObject("android.content.ContentProvider",
                        "mContext", localProvider, app);
            }

            Log.i("demo", "app:"+app);

            app.onCreate();
        }
    }
    private void splitPayLoadFromDex(byte[] apkdata) throws IOException {
        int ablen = apkdata.length;

        byte[] dexlen = new byte[4];
        System.arraycopy(apkdata, ablen - 4, dexlen, 0, 4);
        ByteArrayInputStream bais = new ByteArrayInputStream(dexlen);
        DataInputStream in = new DataInputStream(bais);
        int readInt = in.readInt();
        System.out.println(Integer.toHexString(readInt));
        byte[] newdex = new byte[readInt];

        System.arraycopy(apkdata, ablen - 4 - readInt, newdex, 0, readInt);

        newdex = decrypt(newdex);

        File file = new File(apkFileName);
        try {
            FileOutputStream localFileOutputStream = new FileOutputStream(file);
            localFileOutputStream.write(newdex);
            localFileOutputStream.close();
        } catch (IOException localIOException) {
            throw new RuntimeException(localIOException);
        }

        ZipInputStream localZipInputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(file)));
        while (true) {
            ZipEntry localZipEntry = localZipInputStream.getNextEntry();
            if (localZipEntry == null) {
                localZipInputStream.close();
                break;
            }

            String name = localZipEntry.getName();
            if (name.startsWith("lib/") && name.endsWith(".so")) {
                File storeFile = new File(libPath + "/"
                        + name.substring(name.lastIndexOf('/')));
                storeFile.createNewFile();
                FileOutputStream fos = new FileOutputStream(storeFile);
                byte[] arrayOfByte = new byte[1024];
                while (true) {
                    int i = localZipInputStream.read(arrayOfByte);
                    if (i == -1)
                        break;
                    fos.write(arrayOfByte, 0, i);
                }
                fos.flush();
                fos.close();
            }
            localZipInputStream.closeEntry();
        }
        localZipInputStream.close();
    }

    private byte[] readDexFileFromApk() throws IOException {
        ByteArrayOutputStream dexByteArrayOutputStream = new ByteArrayOutputStream();
        ZipInputStream localZipInputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(
                        this.getApplicationInfo().sourceDir)));
        while (true) {
            ZipEntry localZipEntry = localZipInputStream.getNextEntry();
            if (localZipEntry == null) {
                localZipInputStream.close();
                break;
            }
            if (localZipEntry.getName().equals("classes.dex")) {
                byte[] arrayOfByte = new byte[1024];
                while (true) {
                    int i = localZipInputStream.read(arrayOfByte);
                    if (i == -1)
                        break;
                    dexByteArrayOutputStream.write(arrayOfByte, 0, i);
                }
            }
            localZipInputStream.closeEntry();
        }
        localZipInputStream.close();
        return dexByteArrayOutputStream.toByteArray();
    }

    private byte[] decrypt(byte[] srcdata) {
        for(int i=0;i<srcdata.length;i++){
            srcdata[i] = (byte)(0xFF ^ srcdata[i]);
        }
        return srcdata;
    }

    protected AssetManager mAssetManager;//��Դ������
    protected Resources mResources;//��Դ
    protected Theme mTheme;//����


    protected void loadResources(String dexPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, dexPath);
            mAssetManager = assetManager;
        } catch (Exception e) {
            Log.i("inject", "loadResource error:"+Log.getStackTraceString(e));
            e.printStackTrace();
        }
        Resources superRes = super.getResources();
        superRes.getDisplayMetrics();
        superRes.getConfiguration();
        mResources = new Resources(mAssetManager, superRes.getDisplayMetrics(),superRes.getConfiguration());
        mTheme = mResources.newTheme();
        mTheme.setTo(super.getTheme());
    }

    @Override
    public AssetManager getAssets() {
        return mAssetManager == null ? super.getAssets() : mAssetManager;
    }

    @Override
    public Resources getResources() {
        return mResources == null ? super.getResources() : mResources;
    }

    @Override
    public Theme getTheme() {
        return mTheme == null ? super.getTheme() : mTheme;
    }

}

