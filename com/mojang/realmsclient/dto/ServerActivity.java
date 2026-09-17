package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.realmsclient.util.JsonUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ServerActivity extends ValueObject {
    public String profileUuid;
    public long joinTime;
    public long leaveTime;

    public static ServerActivity parse(JsonObject json) {
        ServerActivity serveractivity = new ServerActivity();

        try {
            serveractivity.profileUuid = JsonUtils.getStringOr("profileUuid", json, null);
            serveractivity.joinTime = JsonUtils.getLongOr("joinTime", json, Long.MIN_VALUE);
            serveractivity.leaveTime = JsonUtils.getLongOr("leaveTime", json, Long.MIN_VALUE);
        } catch (Exception exception) {
        }

        return serveractivity;
    }
}
