package li.cil.oc.client.renderer.block

import java.util
import li.cil.oc.client.Textures
import li.cil.oc.common.blockentity
import net.minecraft.client.Minecraft
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraft.util.RandomSource
import net.minecraft.client.renderer.RenderType
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent
import net.neoforged.neoforge.client.model.data.{ModelData, ModelProperty}

import scala.jdk.CollectionConverters._
import scala.collection.mutable

object NetSplitterModel extends SmartBlockModelBase {
  val NET_SPLITTER_PROPERTY = new ModelProperty[blockentity.NetSplitter]()

  override def getOverrides: ItemOverrides = ItemOverride

  override def getQuads(state: BlockState, side: Direction, rand: RandomSource, data: ModelData, renderType: RenderType): util.List[BakedQuad] = {
    val faces = mutable.ArrayBuffer.empty[BakedQuad]
    faces ++= baseModel
    Option(data.get(NET_SPLITTER_PROPERTY)) match {
      case Some(t) => addSideQuads(faces, Direction.values().map(t.isSideOpen))
      case _ => addSideQuads(faces, Direction.values().map(_ => false))
    }
    faces.asJava
  }

  private def getSprite(location: ResourceLocation, atlas: Option[TextureAtlas]): TextureAtlasSprite = atlas match {
    case Some(atls) => atls.getSprite(location)
    case None => Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(location)
  }

  protected def splitterTexture(atlas: Option[TextureAtlas]) = Array(
    getSprite(Textures.Block.NetSplitterTop,  atlas),
    getSprite(Textures.Block.NetSplitterTop,  atlas),
    getSprite(Textures.Block.NetSplitterSide, atlas),
    getSprite(Textures.Block.NetSplitterSide, atlas),
    getSprite(Textures.Block.NetSplitterSide, atlas),
    getSprite(Textures.Block.NetSplitterSide, atlas)
  )

  protected def GenerateBaseModel(atlas: Option[TextureAtlas]) = {
    val faces = mutable.ArrayBuffer.empty[BakedQuad]
    faces ++= bakeQuads(makeBox(new Vec3(0/16f,  0/16f,  5/16f),  new Vec3(5/16f,  5/16f,  11/16f)), splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(11/16f, 0/16f,  5/16f),  new Vec3(16/16f, 5/16f,  11/16f)), splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(5/16f,  0/16f,  0/16f),  new Vec3(11/16f, 5/16f,  5/16f)),  splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(5/16f,  0/16f,  11/16f), new Vec3(11/16f, 5/16f,  16/16f)), splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(0/16f,  0/16f,  0/16f),  new Vec3(5/16f,  16/16f, 5/16f)),  splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(11/16f, 0/16f,  0/16f),  new Vec3(16/16f, 16/16f, 5/16f)),  splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(0/16f,  0/16f,  11/16f), new Vec3(5/16f,  16/16f, 16/16f)), splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(11/16f, 0/16f,  11/16f), new Vec3(16/16f, 16/16f, 16/16f)), splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(0/16f,  11/16f, 5/16f),  new Vec3(5/16f,  16/16f, 11/16f)), splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(11/16f, 11/16f, 5/16f),  new Vec3(16/16f, 16/16f, 11/16f)), splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(5/16f,  11/16f, 0/16f),  new Vec3(11/16f, 16/16f, 5/16f)),  splitterTexture(atlas), None)
    faces ++= bakeQuads(makeBox(new Vec3(5/16f,  11/16f, 11/16f), new Vec3(11/16f, 16/16f, 16/16f)), splitterTexture(atlas), None)
    faces.toArray
  }

  protected var BaseModel = Array.empty[BakedQuad]

  private def baseModel: Array[BakedQuad] =
    if (BaseModel.nonEmpty) BaseModel else GenerateBaseModel(None)

  def initBaseModel(atlas: TextureAtlas): Unit = {
    if (atlas.location().equals(InventoryMenu.BLOCK_ATLAS)) BaseModel = GenerateBaseModel(Some(atlas))
  }

  @SubscribeEvent
  def onTextureAtlasStitched(event: TextureAtlasStitchedEvent): Unit = {
    if (event.getAtlas.location() == InventoryMenu.BLOCK_ATLAS) {
      val blockAtlas = event.getAtlas
      if (blockAtlas != null) {
        initBaseModel(blockAtlas)
      }
    }
  }

  protected def addSideQuads(faces: mutable.ArrayBuffer[BakedQuad], openSides: Array[Boolean]): Unit = {
    val down  = openSides(Direction.DOWN.ordinal())
    faces ++= bakeQuads(makeBox(new Vec3(5/16f, if (down) 0/16f else 2/16f, 5/16f),   new Vec3(11/16f, 5/16f, 11/16f)),  splitterTexture(None), None)
    val up    = openSides(Direction.UP.ordinal())
    faces ++= bakeQuads(makeBox(new Vec3(5/16f, 11/16f, 5/16f), new Vec3(11/16f, if (up) 16/16f else 14f/16f, 11/16f)),  splitterTexture(None), None)
    val north = openSides(Direction.NORTH.ordinal())
    faces ++= bakeQuads(makeBox(new Vec3(5/16f, 5/16f, if (north) 0/16f else 2/16f),  new Vec3(11/16f, 11/16f, 5/16f)),  splitterTexture(None), None)
    val south = openSides(Direction.SOUTH.ordinal())
    faces ++= bakeQuads(makeBox(new Vec3(5/16f, 5/16f, 11/16f), new Vec3(11/16f, 11/16f, if (south) 16/16f else 14/16f)), splitterTexture(None), None)
    val west  = openSides(Direction.WEST.ordinal())
    faces ++= bakeQuads(makeBox(new Vec3(if (west) 0/16f else 2/16f, 5/16f, 5/16f),   new Vec3(5/16f, 11/16f, 11/16f)),  splitterTexture(None), None)
    val east  = openSides(Direction.EAST.ordinal())
    faces ++= bakeQuads(makeBox(new Vec3(11/16f, 5/16f, 5/16f), new Vec3(if (east) 16/16f else 14/16f, 11/16f, 11/16f)),  splitterTexture(None), None)
  }

  object ItemModel extends SmartBlockModelBase {
    override def getQuads(state: BlockState, side: Direction, rand: RandomSource, data: ModelData, renderType: RenderType): util.List[BakedQuad] = {
      val faces = mutable.ArrayBuffer.empty[BakedQuad]
      faces ++= baseModel
      addSideQuads(faces, Direction.values().map(_ => false))
      faces.asJava
    }
  }

  object ItemOverride extends ItemOverrides {
    override def resolve(originalModel: BakedModel, stack: ItemStack, world: ClientLevel, entity: LivingEntity, seed: Int): BakedModel =
      ItemModel
  }
}
