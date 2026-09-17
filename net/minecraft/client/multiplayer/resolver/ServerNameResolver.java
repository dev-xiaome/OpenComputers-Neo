package net.minecraft.client.multiplayer.resolver;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ServerNameResolver {
    public static final ServerNameResolver DEFAULT = new ServerNameResolver(
        ServerAddressResolver.SYSTEM, ServerRedirectHandler.createDnsSrvRedirectHandler(), AddressCheck.createFromService()
    );
    private final ServerAddressResolver resolver;
    private final ServerRedirectHandler redirectHandler;
    private final AddressCheck addressCheck;

    @VisibleForTesting
    ServerNameResolver(ServerAddressResolver resolver, ServerRedirectHandler redirectHandler, AddressCheck addressCheck) {
        this.resolver = resolver;
        this.redirectHandler = redirectHandler;
        this.addressCheck = addressCheck;
    }

    public Optional<ResolvedServerAddress> resolveAddress(ServerAddress serverAddress) {
        Optional<ResolvedServerAddress> optional = this.resolver.resolve(serverAddress);
        if ((!optional.isPresent() || this.addressCheck.isAllowed(optional.get())) && this.addressCheck.isAllowed(serverAddress)) {
            Optional<ServerAddress> optional1 = this.redirectHandler.lookupRedirect(serverAddress);
            if (optional1.isPresent()) {
                optional = this.resolver.resolve(optional1.get()).filter(this.addressCheck::isAllowed);
            }

            return optional;
        } else {
            return Optional.empty();
        }
    }
}
