package net.minecraft.util;

import com.mojang.authlib.yggdrasil.ServicesKeyInfo;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.logging.LogUtils;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Collection;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public interface SignatureValidator {
    SignatureValidator NO_VALIDATION = (p_216352_, p_216353_) -> true;
    Logger LOGGER = LogUtils.getLogger();

    boolean validate(SignatureUpdater updater, byte[] signature);

    default boolean validate(byte[] digest, byte[] signature) {
        return this.validate(p_216374_ -> p_216374_.update(digest), signature);
    }

    private static boolean verifySignature(SignatureUpdater updater, byte[] signatureBytes, Signature signature) throws SignatureException {
        updater.update(signature::update);
        return signature.verify(signatureBytes);
    }

    static SignatureValidator from(PublicKey publicKey, String algorithm) {
        return (p_216367_, p_216368_) -> {
            try {
                Signature signature = Signature.getInstance(algorithm);
                signature.initVerify(publicKey);
                return verifySignature(p_216367_, p_216368_, signature);
            } catch (Exception exception) {
                LOGGER.error("Failed to verify signature", (Throwable)exception);
                return false;
            }
        };
    }

    @Nullable
    static SignatureValidator from(ServicesKeySet serviceKeySet, ServicesKeyType serviceKeyType) {
        Collection<ServicesKeyInfo> collection = serviceKeySet.keys(serviceKeyType);
        return collection.isEmpty() ? null : (p_284690_, p_284691_) -> collection.stream().anyMatch(p_216361_ -> {
                Signature signature = p_216361_.signature();

                try {
                    return verifySignature(p_284690_, p_284691_, signature);
                } catch (SignatureException signatureexception) {
                    LOGGER.error("Failed to verify Services signature", (Throwable)signatureexception);
                    return false;
                }
            });
    }
}
