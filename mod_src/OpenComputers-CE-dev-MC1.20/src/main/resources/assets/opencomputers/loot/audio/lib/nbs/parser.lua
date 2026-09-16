local byte = 1
local short = 2
local int = 4
local str = -2
local Ubyte = -1

function table.len(tbl)
    local x = 0
    for _,_ in pairs(tbl) do x = x + 1 end
    return x
end


function bytesToInt(str, signed)
   local bytes = {}
   for char in str:gmatch(".") do table.insert(bytes, string.byte(char)) end

   local multiplier = 1
   local int = 0

   for _, byte in pairs(bytes) do
      int = int + byte * multiplier
      multiplier = multiplier * 256
   end

   local max = math.pow(2, #bytes*8-1)
   if int > max and (signed == nil or signed == false) then
        int = int - max*2
    end

   return int
end

function read(file, bytes)
    if bytes == str then
        local str = ""
        local len = file:read(int)
        if len == nil then return nil end
        for _=1,bytesToInt(len) do str = str .. file:read(byte) end
        return str
    elseif bytes ~= Ubyte then
        return bytesToInt(file:read(bytes), false)
    else
        return bytesToInt(file:read(byte), true)
    end
end

function readPart(fields, size, file)
   local part = {}

   local block = {}
   for _=1,size do
       for _, field in pairs(fields) do
           block[field[1]] = read(file, field[2])
           if block[field[1]] == nil then return nil end
       end
       table.insert(part, block)
       block = {}
   end

   return part
end

return function(file)
   file = io.open(file, "rb")

   local classic = read(file, short)

   if classic == 0 then
       version = read(file, byte)
   end

   file:seek("set", 0)

   local fields = require("fields")(version or 0)

   local data = {header = {}, notes = {}}

   -- header
   data.header = readPart(fields.header, 1, file)[1]

   data.header.version = version

   -- notes
   local i = 1
   local note = {}
   local b = file:read(short)
   local tick = -1
   local layer = -1

   while not (i == 1 and bytesToInt(b) == 0) do
      note[fields.notes[i][1]] = bytesToInt(b)

      if i == 1 then
         tick = tick + bytesToInt(b)
      end

      if i == 2 then
         layer = layer + bytesToInt(b)
      end

      if i == 2 and bytesToInt(b) == 0 then
         i = 1
         note = {}
         layer = -1
      elseif i == #fields.notes then
         i = 2
         note.tick = tick
         note.layer = layer
         table.insert(data.notes, note)
         note = {}
      else
         i = i + 1
      end

      b = file:read(fields.notes[i][2])
   end

   print("layers!")
   data.layers = readPart(fields.layers, data.header["layer-count"], file)

   local instruments = read(file, Ubyte)

   if instruments == nil then return data end

   data.instruments = readPart(fields.instruments, instruments, file)
   data.instruments.count = instruments

   for k, instrument in pairs(data.instruments) do if type(instrument) == "table" then
       print("Substitute for: " .. instrument.name .. " from " .. instrument.file .. ": ")
       data.instruments[k].substitute = io.read()
   end end

   return data
end