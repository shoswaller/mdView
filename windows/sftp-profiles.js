const fs = require('node:fs/promises');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const { safeStorage } = require('electron');

function normalize(input = {}) {
  const host = typeof input.host === 'string' ? input.host.trim() : '';
  const username = typeof input.username === 'string' ? input.username.trim() : '';
  const name = typeof input.name === 'string' ? input.name.trim() : '';
  const initialPath = typeof input.initialPath === 'string' ? input.initialPath.trim() || '/' : '/';
  const port = Number(input.port);
  if (!host || host.length > 253 || /[\s/\\]/.test(host)) throw new Error('请输入有效的主机名或 IP 地址。');
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('端口必须是 1 到 65535 之间的整数。');
  if (!username || username.length > 256 || /[\r\n\0]/.test(username)) throw new Error('请输入有效的用户名。');
  if (name.length > 80 || /[\r\n\0]/.test(name)) throw new Error('服务器名称不能超过 80 个字符。');
  if (!initialPath || initialPath.length > 4096 || initialPath.includes('\0')) throw new Error('请输入有效的初始目录。');
  return { name, host, port, username, initialPath };
}

function publicProfile(profile) {
  return {
    id: profile.id,
    name: profile.name,
    host: profile.host,
    port: profile.port,
    username: profile.username,
    initialPath: profile.initialPath,
    hasPassword: Boolean(profile.encryptedPassword),
    createdAt: profile.createdAt,
    updatedAt: profile.updatedAt,
    lastUsedAt: profile.lastUsedAt
  };
}

class SftpProfileStore {
  constructor(filePath) {
    this.filePath = filePath;
    this.profiles = [];
    this.ready = this.load();
    this.queue = Promise.resolve();
  }

  async load() {
    try {
      const data = JSON.parse(await fs.readFile(this.filePath, 'utf8'));
      if (data.version !== 1 || !Array.isArray(data.profiles)) throw new Error('配置格式不受支持。');
      this.profiles = data.profiles.filter((profile) => profile && typeof profile.id === 'string');
    } catch (error) {
      if (error.code !== 'ENOENT') throw new Error('无法读取已保存的 SFTP 服务器配置。');
    }
  }

  enqueue(operation) {
    const result = this.queue.then(async () => {
      await this.ready;
      return operation();
    });
    this.queue = result.catch(() => {});
    return result;
  }

  async persist() {
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    const temporaryPath = `${this.filePath}.${process.pid}.${randomUUID()}.tmp`;
    try {
      await fs.writeFile(temporaryPath, JSON.stringify({ version: 1, profiles: this.profiles }, null, 2), {
        encoding: 'utf8',
        mode: 0o600
      });
      await fs.rename(temporaryPath, this.filePath);
    } finally {
      await fs.rm(temporaryPath, { force: true }).catch(() => {});
    }
  }

  list() {
    return this.enqueue(() => ({
      success: true,
      encryptionAvailable: safeStorage.isEncryptionAvailable(),
      profiles: this.profiles.map(publicProfile).sort((a, b) => b.lastUsedAt - a.lastUsedAt || b.updatedAt - a.updatedAt)
    }));
  }

  save(input, markUsed = true) {
    return this.enqueue(() => this.saveProfile(input, markUsed));
  }

  async saveProfile(input, markUsed) {
    const values = normalize(input);
    const now = Date.now();
    const existingIndex = this.profiles.findIndex((profile) => input.id
      ? profile.id === input.id
      : profile.host.toLowerCase() === values.host.toLowerCase()
        && profile.port === values.port && profile.username === values.username);
    const existing = existingIndex >= 0 ? this.profiles[existingIndex] : null;
    let encryptedPassword = existing?.encryptedPassword || '';
    const password = typeof input.password === 'string' ? input.password : '';
    if (password && safeStorage.isEncryptionAvailable()) {
      encryptedPassword = safeStorage.encryptString(password).toString('base64');
    }
    const profile = {
      id: existing?.id || randomUUID(),
      ...values,
      name: values.name || values.host,
      encryptedPassword,
      createdAt: existing?.createdAt || now,
      updatedAt: now,
      lastUsedAt: markUsed ? now : existing?.lastUsedAt || 0
    };
    if (existing) this.profiles.splice(existingIndex, 1, profile);
    else this.profiles.push(profile);
    await this.persist();
    return { profile: publicProfile(profile), passwordSaved: Boolean(encryptedPassword) };
  }

  update(input) {
    return this.enqueue(async () => {
      const values = normalize(input);
      const duplicate = this.profiles.some((profile) => profile.id !== input.id
        && profile.host.toLowerCase() === values.host.toLowerCase()
        && profile.port === values.port && profile.username === values.username);
      if (duplicate) throw new Error('相同主机、端口和用户名的服务器已存在。');
      if (!this.profiles.some((profile) => profile.id === input.id)) throw new Error('服务器配置不存在，可能已被删除。');
      return this.saveProfile(input, false);
    });
  }

  connectionConfig(id) {
    return this.enqueue(() => {
      const profile = this.profiles.find((item) => item.id === id);
      if (!profile) throw new Error('服务器配置不存在，可能已被删除。');
      if (!profile.encryptedPassword || !safeStorage.isEncryptionAvailable()) {
        throw new Error('未保存可用密码，请编辑服务器并重新输入密码。');
      }
      try {
        return {
          name: profile.name,
          host: profile.host,
          port: profile.port,
          username: profile.username,
          password: safeStorage.decryptString(Buffer.from(profile.encryptedPassword, 'base64')),
          initialPath: profile.initialPath
        };
      } catch {
        throw new Error('保存的密码无法解密，请编辑服务器并重新输入密码。');
      }
    });
  }

  touch(id) {
    return this.enqueue(async () => {
      const profile = this.profiles.find((item) => item.id === id);
      if (!profile) return;
      profile.lastUsedAt = Date.now();
      await this.persist();
    });
  }

  remove(id) {
    return this.enqueue(async () => {
      const previous = this.profiles;
      this.profiles = previous.filter((profile) => profile.id !== id);
      if (this.profiles.length === previous.length) throw new Error('服务器配置不存在，可能已被删除。');
      try {
        await this.persist();
      } catch (error) {
        this.profiles = previous;
        throw error;
      }
      return { success: true };
    });
  }
}

module.exports = { SftpProfileStore };
