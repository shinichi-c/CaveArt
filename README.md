# CaveArt: Dynamic Wallpaper Collection

​CaveArt is a modern, sleek Android application designed to display and set high-quality wallpapers. Built using Jetpack Compose, the app emphasizes a beautiful, swipable UI and features a powerful dynamic loading system that allows you to add new content without writing a single line of Kotlin code.

# ✨ Features

- Dynamic Content Discovery: Automatically scans the res/drawable folder for new wallpapers.
​Intuitive Swiping UI: Features a 3D-effect horizontal pager for browsing wallpapers.

- Category Filtering: Easily filter content using automatically generated category chips.

- ​Wallpaper Destination: Select whether to apply the wallpaper to the Home Screen, Lock Screen, or Both.

- ​Modern Design: Built with Jetpack Compose and supports Material You (Dynamic Color) on compatible devices.

# 🛠️ Adding New Wallpapers

​The most important feature for development is the automatic content loader. To add a new wallpaper to the app.

- Step 1: Place Your File

Place your image file (e.g., .jpg, .png, or even a .xml drawable) into the following directory:

              app/src/main/res/drawable

- Step 2: Follow the Naming Convention

Your file name MUST start with the prefix wp_ and must follow the format wp_TITLE_TAG. The last word of the filename (before the extension) is automatically used as the Category Tag.

Use underscores (_) instead of spaces.

Full File Name Example:

        wp_golden_sunset_arch_landscape.jpg


# File Parsing Logic

The application will read this filename and generate the corresponding wallpaper entry:

# ⚙️ Tech Stack

- Platform: Android

- UI Toolkit: Jetpack Compose

- Language: Kotlin

- Architecture: AndroidViewModel (for resource access)
