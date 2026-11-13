# ScanDex

Integrative Project for the 4th term

## First Screen

### Purpose of the screen

The first screen is used to control the camera responsible for
scanning the Pokemon that the user observes

### Function

Enable the capture of a **camera frame** so that the image can
be sent to the **backend**.

### Structure File

- `/app/src/main/java/com/example/scandex/MainActivity.kt`
- `/app/src/main/res/layout/main_activity.xml`
- `/app/src/main/res/drawable`
  - `/circle_blue.xml`
  - `/circle_green.xml`
  - `/circle_yellow.xml`
  - `/circle_red.xml`

### Design & interface XML

The design of the main first screen is build using a_ **`ConstraintLayout`**
to ensure that the interface is responsive and maintains its proportions

- **Outer Shell (`ConstraintLayout`):** Main container with a red background (`#D32F2F`)
  and a `20dp` padding for the border.
- **Top Lights (`LinearLayout`):** Located at the top left, it shows the big blue
  lens and three small lights ('#B71C1C', `#FDD835`, `#43A047`)
  in a horizontal line.
- **Camera Screen (`FrameLayout`):** Gray frame (`#E0E0E0`)
  in the upper center with a square shape.
- **Action Button (`Button`):** Button centered at the bottom used to take the picture.

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
