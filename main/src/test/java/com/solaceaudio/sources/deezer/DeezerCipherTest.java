package com.solaceaudio.sources.deezer;

import com.solaceaudio.sources.deezer.crypto.DeezerCipher;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;

public class DeezerCipherTest {

    @Test
    public void testTrackKeyDerivation() {
        byte[] key = DeezerCipher.deriveKey("12345678", DeezerCipher.DEFAULT_MASTER_KEY);
        assertNotNull(key);
        assertEquals(16, key.length);

        // Deterministic derivation check
        byte[] key2 = DeezerCipher.deriveKey("12345678", DeezerCipher.DEFAULT_MASTER_KEY);
        assertArrayEquals(key, key2);

        // Different track id produces different key
        byte[] keyOther = DeezerCipher.deriveKey("87654321", DeezerCipher.DEFAULT_MASTER_KEY);
        assertFalse(java.util.Arrays.equals(key, keyOther));
    }

    @Test
    public void testBlowfishRoundTrip() throws Exception {
        byte[] plainData = new byte[2048];
        for (int i = 0; i < plainData.length; i++) {
            plainData[i] = (byte) (i % 256);
        }

        byte[] key = DeezerCipher.deriveKey("99887766", DeezerCipher.DEFAULT_MASTER_KEY);
        SecretKeySpec keySpec = new SecretKeySpec(key, "Blowfish");
        IvParameterSpec ivSpec = new IvParameterSpec(DeezerCipher.STATIC_IV);

        Cipher encCipher = Cipher.getInstance("Blowfish/CBC/NoPadding");
        encCipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = encCipher.doFinal(plainData);

        assertNotNull(encrypted);
        assertEquals(2048, encrypted.length);
        assertFalse(java.util.Arrays.equals(plainData, encrypted));

        DeezerCipher decrypter = new DeezerCipher("99887766", DeezerCipher.DEFAULT_MASTER_KEY);
        decrypter.decryptChunk(encrypted, 0, encrypted.length);

        assertArrayEquals(plainData, encrypted);
    }
}
