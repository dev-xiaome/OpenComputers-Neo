package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class EntityAttachments {
    private final Map<EntityAttachment, List<Vec3>> attachments;

    EntityAttachments(Map<EntityAttachment, List<Vec3>> attachments) {
        this.attachments = attachments;
    }

    public static EntityAttachments createDefault(float width, float height) {
        return builder().build(width, height);
    }

    public static EntityAttachments.Builder builder() {
        return new EntityAttachments.Builder();
    }

    public EntityAttachments scale(float xScale, float yScale, float zScale) {
        Map<EntityAttachment, List<Vec3>> map = new EnumMap<>(EntityAttachment.class);

        for (Entry<EntityAttachment, List<Vec3>> entry : this.attachments.entrySet()) {
            map.put(entry.getKey(), scalePoints(entry.getValue(), xScale, yScale, zScale));
        }

        return new EntityAttachments(map);
    }

    private static List<Vec3> scalePoints(List<Vec3> attachmentPoints, float xScale, float yScale, float zScale) {
        List<Vec3> list = new ArrayList<>(attachmentPoints.size());

        for (Vec3 vec3 : attachmentPoints) {
            list.add(vec3.multiply((double)xScale, (double)yScale, (double)zScale));
        }

        return list;
    }

    @Nullable
    public Vec3 getNullable(EntityAttachment attachment, int index, float yRot) {
        List<Vec3> list = this.attachments.get(attachment);
        return index >= 0 && index < list.size() ? transformPoint(list.get(index), yRot) : null;
    }

    public Vec3 get(EntityAttachment attachment, int index, float yRot) {
        Vec3 vec3 = this.getNullable(attachment, index, yRot);
        if (vec3 == null) {
            throw new IllegalStateException("Had no attachment point of type: " + attachment + " for index: " + index);
        } else {
            return vec3;
        }
    }

    public Vec3 getClamped(EntityAttachment attachment, int index, float yRot) {
        List<Vec3> list = this.attachments.get(attachment);
        if (list.isEmpty()) {
            throw new IllegalStateException("Had no attachment points of type: " + attachment);
        } else {
            Vec3 vec3 = list.get(Mth.clamp(index, 0, list.size() - 1));
            return transformPoint(vec3, yRot);
        }
    }

    private static Vec3 transformPoint(Vec3 point, float yRot) {
        return point.yRot(-yRot * (float) (Math.PI / 180.0));
    }

    public static class Builder {
        private final Map<EntityAttachment, List<Vec3>> attachments = new EnumMap<>(EntityAttachment.class);

        Builder() {
        }

        public EntityAttachments.Builder attach(EntityAttachment attachment, float x, float y, float z) {
            return this.attach(attachment, new Vec3((double)x, (double)y, (double)z));
        }

        public EntityAttachments.Builder attach(EntityAttachment attachment, Vec3 poas) {
            this.attachments.computeIfAbsent(attachment, p_316616_ -> new ArrayList<>(1)).add(poas);
            return this;
        }

        public EntityAttachments build(float width, float height) {
            Map<EntityAttachment, List<Vec3>> map = new EnumMap<>(EntityAttachment.class);

            for (EntityAttachment entityattachment : EntityAttachment.values()) {
                List<Vec3> list = this.attachments.get(entityattachment);
                map.put(entityattachment, list != null ? List.copyOf(list) : entityattachment.createFallbackPoints(width, height));
            }

            return new EntityAttachments(map);
        }
    }
}
