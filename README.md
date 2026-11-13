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
  - `abajo_derecha.jpg`
  - `abajo_izquierda.jpg`
  - `arriba_derecha.jpg`
  - `arriba_enfrente.png`
  - `arriba_izquierda.jpg`
  - `centro.jpg`

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
