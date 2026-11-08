package com.andrerinas.openheadunit.autoshow;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * H.264 encoder fed by a Surface. Open Headunit's decoder renders Android Auto into this Surface,
 * so the car gets a steady stream even when Android Auto sends nothing for a static screen:
 * the encoder repeats the previous frame after a short idle gap.
 */
public final class CarEncoder {
    public interface Sink {
        void frame(byte[] data, boolean codecConfig) throws IOException;
        void log(String text);
        void failed(Exception error);
    }

    private final int width, height;
    private final Sink sink;
    private volatile boolean stopped;
    private MediaCodec codec;
    private Surface surface;
    private Thread drain;

    public CarEncoder(int width, int height, Sink sink) { this.width = width; this.height = height; this.sink = sink; }

    /** Creates the encoder and returns the Surface Android Auto video should be rendered into. */
    public synchronized Surface start() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, Math.min(4000000, Math.max(500000, width * height * 3)));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 250000L);
        codec = MediaCodec.createEncoderByType("video/avc");
        sink.log("CAR_ENCODER: " + codec.getName() + " " + width + "x" + height + " baseline, repeat idle frames after 250 ms");
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = codec.createInputSurface();
        codec.start();
        drain = new Thread(this::drainLoop, "car-encoder-drain");
        drain.start();
        return surface;
    }

    private void drainLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long frames = 0;
        try {
            while (!stopped) {
                int index = codec.dequeueOutputBuffer(info, 100000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    sink.log("CAR_ENCODER_OUTPUT: " + codec.getOutputFormat().getString(MediaFormat.KEY_MIME));
                    continue;
                }
                if (index < 0) continue;
                try {
                    if (info.size > 0) {
                        ByteBuffer encoded = codec.getOutputBuffer(index);
                        if (encoded == null) throw new IOException("Missing encoder output buffer");
                        encoded.position(info.offset).limit(info.offset + info.size);
                        byte[] data = new byte[info.size];
                        encoded.get(data);
                        boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                        if (frames < 2 || config) sink.log("CAR_ENCODER_BUFFER bytes=" + info.size + " config=" + config);
                        frames++;
                        sink.frame(data, config);
                    }
                } finally { codec.releaseOutputBuffer(index, false); }
            }
        } catch (Exception e) { if (!stopped) sink.failed(e); }
    }

    public synchronized void stop() {
        stopped = true;
        if (drain != null) { drain.interrupt(); try { drain.join(500); } catch (InterruptedException ignored) {} }
        if (codec != null) { try { codec.stop(); } catch (Exception ignored) {} codec.release(); codec = null; }
        if (surface != null) { surface.release(); surface = null; }
    }
}
