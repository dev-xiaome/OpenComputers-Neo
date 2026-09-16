# Holo Screens

![Now you see it.](oredict:opencomputers:holoscreen1)

Holo screens project a floating, two-dimensional display above or below their projector. They use the same `screen` component and [graphics card](../item/graphicsCard1.md) API as standard [screens](screen1.md), with the resolution, color depth, and touch capabilities of their tier. Unlike standard screens, a holo screen is a single projector and does not join neighboring screens.

Place a holo screen on the floor to project upward, or against a ceiling to project downward. Hold sneak/shift and right-click an edge of the projected display with an empty hand to grow or shrink it in that direction. Its maximum physical width and height are controlled by the server's screen-size settings; the defaults are 8 blocks wide by 6 blocks high. The physical size changes the display's aspect ratio, not its tier's maximum resolution.

Use a dye on the projector to change the projection's background color. The dye is not consumed. Right-click the projector with an empty hand to open its inventory, which has one slot for a [keyboard](keyboard.md). Installing a keyboard allows the projected display to be opened and typed into. The projected display also accepts touch input when supported by its tier.
