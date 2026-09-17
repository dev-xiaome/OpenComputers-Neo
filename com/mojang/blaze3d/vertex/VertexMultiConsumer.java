package com.mojang.blaze3d.vertex;

import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VertexMultiConsumer {
    public static VertexConsumer create() {
        throw new IllegalArgumentException();
    }

    public static VertexConsumer create(VertexConsumer consumer) {
        return consumer;
    }

    public static VertexConsumer create(VertexConsumer first, VertexConsumer second) {
        return new VertexMultiConsumer.Double(first, second);
    }

    public static VertexConsumer create(VertexConsumer... delegates) {
        return new VertexMultiConsumer.Multiple(delegates);
    }

    @OnlyIn(Dist.CLIENT)
    static class Double implements VertexConsumer {
        private final VertexConsumer first;
        private final VertexConsumer second;

        public Double(VertexConsumer first, VertexConsumer second) {
            if (first == second) {
                throw new IllegalArgumentException("Duplicate delegates");
            } else {
                this.first = first;
                this.second = second;
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.first.addVertex(x, y, z);
            this.second.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.first.setColor(red, green, blue, alpha);
            this.second.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.first.setUv(u, v);
            this.second.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.first.setUv1(u, v);
            this.second.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.first.setUv2(u, v);
            this.second.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.first.setNormal(normalX, normalY, normalZ);
            this.second.setNormal(normalX, normalY, normalZ);
            return this;
        }

        @Override
        public void addVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int packedOverlay,
            int packedLight,
            float normalX,
            float normalY,
            float normalZ
        ) {
            this.first.addVertex(x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
            this.second.addVertex(x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record Multiple(VertexConsumer[] delegates) implements VertexConsumer {
        Multiple(VertexConsumer[] delegates) {
            for (int i = 0; i < delegates.length; i++) {
                for (int j = i + 1; j < delegates.length; j++) {
                    if (delegates[i] == delegates[j]) {
                        throw new IllegalArgumentException("Duplicate delegates");
                    }
                }
            }

            this.delegates = delegates;
        }

        private void forEach(Consumer<VertexConsumer> action) {
            for (VertexConsumer vertexconsumer : this.delegates) {
                action.accept(vertexconsumer);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.forEach(p_349771_ -> p_349771_.addVertex(x, y, z));
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.forEach(p_349757_ -> p_349757_.setColor(red, green, blue, alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.forEach(p_349767_ -> p_349767_.setUv(u, v));
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.forEach(p_349752_ -> p_349752_.setUv1(u, v));
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.forEach(p_349764_ -> p_349764_.setUv2(u, v));
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.forEach(p_349761_ -> p_349761_.setNormal(normalX, normalY, normalZ));
            return this;
        }

        @Override
        public void addVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int packedOverlay,
            int packedLight,
            float normalX,
            float normalY,
            float normalZ
        ) {
            this.forEach(
                p_349749_ -> p_349749_.addVertex(
                        x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ
                    )
            );
        }
    }
}
