# Speaker

![A physical output for synthesized tones and tape playback.](oredict:opencomputers:speaker)

A speaker is an audio endpoint. Connect it to a [tape drive](tape_drive.md) with [audio cables](audio_cable.md) to move tape playback from the drive to the speaker's position. Multiple speakers on the same cable network play together.

Speakers also expose a `speaker` component. `speaker.play([frequency[, seconds]])` emits a bounded synthesized square-wave tone at the speaker. They remain physical endpoints: if the cable network has no speaker, the tape drive uses its own internal output instead.
