// Single source of truth for the app version (semver). Keep in sync with
// VERSION in sw.js and the top entry of CHANGELOG.md; CI checks this.
const APP_VERSION = "1.1.0";

(() => {
  const $ = (id) => document.getElementById(id);
  const state = {
    total: 60, interval: 5, mode: "mix", sound: "double", pitch: "bright",
    running: false, startTs: 0, stopAt: 0, beepsTotal: 0, lastShown: -1,
    audio: null, url: null, ctx: null, master: null, nodes: [], ctxOffset: 0, bufCache: {},
    wakeLock: null, tick: null, stopTimer: null, stoppingByUs: false,
  };

  // ---------- Sounds ----------
  // Each sound is a list of tones [start s, length s]. A voice decides the timbre:
  // "bright" is a high, sharp beep; "bass" is a low, rounder note.
  const SOUNDS = {
    pips:   { label: "Short pips",  bright: { f: 2400, tones: [[0, .05], [.11, .05], [.22, .05]] },
                                    bass:   { f: 220,  tones: [[0, .08], [.14, .08], [.28, .08]] } },
    double: { label: "Double blip", bright: { f: 2000, tones: [[0, .09], [.15, .09]] },
                                    bass:   { f: 196,  tones: [[0, .12], [.2, .12]] } },
    long:   { label: "Two long",    bright: { f: 1000, tones: [[0, .4], [.6, .4]], partials: [[1, 1], [2, .15]] },
                                    bass:   { f: 165,  tones: [[0, .45], [.65, .45]] } },
    finish: {                       bright: { f: 1400, tones: [[0, .09], [.15, .09], [.3, .2]] },
                                    bass:   { f: 147,  tones: [[0, .12], [.2, .12], [.4, .3]] } },
  };
  const VOICES = {
    // triangle-ish wave plus a fifth on top: the original beep
    bright: { partials: [[1, 1], [3, -1 / 9], [5, 1 / 25], [1.5, .3]], attack: .005, release: .01, decay: 0 },
    // fundamental + harmonics so phone speakers (which can't play deep lows) still carry the pitch
    bass:   { partials: [[1, 1], [2, .7], [3, .45], [4, .25], [5, .12]], attack: .012, release: .05, decay: 2.5 },
  };

  // Renders a sound to raw samples. Partials above the sample rate's limit are skipped,
  // so the same sound works for Web Audio (44.1/48 kHz) and the 8 kHz looping track.
  function synth(soundKey, pitch, sr) {
    const spec = SOUNDS[soundKey][pitch], voice = VOICES[pitch];
    const partials = (pitch === "bright" && spec.partials) || voice.partials;
    const end = Math.max(...spec.tones.map(([s, d]) => s + d)) + .02;
    const out = new Float32Array(Math.ceil(end * sr));
    for (const [start, dur] of spec.tones) {
      const s0 = Math.round(start * sr), len = Math.round(dur * sr);
      const usable = partials.filter(([m]) => spec.f * m < sr * .45);
      for (let i = 0; i < len && s0 + i < out.length; i++) {
        const t = i / sr;
        const env = Math.min(1, t / voice.attack, (dur - t) / voice.release) * Math.exp(-voice.decay * t);
        let v = 0;
        for (const [m, a] of usable) v += a * Math.sin(2 * Math.PI * spec.f * m * t);
        out[s0 + i] += env * v;
      }
    }
    let peak = 0;
    for (const x of out) peak = Math.max(peak, Math.abs(x));
    if (peak > 0) for (let i = 0; i < out.length; i++) out[i] *= .85 / peak;
    return out;
  }

  // One interval of 8-bit audio: the sound at t=0, then silence. Looped, it beeps every interval.
  function makeClipURL(seconds, soundKey, pitch) {
    const sr = 8000, n = Math.round(seconds * sr);
    const buf = new Uint8Array(44 + n);
    const dv = new DataView(buf.buffer);
    const str = (o, s) => { for (let i = 0; i < s.length; i++) buf[o + i] = s.charCodeAt(i); };
    str(0, "RIFF"); dv.setUint32(4, 36 + n, true); str(8, "WAVE");
    str(12, "fmt "); dv.setUint32(16, 16, true); dv.setUint16(20, 1, true); dv.setUint16(22, 1, true);
    dv.setUint32(24, sr, true); dv.setUint32(28, sr, true); dv.setUint16(32, 1, true); dv.setUint16(34, 8, true);
    str(36, "data"); dv.setUint32(40, n, true);
    buf.fill(128, 44);
    const beep = synth(soundKey, pitch, sr);
    for (let i = 0; i < beep.length && i < n; i++) buf[44 + i] = Math.round(128 + 127 * beep[i]);
    return URL.createObjectURL(new Blob([buf], { type: "audio/wav" }));
  }

  function newCtx() {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const master = ctx.createGain();
    master.gain.value = 1;
    master.connect(ctx.destination);
    return { ctx, master, cache: {} };
  }

  function bufferFor(holder, soundKey, pitch) {
    const key = soundKey + ":" + pitch;
    if (!holder.cache[key]) {
      const data = synth(soundKey, pitch, holder.ctx.sampleRate);
      const b = holder.ctx.createBuffer(1, data.length, holder.ctx.sampleRate);
      b.getChannelData(0).set(data);
      holder.cache[key] = b;
    }
    return holder.cache[key];
  }

  function playAt(holder, when, soundKey, pitch) {
    const src = holder.ctx.createBufferSource();
    src.buffer = bufferFor(holder, soundKey, pitch);
    src.connect(holder.master);
    src.start(when);
    return src;
  }

  let fx = null;
  function playFx(soundKey = state.sound, pitch = state.pitch) {
    try {
      fx = fx || newCtx();
      if (fx.ctx.state !== "running") fx.ctx.resume();
      playAt(fx, fx.ctx.currentTime + .03, soundKey, pitch);
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
      box.replaceChildren();
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

  // ---------- Saved choices (this device only) ----------
  const store = {
    get(k) { try { return localStorage.getItem("posture." + k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem("posture." + k, v); } catch (e) {} },
  };

  // ---------- Setup controls ----------
  document.querySelectorAll(".chips").forEach((group) => {
    const key = group.dataset.key;
    const input = group.querySelector("input");
    const numeric = key === "total" || key === "interval";
    const select = (b) => group.querySelectorAll(".chip").forEach((c) => c.setAttribute("aria-pressed", String(c === b)));

    if (!numeric) {
      const saved = store.get(key);
      const savedBtn = saved && group.querySelector(`.chip[data-val="${saved}"]`);
      if (savedBtn) { select(savedBtn); state[key] = saved; }
    }

    group.addEventListener("click", (e) => {
      const b = e.target.closest(".chip"); if (!b) return;
      select(b);
      if (numeric) {
        input.value = "";
        state[key] = Number(b.dataset.val);
        idleView();
      } else {
        state[key] = b.dataset.val;
        store.set(key, b.dataset.val);
        playFx();
      }
    });
    if (input) input.addEventListener("input", () => {
      const v = Number(input.value);
      if (v > 0) {
        group.querySelectorAll(".chip").forEach((c) => c.setAttribute("aria-pressed", "false"));
        state[key] = v;
        idleView();
      }
    });
  });

  $("test").addEventListener("click", () => { playFx(); flash("now"); });

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
    const holder = state.ctx;
    if (!holder) return;
    state.nodes.forEach((n) => { try { n.stop(0); n.disconnect(); } catch (e) {} });
    state.nodes = [];
    const I = state.interval * 60, el = elapsed(), now = holder.ctx.currentTime;
    state.ctxOffset = now - el; // audio-clock time of session start
    for (let k = 0; k <= state.beepsTotal; k++) {
      const at = k * I - el;
      if (at < -0.1) continue;
      state.nodes.push(playAt(holder, now + Math.max(0.03, at), state.sound, state.pitch));
    }
  }

  async function startMix() {
    const holder = newCtx();
    state.ctx = holder;
    const ctx = holder.ctx;
    // If the phone suspends or interrupts audio (call, other app), resume and requeue on the wall clock.
    ctx.onstatechange = () => {
      if (!state.running || state.ctx !== holder) return;
      if (ctx.state !== "running" && ctx.state !== "closed") ctx.resume().catch(() => {});
      else if (ctx.state === "running") scheduleRemaining();
    };
    await ctx.resume();
    scheduleRemaining();
  }

  // Audio clock drifted from the wall clock (context was paused)? Requeue.
  function checkMix() {
    const holder = state.ctx;
    if (!holder) return;
    if (holder.ctx.state !== "running") { holder.ctx.resume().catch(() => {}); return; }
    if (Math.abs(holder.ctx.currentTime - (state.ctxOffset + elapsed())) > 1) scheduleRemaining();
  }

  // ---------- Engine 2: screen off, no music (looping media track) ----------
  function startTrack() {
    state.url = makeClipURL(state.interval * 60, state.sound, state.pitch);
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
        navigator.mediaSession.metadata = new MediaMetadata({ title: `Posture check every ${state.interval} min`, artist: "Posture" });
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
    if (state.ctx) { const c = state.ctx.ctx; state.ctx = null; c.onstatechange = null; c.close().catch(() => {}); }
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
    state.stopAt = Math.abs(T - state.beepsTotal * I) < 1e-6 ? T + 1.2 : T;
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
    playFx("finish", state.pitch);
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

  $("ver").textContent = "v" + APP_VERSION;
  idleView();
})();

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => {}));
}
