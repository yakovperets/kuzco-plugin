package com.kuzco.kuzcoplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.MessageBusConnection
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Listens for changes to .iml files and updates the project structure accordingly.
 */
class ImlFileListener : ProjectManagerListener {
    private val logger = Logger.getInstance(ImlFileListener::class.java)
    private var connection: MessageBusConnection? = null
    private val lastRefreshTimes = ConcurrentHashMap<String, AtomicLong>()
    private val executorService = Executors.newSingleThreadExecutor { Thread(it, "ImlFileWatcher") }

    companion object {
        private const val DEBOUNCE_INTERVAL_MS = 1000L // Configurable in the future
    }

    override fun projectOpened(project: Project) {
        logger.info("Activating ImlFileListener for ${project.name}")
        setupVfsListener(project)
        startFileSystemWatcher(project)
    }

    private fun setupVfsListener(project: Project) {
        connection = project.messageBus.connect(project)
        connection?.subscribe(VirtualFileManager.VFS_CHANGES, object : com.intellij.openapi.vfs.newvfs.BulkFileListener {
            override fun after(events: MutableList<out com.intellij.openapi.vfs.newvfs.events.VFileEvent>) {
                events.filter { it.file?.extension == "iml" }.forEach { event ->
                    logger.debug("VFS change detected: ${event.file?.path}")
                    refreshProjectStructure(project, event.file!!)
                }
            }
        })
    }

    private fun startFileSystemWatcher(project: Project) {
        val ideaDir = project.basePath?.let { Path.of(it, ".idea") } ?: return
        if (!ideaDir.toFile().exists()) {
            logger.warn(".idea directory not found for ${project.name}")
            return
        }

        executorService.submit {
            FileSystems.getDefault().newWatchService().use { watchService ->
                ideaDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
                logger.info("Watching .idea directory: $ideaDir")

                while (!Thread.currentThread().isInterrupted) {
                    val key = watchService.poll(1, TimeUnit.SECONDS) ?: continue
                    key.pollEvents()
                        .mapNotNull { it.context() as? Path }
                        .map { ideaDir.resolve(it) }
                        .filter { it.toString().endsWith(".iml") }
                        .forEach { imlPath ->
                            debounceAndRefresh(project, imlPath)
                        }
                    key.reset()
                }
            }
        }
    }

    private fun debounceAndRefresh(project: Project, imlPath: Path) {
        val lastRefresh = lastRefreshTimes.computeIfAbsent(imlPath.toString()) { AtomicLong(0L) }
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRefresh.get() >= DEBOUNCE_INTERVAL_MS) {
            logger.info("External change detected: $imlPath")
            ApplicationManager.getApplication().invokeLater {
                injectImlFileIntoVfs(project, imlPath)
                lastRefresh.set(currentTime)
            }
        }
    }

    private fun injectImlFileIntoVfs(project: Project, imlPath: Path) {
        ApplicationManager.getApplication().runWriteAction {
            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(imlPath.toString()) ?: return@runWriteAction
            vFile.refresh(false, false)
            logger.debug("Injected .iml file: ${vFile.path}")
            ModuleManager.getInstance(project).findModuleByName(project.name)?.let {
                ProjectRootManager.getInstance(project).incModificationCount()
                logger.info("Updated project structure for ${vFile.path}")
            }
        }
    }

    private fun refreshProjectStructure(project: Project, file: com.intellij.openapi.vfs.VirtualFile) {
        ApplicationManager.getApplication().runWriteAction {
            file.refresh(false, true)
            ProjectRootManager.getInstance(project).incModificationCount()
            logger.info("Reloaded project structure due to VFS change: ${file.path}")
        }
    }

    override fun projectClosed(project: Project) {
        logger.info("Deactivating ImlFileListener for ${project.name}")
        connection?.disconnect()
        executorService.shutdownNow()
        lastRefreshTimes.clear()
    }
}