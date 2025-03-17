Below is a polished and clear README for your Kuzco plugin project. It provides an overview, installation instructions, features, usage, and development details in a concise and professional manner.

---

# Kuzco Plugin for PyCharm

A powerful PyCharm plugin designed to enhance Python monorepo workflows. Kuzco automates project configuration updates and provides tools to streamline development in PyCharm Community and Professional editions (versions 2022.3 to 2024.x).

## Features

- **Greeting Message**: Validate plugin installation with a friendly "Hello from Kuzco!" message.
- **File Edit Triggers**: Automatically runs a Python script (`print_file_path.py`) when a file is edited, updating the `.iml` configuration based on the file's location.
- **Dynamic `.iml` Reloading**: Detects and reloads changes to `.iml` files in the `.idea` directory, ensuring the project structure stays up-to-date.
- **Monorepo Support**: Seamlessly integrates with a monorepo structure, targeting `src/services/` and `src/utils/` subdirectories.

## Supported Structure

Kuzco is designed for Python monorepos with the following layout:

```
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
```

## Installation

### Manual Installation
1. **Download the Plugin**:
   - Obtain the plugin ZIP file (e.g., `kuzco-plugin-1.1.0.zip`) from the releases page or build it yourself (see [Development](#development)).
2. **Install in PyCharm**:
   - Open PyCharm.
   - Go to `File > Settings > Plugins`.
   - Click the ⚙ (Gear icon) and select `Install Plugin from Disk...`.
   - Choose the `.zip` file and click `OK`.
   - Restart PyCharm to activate the plugin.

### Prerequisites
- PyCharm Community or Professional (2022.3 - 2024.x).
- Python 3.x installed and available in your system PATH or standard installation directory.

## Usage

1. **Validate Installation**:
   - After installation, go to `Tools > Say Hello from Kuzco` in the PyCharm menu.
   - A dialog will display "Hello from Kuzco!" to confirm the plugin is working.

2. **Edit a File**:
   - Open any file in your monorepo (e.g., under `src/services/` or `src/utils/`).
   - Start editing the file. The plugin will:
     - Detect the edit.
     - Execute `src/scripts/bin/print_file_path.py`.
     - Update the `.iml` file (e.g., `kuzco.iml`) with the appropriate content URL (like `file://$MODULE_DIR$/src/services/service-a`).

3. **Monitor `.iml` Changes**:
   - Modify an `.iml` file manually (e.g., via `modify_iml.py`).
   - The plugin will detect the change and reload the project structure automatically.

## How It Works

- **HelloAction**: Displays a greeting to confirm the plugin is installed.
- **FileChangeListenerComponent**: Listens for file edits, triggers `print_file_path.py`, and shows notifications.
- **ImlFileListener**: Watches for `.iml` file changes in the `.idea` directory and updates the project structure.
- **print_file_path.py**: Analyzes edited file paths and updates the `.iml` content URL based on the monorepo structure.

## Development

### Building the Plugin
1. **Clone the Repository**:
   ```bash
   git clone https://github.com/yakovperets/kuzco-plugin.git
   cd kuzco-plugin
   ```
2. **Set Up Environment**:
   - Ensure you have JDK 17 and Kotlin 1.9.23 installed.
   - Open the project in IntelliJ IDEA with the IntelliJ Platform Plugin SDK configured.
3. **Build**:
   - Run `./gradlew buildPlugin` to generate the plugin ZIP file (e.g., `kuzco-plugin-1.1.0.zip`).
4. **Install Locally**:
   - Follow the [Manual Installation](#manual-installation) steps with your built ZIP file.

### Project Structure
- **`src/main/kotlin/com/kuzco/kuzcoplugin/`**: Core plugin logic (Kotlin).
- **`src/main/resources/META-INF/plugin.xml`**: Plugin configuration.
- **`src/scripts/bin/print_file_path.py`**: Python script for `.iml` updates.
- **`build.gradle.kts`**: Gradle build script for plugin compilation.

### Dependencies
- IntelliJ Platform Plugin SDK (version 2023.2.2).
- Kotlin JVM (1.9.23).
- Targets PyCharm (type "PY").

## Troubleshooting

- **Script Not Executing**: Ensure Python is installed and accessible in your PATH or at a standard location (e.g., `%USERPROFILE%\AppData\Local\Programs\Python` on Windows).
- **Notifications Missing**: Check PyCharm’s notification settings under `File > Settings > Appearance & Behavior > Notifications`.
- **`.iml` Not Updating**: Verify the file path matches `src/services/` or `src/utils/` in your monorepo.

## Contributing

Contributions are welcome! Please:
1. Fork the repository.
2. Create a feature branch.
3. Submit a pull request with a clear description of changes.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Contact

For support or inquiries, reach out to [yakov.perets@gmail.com](mailto:yakov.perets@gmail.com) or visit the [GitHub repository](https://github.com/yakovperets/kuzco-plugin).

---

This README is concise, user-friendly, and covers everything a developer or user needs to know about the Kuzco plugin. Let me know if you'd like any adjustments!