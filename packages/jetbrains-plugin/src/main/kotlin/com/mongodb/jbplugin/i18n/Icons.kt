package com.mongodb.jbplugin.i18n

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.SwingConstants

object Icons {
    private val redCircle = ownIcon("/icons/RedCircle.svg")
    private val jbProvidedMongoDBLogo = AllIcons.Providers.MongoDB

    val loading = AnimatedIcon.Default()

    private val database = themedIcon(
        light = ownIcon("/icons/Database.svg"),
        dark = ownIcon("/icons/DatabaseDark.svg")
    )

    private val collection = themedIcon(
        light = ownIcon("/icons/Collection.svg"),
        dark = ownIcon("/icons/CollectionDark.svg")
    )

    private val field = themedIcon(
        light = ownIcon("/icons/Field.svg"),
        dark = ownIcon("/icons/FieldDark.svg")
    )

    val runQueryGutter = themedIcon(
        light = ownIcon("/icons/ConsoleRun.svg"),
        dark = ownIcon("/icons/ConsoleRunDark.svg")
    )

    val databaseAutocompleteEntry = database
    val collectionAutocompleteEntry = collection
    val fieldAutocompleteEntry = field
    val information = AllIcons.General.Information
    val warning = AllIcons.General.Warning

    private val greenCheckmark = AllIcons.General.GreenCheckmark
    private val questionMark = AllIcons.General.QuestionDialog

    object SidePanel {
        val logo = themedIcon(
            light = ownIcon("/icons/SidePanelLogo.svg"),
            dark = ownIcon("/icons/SidePanelLogoDark.svg")
        )

        val logoAttention =
            LayeredIcon.layeredIcon(arrayOf(logo, redCircle)).apply {
                val scaledRedCircle = IconUtil.resizeSquared(redCircle, 10)
                setIcon(scaledRedCircle, 1, SwingConstants.NORTH_EAST)
            }
    }

    val disabledInspectionIcon = ownIcon("/icons/DisabledInspection.svg").toImageBitmap()

    val queryNotRunIcon = LayeredIcon.layeredIcon(arrayOf(jbProvidedMongoDBLogo, questionMark)).apply {
        val scaledGreenCircle = IconUtil.resizeSquared(questionMark, 9)
        setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
    }

    val indexOkIcon = LayeredIcon.layeredIcon(arrayOf(jbProvidedMongoDBLogo, greenCheckmark)).apply {
        val scaledGreenCircle = IconUtil.resizeSquared(greenCheckmark, 9)
        setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
    }

    val indexWarningIcon = LayeredIcon.layeredIcon(arrayOf(jbProvidedMongoDBLogo, warning)).apply {
        val scaledGreenCircle = IconUtil.resizeSquared(warning, 9)
        setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
    }

    fun Icon.scaledToText(parentComponent: Component? = null): Icon {
        val settingsManager: NotRoamableUiSettings = NotRoamableUiSettings.getInstance()
        val settings = settingsManager.state
        return IconUtil.scaleByFont(this, parentComponent, settings.fontSize)
    }

    private fun Icon.toImageBitmap(): ImageBitmap {
        val consideredWidth = this.iconWidth
        val consideredHeight = this.iconHeight

        val bufferedImage = UIUtil.createImage(null, consideredWidth, consideredHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = bufferedImage.createGraphics()

        this.paintIcon(null, graphics, 0, 0)
        graphics.dispose()

        return bufferedImage.toComposeImageBitmap()
    }

    private fun ownIcon(path: String): Icon {
        return IconLoader.getIcon(path, javaClass.classLoader)
    }

    private fun themedIcon(light: Icon, dark: Icon): Icon {
        return if (JBColor.isBright()) light else dark
    }
}
