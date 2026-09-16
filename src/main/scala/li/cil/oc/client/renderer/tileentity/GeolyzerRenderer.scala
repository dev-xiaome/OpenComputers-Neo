package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import li.cil.oc.common.tileentity

/**
 * 鍦板舰鍒嗘瀽浠紙Geolyzer锛夋柟鍧楀疄浣撴覆鏌撳櫒锛氬湪椤堕潰鐢讳竴灞傚伐浣滃厜鏁堛€? *
 * ==涓?1.7.10 鐗堢殑瀵瑰簲鍏崇郴==
 *  - `TileEntitySpecialRenderer` 鎹㈡垚 `BlockEntityRenderer`锛?*鏃犲弬鏋勯€?*銆? *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 鎹㈡垚 `pose.translate(0.5, 0.5, 0.5)`
 *    锛?.21.1 鐨?`PoseStack` 鍏ュ満鍘熺偣宸茬粡鏄柟鍧楄锛夈€? *  - 鍘熷疄鐜伴潬 `glScaled(1.0025, -1.0025, 1.0025)` 鎶婅鐩栧眰椤跺嚭鏂瑰潡琛ㄩ潰閬垮紑 z-fighting锛? *    杩欓噷鐢?`pose.scale` 淇濈暀鍚屾牱鐨?1.0025 澶栨墿閲忋€? *  - `RenderState.disableLighting/makeItBlend/setBlendAlpha`銆乣glPushAttrib/glPopAttrib`
 *    鏁翠綋鍒犻櫎锛涜嚜鍙戝厜鏀圭敱 `RenderUtil.fullBright` 鍐欒繘椤剁偣銆? *  - `Textures.Geolyzer.iconTopOn`锛坄IIcon`锛夋崲鎴?`Textures.Block.GeolyzerTopOn`
 *    鍔?`RenderUtil.sprite`锛堝彲鑳戒负 `null`锛宍drawSpriteQuad` 鍐呴儴宸插垽绌猴級銆? *
 * ==UV 涓?RenderType 鐨勫彇鑸?=
 *  - `geolyzertopon`锛?6x224锛夋槸鏂瑰潡鍥鹃泦閲岀殑**鍔ㄧ敾璐村浘**锛屾墍浠ュ彧鑳介厤
 *    `RenderType.cutout()`锛堟柟鍧楀浘闆嗗姞鏂瑰潡椤剁偣鏍煎紡锛夛紝涓嶈兘鐢ㄦ寚鍚戠嫭绔嬭创鍥炬枃浠剁殑
 *    `entityCutout` 閭ｇ被 RenderType銆? *  - 瀹冨甫浜屽€?alpha锛屾晠鐢?`cutout()` 鑰岄潪 `solid()`锛堝悗鑰呬笉鍋?alpha 娴嬭瘯锛? *    閫忔槑鍍忕礌浼氭覆鏌撴垚榛戝潡锛夈€? *  - 鏁村紶璐村浘閾烘弧椤堕潰 鈫?鐩存帴鐢?`drawSpriteQuad` 灏辨槸瀵?1.7.10
 *    `icon.getMinU/getMaxU`锛堟寜褰撳墠甯ц繑鍥烇級鍐欐硶鐨勭洿璇戙€? *
 * ==娉ㄦ剰==
 *  - 鍘熷疄鐜板**浠讳綍**鍦板舰鍒嗘瀽浠兘鐢昏繖灞傚厜鏁堬紙涓嶇湅鏂瑰潡鐘舵€侊級锛?.21.1 淇濇寔涓€鑷达紝
 *    涓嶉澶栧紩鍏?`isActive` 涔嬬被鐨勫垽鏂€? */
class GeolyzerRenderer extends BlockEntityRenderer[tileentity.Geolyzer] {

  /** 绛変环浜?1.7.10 鐨?`GL11.glScaled(1.0025, ...)`銆?*/
  private final val OverlayScale = 1.0025f

  override def render(t: tileentity.Geolyzer, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return

    val vc = buffer.getBuffer(RenderType.cutout())
    val topOn = RenderUtil.sprite(Textures.Block.GeolyzerTopOn)

    pose.pushPose()
    // 鍓嶅悗鍚勪竴娆?0.5 骞崇Щ浜掔浉鎶垫秷锛屽彧鍓┿€岀粫鏂瑰潡涓績缂╂斁銆嶃€?    pose.translate(0.5, 0.5, 0.5)
    pose.scale(OverlayScale, OverlayScale, OverlayScale)
    pose.translate(-0.5, -0.5, -0.5)

    // 椤堕潰锛堝眬閮?y = 0锛夛紱椤剁偣椤哄簭涓庡師鏉ョ殑 addVertexWithUV 瀹屽叏涓€鑷淬€?    RenderUtil.drawSpriteQuad(pose, vc, topOn,
      0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, RenderUtil.fullBright, overlay)

    pose.popPose()
  }
}
