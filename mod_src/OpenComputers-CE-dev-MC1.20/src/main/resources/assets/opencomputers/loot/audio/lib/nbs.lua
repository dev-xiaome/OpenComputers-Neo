local M = {}

package.path = package.path .. ";/lib/nbs/?;/lib/nbs/?.lua"

M.read = require("parser")
M.write = require("writer")

return M