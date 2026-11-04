# AppLetterStrip

The index down the end edge of a long list sorted by name, to jump to a letter: # (names that start with a digit or a sign) and A to Z.

- Kit: `AppLetterStrip(letters, current, onLetter)`. 18 wide, one letter per 17 (15 on Compact), 11 SemiBold `on-surface-variant`, over the gutter at the end edge, from the first letter group's top down.
- A letter with no group is drawn at 38% and moves to the next letter that has one.
- Tap or drag: the list jumps to that letter's group label, and a 60 bubble in `primary` shows the letter beside the finger while it is down. The letter of the group at the top of the window sits on `primary-container`.
- Only while the section is sorted by name and holds more than 20 lists. Under the strip the group labels (`AppGroupLabel`, `on-surface-variant`) mark the letters.
- TalkBack: "Jump to a letter", each letter an action; the group labels are headings, so heading navigation does the same.
