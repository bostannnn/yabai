# AppSearchField

The search pill: 40 tall, `surface-container-high`, a search icon, a placeholder that names what it searches, Clear once there is text, and an optional trailing slot (the connection badge while a source is offline).

- Kit: `AppSearchField(query, onQueryChange, placeholder, containerColor, trailingContent)`.
- In the library bar it fills the middle up to 640. The placeholder says the place: "Search Kavita", "Search Neon Ronin", "Search shelves".
- On a dialog, pass `AppSearchFieldDefaults.dialogContainerColor`.
- **The tags a search names** (the library's search): above the results, a row that starts with "Browse" in `on-surface-variant` and holds an `AppChip` for each tag, genre, mood or shelf the words name, its Browse row quieter and its count after it ("Set in Japan 96"), whole names first. Picking one opens Browse with it. The row sits above the results, not in a panel over them, so it never hides what you searched for.
- **Results** while searching are `AppBookRow`s that say why they matched when it isn't the title or a name: the sentence of the description that holds the words, marked with `primary-container`, two lines at most (a long one cut around the first word), with "In the description" under it; or, for a tag or genre, one line named as Browse names it ("Set in **Japan**", "Genre **Historical Fiction**").
