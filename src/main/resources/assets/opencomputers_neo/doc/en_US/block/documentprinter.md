# Document Printer

![Paperwork, but programmable.](item:opencomputers:document_printer)

The Document Printer is the OpenPrinter peripheral integrated into OpenComputers. Connect it to an OpenComputers network and access it as the `openprinter` component.

Load a [black or color ink cartridge](../item/printerink.md) into the left slots and paper into the paper input. The scanner slot accepts [printed pages](../item/printedpage.md) and vanilla books. Completed jobs appear in the output tray.

The printer uses a persistent FIFO queue. Jobs pause automatically when paper, ink, energy, or output space runs out and resume after the problem is corrected. Printed pages prefer the first folder in the output tray with an empty slot; any number of folders may be loaded, and ordinary empty output slots remain available when every folder is full.

## Modern API

The preferred API queues complete documents and returns a job ID:

```lua
local printer = require("component").openprinter

local job, reason = printer.print("Line 1\nLine 2", {
  title = "Report",
  copies = 2,
  wrap = true
})
assert(job, reason)
```

Structured documents support per-line color and alignment:

```lua
printer.print({
  title = "Status",
  lines = {
    {text = "OpenComputers", color = 0x3366FF, alignment = "center"},
    {text = "Ready", color = 0x000000, alignment = "left"}
  }
})
```

Useful callbacks include:

* `print(document[, options])` — queue text or a structured document.
* `printLabel(text[, options])` — print name tags.
* `printMap(image[, options])` — print an RGB pixel table onto an empty map.
* `printImage(data[, options])` — decode PNG, JPEG, GIF, or BMP data and print it as a map.
* `status([jobId])`, `queue()`, `cancel(jobId)` — inspect and manage queued jobs.
* `scan()` — scan a printed page or vanilla book into a document that can be passed back to `print`.
* `supplies()` — report paper, ink, and output capacity.
* `capabilities()` — report API version, limits, and supported features.

Printer state changes emit an `openprinter_job` signal containing the printer address, job ID, state, and reason.

## Classic OpenPrinter API

Programs written for the original OpenPrinter can still use the buffered API:

```lua
local printer = require("component").openprinter

printer.setTitle("Classic Page")
printer.writeln("Hello, world!")
printer.writeln("In color", 0x3366FF, "center")
printer.print()
```

Put a writable book in the paper input to write pages into it instead of producing printed-page items. Book jobs accept only plain black text, and the completed writable book is moved to an ordinary output slot. Use `printAndSign` with the classic buffer to seal it as a vanilla written book:

```lua
printer.writeln("This is a book page.")
local job, reason = printer.printAndSign("My Book", "OpenPrinter")
assert(job, reason)
```

`printAndSign(title[, author][, copies])` uses `OpenPrinter` as the default author. A writable book has up to 100 pages; `copies` appends the buffered document repeatedly to that same book, matching the classic printer behavior.

Compatibility callbacks include `writeln`, `setTitle`, `clear`, `print([copies])`, `getPaperLevel`, `getBlackInkLevel`, `getColorInkLevel`, `charCount`, `width`, `maxWidth`, `scanLine`, and `scanBook`.

The modern document API is recommended for new programs because it supports pagination, queues, status reporting, map/image printing, and automatic job resumption.

## OpenPrinter Tools Disk

The green **OpenPrinter** program disk includes command-line tools for OpenOS:

* `print`
* `printerstatus`
* `xerox`
* `printercopypage`
* `printmap`

Install the disk with OpenOS's normal installer.
