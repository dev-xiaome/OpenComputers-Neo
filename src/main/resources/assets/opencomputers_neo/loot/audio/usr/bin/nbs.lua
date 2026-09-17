local nbs = require("nbs")
local component = require("component")
local filesystem = require("filesystem")
local sound = component.sound
local shell = require("shell")
local notelib = require("note")

local instruments = {
    {sound.modes.square, 1},
    {sound.modes.triangle, 0.6},
    {sound.modes.noise, 0.6},
    {sound.modes.noise, 0.6},
    {sound.modes.noise, 0.6},
    {sound.modes.triangle, 0.6},
    {sound.modes.sine, 0.6},
    {sound.modes.sine, 0.6},
    {sound.modes.square, 0.6},
    {sound.modes.noise, 0.6}
}

local args, options = shell.parse(...)


if #args == 0 then
    io.write("Usage: nbs <filename>")
    return
end

local filename = shell.resolve(args[1])

if not filesystem.exists(filename) then
    io.stderr:write("no such file")
    return 1
end

local data = nbs.read(filename)

function resetState()
    sound.setTotalVolume(1)
    for i=1, 8 do
        sound.setWave(i, sound.modes.sine)
        sound.setFrequency(i, 0)
        sound.setVolume(i, 0)
        sound.resetFM(i)
        sound.resetAM(i)
        sound.resetEnvelope(i)
        sound.open(i)
    end
    sound.process()
end

resetState()

local frame = math.floor(1000 / (data.header.tempo / 100))
local sleep = frame / 1000 - 0.02

local fb = frame / 32
local notes = data.notes
local prev_note = nil
local channel = 1

for i = 0, data.header.song_length do
    if notes[i] then
        local note = notes[i]

        local instrument = instruments[note.instrument]
        local freq = notelib.freq(note.key)

        if prev_note and note.tick == prev_note.tick then
            channel = channel + 1
        else
            channel = 1
        end

        sound.setWave(channel, instrument[1])
        sound.setVolume(channel, instrument[2])
        sound.setFrequency(channel, freq)

        sound.setADSR(channel, fb * 4, fb * 8, instrument[2] - 0.1, fb * 4)


        prev_note = note
    end

    for ch = channel, 8 do
        sound.setVolume(ch, 0)
        sound.setFrequency(ch, 0)
    end

    sound.delay(frame)
    sound.process()

    os.sleep(sleep)
end