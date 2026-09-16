# Audio Card

![PCM streaming and sound synthesis.](oredict:opencomputers:audioCard1)

The audio card is OpenComputers' unified sound device. It has two independent interfaces on the `audio` component:

* PCM streaming for programs that send recorded or generated sample data.
* An eight-channel synthesizer compatible with the classic Computronics sound-card workflow.

The server controls raw PCM with `audio.enablePcm`. When it is disabled, `open()` returns an error and PCM playback—including [tape drive](../block/tape_drive.md) playback—is unavailable. Synthesized tones continue to work. All OC audio, including PCM and synthesized tones, follows Sable/Aeronautics sublevels in physical world space.

## PCM streaming

Call `open(channel, sampleRate, mode)` to create a handle, `send(handle, data)` to append samples, and `play(handle)` to send the completed buffer to nearby players. Use `pause`, `resume`, `stop`, `setLoop`, and `close` to control the handle.

Supported modes are `mono8`, `mono16`, `stereo8`, and `stereo16`. `mono8` is the useful format for DFPWM-decoded samples. The server's audio configuration limits the maximum sample rate, buffer size, and packet chunk size.

```lua
local audio = require("component").audio
local handle, reason = audio.open(0, 12000, "mono8")
assert(handle, reason)
assert(audio.send(handle, samples))
assert(audio.play(handle))
```

## Synthesizer and compatibility

`audio.modes()` returns square, sine, triangle, sawtooth, and noise wave modes. Configure a channel with `setWave`, `setFrequency`, `setVolume`, `setADSR`, `setFM`, and `setAM`, then call `playSynthesized(channel, milliseconds[, delay])`. `beep({[frequency] = seconds})` plays up to eight square-wave tones at once.

OpenOS also provides `require("sound")`, a compatibility wrapper for old Computronics programs. It supports `open`, `close`, `delay`, `process`, wave/frequency/volume/ADSR setup, AM/FM, and `beep`; it delegates to the same audio card. There is no separate sound-card item.

```lua
local sound = require("sound")
sound.setWave(1, sound.modes.sine)
sound.setFrequency(1, 440)
sound.open(1)
sound.delay(1000)
sound.close(1)
sound.process()
```
