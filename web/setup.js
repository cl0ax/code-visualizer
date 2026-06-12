// Setup screen: paste/load code, auto-analyze (debounced), collect inputs, run trace, hand off to player.
(() => {
  const codeEl = document.getElementById('code');
  const fileEl = document.getElementById('file');
  const paramsEl = document.getElementById('params');
  const methodBox = document.getElementById('method-box');
  const methodEl = document.getElementById('method');
  const vizBtn = document.getElementById('visualize');
  const errorsEl = document.getElementById('setup-errors');

  let methods = [];
  let debounce = null;
  let analyzeSeq = 0;
  let lastParamsJson = null;

  window.esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');

  codeEl.addEventListener('input', () => {
    clearTimeout(debounce);
    debounce = setTimeout(analyze, 500);
  });

  fileEl.addEventListener('change', async () => {
    if (fileEl.files.length) {
      codeEl.value = await fileEl.files[0].text();
      analyze();
    }
    fileEl.value = '';
  });

  methodEl.addEventListener('change', renderParams);
  vizBtn.addEventListener('click', runTrace);

  async function analyze() {
    errorsEl.innerHTML = '';
    const code = codeEl.value;
    if (!code.trim()) { methods = []; renderParams(); return; }
    const seq = ++analyzeSeq;
    const res = await fetch('/api/analyze', { method: 'POST', body: JSON.stringify({ code }) })
      .then(r => r.json());
    if (seq !== analyzeSeq) return;
    if (!res.ok) { methods = []; showErrors([{ message: res.error }]); renderParams(); return; }
    const prevName = current()?.name;
    const prevParamsJson = lastParamsJson;
    methods = res.methods;
    methodBox.classList.toggle('hidden', methods.length < 2);
    methodEl.innerHTML = methods.map((m, i) =>
      `<option value="${i}">${esc(m.name)}(${m.params.map(p => esc(p.type)).join(', ')})</option>`).join('');
    if (prevName) {
      const newIdx = methods.findIndex(m => m.name === prevName);
      if (newIdx !== -1) methodEl.value = String(newIdx);
    }
    const newMethod = current();
    if (newMethod && JSON.stringify(newMethod.params) === prevParamsJson) {
      // params unchanged — keep user-typed values
    } else {
      renderParams();
    }
  }

  function current() { return methods[Number(methodEl.value) || 0]; }

  function renderParams() {
    const m = current();
    paramsEl.innerHTML = '';
    if (!m) { vizBtn.disabled = true; lastParamsJson = null; return; }
    lastParamsJson = JSON.stringify(m.params);
    m.params.forEach((p, i) => {
      const div = document.createElement('div');
      div.className = 'param';
      div.innerHTML = `<div class="label">${esc(p.type)} ${esc(p.name)}</div>` +
        `<input data-i="${i}" placeholder="${esc(hint(p.type))}">` +
        `<div class="field-error" id="perr-${i}"></div>`;
      paramsEl.appendChild(div);
    });
    paramsEl.querySelectorAll('input').forEach(inp => inp.addEventListener('input', update));
    update();
  }

  function hint(type) {
    if (type === 'String') return '"text"';
    if (type.endsWith('[]') || type.startsWith('List') || type.startsWith('Set')) return '[1,2,3]';
    if (type.startsWith('Map')) return '{"a":1}';
    if (type === 'char' || type === 'Character') return "'c'";
    if (type === 'boolean' || type === 'Boolean') return 'true';
    return '5';
  }

  function update() {
    const inputs = [...paramsEl.querySelectorAll('input')];
    vizBtn.disabled = !current() || inputs.some(i => !i.value.trim());
  }

  function showErrors(list) {
    errorsEl.innerHTML = list.map(e =>
      `<div class="error">${e.line ? 'line ' + e.line + ': ' : ''}${esc(e.message)}</div>`).join('');
  }

  async function runTrace() {
    errorsEl.innerHTML = '';
    document.querySelectorAll('.field-error').forEach(e => e.textContent = '');
    vizBtn.disabled = true;
    vizBtn.textContent = 'Running…';
    const inputs = [...paramsEl.querySelectorAll('input')].map(i => i.value);
    let res;
    try {
      res = await fetch('/api/trace', {
        method: 'POST',
        body: JSON.stringify({ code: codeEl.value, inputs, method: current().name })
      }).then(r => r.json());
    } finally {
      vizBtn.disabled = false;
      vizBtn.textContent = 'Visualize ▶';
    }
    if (!res.ok) {
      if (res.stage === 'compile') showErrors(res.errors);
      else if (res.stage === 'input' && res.param != null) {
        const f = document.getElementById('perr-' + res.param);
        if (f) f.textContent = res.error; else showErrors([{ message: res.error }]);
      } else showErrors([{ message: res.error }]);
      return;
    }
    VIZ.showPlayer(res.trace);
  }

  window.VIZ_SETUP = {
    show() {
      document.getElementById('player').classList.add('hidden');
      document.getElementById('setup').classList.remove('hidden');
    }
  };
})();
