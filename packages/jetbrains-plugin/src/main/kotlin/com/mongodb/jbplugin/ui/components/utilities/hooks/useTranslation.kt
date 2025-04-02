package com.mongodb.jbplugin.ui.components.utilities.hooks

import androidx.compose.runtime.Composable
import com.mongodb.jbplugin.i18n.SidePanelMessages
import com.mongodb.jbplugin.i18n.SidePanelMessages.BUNDLE
import org.jetbrains.annotations.PropertyKey

@Composable
fun useTranslation(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
    SidePanelMessages.message(key, *params)
