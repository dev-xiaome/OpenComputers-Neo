package li.cil.oc.api.prefab;

import li.cil.oc.api.manual.TabIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Simple implementation of a tab icon renderer using an item stack as its graphic.
 * <br>
 * <b>1.21.1 移植说明</b>：物品图标改由
 * {@link GuiGraphics#renderItem(ItemStack, int, int)} 绘制，它会自行设置 GUI 光照并
 * 处理附魔光效，因此 1.7.10 里手写的 {@code RenderHelper} /
 * {@code OpenGlHelper} / {@code RenderItem} 调用全部去掉。
 * <br>
 * 仅在客户端使用。
 */
@SuppressWarnings("UnusedDeclaration")
public class ItemStackTabIconRenderer implements TabIconRenderer {
    private final ItemStack stack;

    public ItemStackTabIconRenderer(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.renderItem(stack, x, y);
    }
}
