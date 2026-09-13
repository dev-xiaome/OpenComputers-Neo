# OpenComputers Neo 移植约定

源项目：`mod_src/OpenComputers-master-MC1.7.10`（OpenComputers 1.8.10 / MC 1.7.10 / Forge 10.13.4，Scala 2.11 + Java）。
目标：Minecraft 1.21.1 / NeoForge 21.1.244，Scala 2.13.14 + Java 21。

## 基本信息

- mod id：`opencomputers_neo`
- 主类：`li.cil.oc.OpenComputersNeo`（Scala class + `@Mod`）
- 资源命名空间：`opencomputers_neo`（原 `opencomputers`）
- Java 包名保持 `li.cil.oc.*` 不变
- 资源目录：客户端资源 → `src/main/resources/assets/opencomputers_neo/`；数据侧 → `src/main/resources/data/opencomputers_neo/`
- 配置目录：`config/opencomputers_neo.conf`（typesafe config，见 `Settings.scala`）

## 语言与构建

- 实现层保持 **Scala 2.13**（原 2.11）：`src/main/scala`
- API 层保持 Java 21：`src/main/java`
- `compileScala` 依赖 `compileJava`；因此 **Java 代码不得引用 Scala 代码**
- 移植期间通过 `gradle.properties` 的 `scala_ported_packages` 控制只编译已完成移植的包，
  保证任何时刻工程都可编译、可启动。完成一个包就把它加进去。
- Scala 2.13 迁移要点（已由 `tools/scala213-migrate.ps1` 批量处理）：
  - 过程语法 `def foo() { ... }` → `def foo(): Unit = { ... }`
  - `scala.collection.convert.WrapAsScala._` → `scala.jdk.CollectionConverters._`
  - `scala.collection.JavaConversions._` / `JavaConverters._` → `scala.jdk.CollectionConverters._`
    （旧名字 `mapAsScalaMap/asScalaBuffer/...` 由 `li.cil.oc.package` 包对象提供兼容实现）
  - `xs: _*` 需要 `immutable.Seq` → `xs.toSeq: _*`
  - `scala.compat.Platform.EOL` → `System.lineSeparator()`
  - `mutable.MutableList` → `mutable.ArrayBuffer`

## 核心类名/包名映射（1.7.10 → 1.21.1）

| 1.7.10 | 1.21.1 |
| --- | --- |
| `net.minecraft.item.ItemStack` | `net.minecraft.world.item.ItemStack` |
| `net.minecraft.item.Item` | `net.minecraft.world.item.Item` |
| `net.minecraft.item.ItemBlock` | `net.minecraft.world.item.BlockItem` |
| `net.minecraft.item.EnumRarity` | `net.minecraft.world.item.Rarity` |
| `net.minecraft.item.EnumAction` | `net.minecraft.world.item.UseAnim` |
| `net.minecraft.block.Block` | `net.minecraft.world.level.block.Block` |
| `net.minecraft.block.material.Material` | `net.minecraft.world.level.material.Material`（多已改为 `BlockBehaviour.Properties`） |
| `net.minecraft.tileentity.TileEntity` | `net.minecraft.world.level.block.entity.BlockEntity` |
| `net.minecraft.world.World` | `net.minecraft.world.level.Level` |
| `net.minecraft.world.WorldServer` | `net.minecraft.server.level.ServerLevel` |
| `net.minecraft.world.IBlockAccess` | `net.minecraft.world.level.BlockGetter` |
| `net.minecraft.entity.Entity` | `net.minecraft.world.entity.Entity` |
| `net.minecraft.entity.EntityLivingBase` | `net.minecraft.world.entity.LivingEntity` |
| `net.minecraft.entity.player.EntityPlayer` | `net.minecraft.world.entity.player.Player` |
| `net.minecraft.entity.player.EntityPlayerMP` | `net.minecraft.server.level.ServerPlayer` |
| `net.minecraft.entity.player.InventoryPlayer` | `net.minecraft.world.entity.player.Inventory` |
| `net.minecraft.entity.item.EntityItem` | `net.minecraft.world.entity.item.ItemEntity` |
| `net.minecraft.nbt.NBTTagCompound` | `net.minecraft.nbt.CompoundTag` |
| `net.minecraft.nbt.NBTTagList` | `net.minecraft.nbt.ListTag` |
| `net.minecraft.nbt.NBTTagString` | `net.minecraft.nbt.StringTag` |
| `net.minecraft.nbt.NBTTagIntArray` | `net.minecraft.nbt.IntArrayTag` |
| `net.minecraft.nbt.NBTBase` | `net.minecraft.nbt.Tag` |
| `net.minecraft.nbt.CompressedStreamTools` | `net.minecraft.nbt.NbtIo` |
| `net.minecraft.util.Vec3` | `net.minecraft.world.phys.Vec3` |
| `net.minecraft.util.AxisAlignedBB` | `net.minecraft.world.phys.AABB` |
| `net.minecraft.util.MovingObjectPosition` | `net.minecraft.world.phys.HitResult` |
| `net.minecraft.util.MathHelper` | `net.minecraft.util.Mth` |
| `net.minecraft.util.ResourceLocation` | `net.minecraft.resources.ResourceLocation` |
| `net.minecraft.util.EnumFacing` | `net.minecraft.core.Direction` |
| `net.minecraft.util.IChatComponent` | `net.minecraft.network.chat.Component` |
| `net.minecraft.util.ChatComponentText` | `net.minecraft.network.chat.Component.literal(...)` |
| `net.minecraft.util.ChatComponentTranslation` | `net.minecraft.network.chat.Component.translatable(...)` |
| `net.minecraft.util.EnumChatFormatting` | `net.minecraft.ChatFormatting` |
| `net.minecraft.util.StatCollector` | `net.minecraft.locale.Language` / `Component#getString` |
| `net.minecraft.util.DamageSource` | `net.minecraft.world.damagesource.DamageSource` |
| `net.minecraft.util.ChunkCoordinates` | `net.minecraft.core.BlockPos` |
| `net.minecraft.creativetab.CreativeTabs` | `net.minecraft.world.item.CreativeModeTab` |
| `net.minecraft.inventory.IInventory` | `net.neoforged.neoforge.items.IItemHandler` |
| `net.minecraft.inventory.ISidedInventory` | `net.neoforged.neoforge.items.IItemHandler` |
| `net.minecraft.inventory.Slot` | `net.minecraft.world.inventory.Slot` |
| `net.minecraft.inventory.Container` | `net.minecraft.world.inventory.AbstractContainerMenu` |
| `net.minecraftforge.common.util.ForgeDirection` | `net.minecraft.core.Direction` |
| `net.minecraftforge.common.MinecraftForge` | `net.neoforged.neoforge.common.NeoForge` |
| `net.minecraftforge.oredict.OreDictionary` | 物品/方块 tag（`net.minecraft.tags.TagKey`，`c:` 命名空间） |
| `net.minecraftforge.fluids.*` | `net.neoforged.neoforge.fluids.*` |
| `net.minecraftforge.common.DimensionManager` | `MinecraftServer#getLevel` / `ServerLevel` |
| `cpw.mods.fml.common.Loader` | `net.neoforged.fml.ModList` |
| `cpw.mods.fml.relauncher.Side` | `net.neoforged.api.distmarker.Dist` |
| `cpw.mods.fml.relauncher.SideOnly` | **不再使用**（NeoForge 的 `RuntimeDistCleaner` 对类级 `@OnlyIn` 会直接抛异常） |
| `cpw.mods.fml.common.eventhandler.SubscribeEvent` | `net.neoforged.bus.api.SubscribeEvent` |
| `cpw.mods.fml.common.eventhandler.Event` | `net.neoforged.bus.api.Event` |
| `cpw.mods.fml.common.eventhandler.Cancelable` | `implements net.neoforged.bus.api.ICancellableEvent` |
| `@Mod` + `@EventHandler` 阶段事件 | `@Mod` 构造 + `FMLCommonSetupEvent` / `FMLClientSetupEvent` / `FMLDedicatedServerSetupEvent` |
| `GameRegistry.registerBlock/Item` | `DeferredRegister` + `RegisterEvent` |
| `FMLPreInitializationEvent.getSuggestedConfigurationFile` | `FMLPaths.CONFIGDIR` |
| `net.minecraft.client.renderer.Tessellator` / `RenderBlocks` / `IIcon` | `PoseStack` + `VertexConsumer` + 烘焙模型（`ModelLayerLocation`） |
| `TileEntitySpecialRenderer` | `BlockEntityRenderer<T>` + `EntityBlock`/`BlockEntityTicker` |
| `IGuiHandler` | `MenuType` + `AbstractContainerScreen` + `IPayloadRegistrar` |

## 能力（Capability）查询

1.21.1 的 `BlockEntity` **没有** `getCapability`，统一改为：

```scala
Capabilities.ItemHandler.BLOCK.getCapability(level, pos, blockState, blockEntity, side)
Capabilities.FluidHandler.BLOCK.getCapability(level, pos, blockState, blockEntity, side)
```

## 物品 NBT（重要）

1.21.1 的 `ItemStack` **没有** `getTag()/setTag()/hasTag()`，改为数据组件。
本项目注册了统一组件 `opencomputers_neo:nbt`（`li.cil.oc.common.DataComponents.NBT`）：

- **Java 代码**：使用 `li.cil.oc.util.ItemNBT`（`get` / `getOrCreate` / `has` / `set`）
- **Scala 代码**：`li.cil.oc` 包对象已内置隐式类，`stack.getTag()` / `stack.hasTag()` / `stack.setTag(tag)`
  可直接使用（`getTag()` 不存在时返回 `null`）。

`CompoundTag` 方法映射：

| 1.7.10 | 1.21.1 |
| --- | --- |
| `hasKey(k)` | `contains(k)` |
| `setTag(k, v)` | `put(k, v)` |
| `getTag(k)` | `get(k)` |
| `getCompoundTag(k)` | `getCompound(k)` |
| `getTagList(k, t)` | `getList(k, t)` |
| `setInteger/setFloat/setDouble/setString/setBoolean/setLong/setShort/setByte/setByteArray/setIntArray` | `putInt/putFloat/putDouble/putString/putBoolean/putLong/putShort/putByte/putByteArray/putIntArray` |
| `removeTag(k)` | `remove(k)` |
| `new NBTTagList()` / `tagCount()` / `appendTag(x)` / `getCompoundTagAt(i)` | `new ListTag()` / `size()` / `add(x)` / `getCompound(i)` |
| `new NBTTagString(s)` | `StringTag.valueOf(s)` |
| `new NBTTagInt(i)` 等 | `IntTag.valueOf(i)` 等 |
| `ItemStack.writeToNBT(nbt)` / `readFromNBT(nbt)` | `stack.save(provider, nbt)` / `ItemStack.parseOptional(provider, nbt)` |

## 方块实体的生命周期

| 1.7.10 | 1.21.1 |
| --- | --- |
| 构造 `new TileEntity()` + `Block.createTileEntity` | `BlockEntity(BlockEntityType<?>, BlockPos, BlockState)`，由 `BlockEntityType` 注册 |
| `readFromNBT(nbt)` | `loadAdditional(CompoundTag, HolderLookup.Provider)` |
| `writeToNBT(nbt)` | `saveAdditional(CompoundTag, HolderLookup.Provider)` |
| `updateEntity()` | `BlockEntityTicker` / `EntityBlock#getTicker` |
| `invalidate()` | `setRemoved()` |
| `onChunkUnload()` | `onChunkUnloaded()` |
| `worldObj` / `xCoord` / `yCoord` / `zCoord` | `level` / `worldPosition` |
| `getBlockMetadata()` | `getBlockState()` 的属性（BlockState 属性） |
| `markDirty()` | `setChanged()` |

## 网络

`FMLEventChannel`/`PacketHandler` 改为 NeoForge 1.21.1 的
`RegisterPayloadHandlersEvent` + `CustomPacketPayload`（Codec/StreamCodec）+ `IPayloadContext`。

## 架构层

原 `li.cil.oc.common.asm` 的 ASM 变换 + `TransformerLoader` coremod 全部移除，
改为 NeoForge 原生能力/Mixin（如确有必要）。`SimpleComponent*` 模板类同步删除。

## 移植顺序（每步都必须可编译）

1. 骨架：主类、创造模式标签页、配置 ✅
2. `li.cil.oc.api`（Java 接口层）✅
3. `li.cil.oc.util`（工具层）🔄
4. `li.cil.oc.common`（方块 / 物品 / 注册 / 网络包）
5. `li.cil.oc.server`（组件 / 机器 / 文件系统 / Lua）
6. `li.cil.oc.client`（渲染 / GUI）
7. `li.cil.oc.integration`（其它模组兼容，最后做或省略；`integration/opencomputers` 是 OC 自身驱动，必须移植）
