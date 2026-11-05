const ICONS = {
  back: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>',
  forward: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5l7 7-7 7"/></svg>',
  reload: '<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 1 0-2.6 5.9"/><path d="M20 4v7h-7"/></svg>',
  plus: '<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
  go: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h13"/><path d="M12 6l6 6-6 6"/></svg>',
  settings: '<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 8h9M17 8h3M4 16h3M11 16h9"/><circle cx="15" cy="8" r="2.2"/><circle cx="9" cy="16" r="2.2"/></svg>',
}

const STYLES = `
.brs {
  --brs-chrome: #dfe3e8;
  --brs-tab: #c9ced6;
  --brs-surface: #f6f7f9;
  --brs-field: #ffffff;
  --brs-text: #1f2124;
  --brs-muted: #5b6167;
  --brs-line: rgba(0, 0, 0, .12);
  --brs-hover: rgba(0, 0, 0, .07);
  --brs-stage: #101114;
  display: flex;
  flex-direction: column;
  height: 100%;
  color: var(--brs-text);
  font: 13px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
}
@media (prefers-color-scheme: dark) {
  .brs {
    --brs-chrome: #35363a;
    --brs-tab: #25262a;
    --brs-surface: #1f2023;
    --brs-field: #3c4043;
    --brs-text: #e8eaed;
    --brs-muted: #9aa0a6;
    --brs-line: rgba(255, 255, 255, .14);
    --brs-hover: rgba(255, 255, 255, .1);
  }
}
.brs-window {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--brs-chrome);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, .14), 0 8px 24px rgba(0, 0, 0, .10);
}
.brs-only-page .brs-tabbar, .brs-only-page .brs-toolbar, .brs-only-page .brs-menu, .brs-only-page .brs-hud { display: none; }
.brs-only-page .brs-stage { min-height: 0; height: 100%; }
.brs-only-page .brs-window { box-shadow: none; border-radius: 0; }
.brs-tabbar { display: flex; align-items: flex-end; gap: 2px; padding: 8px 8px 0; }
.brs-tabs { display: flex; align-items: flex-end; gap: 2px; flex: 1; min-width: 0; overflow-x: auto; scrollbar-width: none; }
.brs-tabs::-webkit-scrollbar { display: none; }
.brs-tab {
  display: flex; align-items: center; gap: 7px;
  flex: 0 1 220px; min-width: 68px; max-width: 220px; height: 33px;
  padding: 0 6px 0 10px; border: 0; border-radius: 9px 9px 0 0;
  background: transparent; color: var(--brs-muted); font: inherit; text-align: left; cursor: pointer;
}
.brs-tab:hover { background: var(--brs-hover); }
.brs-tab.is-active { background: var(--brs-surface); color: var(--brs-text); }
.brs-tab.is-new { animation: brs-tab-in .18s ease-out; }
.brs-favicon { flex: none; width: 13px; height: 13px; border-radius: 4px; background: currentColor; opacity: .38; transition: opacity .15s ease; }
.brs-favicon.is-loading {
  background: transparent; border-radius: 50%; opacity: .8;
  border: 2px solid currentColor; border-top-color: transparent;
  animation: brs-spin .8s linear infinite;
}
.brs-favicon.is-error { background: #d93025; opacity: .95; }
.brs-tab.is-active .brs-favicon { opacity: .62; }
.brs-tab.is-active .brs-favicon.is-loading { opacity: .85; }
.brs-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.brs-close {
  flex: none; width: 18px; height: 18px; border-radius: 50%;
  display: grid; place-items: center; font-size: 14px; line-height: 1; opacity: 0;
  transition: background .15s ease, opacity .15s ease;
}
.brs-tab:hover .brs-close, .brs-tab.is-active .brs-close { opacity: .55; }
.brs-close:hover { background: var(--brs-hover); opacity: 1; }
.brs-toolbar { display: flex; align-items: center; gap: 2px; padding: 5px 8px 7px; background: var(--brs-surface); border-bottom: 1px solid var(--brs-line); }
.brs-iconbtn {
  display: grid; place-items: center; width: 30px; height: 30px; flex: none;
  border: 0; border-radius: 50%; background: transparent; color: var(--brs-text); cursor: pointer;
}
.brs-iconbtn:hover { background: var(--brs-hover); }
.brs-iconbtn.is-on { background: var(--brs-hover); }
.brs-iconbtn:disabled { opacity: .4; cursor: default; background: transparent; }
.brs-tabs > .brs-iconbtn { width: 28px; height: 28px; margin: 0 0 3px 4px; flex: none; }
.brs-omnibox {
  display: flex; align-items: center; gap: 2px; flex: 1; min-width: 0;
  height: 32px; margin: 0 6px; padding: 0 2px 0 12px;
  border-radius: 999px; background: var(--brs-field); border: 1px solid var(--brs-line);
}
.brs-omnibox:focus-within { border-color: #1a73e8; box-shadow: 0 0 0 2px rgba(26, 115, 232, .18); }
.brs-address { flex: 1; min-width: 0; border: 0; background: transparent; color: inherit; font: inherit; outline: none; }
.brs-stage {
  position: relative; flex: 1 1 auto; height: calc(100vh - 190px); min-height: 420px;
  display: flex; align-items: center; justify-content: center; overflow: hidden; background: var(--brs-stage);
}
.brs-canvas { display: block; max-width: 100%; max-height: 100%; background: var(--brs-stage); outline: none; cursor: default; opacity: 0; transition: opacity .25s ease; }
.brs-canvas.is-ready { opacity: 1; }
.brs-overlay {
  position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%);
  max-width: 70%; padding: 8px 14px; border-radius: 999px; text-align: center;
  background: rgba(0, 0, 0, .62); color: #e6e8eb; font-size: 12px; pointer-events: none;
}
.brs-overlay[hidden] { display: none; }
.brs-hud {
  position: absolute; left: 8px; bottom: 8px; right: 8px; padding: 4px 10px;
  border-radius: 999px; background: rgba(0, 0, 0, .6); color: #e6e8eb;
  font: 11px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; pointer-events: none;
}
.brs-menu {
  position: absolute; right: 10px; top: 82px; z-index: 6; width: min(380px, calc(100% - 20px));
  display: grid; gap: 9px; padding: 12px;
  border-radius: 12px; background: var(--brs-field); color: var(--brs-text);
  border: 1px solid var(--brs-line); box-shadow: 0 10px 30px rgba(0, 0, 0, .24);
  font-size: 12px;
}
.brs-menu[hidden] { display: none; }
.brs-menu-title { font-size: 12px; font-weight: 600; color: var(--brs-muted); }
.brs-row { display: flex; align-items: center; gap: 8px; }
.brs-row > input[type="range"] { flex: 1; min-width: 0; }
.brs-toggle { display: flex; align-items: center; gap: 8px; }
.brs-toggle > select { margin-left: auto; }
.brs-select { background: var(--brs-field); color: inherit; border: 1px solid var(--brs-line); border-radius: 6px; padding: 2px 6px; font: inherit; }
.brs-note { color: var(--brs-muted); font: 11px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; overflow-wrap: anywhere; }
.brs-info { display: grid; gap: 4px; padding-top: 6px; border-top: 1px solid var(--brs-line); }
.brs-menu:not([hidden]) { animation: brs-pop .16s ease-out; }
.brs-iconbtn, .brs-tab, .brs-omnibox { transition: background .15s ease, color .15s ease, border-color .15s ease; }
@keyframes brs-spin { to { transform: rotate(360deg); } }
@keyframes brs-tab-in { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: none; } }
@keyframes brs-pop { from { opacity: 0; transform: translateY(-6px) scale(.98); } to { opacity: 1; transform: none; } }
@media (prefers-reduced-motion: reduce) {
  .brs *, .brs *::before, .brs *::after { animation: none !important; transition: none !important; }
}
`

class BrowserStreamElement extends HTMLElement {
  #socket = null
  #canvas = null
  #resizeObserver = null
  #sessionId = null
  #streamPath = '/browser-stream'
  #activeTabId = null
  #tabs = []
  #stream = {quality: 75, intervalMillis: 40, networkEnabled: false, bandwidthBytesPerSecond: 500 * 1024, latencyMillis: 0}
  #onlyPage = false
  #displayViewport = {width: 0, height: 0}
  #viewport = {width: 0, height: 0}
  #telemetry = null
  #stats = {frames: 0, bytes: 0, renderFrames: 0, windowStart: 0, fps: 0, renderFps: 0, frameBytes: 0, lastFrameBytes: 0, droppedFrames: 0, serverLatencyMs: -1}
  #pending = null
  #chunks = new Map()
  #drawing = false
  #latestFrameId = -1
  #moveFrame = 0
  #pendingMove = null
  #pointerSequence = 0
  #qualityTimer = null
  #focusAddressOnTabChange = false
  #socketGeneration = 0
  #reconnectTimer = null
  #reconnectDelay = 0
  #lastMessageAt = 0
  #livenessTimer = null
  #requestedWidth = 0
  #requestedHeight = 0
  #everDrew = false
  #failedOpens = 0
  #deadSession = false

  connectedCallback() {
    this.#build()
    this.#observeViewport()
    this.#connect()
  }

  disconnectedCallback() {
    this.#socketGeneration += 1
    this.#resizeObserver?.disconnect()
    this.#resizeObserver = null
    clearTimeout(this.#qualityTimer)
    clearTimeout(this.#reconnectTimer)
    clearInterval(this.#livenessTimer)
    if (this.#moveFrame) cancelAnimationFrame(this.#moveFrame)
    this.#moveFrame = 0
    this.#pendingMove = null
    this.#socket?.close()
    this.#socket = null
  }

  get onlyPage() { return this.#onlyPage }

  set onlyPage(value) {
    this.#onlyPage = Boolean(value)
    this.querySelector('.brs')?.classList.toggle('brs-only-page', this.#onlyPage)
  }

  get streamPath() {
    return this.#streamPath
  }

  set streamPath(value) {
    this.#streamPath = value || '/browser-stream'
    this.#connect()
  }

  get sessionId() {
    return this.#sessionId
  }

  set sessionId(value) {
    if (this.#sessionId === value) return
    this.#sessionId = value
    this.#everDrew = false
    this.#failedOpens = 0
    this.#deadSession = false
    this.#canvas?.classList.remove('is-ready')
    this.#updateOverlay()
    this.#connect()
  }

  #build() {
    if (this.#canvas) return
    const root = document.createElement('div')
    root.className = this.#onlyPage ? 'brs brs-only-page' : 'brs'
    root.append(this.#stylesheet())

    const windowEl = document.createElement('div')
    windowEl.className = 'brs-window'

    const tabbar = document.createElement('div')
    tabbar.className = 'brs-tabbar'
    this.tabStrip = document.createElement('div')
    this.tabStrip.className = 'brs-tabs'
    this.newTabButton = this.#iconButton('plus', '新建标签页', () => {
      this.#focusAddressOnTabChange = true
      this.#send({type: 'tab.open', url: 'about:blank'})
    })
    tabbar.append(this.tabStrip)

    const toolbar = document.createElement('div')
    toolbar.className = 'brs-toolbar'
    this.backButton = this.#iconButton('back', '后退', () => this.#send({type: 'history.back'}))
    this.forwardButton = this.#iconButton('forward', '前进', () => this.#send({type: 'history.forward'}))
    this.reloadButton = this.#iconButton('reload', '刷新', () => this.#send({type: 'reload'}))

    const omnibox = document.createElement('div')
    omnibox.className = 'brs-omnibox'
    this.address = document.createElement('input')
    this.address.className = 'brs-address'
    this.address.type = 'text'
    this.address.placeholder = '搜索或输入网址'
    this.address.disabled = true
    this.address.autocomplete = 'off'
    this.address.spellcheck = false
    this.address.addEventListener('keydown', event => {
      if (event.key === 'Enter') {
        event.preventDefault()
        this.#navigate()
      }
    })
    this.openButton = this.#iconButton('go', '打开', () => this.#navigate())
    omnibox.append(this.address, this.openButton)
    this.panelToggle = this.#iconButton('settings', '画面参数', () => this.#togglePanel())
    toolbar.append(this.backButton, this.forwardButton, this.reloadButton, omnibox, this.panelToggle)

    this.panel = document.createElement('div')
    this.panel.className = 'brs-menu'
    this.panel.hidden = true
    const panelTitle = document.createElement('div')
    panelTitle.className = 'brs-menu-title'
    panelTitle.textContent = '画面参数'
    this.qualityInput = this.#slider(20, 90, 5, this.#stream.quality, '质量', value => {
      this.#stream.quality = value
      this.#scheduleQuality()
    })
    this.intervalInput = this.#slider(40, 600, 20, this.#stream.intervalMillis, '间隔', value => {
      this.#stream.intervalMillis = value
      this.#scheduleQuality()
    })
    const networkRow = document.createElement('label')
    networkRow.className = 'brs-toggle'
    this.networkEnabledInput = document.createElement('input')
    this.networkEnabledInput.type = 'checkbox'
    this.networkEnabledInput.checked = this.#stream.networkEnabled
    this.networkEnabledInput.addEventListener('change', () => {
      this.#stream.networkEnabled = this.networkEnabledInput.checked
      this.#scheduleQuality()
    })
    networkRow.append(this.networkEnabledInput, document.createTextNode('网络模拟'), this.bandwidthInput)
    this.bandwidthInput = document.createElement('select')
    this.bandwidthInput.className = 'brs-select'
    for (const value of [64, 128, 256, 500, 1024, 2048]) {
      const option = document.createElement('option')
      option.value = String(value * 1024)
      option.textContent = `${value} KB/s`
      this.bandwidthInput.append(option)
    }
    this.bandwidthInput.value = String(this.#stream.bandwidthBytesPerSecond)
    this.bandwidthInput.addEventListener('change', () => {
      this.#stream.bandwidthBytesPerSecond = Number(this.bandwidthInput.value)
      this.#scheduleQuality()
    })
    this.latencyInput = this.#slider(0, 300, 10, this.#stream.latencyMillis, '延迟', value => {
      this.#stream.latencyMillis = value
      this.#scheduleQuality()
    })
    this.metrics = document.createElement('div')
    this.metrics.className = 'brs-note'
    this.serverInfo = document.createElement('div')
    this.serverInfo.className = 'brs-note'
    this.browserInfo = document.createElement('div')
    this.browserInfo.className = 'brs-note'
    this.infoPanel = document.createElement('div')
    this.infoPanel.className = 'brs-info'
    this.infoPanel.append(this.serverInfo, this.browserInfo)
    this.panel.append(
      panelTitle,
      networkRow,
      this.qualityInput.row,
      this.intervalInput.row,
      this.latencyInput.row,
      this.metrics,
      this.infoPanel)

    const stage = document.createElement('div')
    stage.className = 'brs-stage'
    const canvas = document.createElement('canvas')
    canvas.className = 'brs-canvas'
    canvas.width = 1280
    canvas.height = 720
    canvas.tabIndex = 0
    canvas.addEventListener('pointerdown', event => this.#pointerDown(event))
    canvas.addEventListener('pointermove', event => this.#pointerMove(event))
    canvas.addEventListener('pointerup', event => this.#pointerUp(event))
    canvas.addEventListener('pointercancel', event => this.#pointerCancel(event))
    canvas.addEventListener('wheel', event => this.#wheel(event), {passive: false})
    canvas.addEventListener('keydown', event => this.#key(event))
    canvas.addEventListener('compositionend', event => {
      if (event.data) this.#send({type: 'text', text: event.data})
    })
    canvas.addEventListener('contextmenu', event => event.preventDefault())
    this.hud = document.createElement('div')
    this.hud.className = 'brs-hud'
    this.overlay = document.createElement('div')
    this.overlay.className = 'brs-overlay'
    this.overlay.textContent = '连接中…'
    stage.append(canvas, this.hud, this.overlay)
    this.#canvas = canvas

    windowEl.append(tabbar, toolbar, this.panel, stage)
    root.append(windowEl)
    this.append(root)
    this.#renderTabs()
    this.#renderMetrics()
    this.#renderHud()
    this.#renderInfo()
  }

  #togglePanel() {
    this.panel.hidden = !this.panel.hidden
    this.panelToggle.classList.toggle('is-on', !this.panel.hidden)
  }

  #iconButton(name, title, handler) {
    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'brs-iconbtn'
    button.title = title
    button.setAttribute('aria-label', title)
    button.innerHTML = ICONS[name]
    button.addEventListener('click', handler)
    return button
  }

  #stylesheet() {
    const style = document.createElement('style')
    style.textContent = STYLES
    return style
  }

  #observeViewport() {
    if (!this.#canvas || this.#resizeObserver) return
    this.#resizeObserver = new ResizeObserver(() => this.#syncViewport())
    this.#resizeObserver.observe(this.#canvas.parentElement)
  }

  #syncViewport() {
    if (!this.#canvas) return
    const frame = this.#canvas.parentElement.getBoundingClientRect()
    if (frame.width <= 0 || frame.height <= 0) return
    this.#applyViewport(this.#displayViewport.width, this.#displayViewport.height)
    const width = Math.max(320, Math.round(frame.width))
    const height = Math.max(240, Math.round(frame.height))
    if (this.#requestedWidth === width && this.#requestedHeight === height) return
    this.#requestedWidth = width
    this.#requestedHeight = height
    this.#send({type: 'viewport.resize', width, height})
  }

  #applyViewport(width, height) {
    if (!this.#canvas || !width || !height) return
    if (this.#canvas.width !== width || this.#canvas.height !== height) {
      this.#canvas.width = width
      this.#canvas.height = height
      this.#renderMetrics()
    }
    const frame = this.#canvas.parentElement.getBoundingClientRect()
    if (frame.width <= 0 || frame.height <= 0) return
    const scale = Math.min(frame.width / width, frame.height / height)
    this.#canvas.style.width = `${Math.max(1, Math.round(width * scale))}px`
    this.#canvas.style.height = `${Math.max(1, Math.round(height * scale))}px`
    this.#renderHud()
  }

  #slider(min, max, step, value, label, onChange) {
    const row = document.createElement('label')
    row.className = 'brs-row'
    const text = document.createElement('span')
    text.textContent = label
    const input = document.createElement('input')
    input.type = 'range'
    input.min = String(min)
    input.max = String(max)
    input.step = String(step)
    input.value = String(value)
    const readout = document.createElement('span')
    readout.textContent = String(value)
    input.addEventListener('input', () => {
      const next = Number(input.value)
      readout.textContent = String(next)
      onChange(next)
    })
    row.append(text, input, readout)
    return {row, input, readout}
  }

  #scheduleQuality() {
    clearTimeout(this.#qualityTimer)
    this.#qualityTimer = setTimeout(() => {
      this.#send({
        type: 'stream.configure',
        quality: this.#stream.quality,
        intervalMillis: this.#stream.intervalMillis,
        networkEnabled: this.#stream.networkEnabled,
        bandwidthBytesPerSecond: this.#stream.bandwidthBytesPerSecond,
        latencyMillis: this.#stream.latencyMillis,
      })
    }, 120)
    this.#renderMetrics()
  }

  #connect() {
    const id = this.#sessionId
    if (!this.#canvas) return
    const generation = ++this.#socketGeneration
    this.#socket?.close()
    this.#socket = null
    this.#pending = null
    clearTimeout(this.#reconnectTimer)
    clearInterval(this.#livenessTimer)
    if (!id) return
    this.#latestFrameId = -1
    this.#chunks.clear()
    this.#setEnabled(false)
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
    const socket = new WebSocket(`${scheme}://${location.host}${this.#streamPath}/${id}`)
    socket.binaryType = 'arraybuffer'
    let opened = false
    let gotMessage = false
    socket.onopen = () => {
      if (generation !== this.#socketGeneration) return
      opened = true
      this.#deadSession = false
      this.#reconnectDelay = 0
      this.#lastMessageAt = Date.now()
      clearTimeout(this.#reconnectTimer)
      this.#setEnabled(true)
      this.#requestedWidth = 0
      this.#requestedHeight = 0
      this.#syncViewport()
      this.#armLiveness(generation)
      this.#updateOverlay()
      this.#renderHud()
      this.#renderInfo()
    }
    socket.onclose = () => {
      if (generation !== this.#socketGeneration) return
      if (!gotMessage) {
        this.#failedOpens += 1
        if (this.#failedOpens >= 3) this.#deadSession = true
      }
      this.#setEnabled(false)
      this.#updateOverlay()
      if (!this.#deadSession) this.#scheduleReconnect(generation)
      this.#renderHud()
      this.#renderInfo()
    }
    socket.onmessage = event => {
      if (generation !== this.#socketGeneration) return
      gotMessage = true
      this.#failedOpens = 0
      this.#lastMessageAt = Date.now()
      if (typeof event.data === 'string') {
        const message = JSON.parse(event.data)
        if (message.type === 'telemetry') this.#applyTelemetry(message)
        else if (message.type === 'cursor') this.#applyCursor(message.value)
        else if (message.type === 'clipboard') this.#copyToLocal(message.text)
        else this.#applyState(message)
      } else this.#renderChunk(event.data)
    }
    this.#socket = socket
    this.#stats.windowStart = performance.now()
  }

  #updateOverlay() {
    if (!this.overlay) return
    if (this.#everDrew && !this.#deadSession) {
      this.overlay.hidden = true
      return
    }
    this.overlay.hidden = false
    if (!this.#sessionId) {
      this.overlay.textContent = '点「启动浏览器」开始'
    } else if (this.#deadSession) {
      this.overlay.textContent = '会话已失效，请重新点击「启动浏览器」'
    } else if (this.#socket?.readyState === WebSocket.OPEN) {
      this.overlay.textContent = '等待画面…'
    } else {
      this.overlay.textContent = '连接中…'
    }
  }

  #setEnabled(enabled) {
    for (const control of [
      this.address,
      this.openButton,
      this.newTabButton,
      this.backButton,
      this.forwardButton,
      this.reloadButton,
    ]) {
      if (control) control.disabled = !enabled
    }
  }

  #scheduleReconnect(generation) {
    if (!this.#sessionId) return
    clearTimeout(this.#reconnectTimer)
    this.#reconnectDelay = Math.min(5000, Math.max(400, this.#reconnectDelay * 2))
    this.#reconnectTimer = setTimeout(() => {
      if (generation !== this.#socketGeneration) return
      this.#connect()
    }, this.#reconnectDelay)
  }

  #armLiveness(generation) {
    clearInterval(this.#livenessTimer)
    this.#livenessTimer = setInterval(() => {
      if (generation !== this.#socketGeneration) return
      if (this.#socket?.readyState !== WebSocket.OPEN) return
      if (!this.#lastMessageAt) return
      if (Date.now() - this.#lastMessageAt < 8000) return
      clearInterval(this.#livenessTimer)
      this.#dropAndReconnect()
    }, 3000)
  }

  #dropAndReconnect() {
    const stale = this.#socket
    this.#socket = null
    this.#socketGeneration += 1
    const generation = this.#socketGeneration
    try {
      stale?.close()
    } catch (ignored) {
    }
    this.#scheduleReconnect(generation)
  }

  #applyState(state) {
    const previousActive = this.#activeTabId
    this.#tabs = state.tabs || []
    this.#activeTabId = state.activeTabId
    this.#viewport = {width: state.viewportWidth || 0, height: state.viewportHeight || 0}
    this.#applyViewport(this.#displayViewport.width, this.#displayViewport.height)
    this.#stream = {
      quality: state.quality,
      intervalMillis: state.intervalMillis,
      networkEnabled: state.networkEnabled,
      bandwidthBytesPerSecond: state.bandwidthBytesPerSecond,
      latencyMillis: state.latencyMillis,
    }
    this.qualityInput.input.value = String(state.quality)
    this.qualityInput.readout.textContent = String(state.quality)
    this.intervalInput.input.value = String(state.intervalMillis)
    this.intervalInput.readout.textContent = String(state.intervalMillis)
    this.networkEnabledInput.checked = state.networkEnabled
    this.bandwidthInput.value = String(state.bandwidthBytesPerSecond)
    this.latencyInput.input.value = String(state.latencyMillis)
    this.latencyInput.readout.textContent = String(state.latencyMillis)
    const active = this.#tabs.find(tab => tab.id === this.#activeTabId)
    const activeChanged = this.#activeTabId !== previousActive
    if (active && (activeChanged || document.activeElement !== this.address)) this.address.value = active.url
    this.#renderTabs()
    this.#renderMetrics()
    this.#renderHud()
    this.#renderInfo()
    this.#updateOverlay()
    if (activeChanged && this.#focusAddressOnTabChange) {
      this.#focusAddressOnTabChange = false
      requestAnimationFrame(() => {
        this.address.focus({preventScroll: true})
        this.address.select()
      })
    }
  }

  #applyTelemetry(message) {
    this.#telemetry = message
    this.#stats.serverLatencyMs = Math.max(0, Date.now() - message.timestamp)
    this.#renderHud()
    this.#renderInfo()
  }

  #renderHud() {
    const telemetry = this.#telemetry
    const connection = telemetry?.connection
    const stream = telemetry?.stream
    const socketState = this.#socket ? ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'][this.#socket.readyState] : 'CLOSED'
    const serverFps = stream ? stream.sendFps.toFixed(1) : '0.0'
    const actualRate = stream ? `${this.#bytes(stream.bytesPerSecond)}/s` : '--'
    const cap = stream?.networkEnabled ? `${this.#bytes(stream.bandwidthBytesPerSecond)}/s` : '不限速'
    const latency = this.#stats.serverLatencyMs >= 0 ? `${this.#stats.serverLatencyMs} ms` : '--'
    const average = this.#stats.frameBytes ? `${(this.#stats.frameBytes / 1024).toFixed(1)} KB` : '--'
    this.hud.textContent = `画布 ${this.#canvas.width}×${this.#canvas.height} · 远端 ${this.#viewport.width}×${this.#viewport.height} · 当前 ${this.#stats.lastFrameBytes} B · 均 ${average} · 收 ${this.#stats.fps.toFixed(1)} fps · 绘 ${this.#stats.renderFps.toFixed(1)} fps · 丢 ${this.#stats.droppedFrames} · 服务端 ${serverFps} fps · 实际 ${actualRate} / 限 ${cap} · 遥测 ${latency} · JPEG ${this.#stream.quality} · 间隔 ${this.#stream.intervalMillis} ms · WS ${socketState}`
  }

  #renderInfo() {
    const telemetry = this.#telemetry
    if (!telemetry) {
      this.serverInfo.textContent = '服务器：等待遥测数据'
      this.browserInfo.textContent = '浏览器：等待遥测数据'
      return
    }
    const server = telemetry.server
    const connection = telemetry.connection
    const browser = telemetry.browser
    const input = browser.input || {}
    this.serverInfo.textContent = `服务器 | ${server.hostName} | ${server.osName} ${server.osVersion} ${server.osArch} | Java ${server.javaVersion} | JVM ${server.jvmName} | PID ${server.pid} | CPU ${this.#percent(server.processCpuPercent)} 进程 / ${this.#percent(server.systemCpuPercent)} 系统 | Heap ${this.#bytes(server.heapUsedBytes)} / ${this.#bytes(server.heapMaxBytes)} | RAM ${this.#bytes(server.totalMemoryBytes - server.freeMemoryBytes)} / ${this.#bytes(server.totalMemoryBytes)} | Uptime ${this.#duration(server.uptimeMillis)} | 本地 ${connection.localIp}:${connection.localPort} | 客户端 ${connection.remoteIp}:${connection.remotePort}`
    const stream = telemetry.stream
    const network = stream.networkEnabled
      ? `限速 ${this.#bytes(stream.bandwidthBytesPerSecond)} / 延迟 ${stream.latencyMillis} ms / 实际 ${this.#bytes(stream.bytesPerSecond)}/s`
      : '不限速'
    let runtime = '--'
    if (browser.headless !== null && browser.headless !== undefined) {
      runtime = browser.headless ? 'headless' : 'headed'
    }
    const failure = browser.lastFailure
      ? ` | 最近错误 ${browser.lastFailure.operation}: ${browser.lastFailure.message}`
      : ''
    this.browserInfo.textContent = `浏览器 | ${browser.engine} ${browser.browserVersion} | CDP ${browser.protocolVersion} | 运行 ${runtime} | 环境 ${browser.environmentMode || 'NATIVE'} | 配置 ${browser.profileId || '--'} | Context ${browser.contextOpen ? 'OPEN' : 'CLOSED'} | 标签页 ${browser.tabCount} | 当前 ${browser.activeTitle || '无标题'} | ${browser.activeUrl || 'about:blank'} | ${network} | 服务端帧 ${stream.framesSent} | 流量 ${this.#bytes(stream.bytesSent)} | 当前帧 ${this.#bytes(stream.lastFrameBytes)} | 输入 ${input.lastAppliedSequence ?? '--'}/${input.lastReceivedSequence ?? '--'} | 队列 ${input.pendingOperations ?? '--'} | 拒绝 ${input.rejectedOperations ?? '--'}${failure}`
  }

  #bytes(value) {
    if (value == null || !Number.isFinite(value) || value < 0) return '--'
    if (value < 1024) return `${value} B`
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
    return `${(value / 1024 / 1024).toFixed(1)} MB`
  }

  #percent(value) {
    return value == null || !Number.isFinite(value) || value < 0 ? '--' : `${value.toFixed(1)}%`
  }

  #duration(value) {
    if (value == null || value < 0) return '--'
    const seconds = Math.floor(value / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    return `${hours}h ${minutes % 60}m ${seconds % 60}s`
  }

  #renderTabs() {
    const existing = new Map()
    for (const button of this.tabStrip.querySelectorAll('.brs-tab')) {
      existing.set(button.dataset.tabId, button)
    }
    const ordered = []
    for (const tab of this.#tabs) {
      let button = existing.get(tab.id)
      if (!button) {
        button = this.#createTabButton(tab.id)
        existing.set(tab.id, button)
      }
      button.classList.toggle('is-active', tab.id === this.#activeTabId)
      button.title = tab.error || tab.url || ''
      const favicon = button.querySelector('.brs-favicon')
      favicon.className = `brs-favicon${tab.loading ? ' is-loading' : tab.error ? ' is-error' : ''}`
      button.querySelector('.brs-title').textContent = tab.title || tab.url || '新标签页'
      button.querySelector('.brs-close').hidden = this.#tabs.length === 1
      ordered.push(button)
    }
    this.tabStrip.replaceChildren(...ordered, this.newTabButton)
    const active = this.#tabs.find(tab => tab.id === this.#activeTabId)
    const connected = this.#socket?.readyState === WebSocket.OPEN
    this.backButton.disabled = !connected || !active?.canGoBack
    this.forwardButton.disabled = !connected || !active?.canGoForward
    this.reloadButton.disabled = !connected
  }

  #createTabButton(tabId) {
    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'brs-tab is-new'
    button.addEventListener('animationend', () => button.classList.remove('is-new'), {once: true})
    button.dataset.tabId = tabId
    const favicon = document.createElement('span')
    favicon.className = 'brs-favicon'
    const label = document.createElement('span')
    label.className = 'brs-title'
    const close = document.createElement('span')
    close.className = 'brs-close'
    close.textContent = '×'
    close.addEventListener('click', event => {
      event.stopPropagation()
      this.#send({type: 'tab.close', tabId})
    })
    button.append(favicon, label, close)
    button.addEventListener('click', () => this.#send({type: 'tab.activate', tabId}))
    return button
  }

  #renderMetrics() {
    const width = this.#canvas.width
    const height = this.#canvas.height
    const fps = this.#stream.intervalMillis > 0 ? (1000 / this.#stream.intervalMillis).toFixed(1) : '0'
    const measured = this.#stats.fps ? this.#stats.fps.toFixed(1) : '0.0'
    const bytes = this.#stats.frameBytes ? (this.#stats.frameBytes / 1024).toFixed(1) : '0.0'
    const cap = this.#stream.networkEnabled ? `${(this.#stream.bandwidthBytesPerSecond / 1024).toFixed(0)} KB/s` : '不限速'
    this.metrics.textContent = `${width}×${height} · 画质 ${this.#stream.quality} · 设定 ${fps} fps · 收 ${measured} fps · 绘 ${this.#stats.renderFps.toFixed(1)} fps · 平均 ${bytes} KB/帧 · 当前 ${this.#stats.lastFrameBytes} B · 丢 ${this.#stats.droppedFrames} · 限 ${cap} · 延迟 ${this.#stream.latencyMillis} ms`
  }

  #renderChunk(buffer) {
    const view = new DataView(buffer)
    if (view.byteLength < 20 || view.getUint32(0) !== 0x42535452) return
    const frameId = view.getUint32(4)
    const chunkIndex = view.getUint16(8)
    const chunkCount = view.getUint16(10)
    if (chunkCount === 0 || chunkIndex >= chunkCount) return
    for (const pendingFrameId of this.#chunks.keys()) {
      if (pendingFrameId + 2 < frameId) this.#chunks.delete(pendingFrameId)
    }
    let frame = this.#chunks.get(frameId)
    if (!frame || frame.count !== chunkCount) {
      frame = {count: chunkCount, chunks: new Array(chunkCount), received: 0, bytes: 0}
      this.#chunks.set(frameId, frame)
    }
    if (!frame.chunks[chunkIndex]) {
      const chunk = new Uint8Array(buffer.slice(20))
      frame.chunks[chunkIndex] = chunk
      frame.received += 1
      frame.bytes += chunk.byteLength
    }
    if (frame.received !== frame.count) return
    const merged = new Uint8Array(frame.bytes)
    let offset = 0
    for (const chunk of frame.chunks) {
      merged.set(chunk, offset)
      offset += chunk.byteLength
    }
    this.#chunks.delete(frameId)
    this.#renderFrame(merged.buffer, frameId, view.getUint32(12), view.getUint32(16))
  }

  #renderFrame(buffer, frameId, width, height) {
    if (frameId < this.#latestFrameId) return
    this.#latestFrameId = frameId
    this.#stats.frames += 1
    this.#stats.bytes += buffer.byteLength
    this.#stats.lastFrameBytes = buffer.byteLength
    if (this.#pending) this.#stats.droppedFrames += 1
    const now = performance.now()
    const elapsed = now - this.#stats.windowStart
    if (elapsed >= 1000) {
      this.#stats.fps = this.#stats.frames * 1000 / elapsed
      this.#stats.renderFps = this.#stats.renderFrames * 1000 / elapsed
      this.#stats.frameBytes = this.#stats.bytes / this.#stats.frames
      this.#stats.frames = 0
      this.#stats.bytes = 0
      this.#stats.renderFrames = 0
      this.#stats.windowStart = now
      this.#renderMetrics()
    }
    this.#pending = {frameId, buffer, width, height, generation: this.#socketGeneration}
    this.#scheduleDraw()
  }

  #scheduleDraw() {
    if (this.#drawing || !this.#pending) return
    this.#drawing = true
    requestAnimationFrame(async () => {
      const pending = this.#pending
      this.#pending = null
      try {
        if (!pending) return
        const bitmap = await createImageBitmap(new Blob([pending.buffer], {type: 'image/jpeg'})).catch(() => null)
        if (!bitmap) return
        try {
          if (pending.generation !== this.#socketGeneration) return
          this.#displayViewport = {width: pending.width, height: pending.height}
          this.#applyViewport(pending.width, pending.height)
          this.#canvas.getContext('2d').drawImage(bitmap, 0, 0, this.#canvas.width, this.#canvas.height)
          this.#canvas.classList.add('is-ready')
          this.#everDrew = true
          this.#updateOverlay()
          this.#stats.renderFrames += 1
        } finally {
          bitmap.close()
        }
      } finally {
        this.#drawing = false
        if (this.#pending) this.#scheduleDraw()
        this.#renderHud()
      }
    })
  }

  #navigate() {
    const url = this.address.value.trim()
    if (!url || !this.#activeTabId) return
    let target = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(url) ? url : `https://${url}`
    if (/^https?:\/\//i.test(target)) {
      try {
        target = new URL(target).href
      } catch {
      }
    }
    this.address.value = target
    this.#send({type: 'tab.navigate', url: target})
  }

  #point(event, box = this.#canvas.getBoundingClientRect()) {
    return {
      x: (event.clientX - box.left) * this.#canvas.width / box.width,
      y: (event.clientY - box.top) * this.#canvas.height / box.height,
    }
  }

  #pointerDown(event) {
    this.#canvas.focus({preventScroll: true})
    const point = this.#point(event)
    if (event.button === 2) {
      this.#send(this.#pointerMessage('context', event, point))
      return
    }
    this.#send(this.#pointerMessage('down', event, point))
    try {
      this.#canvas.setPointerCapture(event.pointerId)
    } catch {
    }
  }

  #pointerMove(event) {
    const coalesced = event.getCoalescedEvents ? event.getCoalescedEvents() : []
    this.#pendingMove = coalesced.length ? coalesced[coalesced.length - 1] : event
    if (this.#moveFrame) return
    this.#moveFrame = requestAnimationFrame(() => {
      this.#moveFrame = 0
      const sample = this.#pendingMove
      this.#pendingMove = null
      if (sample) this.#send(this.#pointerMessage('move', sample, this.#point(sample)))
    })
  }

  #pointerUp(event) {
    if (event.button === 2) return
    this.#send(this.#pointerMessage('up', event, this.#point(event)))
    if (this.#canvas.hasPointerCapture(event.pointerId)) this.#canvas.releasePointerCapture(event.pointerId)
  }

  #pointerCancel(event) {
    this.#send(this.#pointerMessage('cancel', event, this.#point(event)))
    if (this.#canvas.hasPointerCapture(event.pointerId)) this.#canvas.releasePointerCapture(event.pointerId)
  }

  #pointerMessage(type, event, point) {
    const moving = type === 'move'
    return {
      type,
      ...point,
      button: moving ? -1 : event.button,
      buttons: event.buttons,
      clickCount: moving ? 1 : Math.max(1, Math.min(3, event.detail || 1)),
      seq: this.#pointerSequence++,
      ...this.#modifiers(event),
    }
  }

  #modifiers(event) {
    return {
      alt: event.altKey,
      ctrl: event.ctrlKey,
      meta: event.metaKey,
      shift: event.shiftKey,
    }
  }

  #wheel(event) {
    event.preventDefault()
    const scale = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? this.#canvas.height : 1
    this.#send({
      type: 'wheel',
      ...this.#point(event),
      deltaX: event.deltaX * scale,
      deltaY: event.deltaY * scale,
    })
  }

  #key(event) {
    event.preventDefault()
    if (event.isComposing) return
    const command = event.ctrlKey || event.metaKey
    const key = event.key.toLowerCase()
    if (command && key === 'v') {
      this.#pasteFromLocal()
      return
    }
    this.#send({
      type: 'key',
      key: event.key,
      code: event.code,
      keyCode: event.keyCode,
      location: event.location,
      repeat: event.repeat,
      ...this.#modifiers(event),
    })
    if (command && ['c', 'x'].includes(key)) {
      setTimeout(() => this.#send({type: 'clipboard.read'}), 150)
    }
  }

  async #pasteFromLocal() {
    try {
      const text = await navigator.clipboard.readText()
      if (text) this.#send({type: 'text', text})
    } catch (ignored) {
    }
  }

  #applyCursor(value) {
    if (!this.#canvas) return
    const raw = typeof value === 'string' ? value.trim() : ''
    const fallback = raw.includes('url(') ? (raw.split(',').pop() || 'default').trim() : raw
    this.#canvas.style.cursor = /^[a-z-]+$/.test(fallback) ? fallback : 'default'
  }

  async #copyToLocal(text) {
    if (!text) return
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch (ignored) {
    }
    const area = document.createElement('textarea')
    area.value = text
    area.style.cssText = 'position:fixed;opacity:0;pointer-events:none'
    document.body.append(area)
    area.select()
    try {
      document.execCommand('copy')
    } finally {
      area.remove()
    }
  }

  #send(value) {
    if (this.#socket?.readyState === WebSocket.OPEN) this.#socket.send(JSON.stringify(value))
  }
}

customElements.define('browser-stream', BrowserStreamElement)
