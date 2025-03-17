package com.kuzco.kuzcoplugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Listens for file edits in the project and triggers a Python script when a new file is edited.
 *
 * @param project The current IntelliJ project.
 */
class FileChangeListenerComponent(private val project: Project) : ProjectComponent {
    private val lastEditedFilePath = AtomicReference<String?>()
    private val notificationGroup = NotificationGroupManager.getInstance()
        .getNotificationGroup("Kuzco Notifications")

    override fun projectOpened() {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                val filePath = file.path
                if (lastEditedFilePath.compareAndSet(filePath, null)) return // Skip if same file
                lastEditedFilePath.set(filePath)
                executePythonScript(filePath)
            }
        }, project)
    }

    private fun executePythonScript(filePath: String) {
        val projectPath = project.basePath ?: run {
            notifyError("Project base path is unavailable")
            return
        }
        try {
            val pythonExe = findPythonExecutable() ?: throw IllegalStateException("Python executable not found")
            val scriptPath = findPythonScript(projectPath) ?: throw IllegalStateException("Script not found at src/scripts/bin/print_edited_file.py")

            val processBuilder = ProcessBuilder(pythonExe, scriptPath, filePath)
                .directory(File(scriptPath).parentFile)
                .redirectErrorStream(true)

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() == 0) {
                notifyInfo("Script executed: $output")
            } else {
                notifyError("Script failed with output: $output")
            }
        } catch (e: Exception) {
            notifyError("Failed to execute script: ${e.message}")
        }
    }

    private fun findPythonExecutable(): String? {
        // Check common installation directory (Windows)
        val userHome = System.getProperty("user.home")
        val pythonBasePath = File("$userHome\\AppData\\Local\\Programs\\Python")
        if (pythonBasePath.exists() && pythonBasePath.isDirectory) {
            val pythonDirs = pythonBasePath.listFiles { file -> file.isDirectory && file.name.startsWith("Python") }
            pythonDirs?.sortedByDescending { it.name.replace("Python", "").toIntOrNull() ?: 0 }?.forEach { dir ->
                val pythonExe = File(dir, "python.exe")
                if (pythonExe.exists()) return pythonExe.absolutePath
            }
        }

        // Fallback: Check system PATH for 'python' or 'python3'
        listOf("python", "python3").forEach { cmd ->
            try {
                val process = ProcessBuilder(cmd, "--version").start()
                if (process.waitFor() == 0) {
                    return cmd // Found in PATH
                }
            } catch (e: Exception) {
                // Command not found, continue to next
            }
        }

        return null // No Python found
    }

    private fun findPythonScript(projectPath: String): String? {
        val script = File(projectPath, "src/scripts/bin/print_edited_file.py")
        return if (script.exists()) script.absolutePath else null
    }

    private fun notifyInfo(message: String) {
        notificationGroup.createNotification(
            "Kuzco Plugin",
            message,
            NotificationType.INFORMATION
        ).notify(project)
    }

    private fun notifyError(message: String) {
        notificationGroup.createNotification(
            "Kuzco Plugin Error",
            message,
            NotificationType.ERROR
        ).notify(project)
    }

    override fun getComponentName(): String = "FileChangeListenerComponent"
}