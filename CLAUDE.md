# android-icon-banner

A Gradle plugin that adds a per-variant banner to an Android app's launcher icon, so a dev build is
distinguishable from production on a device that has both installed.

**The plugin is the product.** `:app` exists only as a demo and manual visual check — somewhere to
apply the plugin and look at the resulting icon on a launcher. It is not shipped, not a template,
and not where features go. Change it only when the demo needs to show something.

Design decisions and their reasoning live in `specs/`. Read the relevant spec before changing
behaviour rather than inferring intent from the code.

Status: designed, not yet implemented. The plugin build does not exist yet.
