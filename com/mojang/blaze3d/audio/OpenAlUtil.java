package com.mojang.blaze3d.audio;

import com.mojang.logging.LogUtils;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;

/**
 * The OpenALUtil class provides utility functions for working with OpenAL audio.
 */
@OnlyIn(Dist.CLIENT)
public class OpenAlUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Converts an OpenAL error code to a human-readable error message.
     * @return A String representing the error message for the given error code.
     *
     * @param errorCode The OpenAL error code to convert
     */
    private static String alErrorToString(int errorCode) {
        switch (errorCode) {
            case 40961:
                return "Invalid name parameter.";
            case 40962:
                return "Invalid enumerated parameter value.";
            case 40963:
                return "Invalid parameter parameter value.";
            case 40964:
                return "Invalid operation.";
            case 40965:
                return "Unable to allocate memory.";
            default:
                return "An unrecognized error occurred.";
        }
    }

    /**
     * Checks for an OpenAL error and logs an error message if one is found.
     * @return true if an OpenAL error was found, false otherwise.
     *
     * @param operationState A String describing the operation being performed when
     *                       the error occurred
     */
    static boolean checkALError(String operationState) {
        int i = AL10.alGetError();
        if (i != 0) {
            LOGGER.error("{}: {}", operationState, alErrorToString(i));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Converts an ALC error code to a human-readable error message.
     * @return A String representing the error message for the given error code.
     *
     * @param errorCode The ALC error code to convert
     */
    private static String alcErrorToString(int errorCode) {
        switch (errorCode) {
            case 40961:
                return "Invalid device.";
            case 40962:
                return "Invalid context.";
            case 40963:
                return "Illegal enum.";
            case 40964:
                return "Invalid value.";
            case 40965:
                return "Unable to allocate memory.";
            default:
                return "An unrecognized error occurred.";
        }
    }

    /**
     * Checks for an ALC error and logs an error message if one is found.
     * @return true if an ALC error was found, false otherwise.
     *
     * @param deviceHandle   The handle of the device to check for errors on
     * @param operationState A String describing the operation being performed when
     *                       the error occurred
     */
    static boolean checkALCError(long deviceHandle, String operationState) {
        int i = ALC10.alcGetError(deviceHandle);
        if (i != 0) {
            LOGGER.error("{} ({}): {}", operationState, deviceHandle, alcErrorToString(i));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Converts an AudioFormat object to the corresponding OpenAL audio format code.
     * @return An integer representing the corresponding OpenAL audio format code.
     * @throws IllegalArgumentException if the given AudioFormat is not a supported format.
     *
     * @param format The AudioFormat object to convert
     */
    static int audioFormatToOpenAl(AudioFormat format) {
        Encoding encoding = format.getEncoding();
        int i = format.getChannels();
        int j = format.getSampleSizeInBits();
        if (encoding.equals(Encoding.PCM_UNSIGNED) || encoding.equals(Encoding.PCM_SIGNED)) {
            if (i == 1) {
                if (j == 8) {
                    return 4352;
                }

                if (j == 16) {
                    return 4353;
                }
            } else if (i == 2) {
                if (j == 8) {
                    return 4354;
                }

                if (j == 16) {
                    return 4355;
                }
            }
        }

        throw new IllegalArgumentException("Invalid audio format: " + format);
    }
}
