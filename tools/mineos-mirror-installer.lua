-- MineOS installer (mirror edition)
-- 原版会去 raw.githubusercontent.com 拉 Installer/Main.lua，该域名在部分网络下
-- DNS 无法解析。此版本改为从 jsDelivr 镜像下载，并在下载后把安装器内部的仓库
-- 地址一并改写成镜像，因此整条安装链都不再依赖 raw.githubusercontent.com。
--
-- 用法：把本文件内容放进电脑（例如 edit /tmp/install.lua 后 Ctrl+V 粘贴），
-- 保存并运行 /tmp/install.lua。它会刷好 EEPROM 并重启，之后自动进入安装器。
local component = require("component")
local computer = require("computer")
local os = require("os")

local gpu = component.gpu

-- Checking if computer is tough enough for such a S T Y L I S H product as MineOS
do
	local potatoes = {}

	-- GPU/screen
	if gpu.getDepth() < 8 or gpu.maxResolution() < 160 then
		table.insert(potatoes, "At least Tier 3 graphics card and screen");
	end

	-- RAM
	if computer.totalMemory() < 2 * 1024 * 1024 then
		table.insert(potatoes, "At least 2x tier 3.5 RAM modules");
	end

	-- HDD
	do
		local filesystemFound = false

		for address in component.list("filesystem") do
			if component.invoke(address, "spaceTotal") >= 2 * 1024 * 1024 then
				filesystemFound = true
				break
			end
		end

		if not filesystemFound then
			table.insert(potatoes, "At least tier 2 hard disk drive");
		end	
	end

	-- Internet
	if not component.isAvailable("internet") then
		table.insert(potatoes, "Internet card");
	end

	-- EEPROM
	if not component.isAvailable("eeprom") then
		table.insert(potatoes, "EEPROM");
	end

	-- SORRY BRO NOT TODAY
	if #potatoes > 0 then
		print("Your computer does not meet the minimum system requirements:")

		for i = 1, #potatoes do
			print("  ⨯ " .. potatoes[i])
		end

		return
	end
end

-- Checking if installer can be downloaded from GitHub, because of PKIX errors, server blacklists, etc
do
	local success, result = pcall(component.internet.request, "https://cdn.jsdelivr.net/gh/IgorTimofeev/MineOS@master/Installer/Main.lua")

	if not success then
		if result then
			if result:match("PKIX") then
				print("Download server SSL sertificate was rejected by Java. Update your Java version or install sertificate for github.com manually")
			else
				print("Download server is unavailable: " .. tostring(result))
			end
		else
			print("Download server is unavailable for unknown reasons")
		end

		return
	end

	local deadline = computer.uptime() + 5
	local message

	while computer.uptime() < deadline do
		success, message = result.finishConnect()

		if success then
			break
		else
			if message then
				break
			else
				os.sleep(0.1)
			end
		end
	end

	result.close()

	if not success then
		print("Download server is unavailable. Check if github.com is not blocked by your internet provider or OpenComputers configuration file")
		return
	end
end

-- Flashing EEPROM with tiny script that will run installer itself after reboot.
-- It's necessary, because we need clean computer without OpenOS hooks to computer.pullSignal()
component.eeprom.set([[
	local connection, data, chunk = component.proxy(component.list("internet")()).request("https://cdn.jsdelivr.net/gh/IgorTimofeev/MineOS@master/Installer/Main.lua"), ""
	
	while true do
		chunk = connection.read(math.huge)
		
		if chunk then
			data = data .. chunk
		else
			break
		end
	end
	
	connection.close()
	
	-- 把安装器内部的仓库地址也指向镜像，否则安装过程中仍会去访问 raw.githubusercontent.com
	data = data:gsub("https://raw%.githubusercontent%.com/IgorTimofeev/MineOS/master/", "https://cdn.jsdelivr.net/gh/IgorTimofeev/MineOS@master/")
	
	load(data)()
]])

computer.shutdown(true)