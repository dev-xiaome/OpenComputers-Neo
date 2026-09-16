package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}

/**
 * 鎷嗚В鏈猴紙Disassembler锛夋柟鍧楀疄浣撴覆鏌撳櫒锛氬伐浣滄椂缁欓《闈笌鍥涗釜渚ч潰鐩栦笂鍏夋晥銆? *
 * ==涓?1.7.10 鐗堢殑瀵瑰簲鍏崇郴==
 *  - `TileEntitySpecialRenderer` 鎹㈡垚 `BlockEntityRenderer`锛?*鏃犲弬鏋勯€?*銆? *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 鎹㈡垚 `pose.translate(0.5, 0.5, 0.5)`
 *    锛?.21.1 鐨?`PoseStack` 鍏ュ満鍘熺偣宸茬粡鏄柟鍧楄锛夈€? *  - 鍘熷疄鐜伴潬 `glScaled(1.0025, -1.0025, 1.0025)` 鎶婅鐩栧眰椤跺嚭鏂瑰潡琛ㄩ潰閬垮紑 z-fighting锛? *    杩欓噷鐢?`pose.scale` 淇濈暀鍚屾牱鐨?1.0025 澶栨墿閲忋€? *  - `RenderState.disableLighting/makeItBlend`銆乣glPushAttrib/glPopAttrib` 鏁翠綋鍒犻櫎锛? *    鑷彂鍏夋敼鐢?`RenderUtil.fullBright` 鍐欒繘椤剁偣銆? *  - `IIcon`锛坄Textures.Disassembler.iconTopOn` / `iconSideOn`锛夋崲鎴? *    `Textures.Block.DisassemblerTopOn` / `DisassemblerSideOn` 鍔?`RenderUtil.sprite`
 *    锛堝彲鑳戒负 `null`锛宍drawSpriteQuad` 鍐呴儴宸插垽绌猴級銆? *
 * ==UV 涓?RenderType 鐨勫彇鑸?=
 *  - `disassemblertopon`锛?6x64锛変笌 `disassemblersideon`锛?6x80锛夐兘鏄柟鍧楀浘闆嗛噷鐨? *    **鍔ㄧ敾璐村浘**锛屾墍浠ュ彧鑳界敤 `RenderType.cutout()`锛堟柟鍧楀浘闆嗗姞鏂瑰潡椤剁偣鏍煎紡锛夛紝
 *    涓嶈兘鐢ㄦ寚鍚戠嫭绔嬭创鍥炬枃浠剁殑 `entityCutout`銆? *  - 涓よ€呴兘甯︿簩鍊?alpha锛屾晠鐢?`cutout()` 鑰岄潪 `solid()`锛堝悗鑰呬笉鍋?alpha 娴嬭瘯锛岄€忔槑鍍忕礌
 *    浼氭覆鏌撴垚榛戝潡锛夈€? *  - 姣忎釜闈㈤兘鏄暣寮犺创鍥鹃摵婊★紝鐩存帴 `drawSpriteQuad` 灏辨槸瀵?1.7.10
 *    `icon.getMinU/getMaxU`锛堝綋鍓嶅抚锛夊啓娉曠殑鐩磋瘧銆? */
class DisassemblerRenderer extends BlockEntityRenderer[tileentity.Disassembler] {

  /** 绛変环浜?1.7.10 鐨?`GL11.glScaled(1.0025, ...)`銆?*/
  private final val OverlayScale = 1.0025f

  override def render(t: tileentity.Disassembler, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    if (!t.isActive) return

    val vc = buffer.getBuffer(RenderType.cutout())
    val topOn = RenderUtil.sprite(Textures.Block.DisassemblerTopOn)
    val sideOn = RenderUtil.sprite(Textures.Block.DisassemblerSideOn)

    pose.pushPose()
    // 鍓嶅悗鍚勪竴娆?0.5 骞崇Щ浜掔浉鎶垫秷锛屽彧鍓┿€岀粫鏂瑰潡涓績缂╂斁銆嶃€?    pose.translate(0.5, 0.5, 0.5)
    pose.scale(OverlayScale, OverlayScale, OverlayScale)
    pose.translate(-0.5, -0.5, -0.5)

    // 椤堕潰锛堝眬閮?y = 0锛夛紱椤剁偣椤哄簭涓庡師鏉ョ殑 addVertexWithUV 瀹屽叏涓€鑷淬€?    RenderUtil.drawSpriteQuad(pose, vc, topOn,
      0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, RenderUtil.fullBright, overlay)

    // 鍖楅潰銆佸崡闈€佷笢闈€佽タ闈€?    RenderUtil.drawSpriteQuad(pose, vc, sideOn,
      1, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, RenderUtil.fullBright, overlay)
    RenderUtil.drawSpriteQuad(pose, vc, sideOn,
      0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, RenderUtil.fullBright, overlay)
    RenderUtil.drawSpriteQuad(pose, vc, sideOn,
      1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, RenderUtil.fullBright, overlay)
    RenderUtil.drawSpriteQuad(pose, vc, sideOn,
      0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, RenderUtil.fullBright, overlay)

    pose.popPose()
  }
}
