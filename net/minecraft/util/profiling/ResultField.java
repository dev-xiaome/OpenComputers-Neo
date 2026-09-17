package net.minecraft.util.profiling;

public final class ResultField implements Comparable<ResultField> {
    public final double percentage;
    public final double globalPercentage;
    public final long count;
    public final String name;

    public ResultField(String name, double percentage, double globalPercentage, long count) {
        this.name = name;
        this.percentage = percentage;
        this.globalPercentage = globalPercentage;
        this.count = count;
    }

    public int compareTo(ResultField p_18618_) {
        if (p_18618_.percentage < this.percentage) {
            return -1;
        } else {
            return p_18618_.percentage > this.percentage ? 1 : p_18618_.name.compareTo(this.name);
        }
    }

    public int getColor() {
        return (this.name.hashCode() & 11184810) + 4473924;
    }
}
