(function () {
  'use strict';

  const sanitizeOptions = {
    USE_PROFILES: { svg:true, svgFilters:true, html:true },
    ADD_TAGS: ['foreignobject'],
    HTML_INTEGRATION_POINTS: { foreignobject:true },
    FORBID_TAGS: ['script', 'iframe', 'object', 'embed'],
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: true,
    RETURN_DOM_FRAGMENT: true
  };
  let renderId = 0;
  let queue = Promise.resolve();

  function prepareDiagrams(content) {
    const diagrams = [];
    content.querySelectorAll('pre > code').forEach((code) => {
      const isMermaid = Array.from(code.classList)
        .some((name) => name.toLowerCase() === 'language-mermaid');
      if (!isMermaid) return;
      const figure = document.createElement('figure');
      figure.className = 'mermaid-diagram';
      const status = document.createElement('div');
      status.className = 'mermaid-status';
      status.setAttribute('role', 'status');
      status.textContent = '正在渲染 Mermaid 图表…';
      figure.append(status);
      diagrams.push({ figure, source:code.textContent || '' });
      code.parentElement.replaceWith(figure);
    });
    return diagrams;
  }

  function errorMessage(error) {
    const message = typeof error?.message === 'string' ? error.message : String(error || '未知错误');
    const normalized = message.replace(/\r\n?/g, '\n').trim();
    return normalized.length > 800 ? normalized.slice(0, 800) + '…' : normalized;
  }

  function showError(diagram, message) {
    diagram.figure.classList.add('is-error');
    const alert = document.createElement('div');
    alert.className = 'mermaid-error';
    alert.setAttribute('role', 'alert');
    const title = document.createElement('strong');
    title.textContent = 'Mermaid 图表渲染失败';
    const detail = document.createElement('pre');
    detail.className = 'mermaid-error-detail';
    detail.textContent = message || '请检查图表语法后重试。';
    alert.append(title, detail);
    const source = document.createElement('pre');
    source.className = 'mermaid-source';
    source.tabIndex = 0;
    source.setAttribute('aria-label', 'Mermaid 图表源码');
    const code = document.createElement('code');
    code.textContent = diagram.source;
    source.append(code);
    diagram.figure.replaceChildren(alert, source);
  }

  function hasUnsafeCss(value) {
    if (/@import|expression\s*\(|javascript\s*:/i.test(value)) return true;
    return Array.from(value.matchAll(/url\(\s*(['"]?)(.*?)\1\s*\)/gi))
      .some((match) => !match[2].trim().startsWith('#'));
  }

  function cleanSvg(svg, index) {
    const content = window.DOMPurify.sanitize(svg, sanitizeOptions);
    const safeUri = /^(?:#|data:image\/(?:png|gif|jpe?g|webp);base64,)/i;
    const uriAttributes = ['href', 'src', 'xlink:href', 'srcset', 'action', 'formaction', 'poster'];
    content.querySelectorAll('*').forEach((node) => {
      Array.from(node.attributes).forEach((attribute) => {
        const name = attribute.name.toLowerCase();
        if (name.startsWith('on') || (uriAttributes.includes(name)
          && attribute.value && !safeUri.test(attribute.value.trim()))) {
          node.removeAttribute(attribute.name);
        }
      });
      const style = node.getAttribute('style');
      if (style && hasUnsafeCss(style)) node.removeAttribute('style');
    });
    content.querySelectorAll('style').forEach((style) => {
      if (hasUnsafeCss(style.textContent)) style.remove();
    });
    const element = content.querySelector('svg');
    if (!element) throw new Error('Mermaid 未生成有效的 SVG。');
    element.setAttribute('role', 'img');
    element.setAttribute('aria-label', `Mermaid 图表 ${index + 1}`);
    return element;
  }

  async function renderDiagram(diagram, index, isCurrent) {
    const id = `markview-mermaid-${++renderId}`;
    try {
      const result = await window.mermaid.render(id, diagram.source);
      if (!isCurrent()) return;
      const svg = cleanSvg(result.svg, index);
      diagram.figure.replaceChildren(svg);
      diagram.figure.classList.add('is-rendered');
    } catch (error) {
      if (isCurrent()) showError(diagram, errorMessage(error));
    } finally {
      document.getElementById(id)?.remove();
      document.getElementById(`d${id}`)?.remove();
      document.getElementById(`i${id}`)?.remove();
    }
  }

  function render(source, options = {}) {
    const template = document.createElement('template');
    template.innerHTML = window.MarkviewMarkdown.render(source);
    const content = template.content;
    const diagrams = prepareDiagrams(content);
    if (!diagrams.length) return Promise.resolve(content);
    const isCurrent = typeof options.isCurrent === 'function' ? options.isCurrent : () => true;
    if (!window.mermaid || typeof window.mermaid.render !== 'function') {
      diagrams.forEach((diagram) => showError(diagram, 'Mermaid 引擎未加载，请重新启动应用。'));
      return Promise.resolve(content);
    }
    const run = queue.then(async () => {
      if (!isCurrent()) return null;
      window.mermaid.initialize({
        startOnLoad:false,
        securityLevel:'strict',
        suppressErrorRendering:true,
        theme:options.theme === 'dark' ? 'dark' : 'default',
        fontFamily:'-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif',
        maxTextSize:50000
      });
      for (let index = 0; index < diagrams.length; index++) {
        if (!isCurrent()) return null;
        await renderDiagram(diagrams[index], index, isCurrent);
      }
      return isCurrent() ? content : null;
    });
    queue = run.catch(() => {});
    return run;
  }

  window.MarkviewMermaid = Object.freeze({ render });
}());
