# Remote Terminal

![Remote access.](oredict:opencomputers:terminal)

The remote terminal can be used to remotely control computers via [terminal servers](terminalserver.md) and [rack KVMs](rackkvm.md). Sneak-activate either device while it is installed in a [rack](../block/rack.md) to bind the remote terminal to it.

A [terminal server](terminalserver.md) provides a virtual [screen](../block/screen1.md) and [keyboard](../block/keyboard.md) which can be controlled via the terminal. A [rack KVM](rackkvm.md) instead provides one isolated virtual console for each server in the same rack and allows selecting among them from the remote. When using the terminal in hand after binding it, a GUI will open in the same manner as a [keyboard](../block/keyboard.md) attached to a [screen](../block/screen1.md).

Multiple terminals can be bound to one [terminal server](terminalserver.md), but they will all display the same information, as they will share the virtual [screen](../block/screen1.md) and [keyboard](../block/keyboard.md). The number of terminals that can be bound to a terminal server is limited. A rack KVM supports one bound remote terminal; binding another one invalidates the previous remote.
