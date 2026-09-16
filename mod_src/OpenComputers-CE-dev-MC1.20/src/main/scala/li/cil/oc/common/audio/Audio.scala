package li.cil.oc.common.audio

import li.cil.oc.Settings
import li.cil.oc.api.audio.{AudioHost, AudioReceiver}
import li.cil.oc.api.detail.AudioAPI

import java.util
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of currently loaded Sound Card [[AudioHost]]s, keyed by their
 * network address.
 *
 * This used to live in `li.cil.oc.client.Audio`, which is a client-only
 * `object`. Since [[li.cil.oc.server.component.SoundCard]] registers itself
 * from `onConnect`/`onDisconnect` on both logical sides (including the
 * dedicated server, where the `client` package is never loaded), the
 * registry has to live in a side-agnostic location instead.
 */
private[oc] object Audio extends AudioAPI {
  override def getSampleRate: Int = Settings.get.audioSampleRate
}