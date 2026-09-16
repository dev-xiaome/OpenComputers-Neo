# OpenComputers datapack examples

Copy this directory into a world's `datapacks` directory, or copy it into a
zip file and install that zip as a datapack. Run `/reload` after installing.

The example registers:

- `example:diagnostics` as a cyan loot floppy. Its filesystem is under
  `data/example/opencomputers/loot_disks/diagnostics/`.
- `example:diagnostics` as an EEPROM. Its code and data are under
  `data/example/opencomputers/eeproms/diagnostics/`.

The floppy contains an `init.lua` that writes a message through the GPU. The
EEPROM contains a small BIOS that loads `/init.lua` from the first filesystem
and displays the message stored in its EEPROM data section.
