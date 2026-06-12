// State renderers: read only the trace contract; never re-interpret logic.
// Renderer choice comes solely from each value's `kind` metadata (honesty rule).
(() => {
  function renderState(root, step, trace, last) {
    root.innerHTML = '';
    const locals = step.locals;
    const pointers = trace.pointers || {};
    const changed = new Set(step.changed || []);
    const byTarget = {};
    for (const [ptr, target] of Object.entries(pointers)) {
      const pv = locals[ptr];
      if (pv && (pv.kind === 'int' || pv.kind === 'long') && locals[target])
        (byTarget[target] ??= []).push(ptr);
    }
    for (const [name, val] of Object.entries(locals)) {
      if (pointers[name] && locals[pointers[name]]) continue;  // drawn under its target instead
      root.appendChild(card(name, val, byTarget[name] || [], locals, changed));
    }
    if (last && trace.result) root.appendChild(resultCard(trace.result));
  }

  function card(name, val, ptrs, locals, changed) {
    const div = document.createElement('div');
    div.className = 'state-card' + (changed.has(name) ? ' flash' : '');
    const label = `<div class="label">${esc(name)}${val.truncated ? ' (truncated)' : ''}</div>`;
    if (val.kind === 'string' || val.kind === 'array' || val.kind === 'list' || val.kind === 'set')
      div.innerHTML = label + cells(val, ptrs, locals);
    else if (val.kind === 'map')
      div.innerHTML = label + mapRows(val);
    else
      div.innerHTML = label + `<div class="value">${esc(short(val))}</div>`;
    return div;
  }

  function elems(val) {
    if (val.kind === 'string') return [...String(val.v ?? '')];
    return (val.elements || []).map(short);
  }

  function cells(val, ptrs, locals) {
    const es = elems(val);
    const len = Number(val.len ?? es.length);
    let h = '<div class="cells">';
    for (let i = 0; i < es.length; i++) {
      const cv = es[i] === ' ' ? '&nbsp;' : esc(es[i]);
      h += `<div class="cell"><div class="cv">${cv}</div><div class="ci">${i}</div>` +
           `<div class="cp">${marks(ptrs, locals, i, len)}</div></div>`;
    }
    h += '</div>';
    const out = ptrs.filter(p => oob(locals[p], len));
    if (out.length)
      h += `<div class="oob">${out.map(p => `${esc(p)}=${esc(locals[p].v)} out of range`).join(', ')}</div>`;
    return h;
  }

  function marks(ptrs, locals, i, len) {
    return ptrs.filter(p => clampi(locals[p], len) === i)
      .map(p => `<span class="ptr${oob(locals[p], len) ? ' red' : ''}">${esc(p)}</span>`).join('');
  }

  function clampi(v, len) { return Math.max(0, Math.min(len - 1, Number(v.v))); }
  function oob(v, len) { const n = Number(v.v); return n < 0 || n > len - 1; }

  function mapRows(val) {
    const rows = (val.entries || []).map(([k, v]) =>
      `<div class="mrow"><span class="mk">${esc(short(k))}</span><span class="mv">${esc(short(v))}</span></div>`).join('');
    return `<div class="map">${rows || '<div class="empty">empty</div>'}</div>`;
  }

  function short(v) {
    if (!v || v.kind === 'null') return 'null';
    if (v.kind === 'string') return '"' + v.v + '"';
    if (v.kind === 'char') return "'" + v.v + "'";
    if (v.kind === 'array' || v.kind === 'list') return '[' + (v.elements || []).map(short).join(', ') + ']';
    if (v.kind === 'set') return '{' + (v.elements || []).map(short).join(', ') + '}';
    if (v.kind === 'map') return '{' + (v.entries || []).map(([k, x]) => short(k) + ': ' + short(x)).join(', ') + '}';
    return String(v.v);
  }

  function resultCard(result) {
    const div = document.createElement('div');
    div.className = 'state-card result ' + result.kind;
    if (result.kind === 'return')
      div.innerHTML = `<div class="label">RESULT</div><div class="value">${esc(short(result.value))}</div>`;
    else if (result.kind === 'exception')
      div.innerHTML = `<div class="label">EXCEPTION</div><div class="value">${esc(result.type)}` +
        `${result.message ? ': ' + esc(result.message) : ''}` +
        `${result.line ? ' (line ' + result.line + ')' : ''}</div>`;
    else
      div.innerHTML = `<div class="label">STOPPED</div><div class="value">${esc(result.message || result.kind)}</div>`;
    return div;
  }

  window.renderState = renderState;
})();
