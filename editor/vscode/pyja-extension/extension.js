const vscode = require('vscode');
const path = require('path');

function activate(context) {
    let disposable = vscode.commands.registerCommand('pyja.run', function () {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showErrorMessage('アクティブなエディタが見つかりません。');
            return;
        }

        const document = editor.document;
        if (document.languageId !== 'pyja') {
            vscode.window.showErrorMessage('アクティブなファイルは PyJa ファイルではありません。');
            return;
        }

        // ファイルを自動保存
        document.save().then(() => {
            const filePath = document.fileName;
            const fileDir = path.dirname(filePath);

            // ワークスペースフォルダを検索
            const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
            const projectRoot = workspaceFolder ? workspaceFolder.uri.fsPath : fileDir;

            // プロジェクトルートからの相対パスから完全修飾クラス名 (FQCN) を計算
            const relativePath = path.relative(projectRoot, filePath);
            const relativePathWithoutExt = relativePath.slice(0, -path.extname(relativePath).length);
            const fqcn = relativePathWithoutExt.replace(/[\\/]/g, '.');

            const isWindows = process.platform === 'win32';
            let terminalCmd = '';

            if (isWindows) {
                // Windows環境の場合
                const pyjacPath = path.join(projectRoot, 'pyjac.bat');
                const pyjaPath = path.join(projectRoot, 'pyja.bat');
                
                // プロジェクトルートディレクトリに移動し、FQCNを指定して実行する
                terminalCmd = `cd /d "${projectRoot}" && "${pyjacPath}" "${filePath}" && "${pyjaPath}" ${fqcn}`;
            } else {
                // macOS / Linux環境の場合
                const pyjacPath = path.join(projectRoot, 'pyjac');
                const pyjaPath = path.join(projectRoot, 'pyja');
                terminalCmd = `cd "${projectRoot}" && "${pyjacPath}" "${filePath}" && "${pyjaPath}" ${fqcn}`;
            }

            // "PyJa Run" という名前のターミナルを探す、または新規作成
            let terminal = vscode.window.terminals.find(t => t.name === 'PyJa Run');
            if (!terminal) {
                const options = { name: 'PyJa Run' };
                if (isWindows) {
                    // Windowsでは cmd.exe を強制して && による順次実行を確実にする
                    options.shellPath = 'cmd.exe';
                }
                terminal = vscode.window.createTerminal(options);
            }
            
            terminal.show();
            terminal.sendText(terminalCmd);
        });
    });

    context.subscriptions.push(disposable);
}

function deactivate() {}

module.exports = {
    activate,
    deactivate
};
