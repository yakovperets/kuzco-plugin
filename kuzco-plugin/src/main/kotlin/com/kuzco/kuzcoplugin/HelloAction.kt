package com.kuzco.kuzcoplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * Displays a greeting message to the user when triggered from the Tools menu.
 */
class HelloAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return // Safely handle null project
        Messages.showMessageDialog(
            project,
            "Hello from Kuzco!",
            "Kuzco Greeting",
            Messages.getInformationIcon()
        )
    }

    override fun update(e: AnActionEvent) {
        // Enable action only when a project is open
        e.presentation.isEnabled = e.project != null
    }
}