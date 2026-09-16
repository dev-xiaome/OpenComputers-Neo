# Quad T2 Graphics Card

![Four GPUs, one unreasonable card.](oredict:opencomputers:quadgraphicscard)

The quad T2 graphics card contains four independent tier two [graphics cards](graphicsCard1.md) in a single card slot. Each GPU appears at its own component address, supports an 80x25 resolution with 16 colors, and provides enough video memory for three full-screen buffers.

The four GPUs count as four components. OpenOS automatically selects only one GPU for its primary terminal; programs can find and bind all four using `component.list("gpu")`.
