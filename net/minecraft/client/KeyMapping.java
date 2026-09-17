package net.minecraft.client;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class KeyMapping implements Comparable<KeyMapping>, net.neoforged.neoforge.client.extensions.IKeyMappingExtension {
    private static final Map<String, KeyMapping> ALL = Maps.newConcurrentMap();
    private static final net.neoforged.neoforge.client.settings.KeyMappingLookup MAP = new net.neoforged.neoforge.client.settings.KeyMappingLookup();
    private static final Set<String> CATEGORIES = Sets.newConcurrentHashSet();
    public static final String CATEGORY_MOVEMENT = "key.categories.movement";
    public static final String CATEGORY_MISC = "key.categories.misc";
    public static final String CATEGORY_MULTIPLAYER = "key.categories.multiplayer";
    public static final String CATEGORY_GAMEPLAY = "key.categories.gameplay";
    public static final String CATEGORY_INVENTORY = "key.categories.inventory";
    public static final String CATEGORY_INTERFACE = "key.categories.ui";
    public static final String CATEGORY_CREATIVE = "key.categories.creative";
    private static final Map<String, Integer> CATEGORY_SORT_ORDER = Util.make(Maps.newHashMap(), p_90845_ -> {
        p_90845_.put("key.categories.movement", 1);
        p_90845_.put("key.categories.gameplay", 2);
        p_90845_.put("key.categories.inventory", 3);
        p_90845_.put("key.categories.creative", 4);
        p_90845_.put("key.categories.multiplayer", 5);
        p_90845_.put("key.categories.ui", 6);
        p_90845_.put("key.categories.misc", 7);
    });
    private final String name;
    private final InputConstants.Key defaultKey;
    private final String category;
    private InputConstants.Key key;
    boolean isDown;
    private int clickCount;

    public static void click(InputConstants.Key key) {
        for (KeyMapping keymapping : MAP.getAll(key))
        if (keymapping != null) {
            keymapping.clickCount++;
        }
    }

    public static void set(InputConstants.Key key, boolean held) {
        for (KeyMapping keymapping : MAP.getAll(key, !held))
            if (keymapping != null) {
                keymapping.setDown(held);
            }
    }

    public static void setAll() {
        for (KeyMapping keymapping : ALL.values()) {
            if (keymapping.key.getType() == InputConstants.Type.KEYSYM && keymapping.key.getValue() != InputConstants.UNKNOWN.getValue()) {
                keymapping.setDown(InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), keymapping.key.getValue()));
            }
        }
    }

    public static void releaseAll() {
        for (KeyMapping keymapping : ALL.values()) {
            keymapping.release();
        }
    }

    public static void resetToggleKeys() {
        for (KeyMapping keymapping : ALL.values()) {
            if (keymapping instanceof ToggleKeyMapping togglekeymapping) {
                togglekeymapping.reset();
            }
        }
    }

    public static void resetMapping() {
        MAP.clear();

        for (KeyMapping keymapping : ALL.values()) {
            MAP.put(keymapping.key, keymapping);
        }
    }

    public KeyMapping(String name, int keyCode, String category) {
        this(name, InputConstants.Type.KEYSYM, keyCode, category);
    }

    public KeyMapping(String name, InputConstants.Type type, int keyCode, String category) {
        this.name = name;
        this.key = type.getOrCreate(keyCode);
        this.defaultKey = this.key;
        this.category = category;
        ALL.put(name, this);
        MAP.put(this.key, this);
        CATEGORIES.add(category);
    }

    public boolean isDown() {
        return this.isDown && isConflictContextAndModifierActive();
    }

    public String getCategory() {
        return this.category;
    }

    public boolean consumeClick() {
        if (this.clickCount == 0) {
            return false;
        } else {
            this.clickCount--;
            return true;
        }
    }

    private void release() {
        this.clickCount = 0;
        this.setDown(false);
    }

    public String getName() {
        return this.name;
    }

    public InputConstants.Key getDefaultKey() {
        return this.defaultKey;
    }

    /**
     * Binds a new KeyCode to this
     */
    public void setKey(InputConstants.Key key) {
        this.key = key;
    }

    public int compareTo(KeyMapping p_90841_) {
        if (this.category.equals(p_90841_.category)) return I18n.get(this.name).compareTo(I18n.get(p_90841_.name));
        Integer tCat = CATEGORY_SORT_ORDER.get(this.category);
        Integer oCat = CATEGORY_SORT_ORDER.get(p_90841_.category);
        if (tCat == null && oCat != null) return 1;
        if (tCat != null && oCat == null) return -1;
        if (tCat == null && oCat == null) return I18n.get(this.category).compareTo(I18n.get(p_90841_.category));
        return  tCat.compareTo(oCat);
    }

    /**
     * Returns a supplier which gets a keybind's current binding (eg, <code>key.forward</code> returns <samp>W</samp> by default), or the keybind's name if no such keybind exists (eg, <code>key.invalid</code> returns <samp>key.invalid</samp>)
     */
    public static Supplier<Component> createNameSupplier(String key) {
        KeyMapping keymapping = ALL.get(key);
        return keymapping == null ? () -> Component.translatable(key) : keymapping::getTranslatedKeyMessage;
    }

    /**
     * Returns {@code true} if the supplied {@code KeyMapping} conflicts with this
     */
    public boolean same(KeyMapping binding) {
        if (getKeyConflictContext().conflicts(binding.getKeyConflictContext()) || binding.getKeyConflictContext().conflicts(getKeyConflictContext())) {
            net.neoforged.neoforge.client.settings.KeyModifier keyModifier = getKeyModifier();
            net.neoforged.neoforge.client.settings.KeyModifier otherKeyModifier = binding.getKeyModifier();
            if (keyModifier.matches(binding.getKey()) || otherKeyModifier.matches(getKey())) {
                return true;
            } else if (getKey().equals(binding.getKey())) {
                // IN_GAME key contexts have a conflict when at least one modifier is NONE.
                // For example: If you hold shift to crouch, you can still press E to open your inventory. This means that a Shift+E hotkey is in conflict with E.
                // GUI and other key contexts do not have this limitation.
                return keyModifier == otherKeyModifier ||
                    (getKeyConflictContext().conflicts(net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME) &&
                    (keyModifier == net.neoforged.neoforge.client.settings.KeyModifier.NONE || otherKeyModifier == net.neoforged.neoforge.client.settings.KeyModifier.NONE));
            }
        }
        return this.key.equals(binding.key);
    }

    public boolean isUnbound() {
        return this.key.equals(InputConstants.UNKNOWN);
    }

    public boolean matches(int keysym, int scancode) {
        return keysym == InputConstants.UNKNOWN.getValue()
            ? this.key.getType() == InputConstants.Type.SCANCODE && this.key.getValue() == scancode
            : this.key.getType() == InputConstants.Type.KEYSYM && this.key.getValue() == keysym;
    }

    /**
     * Returns {@code true} if the {@code KeyMapping} is set to a mouse key and the key matches.
     */
    public boolean matchesMouse(int key) {
        return this.key.getType() == InputConstants.Type.MOUSE && this.key.getValue() == key;
    }

    public Component getTranslatedKeyMessage() {
        return getKeyModifier().getCombinedName(key, () -> {
        return this.key.getDisplayName();
        });
    }

    public boolean isDefault() {
        return this.key.equals(this.defaultKey) && getKeyModifier() == getDefaultKeyModifier();
    }

    public String saveString() {
        return this.key.getName();
    }

    public void setDown(boolean value) {
        this.isDown = value;
    }

    // Neo: Injected Key Mapping controls
    private net.neoforged.neoforge.client.settings.KeyModifier keyModifierDefault = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
    private net.neoforged.neoforge.client.settings.KeyModifier keyModifier = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
    private net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext = net.neoforged.neoforge.client.settings.KeyConflictContext.UNIVERSAL;

    // Neo: Injected Key Mapping constructors to assist modders
    /**
     * Convenience constructor for creating KeyBindings with keyConflictContext set.
     */
    public KeyMapping(String description, net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext, final InputConstants.Type inputType, final int keyCode, String category) {
         this(description, keyConflictContext, inputType.getOrCreate(keyCode), category);
    }

    /**
     * Convenience constructor for creating KeyBindings with keyConflictContext set.
     */
    public KeyMapping(String description, net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext, InputConstants.Key keyCode, String category) {
         this(description, keyConflictContext, net.neoforged.neoforge.client.settings.KeyModifier.NONE, keyCode, category);
    }

    /**
     * Convenience constructor for creating KeyBindings with keyConflictContext and keyModifier set.
     */
    public KeyMapping(String description, net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext, net.neoforged.neoforge.client.settings.KeyModifier keyModifier, final InputConstants.Type inputType, final int keyCode, String category) {
         this(description, keyConflictContext, keyModifier, inputType.getOrCreate(keyCode), category);
    }

    /**
     * Convenience constructor for creating KeyBindings with keyConflictContext and keyModifier set.
     */
    public KeyMapping(String description, net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext, net.neoforged.neoforge.client.settings.KeyModifier keyModifier, InputConstants.Key keyCode, String category) {
        this.name = description;
        this.key = keyCode;
        this.defaultKey = keyCode;
        this.category = category;
        this.keyConflictContext = keyConflictContext;
        this.keyModifier = keyModifier;
        this.keyModifierDefault = keyModifier;
        if (this.keyModifier.matches(keyCode))
            this.keyModifier = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
        ALL.put(description, this);
        MAP.put(keyCode, this);
        CATEGORIES.add(category);
    }

    // Neo: Implemented IKeyMappingExtension methods into vanilla
    @Override
    public InputConstants.Key getKey() {
         return this.key;
    }

    @Override
    public void setKeyConflictContext(net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext) {
         this.keyConflictContext = keyConflictContext;
    }

    @Override
    public net.neoforged.neoforge.client.settings.IKeyConflictContext getKeyConflictContext() {
         return keyConflictContext;
    }

    @Override
    public net.neoforged.neoforge.client.settings.KeyModifier getDefaultKeyModifier() {
         return keyModifierDefault;
    }

    @Override
    public net.neoforged.neoforge.client.settings.KeyModifier getKeyModifier() {
         return keyModifier;
    }

    @Override
    public void setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier keyModifier, InputConstants.Key keyCode) {
         this.key = keyCode;
         if (keyModifier.matches(keyCode))
              keyModifier = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
         MAP.remove(this);
         this.keyModifier = keyModifier;
         MAP.put(keyCode, this);
    }
}
