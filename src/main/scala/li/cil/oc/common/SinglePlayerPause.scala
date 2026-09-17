package li.cil.oc.common

/** Client pause flag mirrored for server-side machine threads (integrated server). */
object SinglePlayerPause {
  @volatile var isPaused = false
}
