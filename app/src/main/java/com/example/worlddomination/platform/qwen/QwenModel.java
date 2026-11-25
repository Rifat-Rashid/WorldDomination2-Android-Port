package com.example.worlddomination.platform.qwen;

import android.content.Context;
import android.os.StatFs;

import com.example.worlddomination.BuildConfig;
import com.example.worlddomination.game.ai.LocalOpponentRuntime.Stage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import javax.net.ssl.HttpsURLConnection;

final class QwenModel {
    static final String REPOSITORY = "Qwen/Qwen3-0.6B-GGUF";
    static final String REVISION = "23749fefcc72300e3a2ad315e1317431b06b590a";
    private static final boolean DEBUG_MODEL = BuildConfig.DEBUG && !BuildConfig.QWEN_DEBUG_MODEL_SHA256.isEmpty();
    static final String SHA256 = DEBUG_MODEL ? BuildConfig.QWEN_DEBUG_MODEL_SHA256
            : "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031";
    static final String NAME = DEBUG_MODEL ? "qwen-debug-" + SHA256 + ".gguf" : "Qwen3-0.6B-Q8_0.gguf";
    static final long SIZE = DEBUG_MODEL ? BuildConfig.QWEN_DEBUG_MODEL_SIZE : 639_446_688L;
    private static final byte[] GGUF_V3 = {'G', 'G', 'U', 'F', 3, 0, 0, 0};
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final long STORAGE_MARGIN_BYTES = 16L * 1024L * 1024L;

    interface Status {
        void report(Stage stage, float fraction);
    }

    private QwenModel() {}

    static File acquire(Context context, BooleanSupplier canceled, Status status) throws IOException {
        File privateDirectory = context.getNoBackupFilesDir();
        if (privateDirectory == null) {
            throw new IOException("App-private storage is unavailable");
        }
        File directory = new File(privateDirectory, "qwen3");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Model directory is unavailable");
        }
        File model = new File(directory, NAME);
        checkCanceled(canceled);
        if (model.isFile()) {
            status.report(Stage.VERIFYING, -1);
            if (verify(model, canceled)) {
                return model;
            }
            if (!model.delete()) {
                throw new IOException("Unable to remove the damaged model");
            }
            throw new IOException(DEBUG_MODEL ? "Cached model failed verification. Provision the pinned debug model again."
                    : "Cached model failed verification. Retry to download it again.");
        }
        if (DEBUG_MODEL) {
            throw new IOException("Provision the pinned debug model with adb");
        }

        File partial = new File(directory, NAME + ".partial");
        if (partial.exists() && !partial.delete()) {
            throw new IOException("Unable to remove the unfinished model download");
        }
        if (new StatFs(directory.getPath()).getAvailableBytes() < SIZE + STORAGE_MARGIN_BYTES) {
            throw new IOException("The model needs 656 MB of free storage");
        }

        try {
            download(partial, canceled, status);
            status.report(Stage.VERIFYING, -1);
            if (!verify(partial, canceled)) {
                throw new IOException("Downloaded model failed verification. Retry to download it again.");
            }
            checkCanceled(canceled);
            if (!partial.renameTo(model)) {
                throw new IOException("Unable to store the verified model");
            }
            return model;
        } finally {
            // Only the exact-sized, validated final file may reach mmap.
            if (partial.exists()) {
                partial.delete();
            }
        }
    }

    private static void download(File partial, BooleanSupplier canceled, Status status) throws IOException {
        URL url = new URL("https://huggingface.co/" + REPOSITORY + "/resolve/" + REVISION + "/" + NAME
                + "?download=true");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(5_000);
        connection.setRequestProperty("Accept-Encoding", "identity");
        status.report(Stage.DOWNLOADING, 0);
        try {
            checkCanceled(canceled);
            if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                throw new IOException("Model download is unavailable. Retry with an internet connection.");
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength >= 0 && contentLength != SIZE) {
                throw new IOException("Model download has an unexpected size");
            }
            try (InputStream body = new BufferedInputStream(connection.getInputStream(), BUFFER_SIZE);
                 FileOutputStream file = new FileOutputStream(partial);
                 BufferedOutputStream output = new BufferedOutputStream(file, BUFFER_SIZE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long received = 0;
                int lastPercent = 0;
                while (received < SIZE) {
                    checkCanceled(canceled);
                    int count = body.read(buffer, 0, (int) Math.min(buffer.length, SIZE - received));
                    if (count < 0) {
                        throw new IOException("Model download was incomplete");
                    }
                    output.write(buffer, 0, count);
                    received += count;
                    int percent = (int) (received * 100 / SIZE);
                    if (percent != lastPercent) {
                        status.report(Stage.DOWNLOADING, (float) ((double) received / SIZE));
                        lastPercent = percent;
                    }
                }
                checkCanceled(canceled);
                if (body.read() != -1) {
                    throw new IOException("Model download exceeded the expected size");
                }
                output.flush();
                file.getFD().sync();
            }
        } catch (UnknownHostException failure) {
            throw new IOException("The model download needs an internet connection", failure);
        } catch (SocketTimeoutException failure) {
            throw new IOException("Model transfer timed out. Retry with an internet connection.", failure);
        } finally {
            connection.disconnect();
        }
    }

    private static boolean verify(File model, BooleanSupplier canceled) throws IOException {
        if (model.length() != SIZE) {
            return false;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(model), BUFFER_SIZE)) {
            for (byte value : GGUF_V3) {
                if (input.read() != (value & 0xff)) {
                    return false;
                }
            }
            digest.update(GGUF_V3);
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkCanceled(canceled);
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return SHA256.contentEquals(hex);
    }

    private static void checkCanceled(BooleanSupplier canceled) throws IOException {
        if (canceled.getAsBoolean()) {
            throw new IOException("Model acquisition canceled");
        }
    }
}
