package com.apposcopy.ella.runtime;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Ella {
    //instrumentation will set values of the following four fields
    private static String id;
    private static String recorderClassName;
    private static String uploadUrl;
    private static boolean useAndroidDebug;

    private static final int UPLOAD_TIME_PERIOD = 1000;
    private static UploadThread uploadThread = new UploadThread();
    private static Set<Integer> coverage = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    public static void m(int mId) {
        coverage.add(mId);
    }

    static {
        uploadThread.start();
    }

    private static class UploadThread extends Thread {
        private boolean stop = false;
        private boolean first = true;
        private OutputStream socketOut;

        public void run() {
            String[] tokens = uploadUrl.split(":");
            String serverAddress = tokens[0];
            int port = Integer.parseInt(tokens[1]);
            try {
                Socket socket = new Socket(serverAddress, port);
                socket.setKeepAlive(true);
                socketOut = socket.getOutputStream();
            } catch (IOException e) {
                throw new Error(e);
            }

            while (!stop) {
                try {
                    sleep(UPLOAD_TIME_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }

                StringBuilder builder = new StringBuilder();
                for (int mId : coverage) {
                    builder.append(mId).append('\n');
                }
                coverage.clear();

                String payload = builder.toString();
                if (payload.length() == 0 && !stop) {
                    // Log.d("ella", "no data to upload");
                    continue;
                }
                JSONObject json = new JSONObject();
                try {
                    json.put("id", id);
                    json.put("cov", payload);
                    json.put("stop", String.valueOf(stop));
                    if (first) {
                        json.put("recorder", recorderClassName);
                        first = false;
                    }
                } catch (JSONException e) {
                    throw new Error(e);
                }
                String jsonString = json.toString();
                byte[] content = jsonString.getBytes(java.nio.charset.Charset.forName("UTF-8"));
                // Log.d("ella", "Uploading coverage. id: "+id+ " data size: "+content.length);

                try {
                    socketOut.write(content);
                    socketOut.write('\r');
                    socketOut.write('\n');
                    socketOut.write('\r');
                    socketOut.write('\n');
                    socketOut.flush();
                } catch (IOException e) {
                    throw new Error(e);
                }
            }

            try {
                socketOut.close();
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }
}
