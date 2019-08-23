package com.googlecode.mp4parser.boxes.mp4.objectdescriptors;

import java.nio.ByteBuffer;

public class BitReaderBuffer {
    private ByteBuffer buffer;
    private int initialPos;
    private int position;

    public BitReaderBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
        initialPos = buffer.position();
    }

    public int readBits(int i) {
        byte b = buffer.get(initialPos + position / 8);
        int v = b < 0 ? b + 256 : b;
        int left = 8 - position % 8;
        int rc;
        if (i <= left) {
            rc = (v << (position % 8) & 0xFF) >> ((position % 8) + (left - i));
            position += i;
        } else {
            int then = i - left;
            rc = readBits(left);
            rc = rc << then;
            rc += readBits(then);
        }
        buffer.position(initialPos + (int) Math.ceil((double) position / 8));
        return rc;
    }

    public int getPosition() {
        return position;
    }

    public int remainingBits() {
        return buffer.limit() * 8 - position;
    }
}
