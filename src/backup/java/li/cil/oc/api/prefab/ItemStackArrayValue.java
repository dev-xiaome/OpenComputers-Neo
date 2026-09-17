package li.cil.oc.api.prefab;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.util.RegistryAccessHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.TreeMap;

/**
 * 把一组物品堆暴露给 Lua 的 {@link li.cil.oc.api.machine.Value} 实现。
 * <br>
 * <b>1.21.1 移植说明</b>：
 * <ul>
 * <li>{@code ItemStack#writeToNBT}/{@code loadItemStackFromNBT} 已被
 * {@code ItemStack#save(HolderLookup.Provider, Tag)} /
 * {@code ItemStack#parseOptional(HolderLookup.Provider, CompoundTag)} 取代，
 * 两者都需要注册表上下文，而 {@link li.cil.oc.api.machine.Value#load}/{@code save}
 * 的签名里没有这个上下文，因此这里通过 {@link RegistryAccessHelper} 取真实的注册表。</li>
 * <li><b>不要退回 {@link RegistryAccess#EMPTY}</b>：1.21.1 的 {@code ItemStack#save} 内部要先用
 * {@code registries.getOrThrow(Registries.ITEM)} 取物品 id，空访问器上取不到，
 * 每个物品都会被静默写成空标签 {@code {"item": {}}}，读档时全部变成空气。</li>
 * <li>NBT 方法名同步为新版：{@code hasKey → contains}、{@code getTagList → getList}、
 * {@code tagCount → size}、{@code getCompoundTagAt → getCompound}、
 * {@code hasNoTags → isEmpty}、{@code setTag → put}、{@code setInteger → putInt}。</li>
 * <li>数组中的空槽位仍然用 {@code null} 表示（与 1.7.10 一致），对外转换成空表。</li>
 * </ul>
 */
public class ItemStackArrayValue extends AbstractValue {

    /**
     * 用于物品编解码的注册表上下文。
     * <br>
     * {@code Value#load/save} 不提供 {@code HolderLookup.Provider}，因此这里使用
     * {@link RegistryAccessHelper} 提供的「服务端 → 客户端 → 缓存」真实注册表；
     * 详见该类的说明。
     */
    private static HolderLookup.Provider registries() {
        return RegistryAccessHelper.getOrEmpty();
    }

    private ItemStack[] array = null;
    private int iteratorIndex;

    private static final String ARRAY_KEY = "Array";
    private static final String INDEX_KEY = "Index";

    private static final HashMap<Object, Object> emptyMap = new HashMap<Object, Object>();

    public ItemStackArrayValue(ItemStack[] arr) {
        if (arr != null) {
            this.array = new ItemStack[arr.length];
            for (int i = 0; i < arr.length; i++) {
                this.array[i] = arr[i] != null ? arr[i].copy() : null;
            }
        }
        this.iteratorIndex = 0;
    }

    public ItemStackArrayValue() {
        this(null);
    }

    @Override
    public Object[] call(Context context, Arguments arguments) {
        if (this.array == null)
            return null;
        if (this.iteratorIndex >= this.array.length)
            return null;
        final int index = this.iteratorIndex++;
        final ItemStack stack = this.array[index];
        // 空槽位对外暴露成空表，而不是把 ItemStack.EMPTY 交给 Lua 转换。
        return new Object[]{stack != null ? stack : emptyMap};
    }

    @Override
    public Object apply(Context context, Arguments arguments) {
        if (arguments.count() == 0 || this.array == null)
            return null;
        if (arguments.isInteger(0)) {//index access
            int luaIndex = arguments.checkInteger(0);
            if (luaIndex > this.array.length || luaIndex < 1) {
                return null;
            }
            return this.array[luaIndex - 1];
        }
        if (arguments.isString(0)) {
            String arg = arguments.checkString(0);
            if (arg.equals("n")) {
                return this.array.length;
            }
        }
        return null;
    }

    @Override
    public void load(CompoundTag nbt) {
        // 1.21.1：hasKey(key, type) → contains(key, type)，类型常量改用 Tag.TAG_*。
        if (nbt.contains(ARRAY_KEY, Tag.TAG_LIST)) {
            final ListTag tagList = nbt.getList(ARRAY_KEY, Tag.TAG_COMPOUND);
            this.array = new ItemStack[tagList.size()];
            for (int i = 0; i < tagList.size(); ++i) {
                final CompoundTag el = tagList.getCompound(i);
                if (el.isEmpty()) {
                    // 空标签表示 1.7.10 里用 null 标记的“空槽位”。
                    this.array[i] = null;
                } else {
                    this.array[i] = ItemStack.parseOptional(registries(), el);
                }
            }
        } else {
            this.array = null;
        }
        this.iteratorIndex = nbt.getInt(INDEX_KEY);
    }

    @Override
    public void save(CompoundTag nbt) {
        if (this.array != null) {
            final ListTag tagList = new ListTag();
            for (ItemStack stack : this.array) {
                if (stack != null && !stack.isEmpty()) {
                    // ItemStack.EMPTY 不能序列化（会抛 IllegalStateException），
                    // 因此空槽位写一个空 CompoundTag 占位。
                    tagList.add(stack.save(registries(), new CompoundTag()));
                } else {
                    tagList.add(new CompoundTag());
                }
            }
            nbt.put(ARRAY_KEY, tagList);
        }
        nbt.putInt(INDEX_KEY, iteratorIndex);
    }

    @Callback(doc = "function():nil -- Reset the iterator index so that the next call will return the first element.")
    public Object[] reset(Context context, Arguments arguments) throws Exception {
        this.iteratorIndex = 0;
        return null;
    }

    @Callback(doc = "function():number -- Returns the number of elements in the this.array.")
    public Object[] count(Context context, Arguments arguments) throws Exception {
        return new Object[]{this.array != null ? this.array.length : 0};
    }

    @Callback(doc = "function():table -- Returns ALL the stack in the this.array. Memory intensive.")
    public Object[] getAll(Context context, Arguments arguments) throws Exception {
        TreeMap<Integer, Object> map = new TreeMap<Integer, Object>();
        if (this.array != null) {
            for (int i = 0; i < this.array.length; i++) {
                map.put(i, this.array[i] != null ? this.array[i] : emptyMap);
            }
        }
        return new Object[]{map};
    }

    public String toString() {
        return "{ItemStack Array}";
    }
}
