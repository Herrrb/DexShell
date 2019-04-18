"# DexShell" 
> 最让我没有想到的是，这种远古方法居然成功了
# 前言
因为最近要准备一些东西，所以决心把这个远古方法的加固给完成了，借此来了解一下基本的加固原理与实现

GitHub：

# 环境
* 真机：Zuk Z2 Pro， Android8.0
* Android Studio
![p1][1]
* 环境：
![p2][2]

注意避免dex分包：

    multiDexEnabled false

# 原理分析
需要的三个对象：
1. 需要加固的apk（源apk）
2. 壳程序apk，即上图中的脱壳Dex的出处
3. 加密工具（将源Apk进行加密和壳Dex合并成新的Dex）

主要步骤：首先需要写一个源Apk，即需要被加壳的apk，只需要一个实现简单跳转和Toast功能的demo即可；然后是一个脱壳apk，这里理解了很久为什么是个脱壳apk，因为我们需要把源apk打进脱壳apk的classes.dex里，然后安装的时候是安装的这个脱壳apk，既然安装的是脱壳apk，那么如何执行源apk的功能呢？这里就涉及到脱壳apk的功能，就是脱壳，将自身dex载入，再将打进去的apk拿出来执行，这样就执行了原来的apk；

最后就是加密工具，即将源apk与脱壳dex合并的工具，这里工具的输出是一个新的classes.dex；

工具操作的主体是dex，因为dex结构，加壳的时候需要注意，checksum、signature、filesize三个字段，即当apk和apk大小（4字节）附加到dex之后，这三个字段需要进行修正；

# 源APK
这边定义包名为：com.herbwen.unshell
结构如下
![p3][3]

## MainActivity
即正常的功能，设定TextView和Intent；

    package com.herbwen.unshell;
    import android.content.Intent;
    import android.support.v7.app.AppCompatActivity;
    import android.os.Bundle;
    import android.util.Log;
    import android.view.View;
    import android.widget.TextView;
    public class MainActivity extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            TextView content = new TextView(this);
            content.setText("I am Source Apk");
            content.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, SubActivity.class);
                    startActivity(intent);
                }
            });
            setContentView(content);
            Log.i("Herrrb", "app:"+getApplicationContext());
        }
    }

## SubActivity
即Intent关联的Activity

    package com.herbwen.unshell;
    import android.os.Bundle;
    import android.support.annotation.Nullable;
    import android.support.v7.app.AppCompatActivity;
    import android.util.Log;
    import android.view.View;
    import android.widget.Button;
    import android.widget.Toast;
    public class SubActivity extends AppCompatActivity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_sub);
            Button button = findViewById(R.id.button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(SubActivity.this, "唉，难受", Toast.LENGTH_SHORT).show();
                }
            });
            Log.i("Herrrb", "App:" + getApplicationContext());
        }
    }

# 加壳程序
首先来看加壳程序才能知道脱壳apk为什么要这样写；
因为总要上传到GitHub上，这里就分段来写
首先是导入

        package com.example.reforceapk;
        import java.io.ByteArrayOutputStream;
        import java.io.File;
        import java.io.FileInputStream;
        import java.io.FileOutputStream;
        import java.io.IOException;
        import java.security.MessageDigest;
        import java.security.NoSuchAlgorithmException;
        import java.util.zip.Adler32;
    
    
类下有一下几个方法
1. main：主要执行载入以及加壳功能；
2. encrypt：对apk文件进行加密；
3. fixCheckSumHeader：修正更改后的dex头checksum字段；
4. intToByte：字面意思，int转byte
5. fixSHA1Header：修正signature字段；
6. fixFileSizeHeader：修正filesize字段；
7. readFileBytes：以二进制读出文件内容；
    
## main
    
    public static void main(String[] args) {
       // TODO Auto-generated method stub
                 try {
                        File payloadSrcFile = new File("force/source_app.apk");    
                        // 获取到需要加壳的程序
                        System.out.println("apk size:"+payloadSrcFile.length());
                        // 打印需要加壳的apk的大小，即长度
                        File unShellDexFile = new File("force/unshell.dex");    
                        // 载入解客dex
                        byte[] payloadArray =  encrpt(readFileBytes(payloadSrcFile));
                        // 以二进制形式读出apk，并进行加密处理，后面可以看到这里采用的是异或加密
    
                        byte[] unShellDexArray = readFileBytes(unShellDexFile);
                        // 以二进制形式读出dex
                        int payloadLen = payloadArray.length;
                        // 加密后源apk大小
                        int unShellDexLen = unShellDexArray.length;
                        int totalLen = payloadLen + unShellDexLen +4;
                        // 总长度，多出4字节是存放长度的。
                        byte[] newdex = new byte[totalLen]; 
                        // 申请了新的长度的数组
    
                        // 添加解壳代码
                        System.arraycopy(unShellDexArray, 0, newdex, 0,  unShellDexLen);
                        // 先拷贝dex内容
    
                        // 添加加密后的解壳数据
                        System.arraycopy(payloadArray, 0, newdex, unShellDexLen,  payloadLen);
                        //再在dex内容后面拷贝apk的内容
    
                        //添加解壳数据长度
                        System.arraycopy(intToByte(payloadLen), 0, newdex,  totalLen-4, 4);
                        //最后4为长度
    
                        //修改DEX file size文件头
                        fixFileSizeHeader(newdex);
                        //修改DEX SHA1 文件头
                        fixSHA1Header(newdex);
                        //修改DEX CheckSum文件头
                        fixCheckSumHeader(newdex);
                        String str = "force/classes.dex";
                        File file = new File(str);
                        if (!file.exists()) {
                               file.createNewFile();
                        }
                        
                        FileOutputStream localFileOutputStream = new  FileOutputStream(str);
                        localFileOutputStream.write(newdex);
                        localFileOutputStream.flush();
                        localFileOutputStream.close();
                 } catch (Exception e) {
                        e.printStackTrace();
                 }
           }

这里可以总结出一个Java的文件写入流程

    String str = "file_name";
    File file = new File(str);
    if (!file.exists()){
        file.createNewFile();
    }
    
    FileOutputStream localFileOutputStream = new FileOutputStream(str);
    localFileOutputStream.write(content_needed_to_be_added);
    localFileOutputStream.flush();
    localFileOutputStream.close();

抓报错流程

    try {
        ...
    }catch (Exception e) {
        Log.i("Herrrb", Log.getStackTraceString(e));
        e.printStackTrace();
    }

主要实现了：
1. 读apk；
2. 读dex；
3. 转化成byte[]合并；
4. 修正几个头部属性；
5. 写入classes.dex；
6. 完成

## encrypt

    private static byte[] encrpt(byte[] srcdata){
        for(int i = 0;i<srcdata.length;i++){
            srcdata[i] = (byte)(0xFF ^ srcdata[i]);
        }
        return srcdata;
    }

每个字节都与0xFF异或，注意是字节；
## fixCheckSumHeader
修改dex头，CheckSum校验码

    private static void fixCheckSumHeader(byte[] dexBytes) {
            Adler32 adler = new Adler32();
            // 定义在java.util.zip.Adler32
            adler.update(dexBytes, 12, dexBytes.length - 12);//从12到文件末尾计算校验码
            long value = adler.getValue();
            int va = (int) value;
            byte[] newcs = intToByte(va);
            //高位在前，低位在前掉个个
            byte[] recs = new byte[4];
            for (int i = 0; i < 4; i++) {
                recs[i] = newcs[newcs.length - 1 - i];
                System.out.println(Integer.toHexString(newcs[i]));
            }
            System.arraycopy(recs, 0, dexBytes, 8, 4);//效验码赋值（8-11）
            System.out.println(Long.toHexString(value));
            System.out.println();
        }

首先：Adler32是个计算校验码的包，定义对象之后引用update方法来获得checksum
**第一点：为什么是从12到文件末尾计算校验码？**
因为dex文件头，magic魔数，是固定的八个字节，"dex\n035"，然后checksum是四个字节，checksum的计算范围是除去magic和checksum的其余所有部分，所以需要计算从12到文件末尾的校验码

得到之后转long，再转int，然后转换成byte，然后低位高位调个个，然后存入原dex序列的第8-11个字节，这边不需要返回，操作的就是原dex序列

## intToByte
int 转 byte[]

    public static byte[] intToByte(int number) {
        byte[] b = new byte[4];
        for (int i=3; i>=0; i--){
            b[i] = (byte) (number % 256)
            number >>= 8;
        }
        return b;
    }

大意就是转字节，4字节一单位
例如：1096
二进制：100 01001000
byte[]： 0 0 4 72

## fixSHA1Header
修改dex头，sha1值

    private static void fixSHA1Header(byte[] dexBytes) throws NoSuchAlgorithmException {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(dexBytes, 32, dexBytes.length - 32);//从32为到结束计算sha--1
            byte[] newdt = md.digest();
            System.arraycopy(newdt, 0, dexBytes, 12, 20);//修改sha-1值（12-31）
            //输出sha-1值，可有可无
            String hexstr = "";
            for (int i = 0; i < newdt.length; i++) {
                hexstr += Integer.toString((newdt[i] & 0xff) + 0x100, 16).substring(1);
            }
            System.out.println(hexstr);
        }

首先是调用`import java.security.MessageDigest;`，拿到sha1方法的对象
类似python的用法去update来传输数据，digest方法得到加密后的结果，因为计算的输入是出去magic、checksum、signature三部分，故需要从32开始；

保存在newdt中，从arraycopy中也可以看出这个签名是20字节的；到这就结束了，后面的输出就可有可无了；


## fixFileSizeHeader
修改dex头，file_size值

    private static void fixFileSizeHeader(byte[] dexBytes) {
        //新文件长度
        byte[] newfs = intToByte(dexBytes.length);
        System.out.println(Integer.toHexString(dexBytes.length));
        byte[] refs = new byte[4];
    
        for (int i = 0; i < 4; i++) {
            refs[i] = newfs[newfs.length - 1 - i];
            System.out.println(Integer.toHexString(newfs[i]));
        }
        System.arraycopy(refs, 0, dexBytes, 32, 4);//修改（32-35）
    }

得到文件长度的byte[]，低位高位颠倒再存入dexBytes，修改32-35四个字节

## readFileBytes
以二进制读出文件

    private static byte[] readFileBytes(File file) throws IOException {
        byte[] arrayOfByte = new byte[1024];
        ByteArrayOutputStream localByteArrayOutputStream = new  ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(file);
        while (true) {
            int i = fis.read(arrayOfByte);
            if (i != -1) {
                localByteArrayOutputStream.write(arrayOfByte, 0, i);
            } else {
                return localByteArrayOutputStream.toByteArray();
            }
        }
    }

最后返回的是ByteArrayOutputStream类型的值，首先FileInputStream，将file传入，一次read1024字节，然后写入ByteArrayOutputStream，调用toByteArray方法返回；

这样加密工具分析就结束了；

正式使用时可在eclipse中
![p5][4]

总结起来就是，结合源apk（加密后）与脱壳程序dex，dex在前apk在后，并修正dex文件头的三个属性；

# 脱壳APK
个人觉得这种方法脱壳的核心就在于动态加载，
LoadedApk.java，负责加载一个apk程序，类内部有一个mClassLoader变量，负责加载apk，只要获取这个类加载器并替换为解密出的apk的dexclassloader，该dexclassloader一方面加载了源程序、另一方面以原mClassLoader为父节点，保证了即加载了源程序又没有放弃原先加载的资源和系统代码；

然后是找到源程序的Application，通过反射建立并运行，这是会Log出一条消息；
单纯地载入是不会运行的，所以需要找到Application类（也就是apk的全局类），运行其onCreate方法，这样源apk才开始它的生命周期；

接下来看一下具体的步骤：（还是不贴完整代码，详情请见GitHub
自定义类ProxyApplication，继承自Application类

首先是定义全局变量：

    private static final String appkey = "APPLICATION_CLASS_NAME";
    private String apkFileName;
    private String odexPath;
    private String libPath;

## RefInvoke
具体解释见 https://herbwen.com/index.php/archives/57/#%E5%8A%A8%E6%80%81%E5%8A%A0%E8%BD%BDActivity

## attachBaseContext
然后是override一下attachBaseContext方法，因为这个方法在onCreate之前执行，
在这个方法内，需要提取出源apk，解密，

    File odex = this.getDir("payload_odex", MODE_PRIVATE);
    File libs = this.getDir("payload_lib", MODE_PRIVATE);
    odexPath = odex.getAbsolutePath();
    libPath = libs.getAbsolutePath();
    apkFileName = odex.getAbsolutePath() + "/payload.apk";
    File dexFile = new File(apkFileName);
    Log.i("demo", "apk size:"+dexFile.length()); // 第一次的时候输出0
    if (!dexFile.exists())
    {
        dexFile.createNewFile();
        // 从apk中读取dex，具体方法放放入readDexFileFromApk中
        byte[] dexdata = this.readDexFileFromApk();
        // 再从dex中取出源apk，放入dexdata
        this.splitPayLoadFromDex(dexdata);
    }


然后替换LoadedApk中的mClassloader变量，将原mClassLoader设置为父类，DexClassLoader加载了。

    Object currentActivityThread = RefInvoke.invokeStaticMethod(
            "android.app.ActivityThread", "currentActivityThread", new Class[] {}, new Object[] {});
    
    String packageName = this.getPackageName();
    // 得到当前的包名，配合mPackage得到与LoadedApk的映射关系
    
    ArrayMap mPackages = (ArrayMap) RefInvoke.getFieldObject(
            "android.app.ActivityThread", currentActivityThread, "mPackages");
    // 存放的是apk包名和LoadedApk类的映射关系
    
    WeakReference wr = (WeakReference) mPackages.get(packageName);
    // 得到LoadedApk
    
    DexClassLoader dLoader = new DexClassLoader(apkFileName, odexPath, libPath, (ClassLoader) RefInvoke.getFieldObject("android.app.LoadedApk", wr.get(), "mClassLoader"));
    // DexClassLoader，将得到的apk载入，父类设置为脱壳apk包名对应的LoadedApk的mClassLoader变量，这样才符合前面说的，既加载源程序，又保持现有程序
    
    RefInvoke.setFieldObject("android.app.LoadedApk", "mClassLoader", wr.get(), dLoader);
    // 将脱壳apk包名对应的LoadedApk的mClassLoader替换为dLoader，即上面的DexClassLoader

这里就涉及动态加载的东西了，具体解释见上面代码的注释

接着就是调用loadClass看能不能找到MainActivity，也不需要做什么

    try{
        Object actObj = dLoader.loadClass("com.herbwen.unshell.MainActivity");
        Log.i("demo", "actObj:"+actObj);
    }catch(Exception e){
        Log.i("demo", "activity:"+Log.getStackTraceString(e));
    }

## onCreate
首先是获得ApplicationInfo和Bundle，并将类名赋给appClassName变量

    String appClassName = null;
    try {
        ApplicationInfo ai = this.getPackageManager().getApplicationInfo(this.getPackageName(),
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

这个APPLICATION_CLASS_NAME和metaData在manifest.xml中有定义，如下所示
![p4][5]

接下来就是一系列的ActivityThread层面的调用与赋值；

    Object currentActivityThread = RefInvoke.invokeStaticMethod(        "android.app.ActivityThread", "currentActivityThread", new Class[] {}, new Object[] {});Log.i("Herrrb", "currentActivityThread:"+currentActivityThread);Object mBoundApplication = RefInvoke.getFieldObject(        "android.app.ActivityThread", currentActivityThread, "mBoundApplication");Log.i("Herrrb", "mBoundApplication:"+mBoundApplication);Object loadedApkInfo = RefInvoke.getFieldObject("android.app.ActivityThread$AppBindData", mBoundApplication, "info");Log.i("Herrrb", "loadedApkinfo:"+loadedApkInfo);RefInvoke.setFieldObject("android.app.LoadedApk", "mApplication", loadedApkInfo, null);

mApplication设置为null


    Object oldApplication = RefInvoke.getFieldObject(
            "android.app.ActivityThread", currentActivityThread,
            "mInitialApplication");
    
    ArrayList<Application> mAllApplications = (ArrayList<Application>) RefInvoke
            .getFieldObject("android.app.ActivityThread",
                    currentActivityThread, "mAllApplications");
    
    mAllApplications.remove(oldApplication);

remove掉oldApplication


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

拿到LoadedApk中的appinfo与appBindData中的appinfo，并将其类名设置为源程序的类名，再调用LoadedApk中loadedApkInfo对应的makeApplication方法，参数类型Boolean和Instrumentation，传入参数false和null，然后会返回一个Application类型的实例 app

再将ActivityThread中currentActivityThread方法mInitialApplication变量替换为app；
这里先记一下mContentProvider，ActivityThread的mProviderMap会缓存已经获取的ContentProvider接口或定义在自己进程内的ContentProvider接口；然后这里使用迭代生成的方法，遍历所有接口，将所有接口的localProvider属性对应的mContext变量全部设置为app，这样应该就接管了content provider了

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

然后执行

    app.onCreate();

就开始了源apk的生命周期；

## splitPayLoadFromDex
System.arraycopy(Object src, int srcPos, Object dest, int destPos, int length);
	1. 
src：源数组；
	2. 
srcPos：源数组要复制的起始位置
	3. 
dest：目标数组
	4. 
destPos：目标数组放置的起始位置
	5. 
length：复制的长度


拿到长度，并新建apk的byte[]序列（`arraycopy -> ByteArrayInputStream -> DataInputStream -> readInt`

    int ablen = apkdata.length;
    byte[] dexlen = new byte[4];
    System.arraycopy(apkdata, ablen - 4, dexlen, 0, 4);
    ByteArrayInputStream bais = new ByteArrayInputStream(dexlen);
    DataInputStream in = new DataInputStream(bais);
    int readInt = in.readInt();
    System.out.println(Integer.toHexString(readInt));
    byte[] newdex = new byte[readInt];

然后进行解密

    newdex = decrypt(newdex);

写入备份文件，即上面的payload.apk

    File file = new File(apkFileName);
    try {
        FileOutputStream localFileOutputStream = new FileOutputStream(file);
        localFileOutputStream.write(newdex);
        localFileOutputStream.close();
    } catch (IOException localIOException) {
        throw new RuntimeException(localIOException);
    }

然后找入口，加载so文件，

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

## decrypt
因为是异或，所以解密和加密一样

    private byte[] decrypt(byte[] srcdata) {
        for(int i=0;i<srcdata.length;i++){
            srcdata[i] = (byte)(0xFF ^ srcdata[i]);
        }
        return srcdata;
    }

## readDexFileFromApk
返回一个ByteArray的输出流，那么首先进行定义

    ByteArrayOutputStream dexByteArrayOutputStream = new ByteArrayOutputStream();

拿到Zip的输入流

    ZipInputStream localZipInputStream = new ZipInputStream(
            new BufferedInputStream(new FileInputStream(
                    this.getApplicationInfo().sourceDir)));

找到classes.dex，然后读取

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

最后关闭返回

    localZipInputStream.close();
    return dexByteArrayOutputStream.toByteArray();

其实中间编译测试有很多奇奇怪怪的问题，一定要注意AndroidManifest.xml中的设置，res目录的话脱壳程序的res目录不需要保留，直接将源程序的res目录放入即可；

最后一个报错是关于Theme的，将AndroidManifest.xml中application标签内加入

    android:theme="@style/Theme.AppCompat"

即可

# 使用
先将源apk程序与脱壳apk程序打包成apk，将源apk改名为source_app.apk，脱壳apk解压删除META-INF目录，将classes.dex更名为unshell.dex，放入加壳程序的force目录下；

运行程序，得到classes.dex，然后放入脱壳apk解压的目录下替换掉classes.dex，然后打包成zip，更名为apk，再使用signapk进行签名安装即可；


  [1]: https://herbwen.com/usr/uploads/image/dexshell/1.png
  [2]: https://herbwen.com/usr/uploads/image/dexshell/2.png
  [3]: https://herbwen.com/usr/uploads/image/dexshell/3.png
  [4]: https://herbwen.com/usr/uploads/image/dexshell/5.png
  [5]: https://herbwen.com/usr/uploads/image/dexshell/4.png
