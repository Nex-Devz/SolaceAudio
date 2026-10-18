package com.solaceaudio.sources.deezer.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * High-performance Blowfish-CBC decrypter for Deezer audio chunks.
 * Deezer encrypts music in 2048-byte blocks where chunkIndex % 3 == 0 is encrypted
 * using a derived 16-byte key and a fixed IV.
 */
public final class DeezerCipher {

    public static final String DEFAULT_MASTER_KEY = "g4el58wc0zvf9na1";
    public static final byte[] STATIC_IV = new byte[]{0, 1, 2, 3, 4, 5, 6, 7};
    public static final int CHUNK_SIZE = 2048;

    private final SecretKeySpec secretKeySpec;

    public DeezerCipher(String songId, String masterKey) {
        String key = (masterKey != null && masterKey.length() == 16) ? masterKey : DEFAULT_MASTER_KEY;
        byte[] derivedKey = deriveKey(songId, key);
        this.secretKeySpec = new SecretKeySpec(derivedKey, "Blowfish");
    }

    /**
     * Derives the 16-byte Blowfish decryption key:
     * Key[i] = Hash[i] ^ Hash[i + 16] ^ MasterKey[i]
     */
    public static byte[] deriveKey(String songId, String masterKey) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] idDigest = md5.digest(songId.getBytes(StandardCharsets.US_ASCII));

            StringBuilder hex = new StringBuilder(32);
            for (byte b : idDigest) {
                String h = Integer.toHexString(0xFF & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            byte[] hexBytes = hex.toString().getBytes(StandardCharsets.US_ASCII);
            byte[] masterBytes = masterKey.getBytes(StandardCharsets.US_ASCII);

            byte[] finalKey = new byte[16];
            for (int i = 0; i < 16; i++) {
                finalKey[i] = (byte) (hexBytes[i] ^ hexBytes[i + 16] ^ masterBytes[i]);
            }
            return finalKey;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 digest algorithm not available", e);
        }
    }

    /**
     * Decrypts a 2048-byte chunk in-place using Blowfish-CBC with static IV.
     */
    public void decryptChunk(byte[] data, int offset, int length) {
        if (length != CHUNK_SIZE) {
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("Blowfish/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(STATIC_IV));
            byte[] decrypted = cipher.doFinal(data, offset, CHUNK_SIZE);
            System.arraycopy(decrypted, 0, data, offset, CHUNK_SIZE);
        } catch (Exception e) {
            throw new RuntimeException("Deezer chunk decryption failed at offset " + offset, e);
        }
    }
}
