package cn.muxicloud.mop.common.id;

import java.security.SecureRandom;

/**
 * ULID（26 位 Crockford Base32）。对外稳定标识用它，数据库主键仍用雪花 BIGINT。
 */
public final class Ulids {
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulids() {
    }

    public static String newUlid() {
        byte[] data = new byte[16];
        long time = System.currentTimeMillis();
        data[0] = (byte) (time >>> 40);
        data[1] = (byte) (time >>> 32);
        data[2] = (byte) (time >>> 24);
        data[3] = (byte) (time >>> 16);
        data[4] = (byte) (time >>> 8);
        data[5] = (byte) time;
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        System.arraycopy(random, 0, data, 6, 10);
        return encode(data);
    }

    static String encode(byte[] data) {
        if (data.length != 16) {
            throw new IllegalArgumentException("ULID 需要 16 字节");
        }
        char[] out = new char[26];
        out[0] = CROCKFORD[(data[0] & 224) >> 5];
        out[1] = CROCKFORD[data[0] & 31];
        out[2] = CROCKFORD[(data[1] & 248) >> 3];
        out[3] = CROCKFORD[((data[1] & 7) << 2) | ((data[2] & 192) >> 6)];
        out[4] = CROCKFORD[(data[2] & 62) >> 1];
        out[5] = CROCKFORD[((data[2] & 1) << 4) | ((data[3] & 240) >> 4)];
        out[6] = CROCKFORD[((data[3] & 15) << 1) | ((data[4] & 128) >> 7)];
        out[7] = CROCKFORD[(data[4] & 124) >> 2];
        out[8] = CROCKFORD[((data[4] & 3) << 3) | ((data[5] & 224) >> 5)];
        out[9] = CROCKFORD[data[5] & 31];
        out[10] = CROCKFORD[(data[6] & 248) >> 3];
        out[11] = CROCKFORD[((data[6] & 7) << 2) | ((data[7] & 192) >> 6)];
        out[12] = CROCKFORD[(data[7] & 62) >> 1];
        out[13] = CROCKFORD[((data[7] & 1) << 4) | ((data[8] & 240) >> 4)];
        out[14] = CROCKFORD[((data[8] & 15) << 1) | ((data[9] & 128) >> 7)];
        out[15] = CROCKFORD[(data[9] & 124) >> 2];
        out[16] = CROCKFORD[((data[9] & 3) << 3) | ((data[10] & 224) >> 5)];
        out[17] = CROCKFORD[data[10] & 31];
        out[18] = CROCKFORD[(data[11] & 248) >> 3];
        out[19] = CROCKFORD[((data[11] & 7) << 2) | ((data[12] & 192) >> 6)];
        out[20] = CROCKFORD[(data[12] & 62) >> 1];
        out[21] = CROCKFORD[((data[12] & 1) << 4) | ((data[13] & 240) >> 4)];
        out[22] = CROCKFORD[((data[13] & 15) << 1) | ((data[14] & 128) >> 7)];
        out[23] = CROCKFORD[(data[14] & 124) >> 2];
        out[24] = CROCKFORD[((data[14] & 3) << 3) | ((data[15] & 224) >> 5)];
        out[25] = CROCKFORD[data[15] & 31];
        return new String(out);
    }
}
