package com.mojang.realmsclient.exception;

import com.mojang.realmsclient.client.RealmsError;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RetryCallException extends RealmsServiceException {
    public static final int DEFAULT_DELAY = 5;
    public final int delaySeconds;

    public RetryCallException(int retryAfter, int httpResultCode) {
        super(RealmsError.CustomError.retry(httpResultCode));
        if (retryAfter >= 0 && retryAfter <= 120) {
            this.delaySeconds = retryAfter;
        } else {
            this.delaySeconds = 5;
        }
    }
}
