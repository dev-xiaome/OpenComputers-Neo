package li.cil.oc.api.event;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * A bit more specific sign change event that holds information about new text of the sign. Used in the sign upgrade.
 * <br>
 * 1.21.1 的告示牌文本放在 {@link net.minecraft.world.level.block.entity.SignText} 中且不可变，
 * 因此本事件直接持有可变的 {@link Component} 数组；处理完成后由调用方写回告示牌
 * （{@link SignBlockEntity#updateText(java.util.function.UnaryOperator, boolean)}）。
 */
public abstract class SignChangeEvent extends Event {
    /**
     * 触发本次变更的告示牌方块实体。
     */
    public final SignBlockEntity sign;

    /**
     * 告示牌的四行新文本，修改该数组即可改变最终写入告示牌的内容。
     */
    public final Component[] lines;

    private SignChangeEvent(SignBlockEntity sign, Component[] lines) {
        this.sign = sign;
        this.lines = lines;
    }

    /**
     * Fired before the sign text is applied to the sign.
     * <br>
     * Canceling this event will prevent the text from being applied.
     */
    public static class Pre extends SignChangeEvent implements ICancellableEvent {
        public Pre(SignBlockEntity sign, Component[] lines) {
            super(sign, lines);
        }
    }

    /**
     * Fired after the sign text has been applied to the sign.
     */
    public static class Post extends SignChangeEvent {
        public Post(SignBlockEntity sign, Component[] lines) {
            super(sign, lines);
        }
    }
}
