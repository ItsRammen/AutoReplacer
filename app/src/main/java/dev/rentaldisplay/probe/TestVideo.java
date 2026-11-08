package dev.rentaldisplay.probe;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Encodes only our generated test card. Never reads the phone screen. */
final class TestVideo implements Runnable {
    interface Output { void frame(byte[] bytes) throws IOException; void note(String text); void failed(Exception error); }
    private final int width, height, encoding;
    private final Output output;
    private final GridState grid;
    private final GridRenderer renderer = new GridRenderer();
    private volatile boolean stopped;
    private final int fps = 5;
    TestVideo(int width, int height, int encoding, GridState grid, Output output) {
        this.width = width; this.height = height; this.encoding = encoding; this.output = output;
        this.grid = grid;
    }
    void stop() { stopped = true; }

    @Override public void run() {
        Bitmap bitmap = null;
        MediaCodec codec = null;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[width * height];
            if (encoding != 0) {
                MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                format.setInteger(MediaFormat.KEY_BIT_RATE, Math.min(4000000, Math.max(500000, width * height * 3)));
                format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
                codec = MediaCodec.createEncoderByType("video/avc");
                output.note("Encoder: " + codec.getName() + "; shared touch grid, 5 fps");
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                codec.start();
            } else output.note("Raw RGB test; header uses source pixel stride=1, row stride=width (unverified on hardware)");
            long start = System.nanoTime(), frame = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!stopped) {
                long began = System.nanoTime();
                draw(bitmap, frame);
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                if (codec == null) {
                    ByteBuffer raw = ByteBuffer.allocate(width * height * 3 + 8);
                    raw.putInt(1).putInt(width);
                    for (int pixel : pixels) raw.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel);
                    output.frame(raw.array());
                } else {
                    int index = codec.dequeueInputBuffer(100000);
                    if (index >= 0) {
                        Image image = codec.getInputImage(index);
                        if (image == null) throw new IOException("Encoder has no writable YUV image input");
                        try { fillYuv(image, pixels); } finally { image.close(); }
                        codec.queueInputBuffer(index, 0, width * height * 3 / 2, (System.nanoTime() - start) / 1000, 0);
                    }
                    for (;;) {
                        int indexOut = codec.dequeueOutputBuffer(info, 10000);
                        if (indexOut == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            output.note("Encoder output ready: " + codec.getOutputFormat().getString(MediaFormat.KEY_MIME));
                            continue;
                        }
                        if (indexOut < 0) break;
                        try {
                            if (info.size > 0) {
                                ByteBuffer encoded = codec.getOutputBuffer(indexOut);
                                if (encoded == null) throw new IOException("Missing encoder output buffer");
                                encoded.position(info.offset).limit(info.offset + info.size);
                                byte[] data = new byte[info.size]; encoded.get(data);
                                if (frame < 2 || (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)
                                    output.note("CODEC_BUFFER bytes=" + info.size + " flags=" + info.flags
                                            + " codecConfig=" + ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0));
                                // Include codec-configuration buffers (SPS/PPS), as the observed client does.
                                output.frame(data);
                            }
                        } finally { codec.releaseOutputBuffer(indexOut, false); }
                    }
                }
                frame++;
                long millis = (200000000L - (System.nanoTime() - began)) / 1000000L;
                if (millis > 0) Thread.sleep(millis);
            }
        } catch (Exception e) { if (!stopped) output.failed(e); }
        finally {
            if (codec != null) { try { codec.stop(); } catch (Exception ignored) {} codec.release(); }
            if (bitmap != null) bitmap.recycle();
        }
    }

    private void draw(Bitmap bitmap, long frame) {
        renderer.draw(new Canvas(bitmap),width,height,grid.snapshot(),frame);
    }

    private void fillYuv(Image image, int[] pixels) throws IOException {
        if (image.getWidth() != width || image.getHeight() != height)
            throw new IOException("Unexpected encoder image dimensions");
        Image.Plane[] planes = image.getPlanes();
        if (planes.length != 3) throw new IOException("Expected three YUV planes");
        ByteBuffer y = planes[0].getBuffer(), u = planes[1].getBuffer(), v = planes[2].getBuffer();
        int yo = y.position(), uo = u.position(), vo = v.position();
        for (int row = 0; row < height; row++) for (int col = 0; col < width; col++) {
            int pixel = pixels[row * width + col], r = (pixel >> 16) & 255, g = (pixel >> 8) & 255, b = pixel & 255;
            y.put(yo + row * planes[0].getRowStride() + col * planes[0].getPixelStride(),
                    (byte) clamp(((66 * r + 129 * g + 25 * b + 128) >> 8) + 16));
            if ((row & 1) == 0 && (col & 1) == 0) {
                int idxU = uo + row / 2 * planes[1].getRowStride() + col / 2 * planes[1].getPixelStride();
                int idxV = vo + row / 2 * planes[2].getRowStride() + col / 2 * planes[2].getPixelStride();
                u.put(idxU, (byte) clamp(((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128));
                v.put(idxV, (byte) clamp(((112 * r - 94 * g - 18 * b + 128) >> 8) + 128));
            }
        }
    }
    private static int clamp(int n) { return Math.max(0, Math.min(255, n)); }
}
