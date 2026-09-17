package net.minecraft.client.renderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Rect2i {
    private int xPos;
    private int yPos;
    private int width;
    private int height;

    public Rect2i(int xPos, int yPos, int width, int height) {
        this.xPos = xPos;
        this.yPos = yPos;
        this.width = width;
        this.height = height;
    }

    public Rect2i intersect(Rect2i other) {
        int i = this.xPos;
        int j = this.yPos;
        int k = this.xPos + this.width;
        int l = this.yPos + this.height;
        int i1 = other.getX();
        int j1 = other.getY();
        int k1 = i1 + other.getWidth();
        int l1 = j1 + other.getHeight();
        this.xPos = Math.max(i, i1);
        this.yPos = Math.max(j, j1);
        this.width = Math.max(0, Math.min(k, k1) - this.xPos);
        this.height = Math.max(0, Math.min(l, l1) - this.yPos);
        return this;
    }

    public int getX() {
        return this.xPos;
    }

    public int getY() {
        return this.yPos;
    }

    public void setX(int xPos) {
        this.xPos = xPos;
    }

    public void setY(int yPos) {
        this.yPos = yPos;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setPosition(int xPos, int yPos) {
        this.xPos = xPos;
        this.yPos = yPos;
    }

    public boolean contains(int x, int y) {
        return x >= this.xPos && x <= this.xPos + this.width && y >= this.yPos && y <= this.yPos + this.height;
    }
}
