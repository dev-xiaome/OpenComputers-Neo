
param(
    [Parameter(Mandatory = $true)][string]$Root,
    [string[]]$Include = @('*.java', '*.scala')
)

$importMap = [ordered]@{
    'net.minecraft.item.ItemStack'                             = 'net.minecraft.world.item.ItemStack'
    'net.minecraft.item.Item'                                  = 'net.minecraft.world.item.Item'
    'net.minecraft.item.ItemBlock'                             = 'net.minecraft.world.item.BlockItem'
    'net.minecraft.item.EnumRarity'                            = 'net.minecraft.world.item.Rarity'
    'net.minecraft.item.EnumAction'                            = 'net.minecraft.world.item.UseAnim'
    'net.minecraft.item.ItemArmor'                              = 'net.minecraft.world.item.ArmorItem'
    'net.minecraft.item.ItemRecord'                             = 'net.minecraft.world.item.RecordItem'
    'net.minecraft.item.ItemMap'                                = 'net.minecraft.world.item.MapItem'
    'net.minecraft.item.ItemBucket'                             = 'net.minecraft.world.item.BucketItem'
    'net.minecraft.block.Block'                                 = 'net.minecraft.world.level.block.Block'
    'net.minecraft.block.BlockBasePressurePlate'                = 'net.minecraft.world.level.block.PressurePlateBlock'
    'net.minecraft.block.material.Material'                     = 'net.minecraft.world.level.material.Material'
    'net.minecraft.tileentity.TileEntity'                       = 'net.minecraft.world.level.block.entity.BlockEntity'
    'net.minecraft.world.World'                                 = 'net.minecraft.world.level.Level'
    'net.minecraft.world.WorldServer'                           = 'net.minecraft.server.level.ServerLevel'
    'net.minecraft.world.IBlockAccess'                          = 'net.minecraft.world.level.BlockGetter'
    'net.minecraft.world.ChunkCoordIntPair'                     = 'net.minecraft.world.level.ChunkPos'
    'net.minecraft.entity.Entity'                               = 'net.minecraft.world.entity.Entity'
    'net.minecraft.entity.EntityLivingBase'                     = 'net.minecraft.world.entity.LivingEntity'
    'net.minecraft.entity.EntityLiving'                         = 'net.minecraft.world.entity.LivingEntity'
    'net.minecraft.entity.player.EntityPlayer'                  = 'net.minecraft.world.entity.player.Player'
    'net.minecraft.entity.player.EntityPlayerMP'                = 'net.minecraft.server.level.ServerPlayer'
    'net.minecraft.entity.player.InventoryPlayer'               = 'net.minecraft.world.entity.player.Inventory'
    'net.minecraft.entity.item.EntityItem'                      = 'net.minecraft.world.entity.item.ItemEntity'
    'net.minecraft.nbt.NBTTagCompound'                          = 'net.minecraft.nbt.CompoundTag'
    'net.minecraft.nbt.NBTTagList'                              = 'net.minecraft.nbt.ListTag'
    'net.minecraft.nbt.NBTTagString'                            = 'net.minecraft.nbt.StringTag'
    'net.minecraft.nbt.NBTTagIntArray'                          = 'net.minecraft.nbt.IntArrayTag'
    'net.minecraft.nbt.NBTTagByteArray'                         = 'net.minecraft.nbt.ByteArrayTag'
    'net.minecraft.nbt.NBTTagInt'                               = 'net.minecraft.nbt.IntTag'
    'net.minecraft.nbt.NBTTagFloat'                             = 'net.minecraft.nbt.FloatTag'
    'net.minecraft.nbt.NBTTagDouble'                            = 'net.minecraft.nbt.DoubleTag'
    'net.minecraft.nbt.NBTTagLong'                              = 'net.minecraft.nbt.LongTag'
    'net.minecraft.nbt.NBTTagShort'                             = 'net.minecraft.nbt.ShortTag'
    'net.minecraft.nbt.NBTTagByte'                              = 'net.minecraft.nbt.ByteTag'
    'net.minecraft.nbt.NBTBase'                                 = 'net.minecraft.nbt.Tag'
    'net.minecraft.nbt.CompressedStreamTools'                   = 'net.minecraft.nbt.NbtIo'
    'net.minecraft.util.Vec3'                                   = 'net.minecraft.world.phys.Vec3'
    'net.minecraft.util.AxisAlignedBB'                          = 'net.minecraft.world.phys.AABB'
    'net.minecraft.util.MovingObjectPosition'                   = 'net.minecraft.world.phys.HitResult'
    'net.minecraft.util.MathHelper'                             = 'net.minecraft.util.Mth'
    'net.minecraft.util.ResourceLocation'                       = 'net.minecraft.resources.ResourceLocation'
    'net.minecraft.util.EnumFacing'                             = 'net.minecraft.core.Direction'
    'net.minecraft.util.IChatComponent'                         = 'net.minecraft.network.chat.Component'
    'net.minecraft.util.ChatComponentText'                      = 'net.minecraft.network.chat.Component'
    'net.minecraft.util.ChatComponentTranslation'               = 'net.minecraft.network.chat.Component'
    'net.minecraft.util.EnumChatFormatting'                     = 'net.minecraft.ChatFormatting'
    'net.minecraft.util.DamageSource'                           = 'net.minecraft.world.damagesource.DamageSource'
    'net.minecraft.util.ChunkCoordinates'                       = 'net.minecraft.core.BlockPos'
    'net.minecraft.creativetab.CreativeTabs'                    = 'net.minecraft.world.item.CreativeModeTab'
    'net.minecraft.inventory.Slot'                              = 'net.minecraft.world.inventory.Slot'
    'net.minecraftforge.common.util.ForgeDirection'             = 'net.minecraft.core.Direction'
    'net.minecraftforge.common.MinecraftForge'                  = 'net.neoforged.neoforge.common.NeoForge'
    'net.minecraftforge.fluids.FluidStack'                      = 'net.neoforged.neoforge.fluids.FluidStack'
    'net.minecraftforge.fluids.Fluid'                           = 'net.neoforged.neoforge.fluids.FluidStack'
    'net.minecraftforge.fluids.FluidTank'                       = 'net.neoforged.neoforge.fluids.capability.templates.FluidTank'
    'net.minecraftforge.fluids.IFluidTank'                      = 'net.neoforged.neoforge.fluids.capability.IFluidHandler'
    'net.minecraftforge.fluids.IFluidHandler'                   = 'net.neoforged.neoforge.fluids.capability.IFluidHandler'
    'net.minecraftforge.fluids.IFluidBlock'                     = 'net.neoforged.neoforge.fluids.IFluidBlock'
    'cpw.mods.fml.common.Loader'                                = 'net.neoforged.fml.ModList'
    'cpw.mods.fml.relauncher.Side'                              = 'net.neoforged.api.distmarker.Dist'
    'cpw.mods.fml.relauncher.SideOnly'                          = 'net.neoforged.api.distmarker.OnlyIn'
    'cpw.mods.fml.common.eventhandler.SubscribeEvent'           = 'net.neoforged.bus.api.SubscribeEvent'
    'cpw.mods.fml.common.eventhandler.Event'                    = 'net.neoforged.bus.api.Event'
    'cpw.mods.fml.common.eventhandler.EventPriority'            = 'net.neoforged.bus.api.EventPriority'
    'cpw.mods.fml.common.eventhandler.Cancelable'               = 'net.neoforged.bus.api.ICancellableEvent'
    'cpw.mods.fml.common.Optional'                              = 'net.neoforged.fml.common.Optional'
    'cpw.mods.fml.common.FMLLog'                                = 'org.apache.logging.log4j.LogManager'
    'net.minecraft.command.ICommandSender'                      = 'net.minecraft.commands.CommandSourceStack'
    'net.minecraft.init.Blocks'                                 = 'net.minecraft.world.level.block.Blocks'
    'net.minecraft.init.Items'                                  = 'net.minecraft.world.item.Items'
    'net.minecraft.world.biome.BiomeGenBase'                    = 'net.minecraft.world.level.biome.Biomes'
    'net.minecraft.world.biome.BiomeGenDesert'                  = 'net.minecraft.world.level.biome.Biomes'
    'net.minecraft.stats.Achievement'                           = 'net.minecraft.stats.Stats'
    'net.minecraft.stats.StatBase'                              = 'net.minecraft.stats.Stat'
    'net.minecraft.potion.Potion'                               = 'net.minecraft.world.effect.MobEffect'
    'net.minecraft.potion.PotionEffect'                         = 'net.minecraft.world.effect.MobEffectInstance'
    'net.minecraft.enchantment.Enchantment'                     = 'net.minecraft.world.item.enchantment.Enchantment'
    'net.minecraft.enchantment.EnchantmentHelper'               = 'net.minecraft.world.item.enchantment.EnchantmentHelper'
    'net.minecraftforge.event.world.WorldEvent'                 = 'net.neoforged.neoforge.event.level.LevelEvent'
    'net.minecraftforge.event.world.BlockEvent'                 = 'net.neoforged.neoforge.event.level.BlockEvent'
    'net.minecraftforge.event.world.ChunkEvent'                 = 'net.neoforged.neoforge.event.level.ChunkEvent'
    'net.minecraftforge.event.entity.player.PlayerInteractEvent' = 'net.neoforged.neoforge.event.entity.player.PlayerInteractEvent'
}

$symbolMap = [ordered]@{
    'NBTTagCompound'      = 'CompoundTag'
    'NBTTagList'          = 'ListTag'
    'NBTTagString'        = 'StringTag'
    'NBTTagIntArray'      = 'IntArrayTag'
    'NBTTagByteArray'     = 'ByteArrayTag'
    'NBTTagInt'           = 'IntTag'
    'NBTTagFloat'         = 'FloatTag'
    'NBTTagDouble'        = 'DoubleTag'
    'NBTTagLong'          = 'LongTag'
    'NBTTagShort'         = 'ShortTag'
    'NBTTagByte'          = 'ByteTag'
    'NBTBase'             = 'Tag'
    'CompressedStreamTools' = 'NbtIo'
    'ForgeDirection'      = 'Direction'
    'EntityPlayerMP'      = 'ServerPlayer'
    'EntityPlayer'        = 'Player'
    'InventoryPlayer'     = 'Inventory'
    'EntityLivingBase'    = 'LivingEntity'
    'EntityItem'          = 'ItemEntity'
    'MathHelper'          = 'Mth'
    'AxisAlignedBB'       = 'AABB'
    'MovingObjectPosition' = 'HitResult'
    'EnumFacing'          = 'Direction'
    'IChatComponent'      = 'Component'
    'EnumChatFormatting'  = 'ChatFormatting'
    'TileEntity'          = 'BlockEntity'
    'WorldServer'         = 'ServerLevel'
    'World'               = 'Level'
    'ChunkCoordinates'    = 'BlockPos'
}

$methodMap = [ordered]@{
    'hasTagCompound'  = 'hasTag'
    'setTagCompound'  = 'setTag'
    'getTagCompound'  = 'getTag'
    'hasKey'          = 'contains'
    'setTag'          = 'put'
    'getCompoundTag'  = 'getCompound'
    'getTagList'      = 'getList'
    'setInteger'      = 'putInt'
    'setFloat'        = 'putFloat'
    'setDouble'       = 'putDouble'
    'setString'       = 'putString'
    'setBoolean'      = 'putBoolean'
    'setLong'         = 'putLong'
    'setShort'        = 'putShort'
    'setByte'         = 'putByte'
    'setByteArray'    = 'putByteArray'
    'setIntArray'     = 'putIntArray'
    'removeTag'       = 'remove'
    'tagCount'        = 'size'
    'getCompoundTagAt' = 'getCompound'
    'getStringTagAt'  = 'getString'
    'appendTag'       = 'add'
    'getItemDamage'   = 'getDamageValue'
    'setItemDamage'   = 'setDamageValue'
    'func_150303_d'   = 'getTagType'
}

$files = Get-ChildItem -Path $Root -Recurse -File -Include $Include
$changed = 0
foreach ($f in $files) {
    $text = [System.IO.File]::ReadAllText($f.FullName)
    $orig = $text

    foreach ($k in $importMap.Keys) {
        $pattern = '(?m)^(\s*import\s+)' + [regex]::Escape($k) + '(?=\s*;?\s*$)'
        $text = [regex]::Replace($text, $pattern, ('${1}' + $importMap[$k]))
    }
    foreach ($k in $symbolMap.Keys) {
        $text = [regex]::Replace($text, '\b' + [regex]::Escape($k) + '\b', $symbolMap[$k])
    }

    $text = $text -replace '\bSide\.CLIENT\b', 'Dist.CLIENT'
    $text = $text -replace '\bSide\.SERVER\b', 'Dist.DEDICATED_SERVER'

    foreach ($k in $methodMap.Keys) {
        $text = [regex]::Replace($text, '\.' + [regex]::Escape($k) + '\s*\(', '.' + $methodMap[$k] + '(')
    }

    if ($text -ne $orig) {
        [System.IO.File]::WriteAllText($f.FullName, $text)
        $changed++
    }
}
Write-Output "rewritten files: $changed / $($files.Count)"
