# UnNest: Directory Flattener

An elegant, high-precision utility for Android constructed with Jetpack Compose. UnNest resolves directory layout complexity by recursively extracting deep, nested subfolder contents up into a single flat level—either as a direct plain copy or wrapped in a streamlined flat `.ZIP` structure.

---

## 🎨 Visual Identity: The Classic Paper Theme

UnNest features a highly tactile **Classic Paper** design motif that evokes professional stationery, legal documents, and vintage accounting ledgers:

- **Warm Linen Base**: A comfortable off-white background (`#F5F3ED`) acts as the primary canvas, avoiding harsh digital white.
- **Graphite & Pencil Gray**: High-contrast text matches dark graphite lead (`#1D1B19`), while auxiliary details use pencil gray (`#6C6963`) to provide clean typographic hierarchy.
- **Dynamic Shadows**: Floating cards simulate real physical paper files layered over each other with realistic, subtle drop shadows.
- **Archival Ledger Grid**: Backing screens are lined with custom paper fiber grids and warm red-wax margin indicators reminiscent of a physical draftsman's desk.
- **De-saturated Stamps**: Status colors appear as ink stamps—archival sage-cream for success, rose-tinted red wax for errors, and antique ochre gold for conflict overrides.

---

## 🚀 Core Functional Modules

### 1. The Splitting Sequence (Interactive Splash)
- Instantly demonstrates the flattening purpose through custom floating document vectors (`.JPG`, `.ZIP`, `.PDF`, `.TXT`, `.MP4`) gliding into a central, stylized golden manila folder.

### 2. Workspace Hub (Setup Deck)
- **Directory Picker Cards**: Connect target source storage subtrees and the desired destination directory easily via native storage interfaces.
- **Plain Copy & Flatten Mode**: Un-nests folder trees and copies physical items directly into the parent output.
- **Compress to .ZIP Mode**: Compiles deep files directly to a single flattened Zip file in real time.

### 3. Conflict Resolution Matrix
- Resolves name clashing dynamically if deep directories contain identical filenames:
  - **Auto-Sequence**: Appends sequential counts (e.g., `invoice_1.pdf`).
  - **Parent Prefix**: Prepends the immediate parent folder name as context (e.g., `invoice_(June).pdf`).
  - **Memory Check**: Option to remember choice to automatically resolve all remaining conflicts.

### 4. Interactive Scanning Terminal
- Implements a retro monospaced CLI log output as scanning progresses, showing real-time terminal output of files being reorganized.

---

## 🛠️ Architecture

The app is built upon standard **MVVM (Model-View-ViewModel)** with Kotlin Coroutines and StateFlow:
- **`UnNestViewModel`** handles reactive state and asynchronous document streaming.
- **`MainActivity`** houses Jetpack Compose views, custom ledger grid graphics, and transition states.
- Follows strictly standard Material Design 3 spacing, interactive ripple markers, and accessibility touch boundaries.
