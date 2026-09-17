package net.minecraft.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RaftModel extends ListModel<Boat> {
    private static final String LEFT_PADDLE = "left_paddle";
    private static final String RIGHT_PADDLE = "right_paddle";
    private static final String BOTTOM = "bottom";
    private final ModelPart leftPaddle;
    private final ModelPart rightPaddle;
    private final ImmutableList<ModelPart> parts;

    public RaftModel(ModelPart root) {
        this.leftPaddle = root.getChild("left_paddle");
        this.rightPaddle = root.getChild("right_paddle");
        this.parts = this.createPartsBuilder(root).build();
    }

    protected Builder<ModelPart> createPartsBuilder(ModelPart root) {
        Builder<ModelPart> builder = new Builder<>();
        builder.add(root.getChild("bottom"), this.leftPaddle, this.rightPaddle);
        return builder;
    }

    public static void createChildren(PartDefinition root) {
        root.addOrReplaceChild(
            "bottom",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-14.0F, -11.0F, -4.0F, 28.0F, 20.0F, 4.0F)
                .texOffs(0, 0)
                .addBox(-14.0F, -9.0F, -8.0F, 28.0F, 16.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, -2.1F, 1.0F, 1.5708F, 0.0F, 0.0F)
        );
        int i = 20;
        int j = 7;
        int k = 6;
        float f = -5.0F;
        root.addOrReplaceChild(
            "left_paddle",
            CubeListBuilder.create().texOffs(0, 24).addBox(-1.0F, 0.0F, -5.0F, 2.0F, 2.0F, 18.0F).addBox(-1.001F, -3.0F, 8.0F, 1.0F, 6.0F, 7.0F),
            PartPose.offsetAndRotation(3.0F, -4.0F, 9.0F, 0.0F, 0.0F, (float) (Math.PI / 16))
        );
        root.addOrReplaceChild(
            "right_paddle",
            CubeListBuilder.create().texOffs(40, 24).addBox(-1.0F, 0.0F, -5.0F, 2.0F, 2.0F, 18.0F).addBox(0.001F, -3.0F, 8.0F, 1.0F, 6.0F, 7.0F),
            PartPose.offsetAndRotation(3.0F, -4.0F, -9.0F, 0.0F, (float) Math.PI, (float) (Math.PI / 16))
        );
    }

    public static LayerDefinition createBodyModel() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        createChildren(partdefinition);
        return LayerDefinition.create(meshdefinition, 128, 64);
    }

    /**
     * Sets this entity's model rotation angles
     */
    public void setupAnim(Boat entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        animatePaddle(entity, 0, this.leftPaddle, limbSwing);
        animatePaddle(entity, 1, this.rightPaddle, limbSwing);
    }

    public ImmutableList<ModelPart> parts() {
        return this.parts;
    }

    private static void animatePaddle(Boat boat, int side, ModelPart paddle, float limbSwing) {
        float f = boat.getRowingTime(side, limbSwing);
        paddle.xRot = Mth.clampedLerp((float) (-Math.PI / 3), (float) (-Math.PI / 12), (Mth.sin(-f) + 1.0F) / 2.0F);
        paddle.yRot = Mth.clampedLerp((float) (-Math.PI / 4), (float) (Math.PI / 4), (Mth.sin(-f + 1.0F) + 1.0F) / 2.0F);
        if (side == 1) {
            paddle.yRot = (float) Math.PI - paddle.yRot;
        }
    }
}
