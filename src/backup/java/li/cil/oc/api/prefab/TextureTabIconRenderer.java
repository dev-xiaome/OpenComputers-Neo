package li.cil.oc.api.prefab;

import li.cil.oc.api.manual.TabIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Simple implementation of a tab icon renderer using a full texture as its graphic.
 * <br>
 * <b>1.21.1 移植说明</b>：{@code Tessellator} 的立即模式绘制已移除，改为
 * {@link GuiGraphics#blit(ResourceLocation, int, int, float, float, int, int, int, int)}。
 * 原实现画的是 0..16 的 UV 覆盖 16x16 的四边形（即整张贴图），这里等价地写成
 * “从纹理 (0,0) 取 16x16 贴到屏幕 (x,y)”，贴图尺寸同样按 16x16 处理。
 * <br>
 * 仅在客户端使用。
 */
@SuppressWarnings("UnusedDeclaration")
public class TextureTabIconRenderer implements TabIconRenderer {
    private final ResourceLocation location;

    public TextureTabIconRenderer(ResourceLocation location) {
        this.location = location;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(location, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
    }
}
