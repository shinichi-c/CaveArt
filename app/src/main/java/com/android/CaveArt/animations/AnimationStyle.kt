package com.android.CaveArt.animations

enum class AnimationStyle(val label: String) {
    MORPH("Organic Morph"),
    NANO_ASSEMBLY("Big Bang")
}

object AnimationFactory {
    fun getAnimation(style: AnimationStyle): WallpaperAnimation {
        return when (style) {
            AnimationStyle.MORPH -> MorphAnimation()
            AnimationStyle.NANO_ASSEMBLY -> NanoAssemblyAnimation()
        }
    }
}