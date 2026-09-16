package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f

/**
 * 閰嶇數绠憋紙PowerDistributor锛夋柟鍧楀疄浣撴覆鏌撳櫒锛氶《闈笌鍥涗釜渚ч潰鐩栦笂宸ヤ綔鍏夋晥锛? * 鍏夋晥鐨?*鏁翠綋閫忔槑搴?*璺熼殢鑷韩缂撳啿鐨勫厖鑳芥瘮渚嬪彉鍖栥€? *
 * ==涓?1.7.10 鐗堢殑瀵瑰簲鍏崇郴==
 *  - `TileEntitySpecialRenderer` 鎹㈡垚 `BlockEntityRenderer`锛?*鏃犲弬鏋勯€?*銆? *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 鎹㈡垚 `pose.translate(0.5, 0.5, 0.5)`
 *    锛?.21.1 鐨?`PoseStack` 鍏ュ満鍘熺偣宸茬粡鏄柟鍧楄锛夈€? *  - 鍘熷疄鐜伴潬 `glScaled(1.0025, -1.0025, 1.0025)` 鎶婅鐩栧眰椤跺嚭鏂瑰潡琛ㄩ潰閬垮紑 z-fighting锛? *    杩欓噷鐢?`pose.scale` 淇濈暀鍚屾牱鐨?1.0025 澶栨墿閲忋€? *  - `RenderState.disableLighting` 鍒犻櫎锛?.21.1 鏃犲浐瀹氱绾垮厜鐓э級锛岃嚜鍙戝厜鏀圭敤
 *    `RenderUtil.fullBright` 鍐欒繘椤剁偣鍏夌収銆? *  - `Textures.PowerDistributor.iconTopOn/iconSideOn`锛坄IIcon`锛夋崲鎴? *    `Textures.Block.PowerDistributorTopOn/PowerDistributorSideOn` 鍔?`RenderUtil.sprite`銆? *
 * ==UV 涓?RenderType 鐨勫彇鑸?=
 *  - `powerdistributortopon`锛?6x16锛変笌 `powerdistributorsideon`锛?6x224锛夐兘鏄柟鍧楀浘闆嗛噷鐨? *    **鍔ㄧ敾璐村浘**锛屽洜姝ゅ彧鑳界敤鏂瑰潡鍥鹃泦鐨?RenderType锛氭暟鍊奸兘鐢? *    `TextureAtlasSprite#getU0/getU1`锛堟寜褰撳墠鍔ㄧ敾甯ц繑鍥烇級锛屼笉鑳界敤鎸囧悜鐙珛璐村浘鏂囦欢鐨? *    `entityCutout` 閭ｇ被 RenderType銆? *  - 姣忎釜闈㈤兘鏄暣寮犺创鍥鹃摵婊?鈫?璇箟涓婄瓑浠蜂簬 `RenderUtil.drawSpriteQuad`銆? *
 * ==鍗婇€忔槑娓愰殣鐨勫彇鑸嶏紙閲嶈锛?=
 *  - 1.7.10 鐢?`RenderState.setBlendAlpha(ratio)`锛坄glColor4f` 鍔? *    `glBlendFunc(GL_SRC_ALPHA, GL_ONE)`锛夎鏁村眰鍏夋晥闅忓厖鑳芥瘮渚嬫笎鏄撅紱
 *    1.21.1 閲?GL 鍥哄畾绠＄嚎鐨?`glColor4f` 宸茬粡涓嶅瓨鍦紝閫忔槑搴﹀彧鑳藉啓杩?*椤剁偣棰滆壊**銆? *    `RenderUtil.drawQuad` 鍥哄畾鍐欐鐧借壊涓嶉€忔槑锛坄setColor(255,255,255,255)`锛夛紝
 *    鑰屽叕鍏卞伐鍏锋槸鍙鐨勶紝鎵€浠ユ湰鏂囦欢鑷甫涓€涓?`drawTintedSpriteQuad` 杈呭姪鏂规硶锛? *    鐢ㄥ悓涓€濂楅《鐐规牸寮忔妸 `alpha` 鍐欒繘椤剁偣棰滆壊銆? *  - RenderType 閫?`RenderType.translucent()`锛氬畠鑷甫
 *    `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` 娣峰悎锛屾槸 1.21.1 閲岃〃杈俱€屽崐閫忔槑鏂瑰潡鍥鹃泦璐村浘銆嶇殑
 *    鏍囧噯鍋氭硶銆備笌鍘熷疄鐜扮殑 `SRC_ALPHA / ONE`锛堝亸鍔犺壊锛夌暐鏈夊樊鍒細
 *    鍦ㄩ粦鑹茶儗鏅笂涓よ€呮帴杩戯紝鍦ㄦ槑浜儗鏅笂 1.21.1 鐨勮鎰熶細鏇淬€屽疄銆嶄竴浜涖€? *    杩欓噷浼樺厛淇濊瘉娓叉煋绠＄嚎涓嶅穿銆佽涔夋纭紝涓嶈拷姹傞€愬儚绱犱竴鑷淬€? *  - 椤剁偣棰滆壊閲岀殑 alpha 浼氬拰 `RenderSystem` 鐨勭潃鑹插櫒棰滆壊璋冨埗鍊肩浉涔橈紝姝ｅ父鎯呭喌涓嬪悗鑰呮槸
 *    (1,1,1,1)锛堟覆鏌撳櫒涓嶆敼瀹冿紝涔熶笉鍐嶉渶瑕?1.7.10 鐨?`setBlendAlpha`锛夛紝鎵€浠ユ渶缁?alpha
 *    灏辨槸杩欓噷浼犲叆鐨勬瘮渚嬪€笺€? */
class PowerDistributorRenderer extends BlockEntityRenderer[tileentity.PowerDistributor] {

  /** 绛変环浜?1.7.10 鐨?`GL11.glScaled(1.0025, ...)`銆?*/
  private final val OverlayScale = 1.0025f

  override def render(t: tileentity.PowerDistributor, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    if (t.globalBuffer <= 0) return

    // 鍘?`distributor.globalBuffer / distributor.globalBufferSize`锛?    // 缂撳啿涓婇檺涓?0 鏃讹紙鍒氭斁涓嬨€佽繕娌″潎琛¤繃锛夊師瀹炵幇浼氬緱鍒?NaN锛岃繖閲屾敼鎴愪笉鐢伙紝閬垮厤鑴忕姸鎬併€?    if (t.globalBufferSize <= 0) return
    val ratio = math.max(0.0, math.min(1.0, t.globalBuffer / t.globalBufferSize)).toFloat
    if (ratio <= 0f) return

    val vc = buffer.getBuffer(RenderType.translucent())
    val topOn = RenderUtil.sprite(Textures.Block.PowerDistributorTopOn)
    val sideOn = RenderUtil.sprite(Textures.Block.PowerDistributorSideOn)

    pose.pushPose()
    // 鍓嶅悗鍚勪竴娆?0.5 骞崇Щ浜掔浉鎶垫秷锛屽彧鍓┿€岀粫鏂瑰潡涓績缂╂斁銆嶃€?    pose.translate(0.5, 0.5, 0.5)
    pose.scale(OverlayScale, OverlayScale, OverlayScale)
    pose.translate(-0.5, -0.5, -0.5)

    // 椤堕潰锛堝眬閮?y = 0锛夈€傞《鐐归『搴忎笌鍘熸潵鐨?addVertexWithUV 瀹屽叏涓€鑷淬€?    drawTintedSpriteQuad(pose, vc, topOn, ratio,
      0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, RenderUtil.fullBright, overlay)

    // 鍖楅潰銆佸崡闈€佷笢闈€佽タ闈€?    drawTintedSpriteQuad(pose, vc, sideOn, ratio,
      1, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, RenderUtil.fullBright, overlay)
    drawTintedSpriteQuad(pose, vc, sideOn, ratio,
      0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, RenderUtil.fullBright, overlay)
    drawTintedSpriteQuad(pose, vc, sideOn, ratio,
      1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, RenderUtil.fullBright, overlay)
    drawTintedSpriteQuad(pose, vc, sideOn, ratio,
      0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, RenderUtil.fullBright, overlay)

    pose.popPose()
  }

  /**
   * 涓?`RenderUtil.drawSpriteQuad` 瀹屽叏鐩稿悓鐨勫洓杈瑰舰锛堟暣寮犺创鍥鹃摵婊★級锛?   * 浣嗘妸 `alpha` 浣滀负椤剁偣棰滆壊鍐欒繘鍘伙紝鐢ㄤ簬琛ㄨ揪鏁翠綋娓愰殣銆?   *
   * TODO(娓叉煋): `RenderUtil` 鏄叕鍏卞彧璇绘枃浠讹紝鏃犳硶缁欏畠鍔犲甫棰滆壊鐨勯噸杞斤紱
   * 鑻ュ悗缁厑璁告敼 `RenderUtil`锛屽簲鎶婃湰鏂规硶鍚堝苟杩囧幓骞惰 `PowerDistributorRenderer` 鐩存帴璋冪敤銆?   */
  private def drawTintedSpriteQuad(pose: PoseStack, vc: VertexConsumer, sprite: TextureAtlasSprite,
                                   alpha: Float,
                                   x0: Double, y0: Double, z0: Double,
                                   x1: Double, y1: Double, z1: Double,
                                   x2: Double, y2: Double, z2: Double,
                                   x3: Double, y3: Double, z3: Double,
                                   light: Int, overlay: Int): Unit = {
    if (sprite == null) return
    drawTintedQuad(pose, vc,
      x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
      sprite.getU0, sprite.getV0, sprite.getU1, sprite.getV1,
      alpha, light, overlay)
  }

  /** 甯﹂《鐐归鑹茬殑鍥涜竟褰紱椤剁偣椤哄簭涓?UV 瀵瑰簲鍏崇郴涓?`RenderUtil.drawQuad` 涓€鑷淬€?*/
  private def drawTintedQuad(pose: PoseStack, vc: VertexConsumer,
                             x0: Double, y0: Double, z0: Double,
                             x1: Double, y1: Double, z1: Double,
                             x2: Double, y2: Double, z2: Double,
                             x3: Double, y3: Double, z3: Double,
                             u0: Float, v0: Float, u1: Float, v1: Float,
                             alpha: Float, light: Int, overlay: Int): Unit = {
    val entry = pose.last()
    val mat = entry.pose()
    val a = math.max(0f, math.min(1f, alpha))
    val aByte = (a * 255f).round.toInt

    // 椤剁偣鍦ㄥЭ鎬佺┖闂撮噷鐨勪綅缃紱娉曠嚎鐢卞墠涓変釜椤剁偣鍦ㄥЭ鎬佺┖闂撮噷姹傚弶绉緱鍒般€?    val p0 = mat.transformPosition(x0.toFloat, y0.toFloat, z0.toFloat, new Vector3f())
    val p1 = mat.transformPosition(x1.toFloat, y1.toFloat, z1.toFloat, new Vector3f())
    val p2 = mat.transformPosition(x2.toFloat, y2.toFloat, z2.toFloat, new Vector3f())
    val normal = quadNormal(p0, p1, p2)

    // 鍥涗釜瑙掔殑 UV 瀵瑰簲鍏崇郴涓?RenderUtil.drawQuad 鐩稿悓锛?x0,y0,z0) 鏄创鍥惧彸涓嬭銆?    vertex(vc, entry, x0, y0, z0, u0, v1, normal, aByte, light, overlay)
    vertex(vc, entry, x1, y1, z1, u1, v1, normal, aByte, light, overlay)
    vertex(vc, entry, x2, y2, z2, u1, v0, normal, aByte, light, overlay)
    vertex(vc, entry, x3, y3, z3, u0, v0, normal, aByte, light, overlay)
  }

  private def vertex(vc: VertexConsumer, entry: PoseStack.Pose,
                     x: Double, y: Double, z: Double,
                     u: Float, v: Float,
                     normal: Vector3f, aByte: Int,
                     light: Int, overlay: Int): Unit = {
    vc.addVertex(entry, x.toFloat, y.toFloat, z.toFloat)
      .setColor(255, 255, 255, aByte)
      .setUv(u, v)
      .setOverlay(overlay)
      .setLight(light)
      .setNormal(entry, normal.x, normal.y, normal.z)
  }

  /** 鐢变笁涓《鐐规眰娉曠嚎锛涢€€鍖栨椂閫€鍖栦负 +Y锛岄伩鍏嶅嚭鐜?NaN 椤剁偣銆?*/
  private def quadNormal(p0: Vector3f, p1: Vector3f, p2: Vector3f): Vector3f = {
    val ax = p1.x - p0.x
    val ay = p1.y - p0.y
    val az = p1.z - p0.z
    val bx = p2.x - p0.x
    val by = p2.y - p0.y
    val bz = p2.z - p0.z
    val nx = ay * bz - az * by
    val ny = az * bx - ax * bz
    val nz = ax * by - ay * bx
    val lengthSq = nx * nx + ny * ny + nz * nz
    if (lengthSq < 1e-9f) new Vector3f(0, 1, 0)
    else {
      val inv = (1.0 / math.sqrt(lengthSq.toDouble)).toFloat
      new Vector3f(nx * inv, ny * inv, nz * inv)
    }
  }
}
