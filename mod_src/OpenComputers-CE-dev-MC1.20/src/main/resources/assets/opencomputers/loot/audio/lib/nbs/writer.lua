local byte = 1
local short = 2
local int = 4
local str = -2
local Ubyte = -1

local format_strings = {
   [byte] = "i1",
   [Ubyte] = "I1",
   [short] = "i2",
   [int] = "i4"
}

function write_int(file, num, type)
    local bytes = {}
    if type == Ubyte then
        if num < 0 or num > 255 then error("Number out of range for u8") end
        bytes[1] = num
    elseif type == byte then
        if num < -128 or num > 127 then error("Number out of range for i8") end
        if num < 0 then num = 0x100 + num end
        bytes[1] = num
    elseif type == short then
        if num < -32768 or num > 32767 then error("Number out of range for i16") end
        if num < 0 then num = 0x10000 + num end
        bytes[1] = num % 256
        bytes[2] = math.floor(num / 256)
    elseif type == int then
        if num < -2147483648 or num > 2147483647 then error("Number out of range for i32") end
        if num < 0 then num = 0x100000000 + num end
        bytes[1] = num % 256
        bytes[2] = math.floor(num / 256) % 256
        bytes[3] = math.floor(num / 65536) % 256
        bytes[4] = math.floor(num / 16777216)
    else
        error("Unsupported type: " .. type)
    end

    -- Write the bytes to the file
    for _, byte in ipairs(bytes) do
        file:write(string.char(byte))
    end
end

function write(file, x, type)
   if type ~= str then
      write_int(file, x, type)
   else
      write(file, #x, int)

      for i = 1, #x do
         local char = x:byte(i)

         write(file, char, Ubyte)
      end
   end
end

function writePart(file, song, part, partS)
   for _, field in pairs(part) do
      write(file, song[partS][field[1]], field[2])
   end
end

return function(song, file, version)
   file = io.open(file, "wb") or io.create(file)
   file = file or io.open(file, "wb")

   local fields = require("fields")(version)

   for _, field in pairs(fields.header) do
      print(field[1], field[2])
      write(file, song.header[field[1]], field[2])
   end

   local prev_tick = -1;
   local prev_layer = -1;

   for _, note in pairs(song.notes) do
      if note.tick - prev_tick > 0 then
         if prev_tick > -1 then write(file, 0, short) end
         write(file, note.tick - prev_tick, short)
         prev_layer = -1
      end

      write(file, note.layer-prev_layer, short)

      write(file, note.instrument, byte)
      write(file, note.key, byte)

      if version >= 4 then
         write(file, note.velocity, byte)
         write(file, note.panning, byte)
         write(file, note.pitch, short)
      end

      prev_tick = note.tick
      prev_layer = note.layer
   end

   for k, _ in pairs(song.layers or {}) do
      for _, field in pairs(fields.layers) do
         write(file, song.layers[k][field[1]], field[2])
      end
   end

   write_int(file, song.instruments.count, Ubyte)
   for _, instrument in pairs(song.instruments or {}) do if type(instrument) == "table" then
      for _, field in pairs(fields.instruments) do
         write(file, instrument[field[1]], field[2])
      end
   end end

   file:close()
end