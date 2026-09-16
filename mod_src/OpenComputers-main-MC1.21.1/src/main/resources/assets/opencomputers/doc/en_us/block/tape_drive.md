# Tape Drive

![Sequential storage and DFPWM playback.](oredict:opencomputers:tape_drive)

The tape drive accepts one [cassette tape](../item/tape.md) and exposes the `tape_drive` component to an adjacent OpenComputers network. It reads and writes the tape's persistent byte data, and can decode its remaining bytes as DFPWM audio.

Sneak-activate the drive to insert or eject a cassette. Activate it normally to start playback. `play()` starts playback from the current position; if the drive is connected to an [audio cable](audio_cable.md), the sound is emitted from connected [speakers](speaker.md). Otherwise, it is emitted from the drive.

The component follows the classic Computronics tape-drive interface:

* `isReady()`, `isEnd()`, `getSize()`, `getPosition()`, and `getState()`.
* `seek(amount)` is **relative**; negative values rewind and the return value is the distance actually moved.
* `read()` returns one unsigned byte; `read(count)` returns up to 256 bytes. `write(byteOrData)` accepts either form.
* `getLabel()` and `setLabel(label)` for tape metadata.
* `play()`, `stop()`, `setSpeed(0.25..2)`, and `setVolume(0..1)`.

DFPWM is emitted at 48,000 Hz. Playback advances the reported tape position at the configured speed and stops at the end. Playback uses the same PCM transport as the [audio card](../item/audiocard1.md), so server administrators can disable it with `audio.enablePcm`.

A redstone signal starts playback; removing the signal stops it.
