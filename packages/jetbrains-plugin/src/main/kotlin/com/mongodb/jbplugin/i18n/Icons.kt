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
import java.awt.Component
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.SwingConstants

object Icons {
    private val greenCircle = IconLoader.getIcon("/icons/GreenCircle.svg", javaClass)
    private val redCircle = IconLoader.getIcon("/icons/RedCircle.svg", javaClass)
    val loading = AnimatedIcon.Default()
    val logo = AllIcons.Providers.MongoDB
    val logoConnected =
        LayeredIcon.layeredIcon(arrayOf(logo, greenCircle)).apply {
            val scaledGreenCircle = IconUtil.resizeSquared(greenCircle, 6)
            setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
        }
    val connectionFailed = AllIcons.General.Error
    val remove = AllIcons.Diff.Remove
    private val databaseLight = IconLoader.getIcon("/icons/Database.svg", javaClass)
    private val databaseDark = IconLoader.getIcon("/icons/DatabaseDark.svg", javaClass)
    private val database = if (JBColor.isBright()) databaseLight else databaseDark
    private val collectionLight = IconLoader.getIcon("/icons/Collection.svg", javaClass)
    private val collectionDark = IconLoader.getIcon("/icons/CollectionDark.svg", javaClass)
    private val collection = if (JBColor.isBright()) collectionLight else collectionDark
    private val fieldLight = IconLoader.getIcon("/icons/Field.svg", javaClass)
    private val fieldDark = IconLoader.getIcon("/icons/FieldDark.svg", javaClass)
    private val field = if (JBColor.isBright()) fieldLight else fieldDark
    private val runQueryGutterLight = IconLoader.getIcon("/icons/ConsoleRun.svg", javaClass)
    private val runQueryGutterDark = IconLoader.getIcon("/icons/ConsoleRunDark.svg", javaClass)
    val runQueryGutter = if (JBColor.isBright()) runQueryGutterLight else runQueryGutterDark
    val databaseAutocompleteEntry = database
    val collectionAutocompleteEntry = collection
    val fieldAutocompleteEntry = field
    val information = AllIcons.General.Information
    val warning = AllIcons.General.Warning
    private val greenCheckmark = AllIcons.General.GreenCheckmark
    private val questionMark = AllIcons.General.QuestionDialog

    object SidePanel {
        private object LogoRaw {
            val logoLight = IconLoader.getIcon("/icons/SidePanelLogo.svg", javaClass)
            val logoDark = IconLoader.getIcon("/icons/SidePanelLogoDark.svg", javaClass)
        }

        val logo = if (JBColor.isBright()) LogoRaw.logoLight else LogoRaw.logoDark
        val logoAttention =
            LayeredIcon.layeredIcon(arrayOf(logo, redCircle)).apply {
                val scaledRedCircle = IconUtil.resizeSquared(redCircle, 10)
                setIcon(scaledRedCircle, 1, SwingConstants.NORTH_EAST)
            }
    }

    val disabledInspectionIcon = IconLoader.getIcon(
        "/icons/DisabledInspection.svg",
        javaClass
    ).toImageBitmap()

    val queryNotRunIcon = LayeredIcon.layeredIcon(arrayOf(logo, questionMark)).apply {
        val scaledGreenCircle = IconUtil.resizeSquared(questionMark, 9)
        setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
    }

    val indexOkIcon = LayeredIcon.layeredIcon(arrayOf(logo, greenCheckmark)).apply {
        val scaledGreenCircle = IconUtil.resizeSquared(greenCheckmark, 9)
        setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
    }

    val indexWarningIcon = LayeredIcon.layeredIcon(arrayOf(logo, warning)).apply {
        val scaledGreenCircle = IconUtil.resizeSquared(warning, 9)
        setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
    }

    fun Icon.scaledToText(parentComponent: Component? = null): Icon {
        val settingsManager: NotRoamableUiSettings = NotRoamableUiSettings.getInstance()
        val settings = settingsManager.state
        return IconUtil.scaleByFont(this, parentComponent, settings.fontSize)
    }

    fun Icon.toImageBitmap(): ImageBitmap {
        val consideredWidth = this.iconWidth
        val consideredHeight = this.iconHeight

        val bufferedImage = BufferedImage(consideredWidth, consideredHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = bufferedImage.createGraphics()

        this.paintIcon(null, graphics, 0, 0)
        graphics.dispose()

        return bufferedImage.toComposeImageBitmap()
    }
}
