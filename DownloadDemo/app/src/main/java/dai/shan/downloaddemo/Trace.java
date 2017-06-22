package dai.shan.downloaddemo;

import android.util.Log;


public class Trace {

    public static final String TAG = "MultiDownload";
	public static final int threadNum=5;

    public static boolean saveLog = false;
    private static String saveLogPath = "";
	private static boolean isDEBUG = true;
	public static void d (String tag, String msg){

		if (isDEBUG || saveLog){
			Log.d(TAG,  tag + "->" + msg);

		}

	}

} 
