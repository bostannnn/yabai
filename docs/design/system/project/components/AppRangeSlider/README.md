# AppRangeSlider

A range on one track: AppSlider's 18 tall pill on `surface-container-highest`, `primary` between two slim 4 x 28 handles in `on-surface`. Above it, `AppHistogram` can show how the values spread: one bar per step, 56 tall at most, 2 apart, top corners 3, each as tall as the square root of its count so a few long books still show beside many short ones. Bars inside the range are `primary`, the rest `outline-variant`.

- Kit: `AppRangeSlider(values, onValuesChange, valueRange, onValueChangeFinished)` and `AppHistogram(counts, highlighted)`. The histogram's bars are the slider's steps, so the two span the same width.
- The caller snaps the handles to its stops and applies the range on release (`onValueChangeFinished`), not on every step.
- The library filter sheet's Pages section: the label with the range at its end in `primary` ("150–300", "150+", "Up to 300", "Any"), the histogram, the slider, From and To fields (`AppTextField`, numbers; an empty field is no bound) and, while some books have no page count, an "Include unknown length" switch row saying how many (dimmed while the range is Any, when nothing is left out). Under the slider, 0, 100, 300 and 1,000+ mark the uneven scale. Stops: 0, 10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1,000+ (the last has no upper bound).
- What counts as pages is what the book page shows: a comic's or a PDF's page count, an EPUB's printed pages (only Grimmory knows them). A series tile fits when one of its issues does.
