package com.android.CaveArt.animations

enum class AnimationStyle(val label: String) {
    MORPH("Organic Morph"),
    NANO_ASSEMBLY("Big Bang"),
    ORGANIC_BLOB("3D Organic Blob")
}

object AnimationFactory {
    fun getAnimation(style: AnimationStyle): WallpaperAnimation {
        return when (style) {
            AnimationStyle.MORPH -> MorphAnimation()
            AnimationStyle.NANO_ASSEMBLY -> NanoAssemblyAnimation()
            AnimationStyle.ORGANIC_BLOB -> OrganicBlobAnimation()
        }
    }
}
