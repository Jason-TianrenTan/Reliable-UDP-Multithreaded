package com.company.Utils;

import java.nio.ByteBuffer;

public class ByteUtils {

    public static byte[] longToBytes(long val) {
        ByteBuffer lBuffer = ByteBuffer.allocate(Long.BYTES);
        lBuffer.putLong(val);
        return lBuffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public static int bytesToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getInt();
    }

    public static byte[] intToBytes(int val) {
        ByteBuffer iBuffer = ByteBuffer.allocate(Integer.BYTES);
        iBuffer.putInt(val);
        return iBuffer.array();
    }
}
