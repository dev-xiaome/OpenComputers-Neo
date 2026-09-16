package li.cil.oc.integration.create;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;

import java.util.HashSet;
import java.util.Set;

abstract class CreateEnvironment<T extends SmartBlockEntity> extends AbstractManagedEnvironment implements NamedBlock {
    protected final T blockEntity;
    private final String componentName;
    private final Set<String> attachedNodes = new HashSet<>();

    protected CreateEnvironment(final T blockEntity, final String componentName) {
        this.blockEntity = blockEntity;
        this.componentName = componentName;
        setNode(Network.newNode(this, Visibility.Network).withComponent(componentName).create());
        CreateComputerBridge.addEnvironment(blockEntity, this);
    }

    @Override
    public String preferredName() {
        return componentName;
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public void onConnect(final Node node) {
        super.onConnect(node);
        // Block environments are always nested in CompoundBlockEnvironment, so
        // their direct neighbor is the compound node rather than a Context.
        if (node != node() && node.address() != null && attachedNodes.add(node.address())) {
            if (attachedNodes.size() == 1)
                onFirstComputerAttach();
            CreateComputerBridge.setAttached(blockEntity, this, true);
        }
    }

    @Override
    public void onDisconnect(final Node node) {
        super.onDisconnect(node);
        if (node == node()) {
            final boolean wasAttached = !attachedNodes.isEmpty();
            attachedNodes.clear();
            CreateComputerBridge.removeEnvironment(blockEntity, this);
            if (wasAttached)
                onLastComputerDetach();
        } else if (node.address() != null && attachedNodes.remove(node.address()) && attachedNodes.isEmpty()) {
            CreateComputerBridge.setAttached(blockEntity, this, false);
            onLastComputerDetach();
        }
    }

    final void queueCreateEvent(final String name, final Object... arguments) {
        final Object[] signal = new Object[arguments.length + 2];
        signal[0] = name;
        signal[1] = attachedNodes.stream().findFirst().orElse(node().address());
        System.arraycopy(arguments, 0, signal, 2, arguments.length);
        node().sendToReachable("computer.signal", signal);
    }

    final boolean hasAttachedComputer() {
        return !attachedNodes.isEmpty();
    }

    final T createBlockEntity() {
        return blockEntity;
    }

    protected void onFirstComputerAttach() {
    }

    protected void onLastComputerDetach() {
    }

    protected static Object[] result(final Object... values) {
        return values;
    }
}
