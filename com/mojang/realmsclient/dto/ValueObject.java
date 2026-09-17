package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class ValueObject {
    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder("{");

        for (Field field : this.getClass().getFields()) {
            if (!isStatic(field)) {
                try {
                    stringbuilder.append(getName(field)).append("=").append(field.get(this)).append(" ");
                } catch (IllegalAccessException illegalaccessexception) {
                }
            }
        }

        stringbuilder.deleteCharAt(stringbuilder.length() - 1);
        stringbuilder.append('}');
        return stringbuilder.toString();
    }

    private static String getName(Field field) {
        SerializedName serializedname = field.getAnnotation(SerializedName.class);
        return serializedname != null ? serializedname.value() : field.getName();
    }

    private static boolean isStatic(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }
}
