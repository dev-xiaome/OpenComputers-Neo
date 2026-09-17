package net.minecraft.util.debugchart;

import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;

public class RemoteSampleLogger extends AbstractSampleLogger {
    private final DebugSampleSubscriptionTracker subscriptionTracker;
    private final RemoteDebugSampleType sampleType;

    public RemoteSampleLogger(int size, DebugSampleSubscriptionTracker subscriptionTracker, RemoteDebugSampleType sampleType) {
        this(size, subscriptionTracker, sampleType, new long[size]);
    }

    public RemoteSampleLogger(int size, DebugSampleSubscriptionTracker subscriptionTracker, RemoteDebugSampleType sampleType, long[] defaults) {
        super(size, defaults);
        this.subscriptionTracker = subscriptionTracker;
        this.sampleType = sampleType;
    }

    @Override
    protected void useSample() {
        this.subscriptionTracker.broadcast(new ClientboundDebugSamplePacket((long[])this.sample.clone(), this.sampleType));
    }
}
