package dai.shan.downloaddemo;

import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.ParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dai.shan.downloaddemo.utils.IOUtil;
import de.greenrobot.event.EventBus;

import static java.nio.channels.FileChannel.MapMode.READ_WRITE;

/**
 * Created by wilson on 2017/6/15.
 */

class DownloadTask extends Thread {

    private static final String TAG = "DownloadTask";
    private String downloadUrl;// 下载链接地址
    private int threadNum;// 开启的线程数
    private String filePath;// 保存文件路径地址
    private int blockSize;// 每一个线程的下载量
    public static ExecutorService service = Executors.newCachedThreadPool();
    private static final int EACH_RECORD_SIZE = 16; //long + long = 8 + 8
    private int RECORD_FILE_TOTAL_SIZE;
    public static boolean mIsPause = false;

    //|*********************|
    //|*****Record  File****|
    //|*********************|
    //|  0L      |     7L   | 0
    //|  8L      |     15L  | 1
    //|  16L     |     31L  | 2
    //|  ...     |     ...  | MAX_THREADS-1
    //|*********************|


    public DownloadTask(String downloadUrl, int threadNum, String filePath) {
        RECORD_FILE_TOTAL_SIZE = EACH_RECORD_SIZE * threadNum;
        this.downloadUrl = downloadUrl;
        this.threadNum = threadNum;
        this.filePath = filePath;
    }


    public void prepareDownload(File tempFile, File saveFile,
                                long fileLength) throws IOException, ParseException {

        RandomAccessFile rFile = null;
        RandomAccessFile rRecord = null;
        FileChannel channel = null;
        try {
            rFile = new RandomAccessFile(saveFile, "rws");
            rFile.setLength(fileLength);//设置下载文件的长度

            rRecord = new RandomAccessFile(tempFile, "rws");
            rRecord.setLength(RECORD_FILE_TOTAL_SIZE); //设置指针记录文件的大小
            channel = rRecord.getChannel();
            MappedByteBuffer buffer = channel.map(READ_WRITE, 0, RECORD_FILE_TOTAL_SIZE);
            long start;
            long end;
            int eachSize = (int) (fileLength / threadNum);
            Trace.d(TAG, "prepareDownload,threadNum=" + threadNum + ",eachSize=" + eachSize);
            for (int i = 0; i < threadNum; i++) {
                if (i == threadNum - 1) {
                    start = i * eachSize;
                    end = fileLength - 1;
                } else {
                    start = i * eachSize;
                    end = (i + 1) * eachSize - 1;
                }
                Trace.d(TAG, "prepareDownload,i=" + i + ",start=" + start + ",end=" + end);
                buffer.putLong(start);
                buffer.putLong(end);
            }
        } finally {
            IOUtil.closeQuietly(channel);
            IOUtil.closeQuietly(rRecord);
            IOUtil.closeQuietly(rFile);
        }
    }

    public boolean downloadNotComplete(File tempFile) throws IOException {
        RandomAccessFile record = null;
        FileChannel channel = null;
        try {
            record = new RandomAccessFile(tempFile, "rws");
            channel = record.getChannel();
            MappedByteBuffer buffer = channel.map(READ_WRITE, 0, RECORD_FILE_TOTAL_SIZE);
            long startByte;
            long endByte;
            for (int i = 0; i < threadNum; i++) {
                startByte = buffer.getLong();
                endByte = buffer.getLong();
                if (startByte <= endByte) {
                    return true;
                }
            }
            return false;
        } finally {
            IOUtil.closeQuietly(channel);
            IOUtil.closeQuietly(record);
        }
    }


    @Override
    public void run() {

        FileDownloadThread[] threads = new FileDownloadThread[threadNum];
        try {
            URL url = new URL(downloadUrl);
            Trace.d(TAG, "download file http path:" + downloadUrl);
            URLConnection conn = url.openConnection();
            // 读取下载文件总大小
            int fileSize = conn.getContentLength();
            if (fileSize <= 0) {
                Trace.d(TAG, "读取文件失败");
                return;
            }

            // 计算每条线程下载的数据长度
            blockSize = (fileSize % threadNum) == 0 ? fileSize / threadNum
                    : fileSize / threadNum + 1;

            Trace.d(TAG, "fileSize:" + fileSize + "  blockSize:" + blockSize);

            File file = new File(filePath);
            File dir = new File(Environment.getExternalStorageDirectory() + "/Mydownload/.cache");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File tempFilefile = new File(dir, "down.tmp");

            if (!tempFilefile.exists()) {
                prepareDownload(tempFilefile, file, fileSize);
            } else if (!downloadNotComplete(tempFilefile)) {
                EventBus.getDefault().post(new EventMessage(Event.DOWNLOAD, Event.DOWNLOAD_SUCCESS, 0, 0, null));
                return;
            }

            for (int i = 0; i < threads.length; i++) {
                // 启动线程，分别下载每个线程需要下载的部分
                threads[i] = new FileDownloadThread(url, tempFilefile, file, fileSize, blockSize,
                        i);
                threads[i].setName("Thread:" + i);
                service.execute(threads[i]);
            }

        } catch (MalformedURLException e) {
            Trace.d(TAG, "MalformedURLException e=" + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            Trace.d(TAG, "IOException e=" + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Trace.d(TAG, "Exception e=" + e.getMessage());
            e.printStackTrace();
        }

    }

    class FileDownloadThread extends Thread {

        /**
         * 当前下载是否完成
         */
        private boolean isCompleted = false;
        /**
         * 当前下载文件长度
         */
        private int downloadLength = 0;
        /**
         * 文件保存路径
         */
        private File file;
        private File tempFile;
        /**
         * 文件下载路径
         */
        private URL downloadUrl;
        /**
         * 当前下载线程ID
         */
        private int threadId;
        /**
         * 线程下载数据长度
         */
        private long blockSize;
        private long startPos;
        private long endPos;

        private long fileSize;
        private static final String TMP_SUFFIX = ".tmp";  //temp file
        private static final String LMF_SUFFIX = ".lmf";  //last modify file
        private static final String CACHE = ".cache";    //cache directory


        /**
         * @param downloadUrl:文件下载地址
         * @param file:文件保存路径
         * @param blocksize:下载数据长度
         * @param threadId:线程ID
         */
        public FileDownloadThread(URL downloadUrl, File tempFile, File file, long fileSize, long blocksize,
                                  int threadId) {
            this.downloadUrl = downloadUrl;
            this.tempFile = tempFile;
            this.file = file;
            this.fileSize = fileSize;
            this.threadId = threadId;
            this.blockSize = blocksize;
        }


        @Override
        public void run() {
            RandomAccessFile record = null;
            FileChannel recordChannel = null;
            BufferedInputStream bis = null;
            RandomAccessFile raf = null;
            FileChannel saveChannel = null;
            try {

                record = new RandomAccessFile(tempFile, "rws");
                recordChannel = record.getChannel();
                MappedByteBuffer recordBuffer = recordChannel.map(READ_WRITE, 0, RECORD_FILE_TOTAL_SIZE);

                URLConnection conn = downloadUrl.openConnection();
                conn.setConnectTimeout(15 * 1000);
                conn.setReadTimeout(25 * 1000);
                conn.setAllowUserInteraction(true);

                long startPos = recordBuffer.getLong(threadId * EACH_RECORD_SIZE);//开始位置
                long endPos = recordBuffer.getLong(threadId * EACH_RECORD_SIZE + 8);//结束位置

                //设置当前线程下载的起点、终点
                conn.setRequestProperty("Range", "bytes=" + startPos + "-" + endPos);

                byte[] buffer = new byte[8 * 1024];
                bis = new BufferedInputStream(conn.getInputStream());

                raf = new RandomAccessFile(file, "rwd");
                saveChannel = raf.getChannel();
                MappedByteBuffer saveBuffer = saveChannel.map(READ_WRITE, startPos, endPos - startPos + 1);
                int len;
                long downloadedAllSize = 0;
                mIsPause = false;
                while ((len = bis.read(buffer)) != -1) {

                    if (mIsPause) {
                        break;
                    }
                    saveBuffer.put(buffer, 0, len);
                    downloadLength += len;
                    recordBuffer.putLong(threadId * EACH_RECORD_SIZE, recordBuffer.getLong(threadId * EACH_RECORD_SIZE) + len);
                    downloadedAllSize = fileSize - getResidue(recordBuffer);
                    EventBus.getDefault().post(new EventMessage(Event.DOWNLOAD, Event.DOWNLOAD_PROGRESS, fileSize, downloadedAllSize, threadId));
                }
                if (downloadedAllSize == fileSize) {
                    isCompleted = true;
                    EventBus.getDefault().post(new EventMessage(Event.DOWNLOAD, Event.DOWNLOAD_SUCCESS, 0, 0, null));
                    return;
                }
                Trace.d(TAG, "current thread task has finished,all size:"
                        + downloadLength);

            } catch (Exception e) {
                Trace.d(TAG, "Exception e="
                        + e.getMessage());
                e.printStackTrace();
            } finally {
                IOUtil.closeQuietly(bis);
                IOUtil.closeQuietly(saveChannel);
                IOUtil.closeQuietly(recordChannel);
                IOUtil.closeQuietly(record);
                IOUtil.closeQuietly(raf);
            }
        }

        /**
         * 线程文件是否下载完毕
         */
        public boolean isCompleted() {
            return isCompleted;
        }

        /**
         * 线程下载文件长度
         */
        public int getDownloadLength() {
            return downloadLength;
        }

        private long getResidue(MappedByteBuffer recordBuffer) {
            long residue = 0;
            for (int j = 0; j < threadNum; j++) {
                long startTemp = recordBuffer.getLong(j * EACH_RECORD_SIZE);
                long endTemp = recordBuffer.getLong(j * EACH_RECORD_SIZE + 8);
                Trace.d(TAG, Thread.currentThread().getName() + " getResidue,startTemp=" + startTemp + ",endTemp=" + endTemp);
                long temp = endTemp - startTemp + 1;
                residue += temp;
            }
            return residue;
        }

    }

}
