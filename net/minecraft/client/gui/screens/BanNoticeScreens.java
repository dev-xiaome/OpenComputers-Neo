package net.minecraft.client.gui.screens;

import com.mojang.authlib.minecraft.BanDetails;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.chat.report.BanReason;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.CommonLinks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;

@OnlyIn(Dist.CLIENT)
public class BanNoticeScreens {
    private static final Component TEMPORARY_BAN_TITLE = Component.translatable("gui.banned.title.temporary").withStyle(ChatFormatting.BOLD);
    private static final Component PERMANENT_BAN_TITLE = Component.translatable("gui.banned.title.permanent").withStyle(ChatFormatting.BOLD);
    public static final Component NAME_BAN_TITLE = Component.translatable("gui.banned.name.title").withStyle(ChatFormatting.BOLD);
    private static final Component SKIN_BAN_TITLE = Component.translatable("gui.banned.skin.title").withStyle(ChatFormatting.BOLD);
    private static final Component SKIN_BAN_DESCRIPTION = Component.translatable(
        "gui.banned.skin.description", Component.translationArg(CommonLinks.SUSPENSION_HELP)
    );

    public static ConfirmLinkScreen create(BooleanConsumer callback, BanDetails banDetails) {
        return new ConfirmLinkScreen(
            callback, getBannedTitle(banDetails), getBannedScreenText(banDetails), CommonLinks.SUSPENSION_HELP, CommonComponents.GUI_ACKNOWLEDGE, true
        );
    }

    public static ConfirmLinkScreen createSkinBan(Runnable callback) {
        URI uri = CommonLinks.SUSPENSION_HELP;
        return new ConfirmLinkScreen(p_351642_ -> {
            if (p_351642_) {
                Util.getPlatform().openUri(uri);
            }

            callback.run();
        }, SKIN_BAN_TITLE, SKIN_BAN_DESCRIPTION, uri, CommonComponents.GUI_ACKNOWLEDGE, true);
    }

    public static ConfirmLinkScreen createNameBan(String username, Runnable callback) {
        URI uri = CommonLinks.SUSPENSION_HELP;
        return new ConfirmLinkScreen(
            p_351639_ -> {
                if (p_351639_) {
                    Util.getPlatform().openUri(uri);
                }

                callback.run();
            },
            NAME_BAN_TITLE,
            Component.translatable(
                "gui.banned.name.description",
                Component.literal(username).withStyle(ChatFormatting.YELLOW),
                Component.translationArg(CommonLinks.SUSPENSION_HELP)
            ),
            uri,
            CommonComponents.GUI_ACKNOWLEDGE,
            true
        );
    }

    private static Component getBannedTitle(BanDetails banDetails) {
        return isTemporaryBan(banDetails) ? TEMPORARY_BAN_TITLE : PERMANENT_BAN_TITLE;
    }

    private static Component getBannedScreenText(BanDetails banDetails) {
        return Component.translatable(
            "gui.banned.description", getBanReasonText(banDetails), getBanStatusText(banDetails), Component.translationArg(CommonLinks.SUSPENSION_HELP)
        );
    }

    private static Component getBanReasonText(BanDetails banDetails) {
        String s = banDetails.reason();
        String s1 = banDetails.reasonMessage();
        if (StringUtils.isNumeric(s)) {
            int i = Integer.parseInt(s);
            BanReason banreason = BanReason.byId(i);
            Component component;
            if (banreason != null) {
                component = ComponentUtils.mergeStyles(banreason.title().copy(), Style.EMPTY.withBold(true));
            } else if (s1 != null) {
                component = Component.translatable("gui.banned.description.reason_id_message", i, s1).withStyle(ChatFormatting.BOLD);
            } else {
                component = Component.translatable("gui.banned.description.reason_id", i).withStyle(ChatFormatting.BOLD);
            }

            return Component.translatable("gui.banned.description.reason", component);
        } else {
            return Component.translatable("gui.banned.description.unknownreason");
        }
    }

    private static Component getBanStatusText(BanDetails banDetails) {
        if (isTemporaryBan(banDetails)) {
            Component component = getBanDurationText(banDetails);
            return Component.translatable(
                "gui.banned.description.temporary",
                Component.translatable("gui.banned.description.temporary.duration", component).withStyle(ChatFormatting.BOLD)
            );
        } else {
            return Component.translatable("gui.banned.description.permanent").withStyle(ChatFormatting.BOLD);
        }
    }

    private static Component getBanDurationText(BanDetails banDetails) {
        Duration duration = Duration.between(Instant.now(), banDetails.expires());
        long i = duration.toHours();
        if (i > 72L) {
            return CommonComponents.days(duration.toDays());
        } else {
            return i < 1L ? CommonComponents.minutes(duration.toMinutes()) : CommonComponents.hours(duration.toHours());
        }
    }

    private static boolean isTemporaryBan(BanDetails banDetails) {
        return banDetails.expires() != null;
    }
}
