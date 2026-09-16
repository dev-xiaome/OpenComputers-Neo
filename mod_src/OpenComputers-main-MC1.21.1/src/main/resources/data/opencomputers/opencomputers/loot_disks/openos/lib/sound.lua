-- Computronics sound-card compatibility for OC's unified audio card.
-- Legacy programs can use require("sound") without requiring a second card.
local component = require("component")
local audio = component.audio

local sound = { modes = audio.modes() }
sound.channel_count = function() return audio.channel_count() end
local open = {}
local elapsed = 0
function sound.setWave(channel, wave) return audio.setWave(channel, wave) end
function sound.setFrequency(channel, frequency) return audio.setFrequency(channel, frequency) end
function sound.setVolume(channel, volume) return audio.setVolume(channel, volume) end
function sound.setTotalVolume(volume) return audio.setTotalVolume(volume) end
function sound.setLFSR(channel, initial, mask) return audio.setLFSR(channel, initial, mask) end
function sound.setADSR(channel, attack, decay, sustain, release)
  return audio.setADSR(channel, attack, decay, sustain, release)
end
function sound.resetEnvelope(channel) return audio.resetEnvelope(channel) end
function sound.setFM(channel, modulator, intensity) return audio.setFM(channel, modulator, intensity) end
function sound.resetFM(channel) return audio.resetFM(channel) end
function sound.setAM(channel, modulator) return audio.setAM(channel, modulator) end
function sound.resetAM(channel) return audio.resetAM(channel) end
function sound.open(channel) open[channel] = true return true end
function sound.close(channel) open[channel] = nil return true end
function sound.delay(milliseconds)
  for channel in pairs(open) do audio.playSynthesized(channel, milliseconds, elapsed) end
  elapsed = elapsed + milliseconds
  return true
end
function sound.process() elapsed = 0 return true end
function sound.clear() elapsed = 0 open = {} return true end
function sound.play(channel, milliseconds, delay) return audio.playSynthesized(channel, milliseconds, delay or 0) end
function sound.beep(entries) return audio.beep(entries) end
return sound
