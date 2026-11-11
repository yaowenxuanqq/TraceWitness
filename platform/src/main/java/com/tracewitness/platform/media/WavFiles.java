package com.tracewitness.platform.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WavFiles {
    private WavFiles() {
    }

    public static byte[] encode(int sampleRate, short[] pcm) {
        int dataBytes = pcm.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'R', 'I', 'F', 'F'});
        buffer.putInt(36 + dataBytes);
        buffer.put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put(new byte[]{'d', 'a', 't', 'a'});
        buffer.putInt(dataBytes);
        for (short sample : pcm) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    public static boolean playable(byte[] wav, int sampleRate, int sampleCount) {
        if (wav == null || wav.length != 44 + sampleCount * 2) {
            return false;
        }
        if (wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') {
            return false;
        }
        if (wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E') {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(24);
        return buffer.getInt() == sampleRate;
    }
}
