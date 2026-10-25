# AppStatusPill

A source's or a queue's state as a pill: 28 tall, `label-large` Bold, an optional 16 icon.

- Kit: `AppStatusPill(label, containerColor, contentColor, icon)`. The caller picks the colours for the state: the sync accent container for Connected, `surface-container-highest` for neutral (Checking, Waiting), `error-container` for a problem.
- The source page and the sync page use it.
