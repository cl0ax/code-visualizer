// Player screen: code panel, playback controls, scrubber, Semantic/Lines modes, keyboard.
(() => {
  let trace = null, mode = 'semantic', idx = 0, timer = null, speed = 1;

  const el = id => document.getElementById(id);
  const seq = () => mode === 'semantic' ? trace.groups : trace.steps;

  function showPlayer(t) {
    trace = t; idx = 0;
    el('setup').classList.add('hidden');
    el('player').classList.remove('hidden');
    el('p-title').textContent = t.entry.name;
    buildCode(t.source);
    el('notice').textContent = t.notice || '';
    el('notice').classList.toggle('hidden', !t.notice);
    el('console').textContent = t.console || '(empty)';
    el('scrub').max = seq().length - 1;
    stop();
    render();
  }

  function buildCode(src) {
    el('code-view').innerHTML = src.split('\n').map((l, i) =>
      `<span class="cl" id="cl-${i + 1}"><span class="ln">${i + 1}</span>${esc(l)}​</span>`).join('\n');
  }

  function currentStep() {
    return mode === 'semantic' ? trace.steps[seq()[idx].to] : trace.steps[idx];
  }

  function caption() {
    if (mode === 'semantic') { const g = seq()[idx]; return g.label + ' — ' + g.caption; }
    const s = trace.steps[idx];
    return 'line ' + s.line + (s.changed.length ? ' — changed: ' + s.changed.join(', ') : '');
  }

  function render() {
    const step = currentStep();
    el('p-step').textContent = 'Step ' + (idx + 1) + ' / ' + seq().length;
    el('scrub').value = idx;
    el('caption').textContent = caption();
    document.querySelectorAll('.cl.on').forEach(e => e.classList.remove('on'));
    const lineEl = el('cl-' + step.line);
    if (lineEl) { lineEl.classList.add('on'); lineEl.scrollIntoView({ block: 'nearest' }); }
    renderState(el('state'), step, trace, idx === seq().length - 1);
  }

  function go(i) { idx = Math.max(0, Math.min(seq().length - 1, i)); render(); }
  function stop() { clearInterval(timer); timer = null; el('c-play').textContent = '▷ Play'; }
  function play() {
    if (timer) { stop(); return; }
    el('c-play').textContent = '⏸ Pause';
    timer = setInterval(() => { idx >= seq().length - 1 ? stop() : go(idx + 1); }, 900 / speed);
  }

  el('c-first').onclick = () => go(0);
  el('c-prev').onclick = () => go(idx - 1);
  el('c-next').onclick = () => go(idx + 1);
  el('c-last').onclick = () => go(seq().length - 1);
  el('c-play').onclick = play;
  el('scrub').oninput = e => go(Number(e.target.value));
  el('edit').onclick = () => { stop(); VIZ_SETUP.show(); };

  el('mode').querySelectorAll('button').forEach(b => b.onclick = () => {
    mode = b.dataset.mode;
    el('mode').querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b));
    idx = 0;
    el('scrub').max = seq().length - 1;
    stop();
    render();
  });

  el('speeds').querySelectorAll('button').forEach(b => b.onclick = () => {
    speed = Number(b.dataset.s);
    el('speeds').querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b));
    if (timer) { stop(); play(); }
  });

  document.addEventListener('keydown', e => {
    if (el('player').classList.contains('hidden')) return;
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
    if (e.key === 'ArrowRight') go(idx + 1);
    else if (e.key === 'ArrowLeft') go(idx - 1);
    else if (e.key === ' ') { e.preventDefault(); play(); }
  });

  window.VIZ = { showPlayer };
})();
