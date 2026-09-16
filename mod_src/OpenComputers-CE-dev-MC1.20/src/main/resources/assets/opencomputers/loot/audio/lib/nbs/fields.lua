local byte = 1
local short = 2
local int = 4
local str = -2
local Ubyte = -1

local versionFields = {
   [0] = {
      header = {
         {"length", short},
	      {"layer-count", short},
   	   {"name", str},
   	   {"author", str},
   	   {"OG-author", str},
   	   {"description", str},
   	   {"tempo", short},
   	   {"auto-saving", byte},
   	   {"auto-saving-dur", byte},
   	   {"time-signature", byte},
   	   {"minutes-spent", int},
   	   {"leftclick", int},
   	   {"rightclick", int},
   	   {"noteblocks-added", int},
   	   {"noteblocks-removed", int},
   	   {"OG-filename", str},
      },

      notes = {
         {"jumps-tick", short},
         {"jumpts-layer", short},
         {"instrument", byte},
         {"key", byte}
      },

      layers = {
         {"name", str},
         {"volume", byte}
      },
      instruments = {
         {"name", str},
         {"file", str},
         {"key", byte},
         {"piano", byte}
      }
    },
    [1] = {
        header = {
            [1] = {"classic", short, "replace"},
            [2] = {"NBSversion", byte, "push"},
            [3] = {"vanilla-instrument-count", byte}
        }
    },
    [2] = {
        layers = {
            [3] = {"stereo", Ubyte, "push"}
        }
    },
    [3] = {
        header = {
            [4] = {"length", short, "push"}
        },

        layers = {
            [2] = {"lock", byte, "push"},
        },

        notes = {
            [5] = {"velocity", byte, "push"},
            [6] = {"panning", byte, "push"},
            [7] = {"pitch", short, "push"}
        }
    },
    [4] = {
        header = {
            [20] = {"loop", byte, "push"},
            [21] = {"loop-count", byte, "push"},
            [22] = {"loop-start", short, "push"}
        }
    },
    [5] = {}
}

function table.copy(original)
	local copy = {}
	for k, v in pairs(original) do
		if type(v) == "table" then
			v = table.copy(v)
		end
		copy[k] = v
	end
	return copy
end

function table.modify(tbl, mods)
    for k, modblock in pairs(mods) do
        local min = 99999
        for i,_ in pairs(modblock) do if i < min then min = i end end
        local len = table.len(modblock)
        for I=1,len do
            local i = min + I - 1
            local mod = modblock[i]
            if mod[3] == "replace" then tbl[k][i] = mod
            else table.insert(tbl[k], i, mod) end
        end
    end
end

return function(version)
   local fields = table.copy(versionFields[0])
   for i=1,version do
      table.modify(fields, versionFields[i])
   end
   return fields
end