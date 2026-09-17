package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LlamaSpitModel<T extends Entity> extends HierarchicalModel<T> {
    private static final String MAIN = "main";
    private final ModelPart root;

    public LlamaSpitModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        int i = 2;
        partdefinition.addOrReplaceChild(
            "main",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F)
                .addBox(0.0F, -4.0F, 0.0F, 2.0F, 2.0F, 2.0F)
                .addBox(0.0F, 0.0F, -4.0F, 2.0F, 2.0F, 2.0F)
                .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F)
                .addBox(2.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F)
                .addBox(0.0F, 2.0F, 0.0F, 2.0F, 2.0F, 2.0F)
                .addBox(0.0F, 0.0F, 2.0F, 2.0F, 2.0F, 2.0F),
            PartPose.ZERO
        );
        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    /**
     * Sets this entity's model rotation angles
     */
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
