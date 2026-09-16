package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction

/**
 * 閫傞厤鍣紙Adapter锛夋柟鍧楀疄浣撴覆鏌撳櫒銆? *
 * ==涓?1.7.10 鐗堢殑瀵瑰簲鍏崇郴==
 *  - `TileEntitySpecialRenderer` 鎹㈡垚 `BlockEntityRenderer`锛?*鏃犲弬鏋勯€?*锛坄client` 鍖呯殑
 *    `Proxy` 鐢?`new AdapterRenderer` 娉ㄥ唽锛夈€? *  - 1.7.10 鐨勬覆鏌撳洖璋冭繕浼氭妸鏂瑰潡鍧愭爣褰撳弬鏁颁紶杩涙潵锛?.21.1 鐨?`PoseStack` **鍏ュ満鏃跺師鐐? *    宸茬粡鏄柟鍧楄**锛屽洜姝ゅ師鏉ョ殑 `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 鍙樻垚
 *    `pose.translate(0.5, 0.5, 0.5)`銆? *  - 鍘熷疄鐜伴潬 `glScaled(1.0025, -1.0025, 1.0025)` 鎶婅鐩栧眰椤跺嚭鏂瑰潡琛ㄩ潰浠ラ伩寮€
 *    z-fighting锛涜繖閲岀敤 `pose.scale` 淇濈暀鍚屾牱鐨?1.0025 澶栨墿閲忋€? *  - `GL11.glPushAttrib/glPopAttrib`銆乣RenderState.disableLighting/enableLighting`銆? *    `RenderState.makeItBlend` 鏁翠綋鍒犻櫎锛?.21.1 娌℃湁鍥哄畾绠＄嚎鐘舵€佽淇濆瓨锛屾贩鍚堜笌娣卞害鍐欏叆
 *    鏀圭敱 `RenderType` 鎻忚堪锛涜嚜鍙戝厜鍒欐妸 `RenderUtil.fullBright` 鍐欒繘椤剁偣銆? *  - `Tessellator#startDrawingQuads/addVertexWithUV/draw` 鎹㈡垚涓€娆? *    `RenderUtil.drawSpriteQuad`锛堝唴閮ㄦ槸 `PoseStack` + `VertexConsumer`锛夈€? *  - `Textures.Adapter.iconOn`锛坄IIcon`锛夋崲鎴?`Textures.Block.AdapterOn`锛坄ResourceLocation`锛? *    鍔?`RenderUtil.sprite`锛涚簿鐏靛彲鑳戒负 `null`锛宍drawSpriteQuad` 鍐呴儴宸插垽绌恒€? *
 * ==UV 涓?RenderType 鐨勫彇鑸?=
 *  - 璐村浘 `adapteron` 鏄?*鍔ㄧ敾鏂瑰潡璐村浘**锛?6x192锛? 甯э級锛屾墍浠ュ彧鑳借蛋鏂瑰潡鍥鹃泦锛? *    鍗?`RenderType.cutout()` 鍔?`TextureAtlasSprite`锛涗笉鑳介€?`entityCutout` 杩欑被
 *    鎸囧悜鐙珛璐村浘鏂囦欢鐨?RenderType锛堝畠浠姹傝创鍥句笉鍦ㄥ浘闆嗛噷锛夈€? *  - 璇ヨ创鍥惧甫浜屽€?alpha锛堥€忔槑鍔犱笉閫忔槑锛夛紝鍥犳閫?`cutout()` 鑰屼笉鏄?`solid()`锛? *    `solid()` 涓嶅仛 alpha 娴嬭瘯锛岄€忔槑鍍忕礌浼氳娓叉煋鎴愰粦鍧椼€傚師瀹炵幇鐨? *    `RenderState.makeItBlend` 鍙湪闇€瑕佺湡姝ｅ崐閫忔槑鏃舵墠鏈夋剰涔夛紝杩欓噷涓嶉渶瑕併€? *  - 鏁村紶璐村浘閾烘弧涓€涓潰锛?.7.10 鐨?`icon.getMinU/getMaxU` 鍙鐩栧浘闆嗙殑**褰撳墠鍔ㄧ敾甯?*锛? *    1.21.1 鐨?`TextureAtlasSprite#getU0/getU1` 璇箟瀹屽叏涓€鑷达紙涔熸寜甯ц繑鍥烇級锛? *    鎵€浠ョ洿鎺ョ敤 `drawSpriteQuad` 灏辨槸瀵瑰師鏁堟灉鐨勭洿璇戯紝涓嶉渶瑕佹墜宸ユ崲绠?UV銆? */
class AdapterRenderer extends BlockEntityRenderer[tileentity.Adapter] {

  /** 绛変环浜?1.7.10 鐨?`GL11.glScaled(1.0025, ...)`銆?*/
  private final val OverlayScale = 1.0025f

  override def render(t: tileentity.Adapter, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    // 鍘熷疄鐜扮殑闂ㄦ锛氬彧瑕佽繕鏈変换鎰忎竴涓€屾墦寮€銆嶇殑渚ч潰灏辫鐢昏鐩栧眰銆?    if (!t.openSides.contains(true)) return

    val vc = buffer.getBuffer(RenderType.cutout())
    val sideActivity = RenderUtil.sprite(Textures.Block.AdapterOn)

    pose.pushPose()
    // 1.7.10 鐨?`glTranslatef(-0.5, -0.5, -0.5)` 涓庡墠闈㈢殑 +0.5 骞崇Щ浜掔浉鎶垫秷锛?    // 杩欓噷鍙墿銆岀粫鏂瑰潡涓績缂╂斁銆嶏紝缂╂斁涓績閫氳繃鍓嶅悗鍚勪竴娆″钩绉诲疄鐜般€?    pose.translate(0.5, 0.5, 0.5)
    pose.scale(OverlayScale, OverlayScale, OverlayScale)
    pose.translate(-0.5, -0.5, -0.5)

    // 鍥涗釜椤剁偣鐨勯『搴忛€愭潯瀵归綈鍘熸潵鐨?addVertexWithUV 璋冪敤椤哄簭锛?    // 鍙敾鎵撳紑鐨勯潰锛屽叧闂殑闈㈢敱鏂瑰潡鏈綋妯″瀷璐熻矗锛岃鐩栧眰鐢讳笂鍘讳細绌挎ā銆?    if (t.isSideOpen(Direction.DOWN)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.UP)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.NORTH)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        1, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.SOUTH)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.WEST)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.EAST)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, RenderUtil.fullBright, overlay)
    }

    pose.popPose()
  }
}
