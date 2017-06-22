package dai.shan.downloaddemo;

import android.app.Application;

import dai.shan.downloaddemo.utils.CrashHandler;

/**
 * Created by wilson on 2017/6/15.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.getInstance().init(this);
    }
}
