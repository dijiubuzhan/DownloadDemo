package dai.shan.downloaddemo.utils;

import android.database.Cursor;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import dai.shan.downloaddemo.Trace;

public class IOUtil {

    private static final String TAG ="IOUtil" ;

    private IOUtil() {
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void closeQuietly(Cursor cursor) {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static byte[] readBytes(InputStream in) throws IOException {
        if (!(in instanceof BufferedInputStream)) {
            in = new BufferedInputStream(in);
        }
        ByteArrayOutputStream out = null;
        try {
            out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        } finally {
            closeQuietly(out);
        }
        return out.toByteArray();
    }

    public static byte[] readBytes(InputStream in, long skip, long size) throws IOException {
        ByteArrayOutputStream out = null;
        try {
            if (skip > 0) {
                long skipSize = 0;
                while (skip > 0 && (skipSize = in.skip(skip)) > 0) {
                    skip -= skipSize;
                }
            }
            out = new ByteArrayOutputStream();
            for (int i = 0; i < size; i++) {
                out.write(in.read());
            }
        } finally {
            closeQuietly(out);
        }
        return out.toByteArray();
    }

    public static String readStr(InputStream in) throws IOException {
        return readStr(in, "UTF-8");
    }

    public static String readStr(InputStream in, String charset) throws IOException {
        if (TextUtils.isEmpty(charset)) charset = "UTF-8";

        if (!(in instanceof BufferedInputStream)) {
            in = new BufferedInputStream(in);
        }
        Reader reader = new InputStreamReader(in, charset);
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int len;
        while ((len = reader.read(buf)) >= 0) {
            sb.append(buf, 0, len);
        }
        return sb.toString().trim();
    }

    public static void writeStr(OutputStream out, String str) throws IOException {
        writeStr(out, str, "UTF-8");
    }

    public static void writeStr(OutputStream out, String str, String charset) throws IOException {
        if (TextUtils.isEmpty(charset)) charset = "UTF-8";

        Writer writer = new OutputStreamWriter(out, charset);
        writer.write(str);
        writer.flush();
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        if (!(in instanceof BufferedInputStream)) {
            in = new BufferedInputStream(in);
        }
        if (!(out instanceof BufferedOutputStream)) {
            out = new BufferedOutputStream(out);
        }
        int len = 0;
        byte[] buffer = new byte[1024];
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.flush();
    }

    public static boolean deleteFileOrDir(File path) {
        if (path == null || !path.exists()) {
            return true;
        }
        if (path.isFile()) {
            return path.delete();
        }
        File[] files = path.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteFileOrDir(file);
            }
        }
        return path.delete();
    }

    public static boolean deleteErrFileExt(File destfile) {
        Trace.d(TAG, "deleteErrFileExt, destfile = " + destfile);
        if (destfile.isDirectory()) {
            String[] children = destfile.list();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    boolean success = deleteErrFileExt(new File(destfile, children[i]));
                    if (!success) {
                        Trace.d(TAG, "deleteErrFileExt err");
                        return false;
                    }
                }
            }
        }

        return destfile.delete();
    }
}
