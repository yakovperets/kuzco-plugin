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
    project_root = os.path.abspath(os.path.join(script_dir, "../.."))

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