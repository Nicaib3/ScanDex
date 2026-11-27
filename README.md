# ScanDex

Integrative Project for the 4th term

## First Screen

### Purpose of the screen

The first screen is used to control the camera responsible for
scanning the Pokemon that the user observes

### Function

- **Goal:** Capture a **camera frame** so that the image
  can be sent to the **backend**.

### Structure File

- `/app/src/main/java/com/example/scandex/MainActivity.kt`
- `/app/src/main/res/layout/activity_main.xml`
- `/app/src/main/res/drawable/`
  - `abajo_atras.png`
  - `abajo_derecha.png`
  - `abajo_izquierda.png`
  - `arriba_derecha.png`
  - `arriba_enfrente.png`
  - `arriba_izquierda.png`
  - `centro.png`

### Design & interface XML

- This section describes the layout of the screen *

- **Main layout:** `ConstraintLayout`, to keep the interface
  responsive and maintain its proportions.
- **Structure:**
  - **Outer Shell:** Two red borders with design. The top border
    holds the style and the camera area; the bottom border holds
    the action buttons, the camera button and the camera area.
  - **Camera Screen (`FrameLayout`):** Gray frame (`#E0E0E0`)
    spread across the background center of the red frames.
  - **Action Buttons (`Button`):** Square buttons in a 2 by 2 grid
    with different colors, reserved for future options.
  - **Picture Button (`Button`):** Blue circle button in the center,
    above the 4 action buttons, used to take the picture.
  - **Library Button (`Button` - `btnLibrary`):** Green pill button
    next to `btnCapture` that opens the Pokémon Library screen.

### Logic kt

- This section completes the understanding of the logic *

- **Main class:** `MainActivity.kt`
- **Behavior:**
  - Requests the `CAMERA` permission at startup (plus `WRITE_EXTERNAL_STORAGE`
    on Android 9 or lower).
  - Uses **CameraX** to show the back camera preview
    inside the screen frame (`PreviewView`).
  - When **CAPTURAR** is pressed, it takes a photo
    and saves it to the gallery in `Pictures/ScanDex`.
  - When **Biblioteca** (`btnLibrary`) is pressed, it launches
    `pokemonlibrary` via `Intent`.

## Second Screen

### Purpose of the screen

Second screen is the complete pokemon library for users to look for information on any pokemon they desire, will allow a middle point between scanning and looking at a pokemon's information directly through filtering and search

### Function

- **Goal:** Display the **151 Generation 1 Pokémon** fetched from **PokéAPI v2**
  using reusable `pokemon_tag` panels, supporting locked/unlocked states
  and preparing **search** and **filtering** options.

### Structure File

- `/app/src/main/java/com/example/scandex/pokemonlibrary.kt`
- `/app/src/main/java/com/example/scandex/PokeApiService.kt`
- `/app/src/main/res/layout/activity_pokemonlibrary.xml`
- `/app/src/main/res/layout/pokemon_tag.xml`
- `/app/src/main/res/drawable/`
  - `lib_top.xml`
  - `tag_bg.xml`
  - `pkm_type.xml`
  - `search.png`
- `/app/src/main/AndroidManifest.xml` (`INTERNET` permission)

### Design & interface XML

This section describes the layout of the screen

- **Main layout:** `ConstraintLayout`, to keep the interface
  responsive and maintain its proportions.
- **Structure:**
  - **Upper shell (`View` - `topBar`):** A red header (`lib_top.xml`) holding filtering features, including a search icon (`imageView25`) and 2 filtering buttons (`Types` and `Gen`).
  - **Filtering buttons (`Button`):** `Types` and `Gen` gray buttons constrained horizontally inside `topBar`.
  - **Library background:** Grayish blue background (`#A9CDD5`) across the screen for eye comfort and contrast.
  - **Scrollable list (`ScrollView` - `scrollPokemon`):** Contains a vertical `LinearLayout` (`pokemonContainer`) where each `pokemon_tag` card is dynamically inflated, plus a centered `ProgressBar` (`progressBar`) while the initial list loads.
  - **Pokemon tag (`pokemon_tag.xml`, `#209ac9`):** Reusable card holding Pokédex number (`tvPokenum`), name (`tvPokename`), favorite toggle (`btnfav`), description (`tvDescripcionPokemon`), up to two type badges (`tvtype1`, `tvtype2`), and sprite image (`ivPokeimage`).

### Logic kt

- **Main classes:** `pokemonlibrary.kt` and `PokeApiService.kt`
- **Behavior:**
  - `PokeApiService.kt` connects to the **PokéAPI** to get the 151 Pokémon from Generation 1, plus each Pokémon's name, types, height, weight, and image.
  - `pokemonlibrary.kt` creates one `pokemon_tag` panel for each of the 151 Pokémon inside `pokemonContainer`.
  - It loads the image, name, description, and types for each Pokémon in the background and updates the panels as they arrive.
  - Each Pokémon has an `isLocked` property (`true` for even numbers, `false` for odd numbers):
    - If `isLocked` is `true`, the image turns black and the name and description are hidden.
    - If `isLocked` is `false`, the image, name, and description are shown normally.
  - Pressing the star button (`btnfav`) marks or unmarks the Pokémon as a favorite.

## Third Screen

### Purpose of the screen

The third screen is used for any Pokémon to show its complete Pokédex information, including description, abilities, base stats, evolution line, type matchups, and moves.

### Function

- **Goal:** Show the full details of the selected Pokémon using **PokéAPI v2**, change the background colors based on its types, and let the user switch between general info and moves.

### Structure File

- `/app/src/main/java/com/example/scandex/pokeinfo.kt`
- `/app/src/main/java/com/example/scandex/PokeApiService.kt`
- `/app/src/main/res/layout/activity_pokeinfo.xml`
- `/app/src/main/res/layout/item_move.xml`
- `/app/src/main/res/drawable/`
  - `bg_pokeinfo_top.xml`
  - `bg_pokeinfo_gradient.xml`
  - `bg_pokeinfo_card.xml`
  - `bg_pokeinfo_pill.xml`
  - `bg_tab_container.xml`
  - `bg_circle_info.xml`
- `/app/src/main/res/values/colors.xml`
- `/app/src/main/AndroidManifest.xml`

### Design & interface XML

This section describes the layout of the screen

- **Main layout:** `ScrollView` (`typecolor`) with a `ConstraintLayout` inside, so the user can scroll vertically while keeping the design responsive.
- **Structure:**
  - **Type background (`typecolor`):** A top-to-bottom gradient using the Pokémon's type colors from `colors.xml` (with `bg_pokeinfo_gradient.xml` as default).
  - **Top half circle (`ImageView` - `circle_top`):** A soft white semi-transparent half circle (`bg_pokeinfo_top.xml`) placed behind the Pokémon image (`ivPokeimage2`).
  - **Main info:** Shows the Pokémon name (`tvpokename2`), number (`pokenum2`), category (`pokedesc`), and up to two type boxes (`tvpoketype1`, `tvpoketype2`) using `bg_pokeinfo_pill.xml`.
  - **Tabs (`layoutTabsMenu`):** A container (`bg_tab_container.xml`) with two buttons (`btnTabInfo` for **Información** and `btnTabMovimientos` for **Movimientos**) to switch between sections.
  - **Info cards (`layoutSeccionInfo`):** White semi-transparent cards (`bg_pokeinfo_card.xml`, `#CCFFFFFF`) holding:
    - Pokédex description (`tvpokeinfo`), weight (`tvpokeweight`), and height (`pokeheight`).
    - Abilities (`rowHab1`–`rowHab3`) with `i` buttons (`btnInfoHab1`–`btnInfoHab3`) using `bg_circle_info.xml`.
    - Base stats (`tvStatHp`, `tvStatAtk`, `tvStatDef`, `tvStatSpAtk`, `tvStatSpDef`, `tvStatSpd`, and `tvStatTotal`).
    - Evolution line (`evoStage1`–`evoStage3`) showing up to 3 evolutions with their images and names.
    - Type interactions (`layoutWeaknesses`, `layoutStrengths`, `layoutResistances`) with rows of colored type boxes.
  - **Moves list (`layoutSeccionMovimientos`):** A card with a vertical `LinearLayout` (`contenedorListaMovimientos`) that loads each level-up move using `item_move.xml` (level, name, and type).

### Logic kt

- **Main class:** `pokeinfo.kt`
- **Behavior:**
  - Receives `pokemonId`, `nombre`, `tipo1`, and `tipo2` through an `Intent` from `pokemonlibrary.kt`, and sets the background gradient (`aplicarFondoPorTipos` and `obtenerColorDeTipo`) right away.
  - If the Pokémon has two types, the background mixes both colors from top to bottom; if it only has one type, it uses the same color for both.
  - `PokeApiService.kt` connects to **PokéAPI v2** to get the Pokémon details, species text and category, abilities, evolutions, type matchups, and level-up moves.
  - `pokeinfo.kt` loads the Pokémon image, evolution images, stats, type matchups, and moves in the background and updates the screen as the data arrives.
  - Pressing **Información** (`btnTabInfo`) or **Movimientos** (`btnTabMovimientos`) hides one section and shows the other.
  - Pressing an ability's `i` button or tapping a move opens a popup (`AlertDialog`) with its description or move details (`tipo`, `categoría`, `potencia`, `precisión`, `PP`, and `efecto`).
