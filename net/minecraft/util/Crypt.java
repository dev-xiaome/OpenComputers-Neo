package net.minecraft.util;

import com.google.common.primitives.Longs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Base64.Encoder;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.network.FriendlyByteBuf;

public class Crypt {
    private static final String SYMMETRIC_ALGORITHM = "AES";
    private static final int SYMMETRIC_BITS = 128;
    private static final String ASYMMETRIC_ALGORITHM = "RSA";
    private static final int ASYMMETRIC_BITS = 1024;
    private static final String BYTE_ENCODING = "ISO_8859_1";
    private static final String HASH_ALGORITHM = "SHA-1";
    public static final String SIGNING_ALGORITHM = "SHA256withRSA";
    public static final int SIGNATURE_BYTES = 256;
    private static final String PEM_RSA_PRIVATE_KEY_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PEM_RSA_PRIVATE_KEY_FOOTER = "-----END RSA PRIVATE KEY-----";
    public static final String RSA_PUBLIC_KEY_HEADER = "-----BEGIN RSA PUBLIC KEY-----";
    private static final String RSA_PUBLIC_KEY_FOOTER = "-----END RSA PUBLIC KEY-----";
    public static final String MIME_LINE_SEPARATOR = "\n";
    public static final Encoder MIME_ENCODER = Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.UTF_8));
    public static final Codec<PublicKey> PUBLIC_KEY_CODEC = Codec.STRING.comapFlatMap(p_274846_ -> {
        try {
            return DataResult.success(stringToRsaPublicKey(p_274846_));
        } catch (CryptException cryptexception) {
            return DataResult.error(cryptexception::getMessage);
        }
    }, Crypt::rsaPublicKeyToString);
    public static final Codec<PrivateKey> PRIVATE_KEY_CODEC = Codec.STRING.comapFlatMap(p_274845_ -> {
        try {
            return DataResult.success(stringToPemRsaPrivateKey(p_274845_));
        } catch (CryptException cryptexception) {
            return DataResult.error(cryptexception::getMessage);
        }
    }, Crypt::pemRsaPrivateKeyToString);

    public static SecretKey generateSecretKey() throws CryptException {
        try {
            KeyGenerator keygenerator = KeyGenerator.getInstance("AES");
            keygenerator.init(128);
            return keygenerator.generateKey();
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    public static KeyPair generateKeyPair() throws CryptException {
        try {
            KeyPairGenerator keypairgenerator = KeyPairGenerator.getInstance("RSA");
            keypairgenerator.initialize(1024);
            return keypairgenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    /**
     * Compute a serverId hash for use by sendSessionRequest()
     */
    public static byte[] digestData(String serverId, PublicKey publicKey, SecretKey secretKey) throws CryptException {
        try {
            return digestData(serverId.getBytes("ISO_8859_1"), secretKey.getEncoded(), publicKey.getEncoded());
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    private static byte[] digestData(byte[]... data) throws Exception {
        MessageDigest messagedigest = MessageDigest.getInstance("SHA-1");

        for (byte[] abyte : data) {
            messagedigest.update(abyte);
        }

        return messagedigest.digest();
    }

    private static <T extends Key> T rsaStringToKey(String keyBase64, String header, String footer, Crypt.ByteArrayToKeyFunction<T> keyFunction) throws CryptException {
        int i = keyBase64.indexOf(header);
        if (i != -1) {
            i += header.length();
            int j = keyBase64.indexOf(footer, i);
            keyBase64 = keyBase64.substring(i, j + 1);
        }

        try {
            return keyFunction.apply(Base64.getMimeDecoder().decode(keyBase64));
        } catch (IllegalArgumentException illegalargumentexception) {
            throw new CryptException(illegalargumentexception);
        }
    }

    public static PrivateKey stringToPemRsaPrivateKey(String keyBase64) throws CryptException {
        return rsaStringToKey(keyBase64, "-----BEGIN RSA PRIVATE KEY-----", "-----END RSA PRIVATE KEY-----", Crypt::byteToPrivateKey);
    }

    public static PublicKey stringToRsaPublicKey(String keyBase64) throws CryptException {
        return rsaStringToKey(keyBase64, "-----BEGIN RSA PUBLIC KEY-----", "-----END RSA PUBLIC KEY-----", Crypt::byteToPublicKey);
    }

    public static String rsaPublicKeyToString(PublicKey key) {
        if (!"RSA".equals(key.getAlgorithm())) {
            throw new IllegalArgumentException("Public key must be RSA");
        } else {
            return "-----BEGIN RSA PUBLIC KEY-----\n" + MIME_ENCODER.encodeToString(key.getEncoded()) + "\n-----END RSA PUBLIC KEY-----\n";
        }
    }

    public static String pemRsaPrivateKeyToString(PrivateKey key) {
        if (!"RSA".equals(key.getAlgorithm())) {
            throw new IllegalArgumentException("Private key must be RSA");
        } else {
            return "-----BEGIN RSA PRIVATE KEY-----\n" + MIME_ENCODER.encodeToString(key.getEncoded()) + "\n-----END RSA PRIVATE KEY-----\n";
        }
    }

    private static PrivateKey byteToPrivateKey(byte[] keyBytes) throws CryptException {
        try {
            EncodedKeySpec encodedkeyspec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyfactory = KeyFactory.getInstance("RSA");
            return keyfactory.generatePrivate(encodedkeyspec);
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    /**
     * Create a new PublicKey from encoded X.509 data
     */
    public static PublicKey byteToPublicKey(byte[] encodedKey) throws CryptException {
        try {
            EncodedKeySpec encodedkeyspec = new X509EncodedKeySpec(encodedKey);
            KeyFactory keyfactory = KeyFactory.getInstance("RSA");
            return keyfactory.generatePublic(encodedkeyspec);
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    /**
     * Decrypt shared secret AES key using RSA private key
     */
    public static SecretKey decryptByteToSecretKey(PrivateKey key, byte[] secretKeyEncrypted) throws CryptException {
        byte[] abyte = decryptUsingKey(key, secretKeyEncrypted);

        try {
            return new SecretKeySpec(abyte, "AES");
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    /**
     * Encrypt byte[] data with RSA public key
     */
    public static byte[] encryptUsingKey(Key key, byte[] data) throws CryptException {
        return cipherData(1, key, data);
    }

    /**
     * Decrypt byte[] data with RSA private key
     */
    public static byte[] decryptUsingKey(Key key, byte[] data) throws CryptException {
        return cipherData(2, key, data);
    }

    /**
     * Encrypt or decrypt byte[] data using the specified key
     */
    private static byte[] cipherData(int opMode, Key key, byte[] data) throws CryptException {
        try {
            return setupCipher(opMode, key.getAlgorithm(), key).doFinal(data);
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    /**
     * Creates the Cipher Instance.
     */
    private static Cipher setupCipher(int opMode, String transformation, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(opMode, key);
        return cipher;
    }

    /**
     * Creates a Cipher instance using the AES/CFB8/NoPadding algorithm. Used for protocol encryption.
     */
    public static Cipher getCipher(int opMode, Key key) throws CryptException {
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(opMode, key, new IvParameterSpec(key.getEncoded()));
            return cipher;
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    interface ByteArrayToKeyFunction<T extends Key> {
        T apply(byte[] bytes) throws CryptException;
    }

    public static record SaltSignaturePair(long salt, byte[] signature) {
        public static final Crypt.SaltSignaturePair EMPTY = new Crypt.SaltSignaturePair(0L, ByteArrays.EMPTY_ARRAY);

        public SaltSignaturePair(FriendlyByteBuf p_216098_) {
            this(p_216098_.readLong(), p_216098_.readByteArray());
        }

        public boolean isValid() {
            return this.signature.length > 0;
        }

        public static void write(FriendlyByteBuf buffer, Crypt.SaltSignaturePair signaturePair) {
            buffer.writeLong(signaturePair.salt);
            buffer.writeByteArray(signaturePair.signature);
        }

        public byte[] saltAsBytes() {
            return Longs.toByteArray(this.salt);
        }
    }

    public static class SaltSupplier {
        private static final SecureRandom secureRandom = new SecureRandom();

        public static long getLong() {
            return secureRandom.nextLong();
        }
    }
}
