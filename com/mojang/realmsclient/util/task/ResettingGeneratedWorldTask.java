package com.mojang.realmsclient.util.task;

import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.util.WorldGenerationInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ResettingGeneratedWorldTask extends ResettingWorldTask {
    private final WorldGenerationInfo generationInfo;

    public ResettingGeneratedWorldTask(WorldGenerationInfo generationInfo, long serverId, Component title, Runnable callback) {
        super(serverId, title, callback);
        this.generationInfo = generationInfo;
    }

    @Override
    protected void sendResetRequest(RealmsClient client, long serverId) throws RealmsServiceException {
        client.resetWorldWithSeed(serverId, this.generationInfo);
    }
}
