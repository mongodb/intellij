package com.mongodb.jbplugin.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object SidePanelMessages {
    @NonNls
    const val BUNDLE = "messages.SidePanelBundle"
    private val instance = DynamicBundle(SidePanelMessages::class.java, BUNDLE)

    fun message(
        key:
        @PropertyKey(resourceBundle = BUNDLE)
        String,
        vararg params: Any,
    ): @Nls String = instance.getMessage(key, *params)
}
