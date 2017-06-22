package dai.shan.downloaddemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

import dai.shan.downloaddemo.utils.IOUtil;
import de.greenrobot.event.EventBus;

import static dai.shan.downloaddemo.Trace.threadNum;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "TopActivity";
    private String url = "http://apk.r1.market.hiapk.com/data/upload/apkres/2017/5_16/11/com.ea.simcitymobile.baidu_110007.apk";

    String path = Environment.getExternalStorageDirectory()+ "/Mydownload/";
    String fileName = "baidu_16785426.apk";

    private ProgressBar mProgressbar;
    private TextView info;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mProgressbar = (ProgressBar) findViewById(R.id.progressBar);
        info = (TextView) findViewById(R.id.info);
        permission();
        EventBus.getDefault().register(this);
    }

    public void download(View v) {

        File file = new File(path);
        // 如果SD卡目录不存在创建
        if (!file.exists()) {
            file.mkdirs();
        }
        // 设置progressBar初始化
        mProgressbar.setMax(100);

        String filepath = path + fileName;
        Trace.d(TAG, "download file  path:" + filepath);
        DownloadTask task = new DownloadTask(url, threadNum, filepath);
        DownloadTask.service.execute(task);
    }

    public void pause(View v) {
        if (!DownloadTask.mIsPause) {
            DownloadTask.mIsPause = true;
        }
        info.setText("DOWNLOAD Paused");
    }

    public void delete(View v) {
        File file = new File(path);
        Trace.d(TAG,"delete file "+ file.exists());
        if (file.exists() && IOUtil.deleteErrFileExt(file)) {
            info.setText("DELETE SUCCESS");
        }else {
            info.setText("DELETE FAIL");
        }
    }

    public void onEventMainThread(EventMessage event) {

        Trace.d(TAG, " event " +
                "  what  = " + event.getWhat() +
                "  param1= " + event.getArg1() +
                "  param2= " + event.getArg2() +
                "  param3= " + event.getArg3() +
                "");

        switch (event.getArg1()) {

            case Event.DOWNLOAD_START:
                info.setText("start download");
                break;
            case Event.DOWNLOAD_SUCCESS:
                info.setText("DOWNLOAD SUCCESS");
                break;
            case Event.DOWNLOAD_PROGRESS:
                notify_downloading(event.getArg2(), event.getArg3());
                break;
            case Event.DOWNLOAD_FAIL:
                info.setText("DOWNLOAD FAIL");
                break;
            default:
                break;
        }

    }

    private void notify_downloading(long total_size, long download_size) {

        if (DownloadTask.mIsPause) {
            info.setText("DOWNLOAD Paused");
        } else {
            int percent = (int) ((total_size <= 0) ? 0 : ((100 * download_size) / total_size));
            info.setText("downloading:" + percent+"%");
            mProgressbar.setProgress(percent);
        }
    }

    private void permission() {

        if (Build.VERSION.SDK_INT == 23) {
            if (!(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {

                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        } else if (Build.VERSION.SDK_INT >= 24) {
            if (!(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {

                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            }
            if (!(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {

                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
