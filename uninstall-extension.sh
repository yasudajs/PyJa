#!/bin/bash

IDE_DIRS=(
    ".vscode"
    ".antigravity-ide"
    ".cursor"
    ".windsurf"
    ".vscode-oss"
)

UNINSTALLED_COUNT=0

for folder in "${IDE_DIRS[@]}"; do
    TARGET_DIR="$HOME/$folder/extensions/pyja-language"
    if [ -d "$TARGET_DIR" ]; then
        echo "Uninstalling from $TARGET_DIR..."
        rm -rf "$TARGET_DIR"
        if [ $? -eq 0 ]; then
            UNINSTALLED_COUNT=$((UNINSTALLED_COUNT + 1))
        fi
    fi
done

echo ""
if [ $UNINSTALLED_COUNT -gt 0 ]; then
    echo "PyJa extension uninstalled successfully from $UNINSTALLED_COUNT IDE(s)."
    echo "Please restart your IDE to apply changes."
else
    echo "PyJa extension was not found in any IDE extensions directory."
fi
