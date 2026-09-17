package li.cil.oc.api.manual;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Allows defining a renderer for a manual tab.
 * <br>
 * Each renderer instance represents the single graphic it is drawing. To
 * provide different graphics for different tabs you'll need to create
 * multiple tab renderer instances.
 * <br>
 * <b>1.21.1 移植说明</b>：1.7.10 版本依赖固定渲染管线，调用方会先把 OpenGL 状态
 * 摆好（原点在图标左上角，绘制范围 16x16），{@code render()} 不需要参数。1.21.1
 * 的 GUI 绘制统一走 {@link GuiGraphics}，因此签名改为把绘制上下文和目标坐标交给
 * 实现者，由实现者自行完成 16x16 区域的绘制。
 * <br>
 * 本接口只在客户端使用（签名引用了客户端类型），不要在公共/服务端逻辑里调用。
 *
 * @see li.cil.oc.api.prefab.ItemStackTabIconRenderer
 * @see li.cil.oc.api.prefab.TextureTabIconRenderer
 */
public interface TabIconRenderer {
    /**
     * 绘制页签图标。
     * <br>
     * 实现应当把图形绘制在 {@code (x, y)} 起始的 16x16 区域内。调用方不再调整
     * 任何渲染状态，实现者需要用传入的 {@link GuiGraphics} 自己完成绘制。
     *
     * @param graphics 客户端 GUI 绘制上下文。
     * @param x        图标区域左上角的 X 坐标（GUI 缩放坐标）。
     * @param y        图标区域左上角的 Y 坐标（GUI 缩放坐标）。
     */
    void render(GuiGraphics graphics, int x, int y);
}
