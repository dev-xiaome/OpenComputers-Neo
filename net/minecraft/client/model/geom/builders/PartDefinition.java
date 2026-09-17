package net.minecraft.client.model.geom.builders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PartDefinition {
    private final List<CubeDefinition> cubes;
    private final PartPose partPose;
    private final Map<String, PartDefinition> children = Maps.newHashMap();

    PartDefinition(List<CubeDefinition> cubes, PartPose partPose) {
        this.cubes = cubes;
        this.partPose = partPose;
    }

    public PartDefinition addOrReplaceChild(String name, CubeListBuilder cubes, PartPose partPose) {
        PartDefinition partdefinition = new PartDefinition(cubes.getCubes(), partPose);
        PartDefinition partdefinition1 = this.children.put(name, partdefinition);
        if (partdefinition1 != null) {
            partdefinition.children.putAll(partdefinition1.children);
        }

        return partdefinition;
    }

    public ModelPart bake(int texWidth, int texHeight) {
        Object2ObjectArrayMap<String, ModelPart> object2objectarraymap = this.children
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    p_171593_ -> p_171593_.getValue().bake(texWidth, texHeight),
                    (p_171595_, p_171596_) -> p_171595_,
                    Object2ObjectArrayMap::new
                )
            );
        List<ModelPart.Cube> list = this.cubes.stream().map(p_171589_ -> p_171589_.bake(texWidth, texHeight)).collect(ImmutableList.toImmutableList());
        ModelPart modelpart = new ModelPart(list, object2objectarraymap);
        modelpart.setInitialPose(this.partPose);
        modelpart.loadPose(this.partPose);
        return modelpart;
    }

    public PartDefinition getChild(String name) {
        return this.children.get(name);
    }
}
