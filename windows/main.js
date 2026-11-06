const { app, BrowserWindow, Menu, dialog, ipcMain, shell } = require('electron');
const fs = require('node:fs/promises');
const path = require('node:path');
const { SftpService } = require('./sftp');
const { SftpProfileStore } = require('./sftp-profiles');

const sftp = new SftpService();
const markdownPattern = /\.(md|markdown|mdown|mkd|txt)$/i;
let mainWindow;
let sftpProfiles;
let pendingPath = findMarkdownPath(process.argv);

async function handleSftp(action, payload = {}) {
  try {
    if (action === 'profileList') return await sftpProfiles.list();
    if (action === 'updateProfile') {
      const result = await sftpProfiles.update(payload);
      return { success: true, profile: result.profile };
    }
    if (action === 'deleteProfile') return await sftpProfiles.remove(payload.id);
    if (action === 'connectSaved') {
      const config = await sftpProfiles.connectionConfig(payload.id);
      const result = await sftp.handle('connect', config);
      if (result.success) {
        result.initialPath = config.initialPath;
        result.profileId = payload.id;
        try {
          await sftpProfiles.touch(payload.id);
        } catch {
          result.warning = '服务器已连接，但最近使用时间未能保存。';
        }
      }
      return result;
    }
    if (action === 'connect') {
      const result = await sftp.handle('connect', payload);
      if (result.success) {
        try {
          const saved = await sftpProfiles.save(payload);
          result.profileId = saved.profile.id;
          result.initialPath = saved.profile.initialPath;
          if (!saved.passwordSaved) result.warning = '服务器已连接，但系统加密不可用，密码未保存。';
        } catch {
          result.warning = '服务器已连接，但配置保存失败，请检查本地存储权限。';
        }
      }
      return result;
    }
    return await sftp.handle(action, payload);
  } catch (error) {
    return { success: false, message: error.message || 'SFTP 服务器配置操作失败。' };
  }
}

function findMarkdownPath(args) {
  return args.slice(1).find((argument) => markdownPattern.test(argument) && !argument.startsWith('--'));
}

async function readDocument(filePath) {
  try {
    const filePathResolved = path.resolve(filePath);
    if (!markdownPattern.test(filePathResolved)) {
      return { message: '请选择 Markdown 或 .txt 文本文件。' };
    }
    const data = await fs.readFile(filePathResolved);
    return {
      name: path.basename(filePathResolved),
      path: filePathResolved,
      encoded: data.toString('base64')
    };
  } catch {
    return { message: '无法读取文件，请确认文件仍然存在且可以访问。' };
  }
}

function openDocument(filePath) {
  if (!filePath) return;
  pendingPath = filePath;
  if (!mainWindow || mainWindow.isDestroyed()) return;
  readDocument(filePath).then((result) => {
    if (mainWindow) mainWindow.webContents.send('markview:open', result);
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    show: false,
    backgroundColor: '#f6f6f5',
    icon: path.join(__dirname, 'icon.ico'),
    title: '墨页',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.webContents.once('did-finish-load', () => {
    const filePath = pendingPath;
    pendingPath = undefined;
    if (filePath) readDocument(filePath).then((result) => {
      if (mainWindow) mainWindow.webContents.send('markview:open', result);
    });
  });
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (url === mainWindow.webContents.getURL()) return;
    event.preventDefault();
    if (/^https?:\/\//i.test(url)) shell.openExternal(url);
  });
  mainWindow.on('closed', () => { mainWindow = undefined; });
  mainWindow.loadFile(path.join(__dirname, '..', 'index.html'));
}

const hasSingleInstanceLock = app.requestSingleInstanceLock();
if (!hasSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', (_event, argv) => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    }
    openDocument(findMarkdownPath(argv));
  });

  app.whenReady().then(() => {
    Menu.setApplicationMenu(null);
    sftpProfiles = new SftpProfileStore(path.join(app.getPath('userData'), 'sftp-profiles.json'));
    ipcMain.handle('markview:choose', async () => {
      const result = await dialog.showOpenDialog(mainWindow, {
        title: '打开 Markdown 文件',
        properties: ['openFile'],
        filters: [
          { name: 'Markdown 文档', extensions: ['md', 'markdown', 'mdown', 'mkd', 'txt'] },
          { name: '所有文件', extensions: ['*'] }
        ]
      });
      if (result.canceled || !result.filePaths[0]) return { canceled: true };
      return readDocument(result.filePaths[0]);
    });

    ipcMain.handle('markview:save', async (_event, name, source) => {
      try {
        const result = await dialog.showSaveDialog(mainWindow, {
          title: '导出 Markdown 文件',
          defaultPath: path.join(app.getPath('documents'), name),
          filters: [{ name: 'Markdown 文档', extensions: ['md'] }]
        });
        if (result.canceled || !result.filePath) return { canceled: true };
        await fs.writeFile(result.filePath, source, 'utf8');
        return { success: true, name: path.basename(result.filePath) };
      } catch {
        return { success: false, message: '导出失败，请检查保存位置和文件权限。' };
      }
    });

    ipcMain.handle('markview:sftp', (_event, action, payload) => handleSftp(action, payload));

    createWindow();
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => {
    sftp.close();
    app.quit();
  });
}
