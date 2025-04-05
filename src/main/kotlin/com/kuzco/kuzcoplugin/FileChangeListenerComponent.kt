package com.kuzco.kuzcoplugin

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Listens for file edits and triggers a Python script asynchronously with debouncing.
 */
class FileChangeListenerComponent(private val project: Project) : ProjectComponent {
    private val logger = Logger.getInstance(FileChangeListenerComponent::class.java)
    private val notificationGroup = NotificationGroupManager.getInstance()
        .getNotificationGroup("Kuzco Notifications")
    private val objectMapper = ObjectMapper()
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val pendingEdits = ConcurrentHashMap<String, Long>() // File path -> last edit timestamp
    private val debounceDelayMs = 500L // Adjust as needed

    override fun projectOpened() {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                val filePath = file.path
                scheduleScriptExecution(filePath)
            }
        }, project)
    }

    private fun scheduleScriptExecution(filePath: String) {
        val now = System.currentTimeMillis()
        pendingEdits[filePath] = now

        // Debounce: Wait briefly before executing, canceling if a newer edit arrives
        executor.schedule({
            if (pendingEdits[filePath] == now) { // Only proceed if this is the latest edit
                pendingEdits.remove(filePath)
                executePythonScriptAsync(filePath)
            }
        }, debounceDelayMs, TimeUnit.MILLISECONDS)
    }

    private fun executePythonScriptAsync(filePath: String) {
        val projectPath = project.basePath ?: run {
            logger.warn("Project base path unavailable")
            return
        }

        executor.execute {
            try {
                val pythonExe = findPythonExecutable(projectPath)
                    ?: throw IllegalStateException("Python executable not found")
                val scriptPath = findPythonScript(projectPath)
                    ?: throw IllegalStateException("Script not found at scripts/bin/dynamic_iml_modification.py")

                val processBuilder = ProcessBuilder(pythonExe, scriptPath, filePath)
                    .directory(File(scriptPath).parentFile)
                    .redirectErrorStream(true)

                val process = processBuilder.start()
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line -> logger.info("Script output: $line") }
                }
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    logger.warn("Script failed with exit code $exitCode")
                    notifyError("Script execution failed for $filePath (check logs)")
                } else {
                    logger.info("Script executed successfully for $filePath")
                }
            } catch (e: IllegalStateException) {
                logger.error("Configuration error: ${e.message}")
                notifyError(e.message ?: "Unknown error")
            } catch (e: java.io.IOException) {
                logger.error("IO error executing script: $filePath", e)
                notifyError("IO error executing script (check logs)")
            } catch (e: Exception) {
                logger.error("Unexpected error executing script: $filePath", e)
                notifyError("Unexpected error (check logs)")
            }
        }
    }

    private fun findPythonExecutable(projectPath: String): String? {
        val lockFile = File(projectPath, "src/python-lock.json")
        if (lockFile.exists()) {
            try {
                val config = objectMapper.readValue(lockFile, PythonLockConfig::class.java)
                for (path in config.pythonScriptsLocation) {
                    val pythonExe = File(path, "python.exe")
                    if (pythonExe.exists() && pythonExe.isFile) return pythonExe.absolutePath
                    val pythonUnix = File(path, "python")
                    if (pythonUnix.exists() && pythonUnix.isFile) return pythonUnix.absolutePath
                }
            } catch (e: Exception) {
                logger.warn("Failed to parse python-lock.json", e)
            }
        }

        // Fallbacks (simplified for brevity; expand as needed)
        val pythonCmd = listOf("python", "python3").firstOrNull { cmd ->
            try {
                ProcessBuilder(cmd, "--version").start().waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }
        return pythonCmd
    }

    private fun findPythonScript(projectPath: String): String? {
        val script = File(projectPath, "scripts/bin/dynamic_iml_modification.py")
        return if (script.exists()) script.absolutePath else null
    }

    private fun notifyError(message: String) {
        notificationGroup.createNotification(
            "Kuzco Plugin Error",
            message,
            NotificationType.ERROR
        ).notify(project)
    }

    override fun projectClosed() {
        executor.shutdown()
    }

    override fun getComponentName(): String = "FileChangeListenerComponent"
}