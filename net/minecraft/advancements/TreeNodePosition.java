package net.minecraft.advancements;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;

public class TreeNodePosition {
    private final AdvancementNode node;
    @Nullable
    private final TreeNodePosition parent;
    @Nullable
    private final TreeNodePosition previousSibling;
    private final int childIndex;
    private final List<TreeNodePosition> children = Lists.newArrayList();
    private TreeNodePosition ancestor;
    @Nullable
    private TreeNodePosition thread;
    private int x;
    private float y;
    private float mod;
    private float change;
    private float shift;

    public TreeNodePosition(AdvancementNode node, @Nullable TreeNodePosition parent, @Nullable TreeNodePosition previousSibling, int childIndex, int x) {
        if (node.advancement().display().isEmpty()) {
            throw new IllegalArgumentException("Can't position an invisible advancement!");
        } else {
            this.node = node;
            this.parent = parent;
            this.previousSibling = previousSibling;
            this.childIndex = childIndex;
            this.ancestor = this;
            this.x = x;
            this.y = -1.0F;
            TreeNodePosition treenodeposition = null;

            for (AdvancementNode advancementnode : node.children()) {
                treenodeposition = this.addChild(advancementnode, treenodeposition);
            }
        }
    }

    @Nullable
    private TreeNodePosition addChild(AdvancementNode child, @Nullable TreeNodePosition previousSibling) {
        if (child.advancement().display().isPresent()) {
            previousSibling = new TreeNodePosition(child, this, previousSibling, this.children.size() + 1, this.x + 1);
            this.children.add(previousSibling);
        } else {
            for (AdvancementNode advancementnode : child.children()) {
                previousSibling = this.addChild(advancementnode, previousSibling);
            }
        }

        return previousSibling;
    }

    private void firstWalk() {
        if (this.children.isEmpty()) {
            if (this.previousSibling != null) {
                this.y = this.previousSibling.y + 1.0F;
            } else {
                this.y = 0.0F;
            }
        } else {
            TreeNodePosition treenodeposition = null;

            for (TreeNodePosition treenodeposition1 : this.children) {
                treenodeposition1.firstWalk();
                treenodeposition = treenodeposition1.apportion(treenodeposition == null ? treenodeposition1 : treenodeposition);
            }

            this.executeShifts();
            float f = (this.children.get(0).y + this.children.get(this.children.size() - 1).y) / 2.0F;
            if (this.previousSibling != null) {
                this.y = this.previousSibling.y + 1.0F;
                this.mod = this.y - f;
            } else {
                this.y = f;
            }
        }
    }

    private float secondWalk(float offsetY, int columnX, float subtreeTopY) {
        this.y += offsetY;
        this.x = columnX;
        if (this.y < subtreeTopY) {
            subtreeTopY = this.y;
        }

        for (TreeNodePosition treenodeposition : this.children) {
            subtreeTopY = treenodeposition.secondWalk(offsetY + this.mod, columnX + 1, subtreeTopY);
        }

        return subtreeTopY;
    }

    private void thirdWalk(float y) {
        this.y += y;

        for (TreeNodePosition treenodeposition : this.children) {
            treenodeposition.thirdWalk(y);
        }
    }

    private void executeShifts() {
        float f = 0.0F;
        float f1 = 0.0F;

        for (int i = this.children.size() - 1; i >= 0; i--) {
            TreeNodePosition treenodeposition = this.children.get(i);
            treenodeposition.y += f;
            treenodeposition.mod += f;
            f1 += treenodeposition.change;
            f += treenodeposition.shift + f1;
        }
    }

    @Nullable
    private TreeNodePosition previousOrThread() {
        if (this.thread != null) {
            return this.thread;
        } else {
            return !this.children.isEmpty() ? this.children.get(0) : null;
        }
    }

    @Nullable
    private TreeNodePosition nextOrThread() {
        if (this.thread != null) {
            return this.thread;
        } else {
            return !this.children.isEmpty() ? this.children.get(this.children.size() - 1) : null;
        }
    }

    private TreeNodePosition apportion(TreeNodePosition node) {
        if (this.previousSibling == null) {
            return node;
        } else {
            TreeNodePosition treenodeposition = this;
            TreeNodePosition treenodeposition1 = this;
            TreeNodePosition treenodeposition2 = this.previousSibling;
            TreeNodePosition treenodeposition3 = this.parent.children.get(0);
            float f = this.mod;
            float f1 = this.mod;
            float f2 = treenodeposition2.mod;

            float f3;
            for (f3 = treenodeposition3.mod;
                treenodeposition2.nextOrThread() != null && treenodeposition.previousOrThread() != null;
                f1 += treenodeposition1.mod
            ) {
                treenodeposition2 = treenodeposition2.nextOrThread();
                treenodeposition = treenodeposition.previousOrThread();
                treenodeposition3 = treenodeposition3.previousOrThread();
                treenodeposition1 = treenodeposition1.nextOrThread();
                treenodeposition1.ancestor = this;
                float f4 = treenodeposition2.y + f2 - (treenodeposition.y + f) + 1.0F;
                if (f4 > 0.0F) {
                    treenodeposition2.getAncestor(this, node).moveSubtree(this, f4);
                    f += f4;
                    f1 += f4;
                }

                f2 += treenodeposition2.mod;
                f += treenodeposition.mod;
                f3 += treenodeposition3.mod;
            }

            if (treenodeposition2.nextOrThread() != null && treenodeposition1.nextOrThread() == null) {
                treenodeposition1.thread = treenodeposition2.nextOrThread();
                treenodeposition1.mod += f2 - f1;
            } else {
                if (treenodeposition.previousOrThread() != null && treenodeposition3.previousOrThread() == null) {
                    treenodeposition3.thread = treenodeposition.previousOrThread();
                    treenodeposition3.mod += f - f3;
                }

                node = this;
            }

            return node;
        }
    }

    private void moveSubtree(TreeNodePosition node, float shift) {
        float f = (float)(node.childIndex - this.childIndex);
        if (f != 0.0F) {
            node.change -= shift / f;
            this.change += shift / f;
        }

        node.shift += shift;
        node.y += shift;
        node.mod += shift;
    }

    private TreeNodePosition getAncestor(TreeNodePosition self, TreeNodePosition other) {
        return this.ancestor != null && self.parent.children.contains(this.ancestor) ? this.ancestor : other;
    }

    private void finalizePosition() {
        this.node.advancement().display().ifPresent(p_300991_ -> p_300991_.setLocation((float)this.x, this.y));
        if (!this.children.isEmpty()) {
            for (TreeNodePosition treenodeposition : this.children) {
                treenodeposition.finalizePosition();
            }
        }
    }

    public static void run(AdvancementNode rootNode) {
        if (rootNode.advancement().display().isEmpty()) {
            throw new IllegalArgumentException("Can't position children of an invisible root!");
        } else {
            TreeNodePosition treenodeposition = new TreeNodePosition(rootNode, null, null, 1, 0);
            treenodeposition.firstWalk();
            float f = treenodeposition.secondWalk(0.0F, 0, treenodeposition.y);
            if (f < 0.0F) {
                treenodeposition.thirdWalk(-f);
            }

            treenodeposition.finalizePosition();
        }
    }
}
