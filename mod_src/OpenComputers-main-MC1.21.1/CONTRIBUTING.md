# Contributing

## General

Set up a new developer's environment with the [developer's setup
guide](OpenComputers-Development-Setup.md)

Bug fixes, focused ports, documentation improvements, translations, and small
Lua programs are welcome. Please keep changes scoped, preserve compatibility
where practical, and run `gradlew build` before opening a pull request.

We're currently focusing on getting the 1.21 version up to date with modern
modding practices (such as being easily configurable with datapacks) and
squishing bugs.

It is unlikely that we are going to add any more new blocks or items to the base
mod at this time. However, we are happy to collaborate on extending the
OpenComputers API if it doesn't yet have everything you need to implement your
ideas as an add-on! In that case, drop us a line on Github or our IRC channels
and we can discuss how to make things work for everyone.

Translations live in
[`src/main/resources/assets/opencomputers/lang`](src/main/resources/assets/opencomputers/lang).
The English locale is the source of truth for keys. Instructions for adding
loot-disk programs are in the [loot README](src/main/resources/assets/opencomputers/loot/README.md).

## Regarding Drivers For Other Mods

In general, the main OpenComputers mod is not the place to add driver support
for components from other mods.

- In the ideal case, drivers should originate from the mod providing the
  component, especially if that mod already provides ComputerCraft peripheral
  integration.
- If this is not possible, then a separate driver-providing compatibility mod
  should be created and used instead.

Integrations with other mods requires more flexibility than direct support from
within OpenComputers allows. Individual drivers need to be able to update and
release independently according to the needs of their integrations. It does not
make sense for the OpenComputers team to maintain drivers that logically belong
to other mods.

Exceptions can be made on a case-by-case basis if there is a compelling reason
to do so. The current exceptions for mod drivers that live in OpenComputers
directly:
- Create (base Create only, none of the add-ons)
- Mekanism
- Applied Energistics 2
- Project Red

## AI Policy

It's not a secret that this 1.21 port was started from a 1.20 port that heavily
used AI-generated changes. However, the authors of this 1.21 port desire to
remove all AI-generated changes as part of the code modernization process, and
consider it to be a case of 'what's done is done' pragmatism than an endorsement
of using AI to generate changes.

In light of the on-going AI removal effort during the moderization process, do
not submit any PRs where an AI has generated any non-trivial work. This includes

- Code, whether Java/Scala for the mod itself or Lua for the in-game computers
- Art assets (graphics/sounds)
- Translations
- Documentation, whether it pertains to the in-game manual or out-of-game such
  as PR descriptions

AI-generated works are based on sources of unknown origin and present potential
copyright ownership issues down the line, so to do our best to respect all
parties involved, we must decline the further use of AI in this project and
continue the work to remove the existing use. You will be barred from further
contributing to this project if it is found that you used AI to author a
significant amount of your contribution.

See [SDL's `AGENTS.md`](https://github.com/libsdl-org/SDL/blob/main/AGENTS.md)
for some additional rationale.

Even in cases otherwise deemed acceptable, Any use of AI output in contributions
submitted to this repository must be disclosed when submitting your PR.
