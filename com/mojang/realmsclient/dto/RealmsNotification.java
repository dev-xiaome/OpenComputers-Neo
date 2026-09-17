package com.mojang.realmsclient.dto;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsNotification {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String NOTIFICATION_UUID = "notificationUuid";
    private static final String DISMISSABLE = "dismissable";
    private static final String SEEN = "seen";
    private static final String TYPE = "type";
    private static final String VISIT_URL = "visitUrl";
    private static final String INFO_POPUP = "infoPopup";
    static final Component BUTTON_TEXT_FALLBACK = Component.translatable("mco.notification.visitUrl.buttonText.default");
    final UUID uuid;
    final boolean dismissable;
    final boolean seen;
    final String type;

    RealmsNotification(UUID uuid, boolean dismissable, boolean seen, String type) {
        this.uuid = uuid;
        this.dismissable = dismissable;
        this.seen = seen;
        this.type = type;
    }

    public boolean seen() {
        return this.seen;
    }

    public boolean dismissable() {
        return this.dismissable;
    }

    public UUID uuid() {
        return this.uuid;
    }

    public static List<RealmsNotification> parseList(String json) {
        List<RealmsNotification> list = new ArrayList<>();

        try {
            for (JsonElement jsonelement : JsonParser.parseString(json).getAsJsonObject().get("notifications").getAsJsonArray()) {
                list.add(parse(jsonelement.getAsJsonObject()));
            }
        } catch (Exception exception) {
            LOGGER.error("Could not parse list of RealmsNotifications", (Throwable)exception);
        }

        return list;
    }

    private static RealmsNotification parse(JsonObject json) {
        UUID uuid = JsonUtils.getUuidOr("notificationUuid", json, null);
        if (uuid == null) {
            throw new IllegalStateException("Missing required property notificationUuid");
        } else {
            boolean flag = JsonUtils.getBooleanOr("dismissable", json, true);
            boolean flag1 = JsonUtils.getBooleanOr("seen", json, false);
            String s = JsonUtils.getRequiredString("type", json);
            RealmsNotification realmsnotification = new RealmsNotification(uuid, flag, flag1, s);

            return (RealmsNotification)(switch (s) {
                case "visitUrl" -> RealmsNotification.VisitUrl.parse(realmsnotification, json);
                case "infoPopup" -> RealmsNotification.InfoPopup.parse(realmsnotification, json);
                default -> realmsnotification;
            });
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class InfoPopup extends RealmsNotification {
        private static final String TITLE = "title";
        private static final String MESSAGE = "message";
        private static final String IMAGE = "image";
        private static final String URL_BUTTON = "urlButton";
        private final RealmsText title;
        private final RealmsText message;
        private final ResourceLocation image;
        @Nullable
        private final RealmsNotification.UrlButton urlButton;

        private InfoPopup(
            RealmsNotification notification,
            RealmsText title,
            RealmsText message,
            ResourceLocation image,
            @Nullable RealmsNotification.UrlButton urlButton
        ) {
            super(notification.uuid, notification.dismissable, notification.seen, notification.type);
            this.title = title;
            this.message = message;
            this.image = image;
            this.urlButton = urlButton;
        }

        public static RealmsNotification.InfoPopup parse(RealmsNotification notification, JsonObject json) {
            RealmsText realmstext = JsonUtils.getRequired("title", json, RealmsText::parse);
            RealmsText realmstext1 = JsonUtils.getRequired("message", json, RealmsText::parse);
            ResourceLocation resourcelocation = ResourceLocation.parse(JsonUtils.getRequiredString("image", json));
            RealmsNotification.UrlButton realmsnotification$urlbutton = JsonUtils.getOptional("urlButton", json, RealmsNotification.UrlButton::parse);
            return new RealmsNotification.InfoPopup(notification, realmstext, realmstext1, resourcelocation, realmsnotification$urlbutton);
        }

        @Nullable
        public PopupScreen buildScreen(Screen backgroundScreen, Consumer<UUID> uuidOutput) {
            Component component = this.title.createComponent();
            if (component == null) {
                RealmsNotification.LOGGER.warn("Realms info popup had title with no available translation: {}", this.title);
                return null;
            } else {
                PopupScreen.Builder popupscreen$builder = new PopupScreen.Builder(backgroundScreen, component)
                    .setImage(this.image)
                    .setMessage(this.message.createComponent(CommonComponents.EMPTY));
                if (this.urlButton != null) {
                    popupscreen$builder.addButton(this.urlButton.urlText.createComponent(RealmsNotification.BUTTON_TEXT_FALLBACK), p_304766_ -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        minecraft.setScreen(new ConfirmLinkScreen(p_304708_ -> {
                            if (p_304708_) {
                                Util.getPlatform().openUri(this.urlButton.url);
                                minecraft.setScreen(backgroundScreen);
                            } else {
                                minecraft.setScreen(p_304766_);
                            }
                        }, this.urlButton.url, true));
                        uuidOutput.accept(this.uuid());
                    });
                }

                popupscreen$builder.addButton(CommonComponents.GUI_OK, p_304476_ -> {
                    p_304476_.onClose();
                    uuidOutput.accept(this.uuid());
                });
                popupscreen$builder.onClose(() -> uuidOutput.accept(this.uuid()));
                return popupscreen$builder.build();
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record UrlButton(String url, RealmsText urlText) {
        private static final String URL = "url";
        private static final String URL_TEXT = "urlText";

        public static RealmsNotification.UrlButton parse(JsonObject json) {
            String s = JsonUtils.getRequiredString("url", json);
            RealmsText realmstext = JsonUtils.getRequired("urlText", json, RealmsText::parse);
            return new RealmsNotification.UrlButton(s, realmstext);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class VisitUrl extends RealmsNotification {
        private static final String URL = "url";
        private static final String BUTTON_TEXT = "buttonText";
        private static final String MESSAGE = "message";
        private final String url;
        private final RealmsText buttonText;
        private final RealmsText message;

        private VisitUrl(RealmsNotification notification, String url, RealmsText buttonText, RealmsText message) {
            super(notification.uuid, notification.dismissable, notification.seen, notification.type);
            this.url = url;
            this.buttonText = buttonText;
            this.message = message;
        }

        public static RealmsNotification.VisitUrl parse(RealmsNotification notification, JsonObject json) {
            String s = JsonUtils.getRequiredString("url", json);
            RealmsText realmstext = JsonUtils.getRequired("buttonText", json, RealmsText::parse);
            RealmsText realmstext1 = JsonUtils.getRequired("message", json, RealmsText::parse);
            return new RealmsNotification.VisitUrl(notification, s, realmstext, realmstext1);
        }

        public Component getMessage() {
            return this.message.createComponent(Component.translatable("mco.notification.visitUrl.message.default"));
        }

        public Button buildOpenLinkButton(Screen lastScreen) {
            Component component = this.buttonText.createComponent(RealmsNotification.BUTTON_TEXT_FALLBACK);
            return Button.builder(component, ConfirmLinkScreen.confirmLink(lastScreen, this.url)).build();
        }
    }
}
