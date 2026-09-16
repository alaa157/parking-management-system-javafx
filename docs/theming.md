# Theming and palette ownership

The canonical palette is now kept in:

- `src/main/java/com/parking/gui/DesignTokens.java` for the dark/application palette and shared effects.
- `src/main/java/com/parking/gui/LightDesignTokens.java` for values used only by the light theme.

The CSS files are generated from tokenized templates. Run:

```bash
mvn generate-resources
```

This runs `scripts/generate_palette_css.py` and produces `parkingos-tokens.css`,
`parkingos.css`, and `parkingos-print.css`. Generated files carry a header and
must not be edited by hand. The template files (`parkingos.css.template` and
`parkingos-print.css.template`) use named placeholders, so JavaFX receives the
same literal CSS values without relying on JavaFX custom-property support.

To change a palette value, edit the appropriate named Java constant and run
`mvn generate-resources`. To add a new color, add a semantic constant first and
use its placeholder in the relevant template. Do not add raw hex or `rgba`
values to Java UI code or the CSS templates. Generated CSS is the only place
where those values should appear outside the token classes.

The generator preserves the existing CSS variable names (`--fx-color-bg`,
`--fx-color-teal`, and so on) and also emits the light-theme variables with the
`--fx-color-light-*` prefix for consumers that need them.
