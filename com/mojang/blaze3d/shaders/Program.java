package com.mojang.blaze3d.shaders;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

@OnlyIn(Dist.CLIENT)
public class Program {
    private static final int MAX_LOG_LENGTH = 32768;
    private final Program.Type type;
    private final String name;
    private int id;

    protected Program(Program.Type type, int id, String name) {
        this.type = type;
        this.id = id;
        this.name = name;
    }

    public void attachToShader(Shader shader) {
        RenderSystem.assertOnRenderThread();
        GlStateManager.glAttachShader(shader.getId(), this.getId());
    }

    public void close() {
        if (this.id != -1) {
            RenderSystem.assertOnRenderThread();
            GlStateManager.glDeleteShader(this.id);
            this.id = -1;
            this.type.getPrograms().remove(this.name);
        }
    }

    public String getName() {
        return this.name;
    }

    public static Program compileShader(Program.Type type, String name, InputStream shaderData, String sourceName, GlslPreprocessor preprocessor) throws IOException {
        RenderSystem.assertOnRenderThread();
        int i = compileShaderInternal(type, name, shaderData, sourceName, preprocessor);
        Program program = new Program(type, i, name);
        type.getPrograms().put(name, program);
        return program;
    }

    protected static int compileShaderInternal(Program.Type type, String name, InputStream shaderData, String sourceName, GlslPreprocessor preprocessor) throws IOException {
        String s = IOUtils.toString(shaderData, StandardCharsets.UTF_8);
        if (s == null) {
            throw new IOException("Could not load program " + type.getName());
        } else {
            int i = GlStateManager.glCreateShader(type.getGlType());
            GlStateManager.glShaderSource(i, preprocessor.process(s));
            GlStateManager.glCompileShader(i);
            if (GlStateManager.glGetShaderi(i, 35713) == 0) {
                String s1 = StringUtils.trim(GlStateManager.glGetShaderInfoLog(i, 32768));
                throw new IOException("Couldn't compile " + type.getName() + " program (" + sourceName + ", " + name + ") : " + s1);
            } else {
                return i;
            }
        }
    }

    protected int getId() {
        return this.id;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Type {
        VERTEX("vertex", ".vsh", 35633),
        FRAGMENT("fragment", ".fsh", 35632);

        private final String name;
        private final String extension;
        private final int glType;
        private final Map<String, Program> programs = Maps.newHashMap();

        private Type(String name, String extension, int glType) {
            this.name = name;
            this.extension = extension;
            this.glType = glType;
        }

        public String getName() {
            return this.name;
        }

        public String getExtension() {
            return this.extension;
        }

        int getGlType() {
            return this.glType;
        }

        public Map<String, Program> getPrograms() {
            return this.programs;
        }
    }
}
