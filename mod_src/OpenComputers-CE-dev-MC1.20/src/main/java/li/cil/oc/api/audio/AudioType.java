package li.cil.oc.api.audio;

import java.util.function.Function;

public enum AudioType {
    Square(pos -> Math.signum(Math.sin(2 * Math.PI * pos)) * 0.5),
    Sine(pos -> Math.sin(2 * Math.PI * pos)),
    Triangle(pos -> 1.0 - (Math.abs(pos - 0.5) * 4.0)),
    Sawtooth(pos -> (double) ((2 * pos) - 1));

    private final Function<Float, Double> generator;

    AudioType(Function<Float, Double> generator) {
        this.generator = generator;
    }

    public double generate(float pos) {
        return generator.apply(pos);
    }

    public static AudioType fromIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : Square;
    }
}