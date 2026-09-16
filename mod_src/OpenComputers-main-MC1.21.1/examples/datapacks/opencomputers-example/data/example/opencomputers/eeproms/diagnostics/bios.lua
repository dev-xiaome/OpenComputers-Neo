local eeprom = component.list("eeprom")()
local message = "Example datapack EEPROM"
if eeprom then
  message = component.invoke(eeprom, "getData") or message
end

local gpu = component.list("gpu")()
local screen = component.list("screen")()
if gpu and screen then
  component.invoke(gpu, "bind", screen)
  component.invoke(gpu, "set", 1, 1, message)
end

local filesystem = component.list("filesystem")()
if not filesystem then
  error("no filesystem available", 0)
end

local handle, reason = component.invoke(filesystem, "open", "/init.lua")
if not handle then
  error(reason or "no bootable init.lua", 0)
end

local source = ""
while true do
  local chunk, readReason = component.invoke(filesystem, "read", handle, math.maxinteger or math.huge)
  if not chunk then
    if readReason then
      component.invoke(filesystem, "close", handle)
      error(readReason, 0)
    end
    break
  end
  source = source .. chunk
end
component.invoke(filesystem, "close", handle)

local init, loadReason = load(source, "=init")
if not init then
  error(loadReason or "could not load init.lua", 0)
end
return init()
