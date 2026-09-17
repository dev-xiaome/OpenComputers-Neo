package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public abstract class HierarchicalModel<E extends Entity> extends EntityModel<E> {
    private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();

    public HierarchicalModel() {
        this(RenderType::entityCutoutNoCull);
    }

    public HierarchicalModel(Function<ResourceLocation, RenderType> renderType) {
        super(renderType);
    }

    protected static net.neoforged.neoforge.client.entity.animation.json.AnimationHolder getAnimation(ResourceLocation key) {
        return net.neoforged.neoforge.client.entity.animation.json.AnimationLoader.INSTANCE.getAnimationHolder(key);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.root().render(poseStack, buffer, packedLight, packedOverlay, color);
    }

    public abstract ModelPart root();

    public Optional<ModelPart> getAnyDescendantWithName(String name) {
        return name.equals("root")
            ? Optional.of(this.root())
            : this.root().getAllParts().filter(p_233400_ -> p_233400_.hasChild(name)).findFirst().map(p_233397_ -> p_233397_.getChild(name));
    }

    protected void animate(AnimationState animationState, AnimationDefinition animationDefinition, float ageInTicks) {
        this.animate(animationState, animationDefinition, ageInTicks, 1.0F);
    }

    protected void animate(AnimationState animationState, net.neoforged.neoforge.client.entity.animation.json.AnimationHolder animation, float ageInTicks) {
        this.animate(animationState, animation.get(), ageInTicks);
    }

    protected void animateWalk(AnimationDefinition animationDefinition, float limbSwing, float limbSwingAmount, float maxAnimationSpeed, float animationScaleFactor) {
        long i = (long)(limbSwing * 50.0F * maxAnimationSpeed);
        float f = Math.min(limbSwingAmount * animationScaleFactor, 1.0F);
        KeyframeAnimations.animate(this, animationDefinition, i, f, ANIMATION_VECTOR_CACHE);
    }

    protected void animateWalk(net.neoforged.neoforge.client.entity.animation.json.AnimationHolder animation, float limbSwing, float limbSwingAmount, float maxAnimationSpeed, float animationScaleFactor) {
        this.animateWalk(animation.get(), limbSwing, limbSwingAmount, maxAnimationSpeed, animationScaleFactor);
    }

    protected void animate(AnimationState animationState, AnimationDefinition animationDefinition, float ageInTicks, float speed) {
        animationState.updateTime(ageInTicks, speed);
        animationState.ifStarted(p_233392_ -> KeyframeAnimations.animate(this, animationDefinition, p_233392_.getAccumulatedTime(), 1.0F, ANIMATION_VECTOR_CACHE));
    }

    protected void animate(AnimationState animationState, net.neoforged.neoforge.client.entity.animation.json.AnimationHolder animation, float ageInTicks, float speed) {
        this.animate(animationState, animation.get(), ageInTicks, speed);
    }

    protected void applyStatic(AnimationDefinition animationDefinition) {
        KeyframeAnimations.animate(this, animationDefinition, 0L, 1.0F, ANIMATION_VECTOR_CACHE);
    }

    protected void applyStatic(net.neoforged.neoforge.client.entity.animation.json.AnimationHolder animation) {
        this.applyStatic(animation.get());
    }
}
