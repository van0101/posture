(() => {
  const $ = (id) => document.getElementById(id);
  const state = {
    total: 60, interval: 5, mode: "mix",
    running: false, startTs: 0, stopAt: 0, beepsTotal: 0, lastShown: -1,
    audio: null, url: null, ctx: null, master: null, nodes: [], ctxOffset: 0,
    wakeLock: null, tick: null, stopTimer: null, stoppingByUs: false,
  };

  // ---------- Sound: the "DRS enabled" double blip ----------
  const BEEP = { f: 2000, len: 0.09, gap: 0.06 };

  // One interval of audio: beep at t=0, then silence. Looped, it beeps every interval.
  function makeClipURL(seconds) {
    const sr = 8000, n = Math.round(seconds * sr);
    const buf = new Uint8Array(44 + n);
    const dv = new DataView(buf.buffer);
    const str = (o, s) => { for (let i = 0; i < s.length; i++) buf[o + i] = s.charCodeAt(i); };
    str(0, "RIFF"); dv.setUint32(4, 36 + n, true); str(8, "WAVE");
    str(12, "fmt "); dv.setUint32(16, 16, true); dv.setUint16(20, 1, true); dv.setUint16(22, 1, true);
    dv.setUint32(24, sr, true); dv.setUint32(28, sr, true); dv.setUint16(32, 1, true); dv.setUint16(34, 8, true);
    str(36, "data"); dv.setUint32(40, n, true);
    buf.fill(128, 44);
    const env = 0.005;
    [0, BEEP.len + BEEP.gap].forEach((t0) => {
      const s0 = Math.round(t0 * sr), s1 = Math.round((t0 + BEEP.len) * sr);
      for (let s = s0; s < s1 && s < n; s++) {
        const t = (s - s0) / sr;
        const a = Math.min(1, t / env, (BEEP.len - t) / env);
        const v = 0.7 * Math.sin(2 * Math.PI * BEEP.f * t) + 0.22 * Math.sin(2 * Math.PI * BEEP.f * 1.5 * t);
        buf[44 + s] = Math.max(0, Math.min(255, Math.round(128 + 127 * a * v)));
      }
    });
    return URL.createObjectURL(new Blob([buf], { type: "audio/wav" }));
  }

  // Web Audio blip, scheduled on the audio clock. Returns the oscillators so they can be cancelled.
  function blips(ctx, dest, when, count = 2, freq = BEEP.f) {
    const made = [];
    for (let i = 0; i < count; i++) {
      const t0 = when + i * (BEEP.len + BEEP.gap);
      const g = ctx.createGain();
      g.gain.setValueAtTime(0, t0);
      g.gain.linearRampToValueAtTime(0.75, t0 + 0.005);
      g.gain.setValueAtTime(0.75, t0 + BEEP.len - 0.005);
      g.gain.linearRampToValueAtTime(0, t0 + BEEP.len);
      g.connect(dest);
      [[freq, 1], [freq * 1.5, 0.3]].forEach(([f, amp]) => {
        const o = ctx.createOscillator(), og = ctx.createGain();
        o.type = "triangle";
        o.frequency.value = f; og.gain.value = amp;
        o.connect(og).connect(g);
        o.start(t0); o.stop(t0 + BEEP.len + 0.01);
        made.push(o);
      });
    }
    return made;
  }

  function newCtx() {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -10; comp.ratio.value = 4;
    comp.connect(ctx.destination);
    return { ctx, master: comp };
  }

  let fx = null;
  function playFx(count, freq) {
    try {
      fx = fx || newCtx();
      if (fx.ctx.state !== "running") fx.ctx.resume();
      blips(fx.ctx, fx.master, fx.ctx.currentTime + 0.03, count, freq);
    } catch (e) { /* sound unavailable */ }
  }

  // ---------- UI helpers ----------
  const fmt = (sec) => {
    sec = Math.max(0, Math.ceil(sec));
    const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    const mm = String(m).padStart(2, "0"), ss = String(s).padStart(2, "0");
    return h ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
  };
  const beepsFor = (T, I) => Math.floor(T / I + 1e-9);
  const cueIdle = () => `every ${state.interval} min`;

  function setStatus(msg, err = false) { const el = $("status"); el.textContent = msg; el.classList.toggle("err", err); }

  function renderLeds(done, total) {
    const box = $("leds");
    const count = Math.min(Math.max(total, 1), 20);
    if (box.childElementCount !== count) {
      box.innerHTML = "";
      for (let i = 0; i < count; i++) {
        const d = document.createElement("div");
        const p = i / count;
        d.className = "led " + (p < 0.45 ? "g" : p < 0.8 ? "r" : "b");
        box.appendChild(d);
      }
    }
    const lit = total ? Math.floor((done / total) * count) : 0;
    [...box.children].forEach((d, i) => d.classList.toggle("on", i < lit));
  }

  function idleView() {
    const T = state.total * 60, I = state.interval * 60;
    $("left").textContent = fmt(T);
    $("next").textContent = fmt(Math.min(I, T));
    $("count").textContent = `0 / ${beepsFor(T, I)}`;
    $("cueText").textContent = cueIdle();
    renderLeds(0, beepsFor(T, I));
  }

  function flash(text) {
    const c = $("cue");
    $("cueText").textContent = text;
    c.classList.add("flash");
    clearTimeout(flash.t);
    flash.t = setTimeout(() => { c.classList.remove("flash"); $("cueText").textContent = cueIdle(); }, 1500);
  }

  // ---------- Setup controls ----------
  document.querySelectorAll(".chips").forEach((group) => {
    const key = group.dataset.key;
    const input = group.querySelector("input");
    group.addEventListener("click", (e) => {
      const b = e.target.closest(".chip"); if (!b) return;
      group.querySelectorAll(".chip").forEach((c) => c.setAttribute("aria-pressed", String(c === b)));
      input.value = "";
      state[key] = Number(b.dataset.val);
      idleView();
    });
    input.addEventListener("input", () => {
      const v = Number(input.value);
      if (v > 0) {
        group.querySelectorAll(".chip").forEach((c) => c.setAttribute("aria-pressed", "false"));
        state[key] = v;
        idleView();
      }
    });
  });

  $("test").addEventListener("click", () => { playFx(2); flash("now"); });

  function validate() {
    const T = state.total, I = state.interval;
    if (!(T >= 1 && T <= 480)) return "Session length must be between 1 and 480 minutes.";
    if (!(I >= 1 && I <= 60)) return "Beep interval must be between 1 and 60 minutes.";
    if (I > T) return "Beep interval can't be longer than the session.";
    return "";
  }

  // ---------- Wake lock ----------
  async function requestWake() {
    if (!$("wake").checked || !state.running) return;
    try { state.wakeLock = await navigator.wakeLock.request("screen"); } catch (e) { /* not allowed here */ }
  }
  function releaseWake() { try { state.wakeLock && state.wakeLock.release(); } catch (e) {} state.wakeLock = null; }
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") { requestWake(); update(); }
  });
  $("wake").addEventListener("change", () => ($("wake").checked ? requestWake() : releaseWake()));

  // ---------- Engine 1: over music (Web Audio, all beeps queued on the audio clock) ----------
  const elapsed = () => (Date.now() - state.startTs) / 1000;

  function scheduleRemaining() {
    const { ctx, master } = state;
    if (!ctx) return;
    state.nodes.forEach((o) => { try { o.stop(0); o.disconnect(); } catch (e) {} });
    state.nodes = [];
    const I = state.interval * 60, el = elapsed(), now = ctx.currentTime;
    state.ctxOffset = now - el; // audio-clock time of session start
    for (let k = 0; k <= state.beepsTotal; k++) {
      const at = k * I - el;
      if (at < -0.1) continue;
      state.nodes.push(...blips(ctx, master, now + Math.max(0.03, at)));
    }
  }

  async function startMix() {
    const { ctx, master } = newCtx();
    state.ctx = ctx; state.master = master;
    // If the phone suspends or interrupts audio (call, other app), resume and requeue on the wall clock.
    ctx.onstatechange = () => {
      if (!state.running || state.ctx !== ctx) return;
      if (ctx.state !== "running" && ctx.state !== "closed") ctx.resume().catch(() => {});
      else if (ctx.state === "running") scheduleRemaining();
    };
    await ctx.resume();
    scheduleRemaining();
  }

  // Audio clock drifted from the wall clock (context was paused)? Requeue.
  function checkMix() {
    const ctx = state.ctx;
    if (!ctx) return;
    if (ctx.state !== "running") { ctx.resume().catch(() => {}); return; }
    if (Math.abs(ctx.currentTime - (state.ctxOffset + elapsed())) > 1) scheduleRemaining();
  }

  // ---------- Engine 2: screen off, no music (looping media track) ----------
  function startTrack() {
    state.url = makeClipURL(state.interval * 60);
    const a = new Audio();
    a.src = state.url; a.loop = true; a.preload = "auto";
    a.addEventListener("timeupdate", update);
    a.addEventListener("pause", () => {
      if (state.running && !state.stoppingByUs) {
        setStatus("Another app took over the speaker, so beeps paused. Stop, pick Over music, and start again.", true);
      }
    });
    state.audio = a;
    if ("mediaSession" in navigator) {
      try {
        navigator.mediaSession.metadata = new MediaMetadata({ title: `Posture check every ${state.interval} min`, artist: "Posture DRS" });
        navigator.mediaSession.setActionHandler("pause", () => stop("Stopped from the lock screen."));
        navigator.mediaSession.setActionHandler("stop", () => stop("Stopped from the lock screen."));
      } catch (e) {}
    }
    return a.play();
  }

  function stopEngines() {
    state.stoppingByUs = true;
    if (state.audio) {
      state.audio.removeEventListener("timeupdate", update);
      state.audio.pause();
      state.audio.removeAttribute("src");
      state.audio.load();
      state.audio = null;
    }
    if (state.url) { URL.revokeObjectURL(state.url); state.url = null; }
    if (state.ctx) { const c = state.ctx; state.ctx = null; c.onstatechange = null; c.close().catch(() => {}); }
    state.nodes = [];
    if ("mediaSession" in navigator) { try { navigator.mediaSession.metadata = null; } catch (e) {} }
    state.stoppingByUs = false;
  }

  // ---------- Run loop ----------
  function update() {
    if (!state.running) return;
    const T = state.total * 60, I = state.interval * 60;
    const el = elapsed();
    if (el >= state.stopAt) { finish(); return; }
    if (state.mode === "mix") checkMix();
    const done = Math.min(Math.floor(el / I + 1e-9), state.beepsTotal);
    const nextAt = (done + 1) * I;
    $("left").textContent = fmt(T - el);
    $("next").textContent = nextAt <= T + 1e-9 ? fmt(nextAt - el) : "—";
    $("count").textContent = `${done} / ${state.beepsTotal}`;
    renderLeds(done, state.beepsTotal);
    if (done > state.lastShown) {
      if (document.visibilityState === "visible" && el - done * I < 3) flash("now");
      state.lastShown = done;
    }
  }

  async function start() {
    const err = validate();
    if (err) { setStatus(err, true); return; }
    const T = state.total * 60, I = state.interval * 60;
    state.mode = document.querySelector('input[name="mode"]:checked').value;
    state.beepsTotal = beepsFor(T, I);
    // If the last beep lands exactly on the end, let it play before stopping.
    state.stopAt = Math.abs(T - state.beepsTotal * I) < 1e-6 ? T + 0.6 : T;
    state.lastShown = 0;
    state.startTs = Date.now();
    state.running = true;
    setRunningUI(true);

    try {
      await (state.mode === "track" ? startTrack() : startMix());
    } catch (e) {
      stop("The browser blocked sound. Tap Start again, and check that media volume is up.", true);
      return;
    }
    flash("now");
    setStatus(state.mode === "track"
      ? `Running. Beeping every ${state.interval} min for ${state.total} min. You can lock the phone.`
      : `Running. Beeping every ${state.interval} min on top of your music. Keep this page open.`);
    requestWake();
    state.tick = setInterval(update, 250);
    clearTimeout(state.stopTimer);
    state.stopTimer = setTimeout(update, state.stopAt * 1000 + 50);
    update();
  }

  function stop(msg = "Stopped.", isErr = false) {
    state.running = false;
    clearInterval(state.tick); clearTimeout(state.stopTimer);
    stopEngines(); releaseWake();
    setRunningUI(false);
    setStatus(msg, isErr);
    idleView();
  }

  function finish() {
    const n = state.beepsTotal;
    stop(`Session done. ${n} posture check${n === 1 ? "" : "s"} completed.`);
    playFx(3, 1400);
    try { navigator.vibrate && navigator.vibrate([120, 80, 120]); } catch (e) {}
    flash("done");
    renderLeds(n, n);
    $("count").textContent = `${n} / ${n}`;
  }

  function setRunningUI(on) {
    $("setup").disabled = on;
    const go = $("go");
    go.textContent = on ? "Stop" : "Start";
    go.classList.toggle("stop", on);
  }

  $("go").addEventListener("click", () => (state.running ? stop() : start()));

  idleView();
})();

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => {}));
}
