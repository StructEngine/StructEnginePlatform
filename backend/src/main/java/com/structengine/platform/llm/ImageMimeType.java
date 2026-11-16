package com.structengine.platform.llm;

/**
 * 根据文件头识别图片 MIME 类型，供多模态请求的 data URI 使用。
 */
public final class ImageMimeType {

    private ImageMimeType() {
    }

    /**
     * @return 识别到的 MIME 类型；无法识别时返回 {@code null}
     */
    public static String detect(byte[] data) {
        if (data == null || data.length < 4) {
            return null;
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
                && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
            return "image/gif";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        if (data[0] == 'B' && data[1] == 'M') {
            return "image/bmp";
        }
        return null;
    }
}
