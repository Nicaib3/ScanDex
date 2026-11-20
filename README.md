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
