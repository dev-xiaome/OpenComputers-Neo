package li.cil.oc.integration.create;

import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.Instruction;
import com.simibubi.create.content.kinetics.transmission.sequencer.InstructionSpeedModifiers;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

public final class CreateKineticEnvironments {
    private CreateKineticEnvironments() {
    }

    public static final class CreativeMotor extends CreateEnvironment<CreativeMotorBlockEntity> {
        CreativeMotor(final CreativeMotorBlockEntity blockEntity) {
            super(blockEntity, "Create_CreativeMotor");
        }

        @Callback(doc = "function(speed:number) -- Set the generated speed in RPM.")
        public Object[] setGeneratedSpeed(final Context context, final Arguments args) {
            blockEntity.generatedSpeed.setValue(args.checkInteger(0));
            return result();
        }

        @Callback(direct = true, doc = "function():number -- Get the generated speed in RPM.")
        public Object[] getGeneratedSpeed(final Context context, final Arguments args) {
            return result(blockEntity.generatedSpeed.getValue());
        }
    }

    public static final class SpeedController extends CreateEnvironment<SpeedControllerBlockEntity> {
        SpeedController(final SpeedControllerBlockEntity blockEntity) {
            super(blockEntity, "Create_RotationSpeedController");
        }

        @Callback(doc = "function(speed:number) -- Set the target speed in RPM.")
        public Object[] setTargetSpeed(final Context context, final Arguments args) {
            blockEntity.targetSpeed.setValue(args.checkInteger(0));
            return result();
        }

        @Callback(direct = true, doc = "function():number -- Get the target speed in RPM.")
        public Object[] getTargetSpeed(final Context context, final Arguments args) {
            return result(blockEntity.targetSpeed.getValue());
        }
    }

    public static final class SpeedGauge extends CreateEnvironment<SpeedGaugeBlockEntity> {
        SpeedGauge(final SpeedGaugeBlockEntity blockEntity) {
            super(blockEntity, "Create_Speedometer");
        }

        @Callback(direct = true, doc = "function():number -- Get the current kinetic speed.")
        public Object[] getSpeed(final Context context, final Arguments args) {
            return result(blockEntity.getSpeed());
        }
    }

    public static final class StressGauge extends CreateEnvironment<StressGaugeBlockEntity> {
        StressGauge(final StressGaugeBlockEntity blockEntity) {
            super(blockEntity, "Create_Stressometer");
        }

        @Callback(direct = true, doc = "function():number -- Get the current network stress.")
        public Object[] getStress(final Context context, final Arguments args) {
            return result(blockEntity.getNetworkStress());
        }

        @Callback(direct = true, doc = "function():number -- Get the current network stress capacity.")
        public Object[] getStressCapacity(final Context context, final Arguments args) {
            return result(blockEntity.getNetworkCapacity());
        }
    }

    public static final class SequencedGearshift extends CreateEnvironment<SequencedGearshiftBlockEntity> {
        SequencedGearshift(final SequencedGearshiftBlockEntity blockEntity) {
            super(blockEntity, "Create_SequencedGearshift");
        }

        @Callback(doc = "function(angle:number[, speedModifier:number]) -- Rotate by an angle.")
        public Object[] rotate(final Context context, final Arguments args) {
            runInstruction(args, SequencerInstructions.TURN_ANGLE);
            return result();
        }

        @Callback(doc = "function(distance:number[, speedModifier:number]) -- Move by a distance.")
        public Object[] move(final Context context, final Arguments args) {
            runInstruction(args, SequencerInstructions.TURN_DISTANCE);
            return result();
        }

        @Callback(direct = true, doc = "function():boolean -- Whether the current sequence is running.")
        public Object[] isRunning(final Context context, final Arguments args) {
            return result(!blockEntity.isIdle());
        }

        private void runInstruction(final Arguments args, final SequencerInstructions instructionType) {
            final int speedModifier = args.optInteger(1, 1);
            blockEntity.getInstructions().clear();
            blockEntity.getInstructions().add(new Instruction(instructionType,
                    InstructionSpeedModifiers.getByModifier(speedModifier), Math.abs(args.checkInteger(0))));
            blockEntity.getInstructions().add(new Instruction(SequencerInstructions.END));
            blockEntity.run(0);
        }
    }
}
