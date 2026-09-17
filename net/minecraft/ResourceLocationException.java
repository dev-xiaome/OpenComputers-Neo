package net.minecraft;

import org.apache.commons.lang3.StringEscapeUtils;

public class ResourceLocationException extends RuntimeException {
    public ResourceLocationException(String message) {
        super(StringEscapeUtils.escapeJava(message));
    }

    public ResourceLocationException(String message, Throwable cause) {
        super(StringEscapeUtils.escapeJava(message), cause);
    }
}
