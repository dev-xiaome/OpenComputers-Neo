package li.cil.oc.api.prefab;

import li.cil.oc.api.manual.ContentProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Basic implementation of a content provider based on Minecraft's resource
 * loading framework.
 * <br>
 * Beware that the manual is unaware of resource domains. In other words, two
 * paths that are identical except for their resource domain will be the same,
 * as seen from the manual. This means you should probably place your
 * documentation somewhere other than <tt>doc/</tt>, because that's where the
 * OpenComputers documentation lives, and it is queried first - meaning if you
 * have a page with the same path as one in OpenComputers, it is practically
 * unreachable (because the OC provider is always queried first).
 * <br>
 * <b>1.21.1 移植说明</b>：
 * <ul>
 * <li>{@code new ResourceLocation(namespace, path)} 的构造器已私有化，改用
 * {@link ResourceLocation#fromNamespaceAndPath(String, String)}。</li>
 * <li>{@code ResourceManager#getResource} 现在返回 {@link Optional}，并且
 * {@link Resource#open()} 需要显式关闭，这里改用 try-with-resources。</li>
 * <li>{@code Charsets.UTF_8} 换成 JDK 的 {@link StandardCharsets#UTF_8}。</li>
 * <li>手册本身是客户端功能，本类只在客户端使用。</li>
 * </ul>
 */
@SuppressWarnings("UnusedDeclaration")
public class ResourceContentProvider implements ContentProvider {
    private final String resourceDomain;

    private final String basePath;

    public ResourceContentProvider(String resourceDomain, String basePath) {
        this.resourceDomain = resourceDomain;
        this.basePath = basePath;
    }

    public ResourceContentProvider(String resourceDomain) {
        this(resourceDomain, "");
    }

    @Override
    public Iterable<String> getContent(String path) {
        final ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                resourceDomain, basePath + (path.startsWith("/") ? path.substring(1) : path));
        try {
            final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream is = resource.get().open()) {
                final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                final ArrayList<String> lines = new ArrayList<String>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                return lines;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }
}
