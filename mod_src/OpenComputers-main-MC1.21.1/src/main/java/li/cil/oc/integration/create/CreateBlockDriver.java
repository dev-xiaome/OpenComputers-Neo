package li.cil.oc.integration.create;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Function;
import java.util.function.Predicate;

final class CreateBlockDriver<T extends SmartBlockEntity> implements DriverBlock {
    private final Class<T> blockEntityClass;
    private final Function<T, ManagedEnvironment> factory;
    private final Predicate<T> predicate;

    CreateBlockDriver(final Class<T> blockEntityClass, final Function<T, ManagedEnvironment> factory) {
        this(blockEntityClass, ignored -> true, factory);
    }

    CreateBlockDriver(final Class<T> blockEntityClass, final Predicate<T> predicate,
                      final Function<T, ManagedEnvironment> factory) {
        this.blockEntityClass = blockEntityClass;
        this.predicate = predicate;
        this.factory = factory;
    }

    @Override
    public boolean worksWith(final Level level, final BlockPos pos, final Direction side) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntityClass.isInstance(blockEntity) && predicate.test(blockEntityClass.cast(blockEntity));
    }

    @Override
    public ManagedEnvironment createEnvironment(final Level level, final BlockPos pos, final Direction side) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntityClass.isInstance(blockEntity) && predicate.test(blockEntityClass.cast(blockEntity))
                ? factory.apply(blockEntityClass.cast(blockEntity)) : null;
    }
}
