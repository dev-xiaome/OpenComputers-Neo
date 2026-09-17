package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

public class OldUsersConverter {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final File OLD_IPBANLIST = new File("banned-ips.txt");
    public static final File OLD_USERBANLIST = new File("banned-players.txt");
    public static final File OLD_OPLIST = new File("ops.txt");
    public static final File OLD_WHITELIST = new File("white-list.txt");

    static List<String> readOldListFormat(File inFile, Map<String, String[]> read) throws IOException {
        List<String> list = Files.readLines(inFile, StandardCharsets.UTF_8);

        for (String s : list) {
            s = s.trim();
            if (!s.startsWith("#") && s.length() >= 1) {
                String[] astring = s.split("\\|");
                read.put(astring[0].toLowerCase(Locale.ROOT), astring);
            }
        }

        return list;
    }

    private static void lookupPlayers(MinecraftServer server, Collection<String> names, ProfileLookupCallback callback) {
        String[] astring = names.stream().filter(p_11077_ -> !StringUtil.isNullOrEmpty(p_11077_)).toArray(String[]::new);
        if (server.usesAuthentication()) {
            server.getProfileRepository().findProfilesByNames(astring, callback);
        } else {
            for (String s : astring) {
                callback.onProfileLookupSucceeded(UUIDUtil.createOfflineProfile(s));
            }
        }
    }

    public static boolean convertUserBanlist(final MinecraftServer server) {
        final UserBanList userbanlist = new UserBanList(PlayerList.USERBANLIST_FILE);
        if (OLD_USERBANLIST.exists() && OLD_USERBANLIST.isFile()) {
            if (userbanlist.getFile().exists()) {
                try {
                    userbanlist.load();
                } catch (IOException ioexception1) {
                    LOGGER.warn("Could not load existing file {}", userbanlist.getFile().getName(), ioexception1);
                }
            }

            try {
                final Map<String, String[]> map = Maps.newHashMap();
                readOldListFormat(OLD_USERBANLIST, map);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(GameProfile gameProfile) {
                        server.getProfileCache().add(gameProfile);
                        String[] astring = map.get(gameProfile.getName().toLowerCase(Locale.ROOT));
                        if (astring == null) {
                            OldUsersConverter.LOGGER.warn("Could not convert user banlist entry for {}", gameProfile.getName());
                            throw new OldUsersConverter.ConversionError("Profile not in the conversionlist");
                        } else {
                            Date date = astring.length > 1 ? OldUsersConverter.parseDate(astring[1], null) : null;
                            String s = astring.length > 2 ? astring[2] : null;
                            Date date1 = astring.length > 3 ? OldUsersConverter.parseDate(astring[3], null) : null;
                            String s1 = astring.length > 4 ? astring[4] : null;
                            userbanlist.add(new UserBanListEntry(gameProfile, date, s, date1, s1));
                        }
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user banlist entry for {}", profileName, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }
                };
                lookupPlayers(server, map.keySet(), profilelookupcallback);
                userbanlist.save();
                renameOldFile(OLD_USERBANLIST);
                return true;
            } catch (IOException ioexception) {
                LOGGER.warn("Could not read old user banlist to convert it!", (Throwable)ioexception);
                return false;
            } catch (OldUsersConverter.ConversionError oldusersconverter$conversionerror) {
                LOGGER.error("Conversion failed, please try again later", (Throwable)oldusersconverter$conversionerror);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertIpBanlist(MinecraftServer server) {
        IpBanList ipbanlist = new IpBanList(PlayerList.IPBANLIST_FILE);
        if (OLD_IPBANLIST.exists() && OLD_IPBANLIST.isFile()) {
            if (ipbanlist.getFile().exists()) {
                try {
                    ipbanlist.load();
                } catch (IOException ioexception1) {
                    LOGGER.warn("Could not load existing file {}", ipbanlist.getFile().getName(), ioexception1);
                }
            }

            try {
                Map<String, String[]> map = Maps.newHashMap();
                readOldListFormat(OLD_IPBANLIST, map);

                for (String s : map.keySet()) {
                    String[] astring = map.get(s);
                    Date date = astring.length > 1 ? parseDate(astring[1], null) : null;
                    String s1 = astring.length > 2 ? astring[2] : null;
                    Date date1 = astring.length > 3 ? parseDate(astring[3], null) : null;
                    String s2 = astring.length > 4 ? astring[4] : null;
                    ipbanlist.add(new IpBanListEntry(s, date, s1, date1, s2));
                }

                ipbanlist.save();
                renameOldFile(OLD_IPBANLIST);
                return true;
            } catch (IOException ioexception) {
                LOGGER.warn("Could not parse old ip banlist to convert it!", (Throwable)ioexception);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertOpsList(final MinecraftServer server) {
        final ServerOpList serveroplist = new ServerOpList(PlayerList.OPLIST_FILE);
        if (OLD_OPLIST.exists() && OLD_OPLIST.isFile()) {
            if (serveroplist.getFile().exists()) {
                try {
                    serveroplist.load();
                } catch (IOException ioexception1) {
                    LOGGER.warn("Could not load existing file {}", serveroplist.getFile().getName(), ioexception1);
                }
            }

            try {
                List<String> list = Files.readLines(OLD_OPLIST, StandardCharsets.UTF_8);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(GameProfile gameProfile) {
                        server.getProfileCache().add(gameProfile);
                        serveroplist.add(new ServerOpListEntry(gameProfile, server.getOperatorUserPermissionLevel(), false));
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup oplist entry for {}", profileName, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }
                };
                lookupPlayers(server, list, profilelookupcallback);
                serveroplist.save();
                renameOldFile(OLD_OPLIST);
                return true;
            } catch (IOException ioexception) {
                LOGGER.warn("Could not read old oplist to convert it!", (Throwable)ioexception);
                return false;
            } catch (OldUsersConverter.ConversionError oldusersconverter$conversionerror) {
                LOGGER.error("Conversion failed, please try again later", (Throwable)oldusersconverter$conversionerror);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertWhiteList(final MinecraftServer server) {
        final UserWhiteList userwhitelist = new UserWhiteList(PlayerList.WHITELIST_FILE);
        if (OLD_WHITELIST.exists() && OLD_WHITELIST.isFile()) {
            if (userwhitelist.getFile().exists()) {
                try {
                    userwhitelist.load();
                } catch (IOException ioexception1) {
                    LOGGER.warn("Could not load existing file {}", userwhitelist.getFile().getName(), ioexception1);
                }
            }

            try {
                List<String> list = Files.readLines(OLD_WHITELIST, StandardCharsets.UTF_8);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(GameProfile gameProfile) {
                        server.getProfileCache().add(gameProfile);
                        userwhitelist.add(new UserWhiteListEntry(gameProfile));
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", profileName, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }
                };
                lookupPlayers(server, list, profilelookupcallback);
                userwhitelist.save();
                renameOldFile(OLD_WHITELIST);
                return true;
            } catch (IOException ioexception) {
                LOGGER.warn("Could not read old whitelist to convert it!", (Throwable)ioexception);
                return false;
            } catch (OldUsersConverter.ConversionError oldusersconverter$conversionerror) {
                LOGGER.error("Conversion failed, please try again later", (Throwable)oldusersconverter$conversionerror);
                return false;
            }
        } else {
            return true;
        }
    }

    @Nullable
    public static UUID convertMobOwnerIfNecessary(final MinecraftServer server, String username) {
        if (!StringUtil.isNullOrEmpty(username) && username.length() <= 16) {
            Optional<UUID> optional = server.getProfileCache().get(username).map(GameProfile::getId);
            if (optional.isPresent()) {
                return optional.get();
            } else if (!server.isSingleplayer() && server.usesAuthentication()) {
                final List<GameProfile> list = Lists.newArrayList();
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(GameProfile gameProfile) {
                        server.getProfileCache().add(gameProfile);
                        list.add(gameProfile);
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", profileName, exception);
                    }
                };
                lookupPlayers(server, Lists.newArrayList(username), profilelookupcallback);
                return !list.isEmpty() ? list.get(0).getId() : null;
            } else {
                return UUIDUtil.createOfflinePlayerUUID(username);
            }
        } else {
            try {
                return UUID.fromString(username);
            } catch (IllegalArgumentException illegalargumentexception) {
                return null;
            }
        }
    }

    public static boolean convertPlayers(final DedicatedServer server) {
        final File file1 = getWorldPlayersDirectory(server);
        final File file2 = new File(file1.getParentFile(), "playerdata");
        final File file3 = new File(file1.getParentFile(), "unknownplayers");
        if (file1.exists() && file1.isDirectory()) {
            File[] afile = file1.listFiles();
            List<String> list = Lists.newArrayList();

            for (File file4 : afile) {
                String s = file4.getName();
                if (s.toLowerCase(Locale.ROOT).endsWith(".dat")) {
                    String s1 = s.substring(0, s.length() - ".dat".length());
                    if (!s1.isEmpty()) {
                        list.add(s1);
                    }
                }
            }

            try {
                final String[] astring = list.toArray(new String[list.size()]);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(GameProfile gameProfile) {
                        server.getProfileCache().add(gameProfile);
                        UUID uuid = gameProfile.getId();
                        this.movePlayerFile(file2, this.getFileNameForProfile(gameProfile.getName()), uuid.toString());
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user uuid for {}", profileName, exception);
                        if (exception instanceof ProfileNotFoundException) {
                            String s2 = this.getFileNameForProfile(profileName);
                            this.movePlayerFile(file3, s2, s2);
                        } else {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }

                    private void movePlayerFile(File file, String oldFileName, String newFileName) {
                        File file5 = new File(file1, oldFileName + ".dat");
                        File file6 = new File(file, newFileName + ".dat");
                        OldUsersConverter.ensureDirectoryExists(file);
                        if (!file5.renameTo(file6)) {
                            throw new OldUsersConverter.ConversionError("Could not convert file for " + oldFileName);
                        }
                    }

                    private String getFileNameForProfile(String profileName) {
                        String s2 = null;

                        for (String s3 : astring) {
                            if (s3 != null && s3.equalsIgnoreCase(profileName)) {
                                s2 = s3;
                                break;
                            }
                        }

                        if (s2 == null) {
                            throw new OldUsersConverter.ConversionError("Could not find the filename for " + profileName + " anymore");
                        } else {
                            return s2;
                        }
                    }
                };
                lookupPlayers(server, Lists.newArrayList(astring), profilelookupcallback);
                return true;
            } catch (OldUsersConverter.ConversionError oldusersconverter$conversionerror) {
                LOGGER.error("Conversion failed, please try again later", (Throwable)oldusersconverter$conversionerror);
                return false;
            }
        } else {
            return true;
        }
    }

    static void ensureDirectoryExists(File dir) {
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                throw new OldUsersConverter.ConversionError("Can't create directory " + dir.getName() + " in world save directory.");
            }
        } else if (!dir.mkdirs()) {
            throw new OldUsersConverter.ConversionError("Can't create directory " + dir.getName() + " in world save directory.");
        }
    }

    public static boolean serverReadyAfterUserconversion(MinecraftServer server) {
        boolean flag = areOldUserlistsRemoved();
        return flag && areOldPlayersConverted(server);
    }

    private static boolean areOldUserlistsRemoved() {
        boolean flag = false;
        if (OLD_USERBANLIST.exists() && OLD_USERBANLIST.isFile()) {
            flag = true;
        }

        boolean flag1 = false;
        if (OLD_IPBANLIST.exists() && OLD_IPBANLIST.isFile()) {
            flag1 = true;
        }

        boolean flag2 = false;
        if (OLD_OPLIST.exists() && OLD_OPLIST.isFile()) {
            flag2 = true;
        }

        boolean flag3 = false;
        if (OLD_WHITELIST.exists() && OLD_WHITELIST.isFile()) {
            flag3 = true;
        }

        if (!flag && !flag1 && !flag2 && !flag3) {
            return true;
        } else {
            LOGGER.warn("**** FAILED TO START THE SERVER AFTER ACCOUNT CONVERSION!");
            LOGGER.warn("** please remove the following files and restart the server:");
            if (flag) {
                LOGGER.warn("* {}", OLD_USERBANLIST.getName());
            }

            if (flag1) {
                LOGGER.warn("* {}", OLD_IPBANLIST.getName());
            }

            if (flag2) {
                LOGGER.warn("* {}", OLD_OPLIST.getName());
            }

            if (flag3) {
                LOGGER.warn("* {}", OLD_WHITELIST.getName());
            }

            return false;
        }
    }

    private static boolean areOldPlayersConverted(MinecraftServer server) {
        File file1 = getWorldPlayersDirectory(server);
        if (!file1.exists() || !file1.isDirectory() || file1.list().length <= 0 && file1.delete()) {
            return true;
        } else {
            LOGGER.warn("**** DETECTED OLD PLAYER DIRECTORY IN THE WORLD SAVE");
            LOGGER.warn("**** THIS USUALLY HAPPENS WHEN THE AUTOMATIC CONVERSION FAILED IN SOME WAY");
            LOGGER.warn("** please restart the server and if the problem persists, remove the directory '{}'", file1.getPath());
            return false;
        }
    }

    private static File getWorldPlayersDirectory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.PLAYER_OLD_DATA_DIR).toFile();
    }

    private static void renameOldFile(File convertedFile) {
        File file1 = new File(convertedFile.getName() + ".converted");
        convertedFile.renameTo(file1);
    }

    static Date parseDate(String input, Date defaultValue) {
        Date date;
        try {
            date = BanListEntry.DATE_FORMAT.parse(input);
        } catch (ParseException parseexception) {
            date = defaultValue;
        }

        return date;
    }

    static class ConversionError extends RuntimeException {
        ConversionError(String message, Throwable cause) {
            super(message, cause);
        }

        ConversionError(String message) {
            super(message);
        }
    }
}
