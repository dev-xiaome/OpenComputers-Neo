package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.security.PrivateKey;
import java.security.Signature;
import org.slf4j.Logger;

public interface Signer {
    Logger LOGGER = LogUtils.getLogger();

    byte[] sign(SignatureUpdater updater);

    default byte[] sign(byte[] signature) {
        return this.sign(p_216394_ -> p_216394_.update(signature));
    }

    static Signer from(PrivateKey privateKey, String algorithm) {
        return p_216386_ -> {
            try {
                Signature signature = Signature.getInstance(algorithm);
                signature.initSign(privateKey);
                p_216386_.update(signature::update);
                return signature.sign();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to sign message", exception);
            }
        };
    }
}
