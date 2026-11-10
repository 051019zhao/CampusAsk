// Keep only presentation markup. Uploaded text and model output are untrusted.
export function safeMarkdown(html) {
  const source = new DOMParser().parseFromString(html, 'text/html')
  const allowed = new Set(['P', 'BR', 'STRONG', 'EM', 'DEL', 'UL', 'OL', 'LI', 'PRE', 'CODE', 'BLOCKQUOTE', 'H1', 'H2', 'H3', 'H4', 'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD', 'HR', 'A'])
  const root = document.createElement('div')
  function copy(node, parent) {
    if (node.nodeType === 3) { parent.appendChild(document.createTextNode(node.textContent)); return }
    if (node.nodeType !== 1) return
    if (['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT', 'SVG', 'MATH'].includes(node.tagName)) return
    const target = allowed.has(node.tagName) ? document.createElement(node.tagName.toLowerCase()) : document.createDocumentFragment()
    if (node.tagName === 'A') {
      try {
        const url = new URL(node.getAttribute('href'), window.location.origin)
        if (['http:', 'https:'].includes(url.protocol)) {
          target.setAttribute('href', url.href)
          target.setAttribute('target', '_blank')
          target.setAttribute('rel', 'noopener noreferrer')
        }
      } catch { /* Render invalid links as plain text. */ }
    }
    for (const child of node.childNodes) copy(child, target)
    parent.appendChild(target)
  }
  for (const node of source.body.childNodes) copy(node, root)
  return root.innerHTML
}
