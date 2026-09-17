package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Strider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StriderModel<T extends Strider> extends HierarchicalModel<T> {
    private static final String RIGHT_BOTTOM_BRISTLE = "right_bottom_bristle";
    private static final String RIGHT_MIDDLE_BRISTLE = "right_middle_bristle";
    private static final String RIGHT_TOP_BRISTLE = "right_top_bristle";
    private static final String LEFT_TOP_BRISTLE = "left_top_bristle";
    private static final String LEFT_MIDDLE_BRISTLE = "left_middle_bristle";
    private static final String LEFT_BOTTOM_BRISTLE = "left_bottom_bristle";
    private final ModelPart root;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart body;
    private final ModelPart rightBottomBristle;
    private final ModelPart rightMiddleBristle;
    private final ModelPart rightTopBristle;
    private final ModelPart leftTopBristle;
    private final ModelPart leftMiddleBristle;
    private final ModelPart leftBottomBristle;

    public StriderModel(ModelPart root) {
        this.root = root;
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.body = root.getChild("body");
        this.rightBottomBristle = this.body.getChild("right_bottom_bristle");
        this.rightMiddleBristle = this.body.getChild("right_middle_bristle");
        this.rightTopBristle = this.body.getChild("right_top_bristle");
        this.leftTopBristle = this.body.getChild("left_top_bristle");
        this.leftMiddleBristle = this.body.getChild("left_middle_bristle");
        this.leftBottomBristle = this.body.getChild("left_bottom_bristle");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        partdefinition.addOrReplaceChild(
            "right_leg", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 16.0F, 4.0F), PartPose.offset(-4.0F, 8.0F, 0.0F)
        );
        partdefinition.addOrReplaceChild(
            "left_leg", CubeListBuilder.create().texOffs(0, 55).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 16.0F, 4.0F), PartPose.offset(4.0F, 8.0F, 0.0F)
        );
        PartDefinition partdefinition1 = partdefinition.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -6.0F, -8.0F, 16.0F, 14.0F, 16.0F), PartPose.offset(0.0F, 1.0F, 0.0F)
        );
        partdefinition1.addOrReplaceChild(
            "right_bottom_bristle",
            CubeListBuilder.create().texOffs(16, 65).addBox(-12.0F, 0.0F, 0.0F, 12.0F, 0.0F, 16.0F, true),
            PartPose.offsetAndRotation(-8.0F, 4.0F, -8.0F, 0.0F, 0.0F, -1.2217305F)
        );
        partdefinition1.addOrReplaceChild(
            "right_middle_bristle",
            CubeListBuilder.create().texOffs(16, 49).addBox(-12.0F, 0.0F, 0.0F, 12.0F, 0.0F, 16.0F, true),
            PartPose.offsetAndRotation(-8.0F, -1.0F, -8.0F, 0.0F, 0.0F, -1.134464F)
        );
        partdefinition1.addOrReplaceChild(
            "right_top_bristle",
            CubeListBuilder.create().texOffs(16, 33).addBox(-12.0F, 0.0F, 0.0F, 12.0F, 0.0F, 16.0F, true),
            PartPose.offsetAndRotation(-8.0F, -5.0F, -8.0F, 0.0F, 0.0F, -0.87266463F)
        );
        partdefinition1.addOrReplaceChild(
            "left_top_bristle",
            CubeListBuilder.create().texOffs(16, 33).addBox(0.0F, 0.0F, 0.0F, 12.0F, 0.0F, 16.0F),
            PartPose.offsetAndRotation(8.0F, -6.0F, -8.0F, 0.0F, 0.0F, 0.87266463F)
        );
        partdefinition1.addOrReplaceChild(
            "left_middle_bristle",
            CubeListBuilder.create().texOffs(16, 49).addBox(0.0F, 0.0F, 0.0F, 12.0F, 0.0F, 16.0F),
            PartPose.offsetAndRotation(8.0F, -2.0F, -8.0F, 0.0F, 0.0F, 1.134464F)
        );
        partdefinition1.addOrReplaceChild(
            "left_bottom_bristle",
            CubeListBuilder.create().texOffs(16, 65).addBox(0.0F, 0.0F, 0.0F, 12.0F, 0.0F, 16.0F),
            PartPose.offsetAndRotation(8.0F, 3.0F, -8.0F, 0.0F, 0.0F, 1.2217305F)
        );
        return LayerDefinition.create(meshdefinition, 64, 128);
    }

    /**
     * Sets this entity's model rotation angles
     */
    public void setupAnim(Strider entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        limbSwingAmount = Math.min(0.25F, limbSwingAmount);
        if (!entity.isVehicle()) {
            this.body.xRot = headPitch * (float) (Math.PI / 180.0);
            this.body.yRot = netHeadYaw * (float) (Math.PI / 180.0);
        } else {
            this.body.xRot = 0.0F;
            this.body.yRot = 0.0F;
        }

        float f = 1.5F;
        this.body.zRot = 0.1F * Mth.sin(limbSwing * 1.5F) * 4.0F * limbSwingAmount;
        this.body.y = 2.0F;
        this.body.y = this.body.y - 2.0F * Mth.cos(limbSwing * 1.5F) * 2.0F * limbSwingAmount;
        this.leftLeg.xRot = Mth.sin(limbSwing * 1.5F * 0.5F) * 2.0F * limbSwingAmount;
        this.rightLeg.xRot = Mth.sin(limbSwing * 1.5F * 0.5F + (float) Math.PI) * 2.0F * limbSwingAmount;
        this.leftLeg.zRot = (float) (Math.PI / 18) * Mth.cos(limbSwing * 1.5F * 0.5F) * limbSwingAmount;
        this.rightLeg.zRot = (float) (Math.PI / 18) * Mth.cos(limbSwing * 1.5F * 0.5F + (float) Math.PI) * limbSwingAmount;
        this.leftLeg.y = 8.0F + 2.0F * Mth.sin(limbSwing * 1.5F * 0.5F + (float) Math.PI) * 2.0F * limbSwingAmount;
        this.rightLeg.y = 8.0F + 2.0F * Mth.sin(limbSwing * 1.5F * 0.5F) * 2.0F * limbSwingAmount;
        this.rightBottomBristle.zRot = -1.2217305F;
        this.rightMiddleBristle.zRot = -1.134464F;
        this.rightTopBristle.zRot = -0.87266463F;
        this.leftTopBristle.zRot = 0.87266463F;
        this.leftMiddleBristle.zRot = 1.134464F;
        this.leftBottomBristle.zRot = 1.2217305F;
        float f1 = Mth.cos(limbSwing * 1.5F + (float) Math.PI) * limbSwingAmount;
        this.rightBottomBristle.zRot += f1 * 1.3F;
        this.rightMiddleBristle.zRot += f1 * 1.2F;
        this.rightTopBristle.zRot += f1 * 0.6F;
        this.leftTopBristle.zRot += f1 * 0.6F;
        this.leftMiddleBristle.zRot += f1 * 1.2F;
        this.leftBottomBristle.zRot += f1 * 1.3F;
        float f2 = 1.0F;
        float f3 = 1.0F;
        this.rightBottomBristle.zRot = this.rightBottomBristle.zRot + 0.05F * Mth.sin(ageInTicks * 1.0F * -0.4F);
        this.rightMiddleBristle.zRot = this.rightMiddleBristle.zRot + 0.1F * Mth.sin(ageInTicks * 1.0F * 0.2F);
        this.rightTopBristle.zRot = this.rightTopBristle.zRot + 0.1F * Mth.sin(ageInTicks * 1.0F * 0.4F);
        this.leftTopBristle.zRot = this.leftTopBristle.zRot + 0.1F * Mth.sin(ageInTicks * 1.0F * 0.4F);
        this.leftMiddleBristle.zRot = this.leftMiddleBristle.zRot + 0.1F * Mth.sin(ageInTicks * 1.0F * 0.2F);
        this.leftBottomBristle.zRot = this.leftBottomBristle.zRot + 0.05F * Mth.sin(ageInTicks * 1.0F * -0.4F);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
