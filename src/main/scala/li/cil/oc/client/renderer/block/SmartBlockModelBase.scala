package li.cil.oc.client.renderer.block

import java.util
import java.util.Collections
import li.cil.oc.client.Textures
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.block.model.ItemTransform
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import net.minecraft.util.RandomSource
import net.minecraft.client.renderer.RenderType
import net.neoforged.neoforge.client.model.data.ModelData
import org.joml.Vector3f

abstract class SmartBlockModelBase extends BakedModel {
  override def getOverrides: ItemOverrides = ItemOverrides.EMPTY

  override def getQuads(state: BlockState, side: Direction, rand: RandomSource): util.List[BakedQuad] =
    Collections.emptyList()

  override def getQuads(state: BlockState, side: Direction, rand: RandomSource, extraData: ModelData, renderType: RenderType): util.List[BakedQuad] =
    getQuads(state, side, rand)

  override def useAmbientOcclusion = true

  override def isGui3d = true

  override def usesBlockLight = true

  override def isCustomRenderer = false

  @Deprecated
  override def getParticleIcon = Textures.getSprite(Textures.Block.GenericTop)

  @Deprecated
  override def getTransforms = DefaultBlockCameraTransforms

  @Deprecated
  protected final val DefaultBlockCameraTransforms = {
    val gui                   = new ItemTransform(new Vector3f(30, 225, 0), new Vector3f(0, 0, 0),        new Vector3f(0.625f, 0.625f, 0.625f), new Vector3f())
    val ground                = new ItemTransform(new Vector3f(0, 0, 0),   new Vector3f(0, 3, 0),         new Vector3f(0.25f, 0.25f, 0.25f),   new Vector3f())
    val fixed                 = new ItemTransform(new Vector3f(0, 0, 0),   new Vector3f(0, 0, 0),         new Vector3f(0.5f, 0.5f, 0.5f),      new Vector3f())
    val thirdperson_righthand = new ItemTransform(new Vector3f(75, 45, 0), new Vector3f(0, 2.5f, 0),     new Vector3f(0.375f, 0.375f, 0.375f), new Vector3f())
    val firstperson_righthand = new ItemTransform(new Vector3f(0, 45, 0),  new Vector3f(0, 0, 0),         new Vector3f(0.40f, 0.40f, 0.40f),   new Vector3f())
    val firstperson_lefthand  = new ItemTransform(new Vector3f(0, 225, 0), new Vector3f(0, 0, 0),         new Vector3f(0.40f, 0.40f, 0.40f),   new Vector3f())

    gui.translation.mul(0.0625f)
    ground.translation.mul(0.0625f)
    fixed.translation.mul(0.0625f)
    thirdperson_righthand.translation.mul(0.0625f)
    firstperson_righthand.translation.mul(0.0625f)
    firstperson_lefthand.translation.mul(0.0625f)

    new ItemTransforms(
      ItemTransform.NO_TRANSFORM,
      thirdperson_righthand,
      firstperson_lefthand,
      firstperson_righthand,
      ItemTransform.NO_TRANSFORM,
      gui,
      ground,
      fixed)
  }

  protected def missingModel = Minecraft.getInstance.getModelManager.getMissingModel

  protected final val UnitCube = Array(
    Array(new Vec3(0, 0, 1), new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(1, 0, 1)),
    Array(new Vec3(0, 1, 0), new Vec3(0, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 0)),
    Array(new Vec3(1, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 0), new Vec3(0, 1, 0)),
    Array(new Vec3(0, 1, 1), new Vec3(0, 0, 1), new Vec3(1, 0, 1), new Vec3(1, 1, 1)),
    Array(new Vec3(0, 1, 0), new Vec3(0, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 1)),
    Array(new Vec3(1, 1, 1), new Vec3(1, 0, 1), new Vec3(1, 0, 0), new Vec3(1, 1, 0))
  )

  protected final val Planes = Array(
    (new Vec3(1, 0, 0),  new Vec3(0, 0, -1)),
    (new Vec3(1, 0, 0),  new Vec3(0, 0,  1)),
    (new Vec3(-1, 0, 0), new Vec3(0, -1, 0)),
    (new Vec3(1, 0, 0),  new Vec3(0, -1, 0)),
    (new Vec3(0, 0, 1),  new Vec3(0, -1, 0)),
    (new Vec3(0, 0, -1), new Vec3(0, -1, 0))
  )

  protected final val White = 0xFFFFFF

  protected def makeBox(from: Vec3, to: Vec3) = {
    val minX = math.min(from.x, to.x); val minY = math.min(from.y, to.y); val minZ = math.min(from.z, to.z)
    val maxX = math.max(from.x, to.x); val maxY = math.max(from.y, to.y); val maxZ = math.max(from.z, to.z)
    UnitCube.map(face => face.map(vertex => new Vec3(
      math.max(minX, math.min(maxX, vertex.x)),
      math.max(minY, math.min(maxY, vertex.y)),
      math.max(minZ, math.min(maxZ, vertex.z)))))
  }

  protected def bakeQuads(box: Array[Array[Vec3]], texture: Array[TextureAtlasSprite], color: Option[Int]): Array[BakedQuad] =
    bakeQuads(box, texture, color.getOrElse(White))

  protected def bakeQuads(box: Array[Array[Vec3]], texture: Array[TextureAtlasSprite], colorRGB: Int): Array[BakedQuad] = {
    Direction.values.map(side => {
      val vertices = box(side.get3DDataValue)
      val data = quadData(vertices, side, texture(side.get3DDataValue), colorRGB, 0)
      new BakedQuad(data, -1, side, texture(side.get3DDataValue), true)
    })
  }

  protected def bakeQuad(side: Direction, texture: TextureAtlasSprite, color: Option[Int], rotation: Int) = {
    val colorRGB = color.getOrElse(White)
    val vertices = UnitCube(side.get3DDataValue)
    val data = quadData(vertices, side, texture, colorRGB, rotation)
    new BakedQuad(data, -1, side, texture, true)
  }

  protected def quadData(vertices: Array[Vec3], facing: Direction, texture: TextureAtlasSprite, colorRGB: Int, rotation: Int): Array[Int] = {
    val (uAxis, vAxis) = Planes(facing.get3DDataValue)
    val rot = (rotation + 4) % 4
    vertices.flatMap(vertex => {
      var u = vertex.dot(uAxis).toFloat
      var v = vertex.dot(vAxis).toFloat
      if (uAxis.x + uAxis.y + uAxis.z < 0) u = 1 + u
      if (vAxis.x + vAxis.y + vAxis.z < 0) v = 1 + v
      for (i <- 0 until rot) {
        val tmp = u; u = v; v = (-(tmp - 0.5f)) + 0.5f
      }
      // Since 1.21 getU/getV take normalized sprite coordinates (0..1), not
      // the legacy 0..16 model coordinates used by older Minecraft versions.
      rawData(vertex.x, vertex.y, vertex.z, facing, texture, texture.getU(u), texture.getV(v), colorRGB)
    })
  }

  protected def rawData(x: Double, y: Double, z: Double, face: Direction, texture: TextureAtlasSprite, u: Float, v: Float, colorRGB: Int, light: Int = 0) = {
    val vx = (face.getStepX * 127) & 0xFF
    val vy = (face.getStepY * 127) & 0xFF
    val vz = (face.getStepZ * 127) & 0xFF
    val r  = (colorRGB >> 16) & 0xFF
    val g  = (colorRGB >> 8)  & 0xFF
    val b  = colorRGB & 0xFF
    Array(
      java.lang.Float.floatToRawIntBits(x.toFloat),
      java.lang.Float.floatToRawIntBits(y.toFloat),
      java.lang.Float.floatToRawIntBits(z.toFloat),
      0xFF000000 | b << 16 | g << 8 | r,
      java.lang.Float.floatToRawIntBits(u),
      java.lang.Float.floatToRawIntBits(v),
      light,
      vx | (vy << 0x08) | (vz << 0x10)
    )
  }
}
