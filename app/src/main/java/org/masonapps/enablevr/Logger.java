package org.masonapps.enablevr;

import android.util.Log;

/**
 * Created by Bob on 8/29/2017.
 */

public class Logger {

    private static boolean debugEnabled = true;

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void setDebugEnabled(boolean debug) {
        Logger.debugEnabled = debug;
    }

    public static void i(String message) {
        final StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
        String cls = stackTraceElement.getClassName();
        String method = stackTraceElement.getMethodName();
        String fileName = stackTraceElement.getFileName();
        int lineNumber = stackTraceElement.getLineNumber();
        Log.i(String.format("%s.%s(%s:%d)", cls, method, fileName, lineNumber), message);
    }

    /**
     * only logs message when debugEnabled is true
     *
     * @param message
     */
    public static void d(String message) {
        if (!debugEnabled) return;
        final StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[3];
        Log.d(String.format("(%s:%d)", stackTraceElement.getFileName(), stackTraceElement.getLineNumber()), message);
    }

    public static void e(String message) {
        final StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[3];
        Log.e(String.format("(%s:%d)", stackTraceElement.getFileName(), stackTraceElement.getLineNumber()), message);
    }

    public static void e(String msg, Throwable throwable) {
        final StackTraceElement[] stackTrace = throwable.getStackTrace();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(msg);
        stringBuilder.append("\n");
        stringBuilder.append(throwable.getLocalizedMessage());
        for (int i = 0; i < stackTrace.length; i++) {
            stringBuilder.append("\nat ");
            stringBuilder.append(stackTrace[i]);
        }
        Log.e("ERROR", stringBuilder.toString());
    }
}
