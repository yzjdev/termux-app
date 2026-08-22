package com.termux.app.file;

import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

/**
 * Detects whether a file is a text file by probing its real content type, instead of relying
 * only on the file name extension.
 *
 * <p>The detection works in several steps:
 * <ol>
 *   <li>Sniff magic bytes of the content to get the real MIME type. If the content has a
 *       signature of a known binary format (image/audio/video/archive/...), it is binary.</li>
 *   <li>Otherwise analyse a sample of the content: NUL bytes and the control character
 *       ratio decide whether it looks like text.</li>
 * </ol>
 * The extension is only used as a fallback for {@link #detectMimeType(File)}, never as the
 * primary decision for {@link #isTextFile(File)}.</p>
 */
public final class TextFileDetector {

    /** Number of bytes read from the file head for content analysis. */
    private static final int SAMPLE_SIZE = 8192;

    /** Maximum allowed fraction of control characters in a text sample. */
    private static final float MAX_CONTROL_CHAR_RATIO = 0.10f;

    private TextFileDetector() {
    }

    /**
     * Check whether {@code file} is very likely a text file, based on its real content.
     *
     * @return {@code true} if the file can be read and its content looks like text, otherwise
     * {@code false} (including unreadable files and empty detection results).
     */
    public static boolean isTextFile(File file) {
        if (file == null || !file.isFile()) return false;

        byte[] sample;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            sample = readSample(in);
        } catch (IOException e) {
            return false;
        }

        if (sample.length == 0) return true; // Empty file is editable text.

        // Detect MIME type from the real content (magic bytes).
        String mime = probeMimeType(sample);
        if (mime != null) {
            return isProbableTextMime(mime);
        }

        // No signature matched; analyse the content itself.
        return looksLikeText(sample);
    }

    /**
     * Get the MIME type of {@code file} detected from its real content (magic bytes).
     * Falls back to the extension based guess when the content has no known signature.
     *
     * @return The detected MIME type, or {@code null} if it cannot be determined.
     */
    public static String detectMimeType(File file) {
        if (file == null || !file.isFile()) return null;

        byte[] sample;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            sample = readSample(in);
        } catch (IOException e) {
            return null;
        }

        String mime = probeMimeType(sample);
        if (mime != null) return mime;

        // Fallback: guess from the extension if no content signature was found.
        String ext = getExtension(file.getName());
        if (ext != null) {
            String guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (guessed != null) return guessed;
        }
        return null;
    }

    /**
     * Read up to {@link #SAMPLE_SIZE} bytes from {@code in}, without consuming more than needed.
     */
    private static byte[] readSample(InputStream in) throws IOException {
        byte[] buffer = new byte[SAMPLE_SIZE];
        int total = 0;
        while (total < buffer.length) {
            int count = in.read(buffer, total, buffer.length - total);
            if (count == -1) break;
            total += count;
        }
        if (total == buffer.length) return buffer;
        byte[] result = new byte[total];
        System.arraycopy(buffer, 0, result, 0, total);
        return result;
    }

    /**
     * Probe the MIME type of the content via magic bytes.
     *
     * @return The probed MIME type, or {@code null} if the content has no known signature.
     */
    private static String probeMimeType(byte[] sample) {
        try (InputStream in = new BufferedInputStream(new ByteArrayInputStream(sample))) {
            return URLConnection.guessContentTypeFromStream(in);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A MIME type is text if it is a {@code text/*} type or a known text based application type.
     * Any other known type (image/audio/video/archive/...) is treated as binary.
     */
    private static boolean isProbableTextMime(String mime) {
        if (mime == null) return false;
        if (mime.startsWith("text/")) return true;
        switch (mime) {
            case "application/xml":
            case "application/json":
            case "application/javascript":
            case "application/x-javascript":
            case "application/rtf":
            case "application/x-sh":
            case "application/x-shellscript":
            case "application/x-python":
            case "application/x-perl":
            case "application/x-php":
            case "application/x-yaml":
                return true;
            default:
                return false;
        }
    }

    /**
     * Analyse the content sample: NUL bytes and the control character ratio decide whether the
     * content looks like text. ESC (ANSI coloured logs), TAB, LF, CR and FF are allowed control
     * characters in text files. A strict UTF-8 validity check is intentionally not used here,
     * since legacy encodings (GBK, Latin-1, Shift-JIS, ...) are still valid text.
     */
    private static boolean looksLikeText(byte[] sample) {
        int controlCount = 0;
        for (byte value : sample) {
            int c = value & 0xFF;
            if (c == 0x00) return false; // NUL byte - strong binary indicator.
            if (c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0D && c != 0x0C && c != 0x1B) controlCount++;
            if (c == 0x7F) controlCount++;
        }
        return controlCount <= sample.length * MAX_CONTROL_CHAR_RATIO;
    }

    private static String getExtension(String name) {
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) return null;
        return name.substring(dotIndex + 1).toLowerCase();
    }
}
