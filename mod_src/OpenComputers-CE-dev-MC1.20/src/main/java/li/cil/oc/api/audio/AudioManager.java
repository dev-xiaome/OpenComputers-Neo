package li.cil.oc.api.audio;

import java.util.HashMap;
import java.util.Map;

public final class AudioManager {
    private static int currentId = 0;
    private static final Map<Integer, StreamingAudioPlayer> players = new HashMap<>();

    public static int newPlayer() {
        StreamingAudioPlayer codec = new StreamingAudioPlayer(false, false, -1);
        players.put(currentId++, codec);
        return currentId - 1;
    }

    public static void removePlayer(int id) {
        if (players.containsKey(id)) {
            players.get(id).stop();
            players.remove(id);
        }
    }

    public static StreamingAudioPlayer getPlayer(int id) {
        return players.computeIfAbsent(id, ignored -> new StreamingAudioPlayer(false, false, -1));
    }

    public static boolean exists(int id) {
        return players.containsKey(id);
    }

    public static void removeAll() {
        players.values().forEach(StreamingAudioPlayer::stop);
        players.clear();
    }
}
