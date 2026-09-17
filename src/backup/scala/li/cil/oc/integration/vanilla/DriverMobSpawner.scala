package li.cil.oc.integration.vanilla

import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.SpawnData
import net.minecraft.world.level.block.entity.SpawnerBlockEntity

/**
 * 刷怪笼（`SpawnerBlockEntity`）驱动。
 *
 * 1.21.1 迁移要点：
 *  - `TileEntityMobSpawner` → `SpawnerBlockEntity`；`func_145881_a` → `getSpawner`（`BaseSpawner`）
 *  - `MobSpawnerBaseLogic#getEntityNameToSpawn` 已移除；1.21.1 的刷怪配置保存在
 *    私有的 `nextSpawnData` 字段（`SpawnData`）中，因此通过 [[VanillaReflection]] 读取，
 *    再从 `entity.id` 解析出 `EntityType` 并返回其显示名。
 *
 * TODO(port): 1.7.10 返回的是刷怪实体的名称字符串，1.21.1 改为返回
 * `EntityType#getDescription` 的本地化文本；解析失败时退化为原始的注册名。
 */
object DriverMobSpawner extends DriverSidedTileEntity {
  override def getTileEntityClass: Class[_] = classOf[SpawnerBlockEntity]

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(new BlockPos(x, y, z)).asInstanceOf[SpawnerBlockEntity])

  final class Environment(entity: SpawnerBlockEntity) extends ManagedTileEntityEnvironment[SpawnerBlockEntity](entity, "mob_spawner") with NamedBlock {
    override def preferredName = "mob_spawner"

    override def priority = 0

    @Callback(doc = "function():string -- Get the name of the entity that is being spawned by this spawner.")
    def getSpawningMobName(context: Context, args: Arguments): Array[AnyRef] = {
      result(spawningMobName)
    }

    private def spawningMobName: String = {
      val spawner = tileEntity.getSpawner
      if (spawner == null) null
      else {
        val spawnData = VanillaReflection.obj[SpawnData](spawner, "nextSpawnData")
        if (spawnData == null) null
        else {
          val id = spawnData.getEntityToSpawn.getString("id")
          if (id == null || id.isEmpty) null
          else {
            val entityType = EntityType.byString(id).orElse(null)
            if (entityType == null) id else entityType.getDescription.getString
          }
        }
      }
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (stack != null && !stack.isEmpty &&
        net.minecraft.world.level.block.Block.byItem(stack.getItem) == net.minecraft.world.level.block.Blocks.SPAWNER)
        classOf[Environment]
      else null
    }
  }

}
