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

## Second Screen

### Purpose of the screen

Second screen is the complete pokemon library for users to look for information on any pokemon they desire, will allow a middle point between scanning and looking at a pokemon's information directly through filtering and search

### Function

- **Goal:** Allow users to **filter** through all pokemon with a
  **search** and **filtering** option, to then send them to that specific pokemon's **info screen**.

### Structure File

- `/app/src/main/java/com/example/scandex/pokemonlibrary.kt`
- `/app/src/main/res/layout/activity_pokemonlibrary.xml`
- `/app/src/main/res/drawable/`
  - `tag_bg_xml`
  - `pokemon_tag_xml`
  - `pkm_type_xml`
  - `search.png`

### Design & interface XML

This section describes the layout of the screen

- **Main layout:** `ConstraintLayout`, to keep the interface
  responsive and maintain its proportions.
- **Structure:**
  - **Upper shell(`ImageView`):** A red header to hold filtering features. including a search icon to filter pokemon by name,
  and 2 filtering squares to select type and generation.
  - **Filtering buttons** (`Button`): 2 gray buttons that name either **type** or **gen** to filter between the options, as well as a search icon that will allow
  for name filtering.
  - **Library** Grayish blue background (`#A9CDD5`) Spands whole background for eye comfort and contrast 
  with other features with bolder colors.
  - **Favorite option(`Button`):** Uses a default star element from android studio, it is a button to add 
  pokemon to a favorites list later on.
  - **Pokemon tag (`#209ac9`):** The tag that will appear and hold every pokemon specifically, 
  it contains name, type(s), picture, favorite icon, number and clasification, this is all id'd
  so it may be reused when importing database connection, additionally, it is divided in 3 key sections, 
  the background, the type box(es), and the information.~~~~
