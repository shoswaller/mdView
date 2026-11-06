const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('DesktopBridge', {
  openDocument: () => ipcRenderer.invoke('markview:choose'),
  saveDocument: (name, source) => ipcRenderer.invoke('markview:save', name, source),
  sftp: (action, payload) => ipcRenderer.invoke('markview:sftp', action, payload),
  onOpenDocument: (callback) => {
    const listener = (_event, result) => callback(result);
    ipcRenderer.on('markview:open', listener);
    return () => ipcRenderer.removeListener('markview:open', listener);
  }
});
