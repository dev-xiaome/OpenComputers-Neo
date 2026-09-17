package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.lighting.LevelLightEngine;

public interface BlockAndTintGetter extends BlockGetter, net.neoforged.neoforge.common.extensions.IBlockAndTintGetterExtension {
    float getShade(Direction direction, boolean shade);

    LevelLightEngine getLightEngine();

    int getBlockTint(BlockPos blockPos, ColorResolver colorResolver);

    default int getBrightness(LightLayer lightType, BlockPos blockPos) {
        return this.getLightEngine().getLayerListener(lightType).getLightValue(blockPos);
    }

    default int getRawBrightness(BlockPos blockPos, int amount) {
        return this.getLightEngine().getRawBrightness(blockPos, amount);
    }

    default boolean canSeeSky(BlockPos blockPos) {
        return this.getBrightness(LightLayer.SKY, blockPos) >= this.getMaxLightLevel();
    }
}
