package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class WorldTemplate extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    public String id = "";
    public String name = "";
    public String version = "";
    public String author = "";
    public String link = "";
    @Nullable
    public String image;
    public String trailer = "";
    public String recommendedPlayers = "";
    public WorldTemplate.WorldTemplateType type = WorldTemplate.WorldTemplateType.WORLD_TEMPLATE;

    public static WorldTemplate parse(JsonObject json) {
        WorldTemplate worldtemplate = new WorldTemplate();

        try {
            worldtemplate.id = JsonUtils.getStringOr("id", json, "");
            worldtemplate.name = JsonUtils.getStringOr("name", json, "");
            worldtemplate.version = JsonUtils.getStringOr("version", json, "");
            worldtemplate.author = JsonUtils.getStringOr("author", json, "");
            worldtemplate.link = JsonUtils.getStringOr("link", json, "");
            worldtemplate.image = JsonUtils.getStringOr("image", json, null);
            worldtemplate.trailer = JsonUtils.getStringOr("trailer", json, "");
            worldtemplate.recommendedPlayers = JsonUtils.getStringOr("recommendedPlayers", json, "");
            worldtemplate.type = WorldTemplate.WorldTemplateType.valueOf(
                JsonUtils.getStringOr("type", json, WorldTemplate.WorldTemplateType.WORLD_TEMPLATE.name())
            );
        } catch (Exception exception) {
            LOGGER.error("Could not parse WorldTemplate: {}", exception.getMessage());
        }

        return worldtemplate;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum WorldTemplateType {
        WORLD_TEMPLATE,
        MINIGAME,
        ADVENTUREMAP,
        EXPERIENCE,
        INSPIRATION;
    }
}
