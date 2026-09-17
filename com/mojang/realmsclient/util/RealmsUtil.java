package com.mojang.realmsclient.util;

import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.Date;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsUtil {
    private static final Component RIGHT_NOW = Component.translatable("mco.util.time.now");
    private static final int MINUTES = 60;
    private static final int HOURS = 3600;
    private static final int DAYS = 86400;

    public static Component convertToAgePresentation(long millis) {
        if (millis < 0L) {
            return RIGHT_NOW;
        } else {
            long i = millis / 1000L;
            if (i < 60L) {
                return Component.translatable("mco.time.secondsAgo", i);
            } else if (i < 3600L) {
                long l = i / 60L;
                return Component.translatable("mco.time.minutesAgo", l);
            } else if (i < 86400L) {
                long k = i / 3600L;
                return Component.translatable("mco.time.hoursAgo", k);
            } else {
                long j = i / 86400L;
                return Component.translatable("mco.time.daysAgo", j);
            }
        }
    }

    public static Component convertToAgePresentationFromInstant(Date date) {
        return convertToAgePresentation(System.currentTimeMillis() - date.getTime());
    }

    public static void renderPlayerFace(GuiGraphics guiGraphics, int x, int y, int size, UUID playerUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        ProfileResult profileresult = minecraft.getMinecraftSessionService().fetchProfile(playerUuid, false);
        PlayerSkin playerskin = profileresult != null ? minecraft.getSkinManager().getInsecureSkin(profileresult.profile()) : DefaultPlayerSkin.get(playerUuid);
        PlayerFaceRenderer.draw(guiGraphics, playerskin.texture(), x, y, size);
    }
}
