const path = require('node:path');
const { Client } = require('ssh2');

const markdownPattern = /\.(md|markdown|mdown|mkd|txt)$/i;
const maxDocumentSize = 10 * 1024 * 1024;

function callbackCall(target, method, ...args) {
  return new Promise((resolve, reject) => {
    target[method](...args, (error, result) => error ? reject(error) : resolve(result));
  });
}

function remoteError(error) {
  if (error?.level === 'client-authentication') return '服务器拒绝了用户名或密码。';
  if (error?.code === 'ECONNREFUSED') return '无法连接服务器，请检查地址、端口和 SFTP 服务。';
  if (error?.code === 'ETIMEDOUT' || error?.level === 'client-timeout') return '连接服务器超时，请检查网络后重试。';
  if (error?.code === 'ENOTFOUND' || error?.code === 'EAI_AGAIN') return '找不到服务器，请检查主机地址。';
  return error?.message || 'SFTP 操作失败，请检查连接和远程路径。';
}

function validRemotePath(remotePath) {
  return typeof remotePath === 'string' && remotePath.length > 0 && remotePath.length <= 4096 && !remotePath.includes('\0');
}

class SftpService {
  constructor() {
    this.client = null;
    this.sftp = null;
    this.description = '';
    this.queue = Promise.resolve();
  }

  enqueue(operation) {
    const result = this.queue.then(operation, operation);
    this.queue = result.catch(() => {});
    return result;
  }

  async connect(config) {
    const host = typeof config.host === 'string' ? config.host.trim() : '';
    const username = typeof config.username === 'string' ? config.username.trim() : '';
    const password = typeof config.password === 'string' ? config.password : '';
    const port = Number(config.port);
    if (!host || host.length > 253 || /[\s/\\]/.test(host)) throw new Error('请输入有效的主机名或 IP 地址。');
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('端口必须是 1 到 65535 之间的整数。');
    if (!username || /[\r\n\0]/.test(username)) throw new Error('请输入有效的用户名。');
    if (!password) throw new Error('请输入服务器密码。');

    this.close();
    const client = new Client();
    try {
      const sftp = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('建立 SFTP 会话超时，请重试。')), 20000);
        client.once('ready', () => {
          client.sftp((error, value) => {
            clearTimeout(timer);
            if (error) reject(error);
            else resolve(value);
          });
        });
        client.once('error', (error) => {
          clearTimeout(timer);
          reject(error);
        });
        client.connect({ host, port, username, password, readyTimeout: 15000 });
      });
      this.client = client;
      this.sftp = sftp;
      this.description = `${username}@${host}:${port}`;
      client.on('close', () => {
        if (this.client === client) {
          this.client = null;
          this.sftp = null;
        }
      });
      return { success: true, description: this.description };
    } catch (error) {
      client.end();
      throw error;
    }
  }

  close() {
    if (this.client) this.client.end();
    this.client = null;
    this.sftp = null;
    this.description = '';
  }

  requireSession() {
    if (!this.client || !this.sftp) throw new Error('SFTP 连接已断开，请重新连接。');
    return this.sftp;
  }

  async list(remotePath) {
    if (!validRemotePath(remotePath)) throw new Error('请输入有效的远程路径。');
    const sftp = this.requireSession();
    const absolutePath = await callbackCall(sftp, 'realpath', remotePath);
    const entries = await callbackCall(sftp, 'readdir', absolutePath);
    return {
      success: true,
      path: absolutePath,
      entries: entries.map((entry) => ({
        name: entry.filename,
        path: path.posix.join(absolutePath, entry.filename),
        directory: entry.attrs.isDirectory(),
        size: Number(entry.attrs.size) || 0,
        modified: Number(entry.attrs.mtime) * 1000 || 0
      })).sort((a, b) => a.directory !== b.directory ? (a.directory ? -1 : 1) : a.name.localeCompare(b.name, 'zh-CN'))
    };
  }

  async read(remotePath) {
    if (!validRemotePath(remotePath) || !markdownPattern.test(remotePath)) throw new Error('请选择 Markdown 或 .txt 文本文件。');
    const sftp = this.requireSession();
    const info = await callbackCall(sftp, 'stat', remotePath);
    if (info.isDirectory()) throw new Error('远程路径是目录，请选择文件。');
    if (info.size > maxDocumentSize) throw new Error('文件超过 10 MB，请选择较小的文本文件。');
    const stream = sftp.createReadStream(remotePath);
    const chunks = [];
    await new Promise((resolve, reject) => {
      stream.on('data', (chunk) => chunks.push(chunk));
      stream.once('error', reject);
      stream.once('end', resolve);
    });
    const data = Buffer.concat(chunks);
    if (data.length > maxDocumentSize) throw new Error('文件超过 10 MB，请选择较小的文本文件。');
    return {
      success: true,
      name: path.posix.basename(remotePath),
      path: remotePath,
      encoded: data.toString('base64')
    };
  }

  async write(remotePath, source) {
    if (!validRemotePath(remotePath) || typeof source !== 'string') throw new Error('远程保存参数无效。');
    if (Buffer.byteLength(source, 'utf8') > maxDocumentSize) throw new Error('文件超过 10 MB，无法通过当前窗口保存。');
    const sftp = this.requireSession();
    const stream = sftp.createWriteStream(remotePath, { flags: 'w' });
    await new Promise((resolve, reject) => {
      stream.once('error', reject);
      stream.once('close', resolve);
      stream.end(Buffer.from(source, 'utf8'));
    });
    return { success: true, path: remotePath };
  }

  handle(action, payload = {}) {
    return this.enqueue(async () => {
      try {
        if (action === 'connect') return await this.connect(payload);
        if (action === 'disconnect') {
          this.close();
          return { success: true };
        }
        if (action === 'list') return await this.list(payload.path);
        if (action === 'read') return await this.read(payload.path);
        if (action === 'write') return await this.write(payload.path, payload.source);
        throw new Error('不支持的 SFTP 操作。');
      } catch (error) {
        return { success: false, message: remoteError(error) };
      }
    });
  }
}

module.exports = { SftpService };
