# kuzco upgrade
## kuzco is a tool for manage python monorepos(by kuzco pip package & pycharm plugin)
kuzco support structure:
````
my-monorepo
    ├── README.md  
    ├── .idea
    │   ├── modify_iml.py         
    │   └── my-monorepo.iml  
    ├── src 
    │   ├── scripts
    │   │   └── bin   
    │   │       └── print_file_path.py 
    │   ├── services
    │   │   ├── service-a
    │   │   └── service-b
    │   ├── utils
    │   │   ├── util-a
    │   │   └── util-b
````
### understanding kuzco plugin

````dtd
.
├── Readme.md
├── build
...
├── build.gradle.kts
...
└── src
    └── main
        ├── kotlin
        │   └── com
        │       └── kuzco
        │           └── kuzcoplugin
        │               ├── imlFileListener.kt
        │               ├── HelloAction.kt
        │               └── FileChangeListenerComponent.kt
        └── resources
            └── META-INF
                ├── plugin.xml
                └── pluginIcon.svg
````

#### build.gradle.kts
````
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij") version "1.17.2" // Consider upgrading to 1.18.0 if needed
}

group = "com.kuzco"
version = "1.1.0" // Matches plugin.xml

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.2.2") // Base version for development
    type.set("PY") // Targets both PyCharm Community and Professional
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    patchPluginXml {
        sinceBuild.set("223.7571") // Compatible with 2022.3
        untilBuild.set("243.*") // Compatible up to 2024.x
    }
    buildSearchableOptions {
        enabled = false
    }
}
````
#### src/main/resources/META-INF/plugin.xml
````
<idea-plugin>
    <id>com.kuzco.kuzcoplugin</id>
    <name>Kuzco Plugin</name>
    <vendor email="yakov.perets@gmail.com" url="https://github.com/yakovperets/kuzco-plugin" />
    <description>
        <![CDATA[
        A powerful plugin for PyCharm that enhances monorepo workflows.<br>
        Features:
        <ul>
            <li>Displays a friendly "Hello from Kuzco!" message.</li>
            <li>Triggers Python scripts on file edits.</li>
            <li>Automatically reloads .iml files when modified.</li>
        </ul>
        Compatible with PyCharm Community and Professional editions (2022.3 - 2024.x).
        ]]>
    </description>
    <version>1.1.0</version>
    <idea-version since-build="223.7571" until-build="243.*" />
    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.python</depends>

    <!-- Actions -->
    <actions>
        <action id="com.kuzco.HelloAction"
                class="com.kuzco.kuzcoplugin.HelloAction"
                text="Say Hello from Kuzco"
                description="Displays a friendly greeting from Kuzco">
            <add-to-group group-id="ToolsMenu" anchor="last" />
        </action>
    </actions>

    <!-- Project listeners -->
    <projectListeners>
        <listener class="com.kuzco.kuzcoplugin.ImlFileListener"
                  topic="com.intellij.openapi.project.ProjectManagerListener"/>
    </projectListeners>

    <!-- Project components -->
    <project-components>
        <component>
            <implementation-class>com.kuzco.kuzcoplugin.FileChangeListenerComponent</implementation-class>
        </component>
    </project-components>

    <!-- Optional: Add extension points for future enhancements -->
    <extensions defaultExtensionNs="com.intellij">
        <notificationGroup id="Kuzco Notifications"
                           displayType="BALLOON"
                           toolWindowId="ProjectView" />
        <!-- Optional: ties notifications to a tool window -->
        key="kuzco.notification.group"/> <!-- Optional: for i18n -->
    </extensions>
</idea-plugin>
````
1. 
#### src/main/kotlin/com/kuzco/kuzcoplugin/HelloAction.kt
##### HelloAction: just a greet message to validate installation on pycharm.
````
package com.kuzco.kuzcoplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class HelloAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showMessageDialog(
            "Hello from Kuzco!",
            "Greeting",
            Messages.getInformationIcon()
        )
    }
}
````
2. 
### src/main/kotlin/com/kuzco/kuzcoplugin/FileChangeListenerComponent.kt
#### trigger a python script when developer start to edit a new file.
````
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
````
### src/scripts/bin/print_file_path.py
````
import sys
import os
import xml.etree.ElementTree as ET


def update_iml_content_url(iml_file, new_url):
    """Update the content URL in the specified .iml file."""
    tree = ET.parse(iml_file)
    root = tree.getroot()

    # Find the NewModuleRootManager component and its content element
    for component in root.findall(".//component[@name='NewModuleRootManager']"):
        content = component.find("content")
        if content is not None:
            content.set("url", new_url)
            break  # Assume one content element per module

    # Write the updated XML back to the file
    tree.write(iml_file, encoding="UTF-8", xml_declaration=True)
    print(f"Updated {iml_file} with content URL: {new_url}")


def analyze_file_path(file_path, project_root):
    """Analyze the file path and determine the appropriate .iml content URL."""
    # Normalize the file path and make it relative to the project root
    rel_path = os.path.relpath(file_path, project_root).replace(os.sep, '/')

    # Check if the file is in src/services/ or src/utils/
    if rel_path.startswith("src/services/"):
        # Extract service name (e.g., 'service-a' from 'src/services/service-a/file.py')
        parts = rel_path.split('/')
        if len(parts) >= 3:  # Ensure we have at least src/services/<service-name>
            service_name = parts[2]
            return f"file://$MODULE_DIR$/src/services/{service_name}"
    elif rel_path.startswith("src/utils/"):
        # For now, we focus on services, so utils can be handled later
        parts = rel_path.split('/')
        if len(parts) >= 3:
            util_name = parts[2]
            return f"file://$MODULE_DIR$/src/utils/{util_name}"

    # Default case: return None if no match (or handle differently later)
    return None


def main():
    if len(sys.argv) < 2:
        print("No file path provided")
        sys.exit(1)

    # Get the edited file path from the plugin
    edited_file_path = sys.argv[1]
    print(f"Edited file: {edited_file_path}")

    # Determine the project root (assuming the script runs from src/scripts/bin/)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(script_dir, "../../.."))

    # Analyze the file path and get the new content URL
    new_content_url = analyze_file_path(edited_file_path, project_root)

    if new_content_url:
        # Locate the .iml file in .idea/
        iml_file = os.path.join(project_root, ".idea", "kuzco.iml")
        if os.path.exists(iml_file):
            update_iml_content_url(iml_file, new_content_url)
        else:
            print(f"Error: {iml_file} not found")
    else:
        print("File path does not match src/services/ or src/utils/, no .iml update performed")


if __name__ == "__main__":
    main()

````
3. 
### src/main/kotlin/com/kuzco/kuzcoplugin/ImlFileListener.kt
#### load the .idea/*.iml configuration whenever it has modifyed.
````
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
````

#### manually installation:
i done:
Open PyCharm.
Go to File > Settings > Plugins.
Click ⚙ (Gear icon) > Install Plugin from Disk....
Select the .zip file (kuzco-plugin-1.0-SNAPSHOT.zip).
Click OK, then Restart PyCharm.

the functionallity works as i want.

# .idea/modify_iml.py
````
import xml.etree.ElementTree as ET

def update_content_url(xml_file, new_url):
    tree = ET.parse(xml_file)
    root = tree.getroot()
    
    for component in root.findall(".//component[@name='NewModuleRootManager']"):
        content = component.find("content")
        if content is not None:
            content.set("url", new_url)
    
    tree.write(xml_file, encoding="UTF-8", xml_declaration=True)


xml_path = "kuzco.iml"  # Replace with the actual file path
new_url_value = "file://$MODULE_DIR$/src/utils/z"  # Replace with the new URL
update_content_url(xml_path, new_url_value)
````

# Result for new URL :
````
2025-03-11 14:09:33,918 [  38580]   INFO - STDOUT - Edited fileeeeeee: /modify_iml.py
2025-03-11 14:09:33,918 [  38580]   INFO - STDOUT - 
2025-03-11 14:09:34,208 [  38870]   INFO - STDOUT - Edited fileeeeeee: C:/Users/Admin/Desktop/kuzco/kuzco/.idea/modify_iml.py
2025-03-11 14:09:34,208 [  38870]   INFO - STDOUT - 
2025-03-11 14:09:36,427 [  41089]   INFO - #c.i.w.i.i.j.s.JpsGlobalModelSynchronizerImpl - Saving global entities to files
2025-03-11 14:09:36,465 [  41127]   INFO - #c.i.a.o.PathMacrosImpl - Saved path macros: {}
2025-03-11 14:09:36,496 [  41158]   INFO - #c.i.c.ComponentStoreImpl - Saving appDaemonCodeAnalyzerSettings took 16 ms, HttpConfigurable took 15 ms, PluginFeatureCacheService took 16 ms, ProjectJdkTable took 15 ms
2025-03-11 14:09:36,587 [  41249]   WARN - #c.i.u.x.Binding - no accessors for java.time.LocalTime
2025-03-11 14:09:36,618 [  41280]   INFO - #c.i.c.ComponentStoreImpl - Saving Project(name=kuzco, containerState=COMPONENT_CREATED, componentStore=C:\Users\Admin\Desktop\kuzco\kuzco)Vcs.Log.Tabs.Properties took 16 ms, XDebuggerManager took 15 ms
2025-03-11 14:09:41,713 [  46375]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Detected external change in .iml file: C:\Users\Admin\Desktop\kuzco\kuzco\.idea\kuzco.iml
2025-03-11 14:09:41,713 [  46375]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Detected external change in .iml file: C:\Users\Admin\Desktop\kuzco\kuzco\.idea\kuzco.iml
2025-03-11 14:09:41,776 [  46438]   INFO - STDOUT - Edited fileeeeeee: C:/Users/Admin/Desktop/kuzco/kuzco/.idea/kuzco.iml
2025-03-11 14:09:41,776 [  46438]   INFO - STDOUT - 
2025-03-11 14:09:41,776 [  46438]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Detected VFS change in .iml file: C:/Users/Admin/Desktop/kuzco/kuzco/.idea/kuzco.iml
2025-03-11 14:09:41,776 [  46438]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Project structure reloaded for kuzco due to VFS change in C:/Users/Admin/Desktop/kuzco/kuzco/.idea/kuzco.iml
2025-03-11 14:09:41,776 [  46438]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Injected/updated .iml file into VFS: C:/Users/Admin/Desktop/kuzco/kuzco/.idea/kuzco.iml
2025-03-11 14:09:41,776 [  46438]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Module kuzco detected for C:/Users/Admin/Desktop/kuzco/kuzco/.idea/kuzco.iml, triggering update
2025-03-11 14:09:41,776 [  46438]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Project structure updated for kuzco due to change in C:/Users/Admin/Desktop/kuzco/kuzco/.idea/kuzco.iml
2025-03-11 14:09:41,791 [  46453]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Injected/updated .iml file into VFS: C:/Users/Admin/Desktop/kuzco/kuzco/.idea/kuzco.iml
2025-03-11 14:09:41,791 [  46453]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Module kuzco detected for C:/Users/Admin/Desktop/kuzco/kuzco/.idea/kuzco.iml, triggering update
2025-03-11 14:09:41,791 [  46453]   INFO - #com.kuzco.kuzcoplugin.ImlFileListener - Project structure updated for kuzco due to change in C:/Users/Admin/Desktop/kuzco/kuzco/.idea/kuzco.iml
2025-03-11 14:09:42,111 [  46773]   INFO - #c.i.w.i.i.EntitiesOrphanageImpl - Update orphanage. 0 modules added
2025-03-11 14:09:42,111 [  46773]   INFO - #c.i.w.i.i.WorkspaceModelImpl - Project model updated to version 4 in 12 ms: Reload entities after changes in JPS configuration files
2025-03-11 14:09:42,142 [  46804]   INFO - #c.i.o.p.MergingQueueGuiExecutor - Running task: UnindexedFilesScanner[kuzco, 1 iterators]
2025-03-11 14:09:42,144 [  46806]   INFO - #c.i.u.i.UnindexedFilesScanner - Started scanning for indexing of kuzco. Reason: changes in: "Module 'kuzco' (utils)"
2025-03-11 14:09:42,144 [  46806]   INFO - #c.i.u.i.UnindexedFilesScanner - Performing delayed pushing properties tasks for kuzco took 0ms; general responsiveness: ok; EDT responsiveness: ok
2025-03-11 14:09:42,144 [  46806]   INFO - #c.i.u.i.UnindexedFilesScanner - Scanning of kuzco uses 7 scanning threads
2025-03-11 14:09:42,144 [  46806]   INFO - #c.i.o.p.MergingQueueGuiExecutor - Running task: (dumb mode task) com.jetbrains.python.psi.impl.PythonLanguageLevelPusher$MyDumbModeTask@82703ef
2025-03-11 14:09:42,176 [  46838]   INFO - #c.i.o.p.DumbServiceMergingTaskQueue - Initializing DumbServiceMergingTaskQueue...
2025-03-11 14:09:42,196 [  46858]   INFO - #c.i.u.i.UnindexedFilesScanner - Scanning completed for kuzco. Number of scanned files: 8; Number of files for indexing: 0 took 52ms; general responsiveness: ok; EDT responsiveness: ok
2025-03-11 14:09:42,196 [  46858]   INFO - #c.i.u.i.PerProjectIndexingQueue - Finished for kuzco. No files to index with loading content.
2025-03-11 14:09:42,196 [  46858]   INFO - #c.i.o.p.MergingQueueGuiExecutor - Task finished: UnindexedFilesScanner[kuzco, 1 iterators]
2025-03-11 14:09:42,276 [  46938]   INFO - #c.i.o.p.MergingQueueGuiExecutor - Task finished: (dumb mode task) com.jetbrains.python.psi.impl.PythonLanguageLevelPusher$MyDumbModeTask@82703ef
2025-03-11 14:09:50,094 [  54756]   INFO - #c.j.p.PyToolWindowLayoutProvider - Python default layout applied
2025-03-11 14:09:54,217 [  58879]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4729 ms since showing to preload popup menu 'Refactor' at 'MainMenu(preload-bgt)' in 3 ms
2025-03-11 14:09:54,217 [  58879]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4731 ms since showing to preload popup menu 'Tools' at 'MainMenu(preload-bgt)' in 4 ms
2025-03-11 14:09:54,217 [  58879]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4732 ms since showing to preload popup menu 'Help' at 'MainMenu(preload-bgt)' in 5 ms
2025-03-11 14:09:54,217 [  58879]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4739 ms since showing to preload popup menu 'Window' at 'MainMenu(preload-bgt)' in 12 ms
2025-03-11 14:09:54,237 [  58899]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4751 ms since showing to preload popup menu 'Git' at 'MainMenu(preload-bgt)' in 24 ms
2025-03-11 14:09:54,253 [  58915]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4774 ms since showing to preload popup menu 'View' at 'MainMenu(preload-bgt)' in 47 ms
2025-03-11 14:09:54,284 [  58946]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4794 ms since showing to preload popup menu 'Code' at 'MainMenu(preload-bgt)' in 66 ms
2025-03-11 14:09:54,284 [  58946]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4797 ms since showing to preload popup menu 'Edit' at 'MainMenu(preload-bgt)' in 69 ms
2025-03-11 14:09:54,315 [  58977]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4833 ms since showing to preload popup menu 'Navigate' at 'MainMenu(preload-bgt)' in 105 ms
2025-03-11 14:09:54,315 [  58977]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4837 ms since showing to preload popup menu 'Run' at 'MainMenu(preload-bgt)' in 109 ms
2025-03-11 14:09:54,384 [  59046]   INFO - #c.i.o.a.i.PopupMenuPreloader - 4900 ms since showing to preload popup menu 'File' at 'MainMenu(preload-bgt)' in 171 ms
2025-03-11 14:09:54,911 [  59573]   INFO - #c.i.w.i.i.j.s.JpsGlobalModelSynchronizerImpl - Saving global entities to files
2025-03-11 14:09:54,943 [  59605]   INFO - #c.i.c.ComponentStoreImpl - Saving Project(name=kuzco, containerState=COMPONENT_CREATED, componentStore=C:\Users\Admin\Desktop\kuzco\kuzco)ProjectStartupSharedConfiguration took 15 ms
````