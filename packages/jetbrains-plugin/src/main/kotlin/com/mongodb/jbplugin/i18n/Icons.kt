package com.mongodb.jbplugin.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import java.awt.Component
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.SwingConstants

@Service(Service.Level.APP)
class Icons {
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

    val sidePanelLogo = themedIcon(
        light = ownIcon("/icons/SidePanelLogo.svg"),
        dark = ownIcon("/icons/SidePanelLogoDark.svg")
    )

    val sidePanelLogoAttention =
        LayeredIcon.layeredIcon(arrayOf(sidePanelLogo, redCircle)).apply {
            val scaledRedCircle = IconUtil.resizeSquared(redCircle, 10)
            setIcon(scaledRedCircle, 1, SwingConstants.NORTH_EAST)
        }

    val disabledInspectionIcon = ownIcon("/icons/DisabledInspection.svg")

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

    private fun ownIcon(path: String): Icon {
        return IconLoader.getIcon(path, javaClass.classLoader)
    }

    private fun themedIcon(
        light: Icon,
        dark: Icon
    ): Icon {
        return if (JBColor.isBright()) light else dark
    }

    companion object {
        val instance: Icons
            get() = ApplicationManager.getApplication().service<Icons>()

        fun Icon.scaledToText(parentComponent: Component? = null): Icon {
            val settingsManager: NotRoamableUiSettings = NotRoamableUiSettings.getInstance()
            val settings = settingsManager.state
            return IconUtil.scaleByFont(this, parentComponent, settings.fontSize)
        }

        @Composable
        fun Icon.toImageBitmap(): ImageBitmap {
            return remember {
                val consideredWidth = this.iconWidth
                val consideredHeight = this.iconHeight

                val bufferedImage = BufferedImage(
                    consideredWidth,
                    consideredHeight,
                    BufferedImage.TYPE_INT_ARGB
                )
                val graphics = bufferedImage.createGraphics()

                this.paintIcon(null, graphics, 0, 0)
                graphics.dispose()

                bufferedImage.toComposeImageBitmap()
            }
        }
    }
}
