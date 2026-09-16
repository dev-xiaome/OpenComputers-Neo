local gpu = component.list("gpu")()
local screen = component.list("screen")()

if gpu and screen then
  component.invoke(gpu, "bind", screen)
  component.invoke(gpu, "set", 1, 1, "Example datapack floppy booted")
end
