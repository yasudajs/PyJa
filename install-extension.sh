#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

IDE_DIRS=(
    ".vscode"
    ".antigravity-ide"
    ".cursor"
    ".windsurf"
    ".vscode-oss"
)

INSTALLED_COUNT=0

for folder in "${IDE_DIRS[@]}"; do
    TARGET_PARENT="$HOME/$folder"
    if [ -d "$TARGET_PARENT" ]; then
        TARGET_DIR="$TARGET_PARENT/extensions/pyja-language"
        echo "Installing to $TARGET_DIR..."
        
        # 拡張機能フォルダを準備してコピー
        mkdir -p "$TARGET_PARENT/extensions"
        if [ -d "$TARGET_DIR" ]; then
            rm -rf "$TARGET_DIR"
        fi
        
        cp -r "$SCRIPT_DIR/editor/vscode/pyja-extension" "$TARGET_DIR"
        if [ $? -eq 0 ]; then
            INSTALLED_COUNT=$((INSTALLED_COUNT + 1))
        fi
    fi
done

echo ""
if [ $INSTALLED_COUNT -gt 0 ]; then
    echo "PyJa extension installed successfully for $INSTALLED_COUNT IDE(s)."
    echo "Please restart your IDE to apply changes."
else
    echo "No compatible IDE directories found."
    echo "Creating extension folder in .vscode by default."
    mkdir -p "$HOME/.vscode/extensions"
    cp -r "$SCRIPT_DIR/editor/vscode/pyja-extension" "$HOME/.vscode/extensions/pyja-language"
fi
