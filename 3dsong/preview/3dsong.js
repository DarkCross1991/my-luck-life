/* 3DSong 0.4 web preview — layout constants match 3dsong/include/ui_layout.h */
"use strict";

const TOP_W = 400, TOP_H = 240, BOT_W = 320, BOT_H = 240;
const SEEK = { x: 8, y: 28, w: 304, h: 12 };
const PREV = { x: 6, y: 48, w: 36, h: 30 };
const PLAY = { x: 46, y: 46, w: 48, h: 34 };
const NEXT = { x: 98, y: 48, w: 36, h: 30 };
const STOP = { x: 138, y: 48, w: 36, h: 30 };
const EQBTN = { x: 180, y: 48, w: 62, h: 30 };
const FLAT = { x: 248, y: 48, w: 66, h: 30 };
const FOLDER_Y = 86, FOLDER_H = 16;
const LIST_Y = 104, LIST_H = 136, ROW_H = 17;
const LIST_ROWS = Math.floor(LIST_H / ROW_H);
const EQ_BACK = { x: 8, y: 6, w: 64, h: 26 };
const EQ_SLIDER_Y = 62, EQ_SLIDER_H = 148, EQ_SLIDER_W = 18, EQ_COL0 = 46, EQ_COL_GAP = 90;
const BANDS = [
  { name: "BASS", hz: 110, q: 0.7 },
  { name: "MID", hz: 900, q: 0.85 },
  { name: "TREBLE", hz: 6500, q: 0.7 },
];

const topCanvas = document.getElementById("top");
const botCanvas = document.getElementById("bot");
const tctx = topCanvas.getContext("2d");
const bctx = botCanvas.getContext("2d");
const fileInput = document.getElementById("fileInput");

const state = {
  tracks: [],
  cursor: 0,
  scroll: 0,
  index: -1,
  playing: false,
  shuffle: false,
  repeat: 0,
  eq: [0, 0, 0],
  title: "NO SIGNAL",
  format: "FILE",
  error: "",
  screen: "player",
  cwd: "sdmc:/Music",
  currentPath: "",
  userFiles: [],
  vuL: 0,
  vuR: 0,
  tubes: [0.18, 0.18, 0.18, 0.18],
  wave: new Float32Array(128),
  bins: new Float32Array(16),
  spec: new Float32Array(80),
  sparks: null,
  persist: null,
};

let audioCtx = null;
let nodes = null;
let sourceNode = null;
let mediaEl = null;
let demoTracks = [];
let currentKind = null; /* "buffer" | "media" */
let startedAt = 0;
let pauseOffset = 0;
let duration = 0;
let dragging = null;

function inBox(px, py, b) {
  return px >= b.x && px < b.x + b.w && py >= b.y && py < b.y + b.h;
}

function fmtName(name) {
  const m = /\.([a-z0-9]+)$/i.exec(name || "");
  return (m ? m[1] : "file").toUpperCase();
}

function parentOf(path) {
  if (path === "sdmc:/Music/Live") return "sdmc:/Music";
  if (path === "sdmc:/Music") return "sdmc:/";
  return "sdmc:/";
}

function truncLeft(path, maxChars) {
  if (!path) return "";
  if (path.length <= maxChars) return path;
  return "..." + path.slice(-(maxChars - 3));
}

function ensureAudio() {
  if (audioCtx) return audioCtx;
  const AC = window.AudioContext || window.webkitAudioContext;
  audioCtx = new AC();
  const bass = audioCtx.createBiquadFilter();
  const mid = audioCtx.createBiquadFilter();
  const treble = audioCtx.createBiquadFilter();
  const analyser = audioCtx.createAnalyser();
  const gain = audioCtx.createGain();
  bass.type = "peaking"; bass.frequency.value = 110; bass.Q.value = 0.7; bass.gain.value = 0;
  mid.type = "peaking"; mid.frequency.value = 900; mid.Q.value = 0.85; mid.gain.value = 0;
  treble.type = "peaking"; treble.frequency.value = 6500; treble.Q.value = 0.7; treble.gain.value = 0;
  analyser.fftSize = 256;
  analyser.smoothingTimeConstant = 0.55;
  gain.gain.value = 1;
  bass.connect(mid);
  mid.connect(treble);
  treble.connect(analyser);
  analyser.connect(gain);
  gain.connect(audioCtx.destination);
  nodes = { bass, mid, treble, analyser, gain };
  applyEq();
  return audioCtx;
}

function applyEq() {
  if (!nodes) return;
  nodes.bass.gain.value = state.eq[0];
  nodes.mid.gain.value = state.eq[1];
  nodes.treble.gain.value = state.eq[2];
}

function disconnectSource() {
  if (sourceNode) {
    try { sourceNode.stop(); } catch (_) {}
    try { sourceNode.disconnect(); } catch (_) {}
    sourceNode = null;
  }
  if (mediaEl) {
    mediaEl.pause();
    mediaEl.src = "";
    mediaEl = null;
  }
  currentKind = null;
}

function attach(node) {
  node.connect(nodes.bass);
  sourceNode = node;
}

function currentTime() {
  if (!audioCtx || !state.playing) return pauseOffset;
  if (currentKind === "media" && mediaEl) return mediaEl.currentTime;
  return Math.min(duration, pauseOffset + (audioCtx.currentTime - startedAt));
}

function progress() {
  if (duration <= 0) return 0;
  return Math.min(1, Math.max(0, currentTime() / duration));
}

function clock(sec) {
  sec = Math.max(0, Math.floor(sec || 0));
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, "0")}`;
}

function rebuildList() {
  const items = [];
  if (state.cwd !== "sdmc:/") {
    items.push({ kind: "parent", title: "..", path: parentOf(state.cwd), format: "" });
  }
  if (state.cwd === "sdmc:/") {
    items.push({ kind: "dir", title: "Music", path: "sdmc:/Music", format: "" });
  }
  if (state.cwd === "sdmc:/Music") {
    items.push({ kind: "dir", title: "Live", path: "sdmc:/Music/Live", format: "" });
    demoTracks.forEach((tr) => items.push({ ...tr, kind: "file", folder: "sdmc:/Music" }));
  }
  state.userFiles.filter((f) => f.folder === state.cwd).forEach((f) => items.push(f));
  state.tracks = items;
  if (state.cursor >= state.tracks.length) state.cursor = Math.max(0, state.tracks.length - 1);
  if (state.scroll > state.cursor) state.scroll = state.cursor;
}

function openFolder(path) {
  state.cwd = path;
  state.cursor = 0;
  state.scroll = 0;
  rebuildList();
}

function onEnded() {
  if (state.repeat === 1) {
    seekFrac(0);
    play();
    return;
  }
  next(1);
}

function fileIndices() {
  const out = [];
  state.tracks.forEach((t, i) => { if (t.kind === "file") out.push(i); });
  return out;
}

async function openIndex(i, autoplay) {
  if (i < 0 || i >= state.tracks.length) return;
  const tr = state.tracks[i];
  if (!tr || tr.kind !== "file") return;
  ensureAudio();
  if (audioCtx.state === "suspended") await audioCtx.resume();
  disconnectSource();
  state.index = i;
  state.cursor = i;
  if (i < state.scroll) state.scroll = i;
  if (i >= state.scroll + LIST_ROWS) state.scroll = i - LIST_ROWS + 1;
  state.title = tr.title;
  state.format = tr.format;
  state.currentPath = tr.path || (tr.title + ":" + tr.format);
  state.error = "";
  pauseOffset = 0;
  duration = tr.duration || 0;
  try {
    if (tr.buffer) {
      duration = tr.buffer.duration;
      currentKind = "buffer";
    } else if (tr.url) {
      mediaEl = new Audio();
      mediaEl.crossOrigin = "anonymous";
      mediaEl.src = tr.url;
      mediaEl.addEventListener("ended", onEnded);
      await new Promise((resolve, reject) => {
        mediaEl.onloadedmetadata = resolve;
        mediaEl.onerror = () => reject(new Error("decode"));
      });
      duration = mediaEl.duration || 0;
      const mediaSrc = audioCtx.createMediaElementSource(mediaEl);
      attach(mediaSrc);
      currentKind = "media";
    }
  } catch (err) {
    state.error = "Cannot decode " + tr.format;
    state.playing = false;
    return;
  }
  if (autoplay) play();
}

function play() {
  const files = fileIndices();
  if (state.index < 0 || !state.tracks[state.index] || state.tracks[state.index].kind !== "file") {
    if (files.length) openIndex(files[0], true);
    return;
  }
  ensureAudio();
  audioCtx.resume();
  const tr = state.tracks[state.index];
  if (currentKind === "buffer" && tr.buffer) {
    disconnectSource();
    const src = audioCtx.createBufferSource();
    src.buffer = tr.buffer;
    src.onended = () => {
      if (state.playing && currentTime() >= duration - 0.05) onEnded();
    };
    attach(src);
    src.start(0, pauseOffset);
    startedAt = audioCtx.currentTime;
    currentKind = "buffer";
  } else if (currentKind === "media" && mediaEl) {
    mediaEl.currentTime = pauseOffset;
    mediaEl.play();
  }
  state.playing = true;
}

function pause() {
  pauseOffset = currentTime();
  state.playing = false;
  if (currentKind === "buffer") {
    if (sourceNode) {
      try { sourceNode.stop(); } catch (_) {}
      try { sourceNode.disconnect(); } catch (_) {}
      sourceNode = null;
    }
  } else if (mediaEl) {
    mediaEl.pause();
  }
}

function stop() {
  pauseOffset = 0;
  state.playing = false;
  if (currentKind === "buffer") {
    if (sourceNode) {
      try { sourceNode.stop(); } catch (_) {}
      try { sourceNode.disconnect(); } catch (_) {}
      sourceNode = null;
    }
  } else if (mediaEl) {
    mediaEl.pause();
    mediaEl.currentTime = 0;
  }
}

function toggle() {
  if (state.playing) pause();
  else play();
}

function activate(i) {
  const tr = state.tracks[i];
  if (!tr) return;
  state.cursor = i;
  if (tr.kind === "file") {
    if (state.index === i && currentKind) toggle();
    else openIndex(i, true);
    return;
  }
  if (tr.path) openFolder(tr.path);
}

function onPlayButton() {
  const tr = state.tracks[state.cursor];
  if (tr && tr.kind !== "file") {
    activate(state.cursor);
    return;
  }
  if (tr && state.index === state.cursor && currentKind) toggle();
  else if (tr && tr.kind === "file") openIndex(state.cursor, true);
  else toggle();
}

function next(dir) {
  const files = fileIndices();
  if (!files.length) return;
  let pos = files.indexOf(state.index);
  let i;
  if (state.shuffle && files.length > 1) {
    i = files[Math.floor(Math.random() * files.length)];
    while (i === state.index && files.length > 1) i = files[Math.floor(Math.random() * files.length)];
  } else {
    if (pos < 0) pos = dir > 0 ? -1 : 0;
    pos += dir;
    if (pos < 0) pos = state.repeat === 2 ? files.length - 1 : 0;
    if (pos >= files.length) pos = state.repeat === 2 ? 0 : files.length - 1;
    i = files[pos];
  }
  openIndex(i, true);
}

function seekFrac(f) {
  f = Math.min(1, Math.max(0, f));
  pauseOffset = f * duration;
  if (state.playing) play();
  else if (mediaEl) mediaEl.currentTime = pauseOffset;
}

function fillBuffer(ctx, spec) {
  const sr = ctx.sampleRate;
  const n = Math.floor(sr * spec.dur);
  const buf = ctx.createBuffer(2, n, sr);
  const L = buf.getChannelData(0);
  const R = buf.getChannelData(1);
  for (let i = 0; i < n; i++) {
    const t = i / sr;
    const beat = spec.chords[Math.floor((t / spec.dur) * spec.chords.length) % spec.chords.length];
    let s = 0;
    for (let h = 0; h < beat.length; h++) {
      const f = beat[h];
      s += Math.sin(2 * Math.PI * f * t) / (h + 1.15);
      s += 0.18 * Math.sin(2 * Math.PI * f * 2 * t);
    }
    s += spec.bass * Math.sin(2 * Math.PI * beat[0] * 0.5 * t);
    const env = 0.25 + 0.75 * Math.min(1, t * 3) * (1 - Math.pow(t / spec.dur, 8));
    const trem = 1 + spec.trem * Math.sin(2 * Math.PI * 5.5 * t);
    const v = s * spec.gain * env * trem;
    const d = 0.004 * spec.detune;
    L[i] = Math.max(-0.95, Math.min(0.95, v + d * Math.sin(2 * Math.PI * 3 * t)));
    R[i] = Math.max(-0.95, Math.min(0.95, v - d * Math.sin(2 * Math.PI * 3.2 * t)));
  }
  return buf;
}

async function bootDemos() {
  ensureAudio();
  const a = fillBuffer(audioCtx, {
    dur: 16,
    gain: 0.22,
    bass: 0.35,
    trem: 0.04,
    detune: 1,
    chords: [
      [220.0, 261.63, 329.63],
      [174.61, 220.0, 261.63],
      [130.81, 196.0, 261.63],
      [196.0, 246.94, 293.66],
    ],
  });
  const b = fillBuffer(audioCtx, {
    dur: 14,
    gain: 0.2,
    bass: 0.55,
    trem: 0.07,
    detune: 1.4,
    chords: [
      [110, 164.81, 220],
      [146.83, 196, 246.94],
      [98, 146.83, 196],
      [123.47, 185, 246.94],
    ],
  });
  demoTracks = [
    { title: "Filament Warm-up", format: "DEMO", buffer: a, duration: a.duration, path: "sdmc:/Music/filament" },
    { title: "Amber Trio", format: "DEMO", buffer: b, duration: b.duration, path: "sdmc:/Music/amber" },
  ];
  openFolder("sdmc:/Music");
}

function addFiles(fileList) {
  ensureAudio();
  const folder = state.cwd === "sdmc:/" ? "sdmc:/Music" : state.cwd;
  const files = Array.from(fileList || []);
  files.forEach((file) => {
    const url = URL.createObjectURL(file);
    state.userFiles.push({
      kind: "file",
      title: file.name.replace(/\.[^.]+$/, ""),
      format: fmtName(file.name),
      url,
      duration: 0,
      folder,
      path: folder + "/" + file.name,
    });
  });
  if (state.cwd === "sdmc:/") openFolder("sdmc:/Music");
  else rebuildList();
}

function analyze() {
  if (!nodes) return;
  const analyser = nodes.analyser;
  const freq = new Uint8Array(analyser.frequencyBinCount);
  const time = new Uint8Array(analyser.fftSize);
  analyser.getByteFrequencyData(freq);
  analyser.getByteTimeDomainData(time);
  let sumL = 0, peak = 0;
  for (let i = 0; i < time.length; i++) {
    const v = (time[i] - 128) / 128;
    sumL += v * v;
    if (Math.abs(v) > peak) peak = Math.abs(v);
  }
  const rms = Math.sqrt(sumL / time.length);
  const targetVu = Math.min(1, rms * 3.2);
  const atk = state.playing ? 0.28 : 0.08;
  state.vuL += (targetVu - state.vuL) * atk;
  state.vuR += (targetVu * (0.92 + peak * 0.15) - state.vuR) * atk;
  for (let i = 0; i < 16; i++) {
    const a = Math.floor(i * freq.length / 16);
    const b = Math.floor((i + 1) * freq.length / 16);
    let s = 0;
    for (let k = a; k < b; k++) s += freq[k];
    const e = (s / Math.max(1, b - a)) / 255;
    state.bins[i] += (e - state.bins[i]) * 0.35;
  }
  for (let i = 0; i < state.spec.length; i++) {
    const a = Math.floor((i / state.spec.length) * freq.length);
    const b = Math.floor(((i + 1) / state.spec.length) * freq.length);
    let s = 0;
    for (let k = a; k < b; k++) s += freq[k];
    const e = (s / Math.max(1, b - a)) / 255;
    const atk = state.playing ? 0.44 : 0.14;
    state.spec[i] += (e - state.spec[i]) * atk;
  }
  const step = Math.floor(time.length / state.wave.length);
  for (let i = 0; i < state.wave.length; i++) {
    state.wave[i] = (time[i * step] - 128) / 128;
  }
  const t = [
    state.bins[1] * 1.2 + rms,
    state.bins[4] * 1.2 + rms,
    state.bins[8] * 1.1 + rms,
    state.bins[12] * 1.3 + peak,
  ];
  for (let i = 0; i < 4; i++) {
    const tgt = 0.16 + Math.min(1, t[i]) * 0.84;
    state.tubes[i] += (tgt - state.tubes[i]) * 0.2;
  }
  if (!state.playing) {
    state.vuL *= 0.96;
    state.vuR *= 0.96;
    for (let i = 0; i < 4; i++) state.tubes[i] = 0.16 + (state.tubes[i] - 0.16) * 0.96;
    for (let i = 0; i < state.spec.length; i++) state.spec[i] *= 0.94;
  }
}

const LED_COLS = 80;
const LED_ROWS = 60;
const LED_CELL_W = TOP_W / LED_COLS;
const LED_CELL_H = TOP_H / LED_ROWS;

function warmLedRgb(t, bright) {
  const stops = [
    [0, [42, 24, 10]],
    [0.22, [88, 48, 16]],
    [0.45, [168, 108, 32]],
    [0.62, [228, 176, 64]],
    [0.78, [255, 148, 40]],
    [1, [168, 44, 18]],
  ];
  let i = 0;
  while (i < stops.length - 2 && t > stops[i + 1][0]) i++;
  const a = stops[i];
  const b = stops[i + 1];
  const f = (t - a[0]) / Math.max(0.001, b[0] - a[0]);
  const r = Math.round((a[1][0] + (b[1][0] - a[1][0]) * f) * bright);
  const g = Math.round((a[1][1] + (b[1][1] - a[1][1]) * f) * bright);
  const bl = Math.round((a[1][2] + (b[1][2] - a[1][2]) * f) * bright);
  return `rgb(${Math.min(255, r)},${Math.min(255, g)},${Math.min(255, bl)})`;
}

function drawLedCell(ctx, col, row, color, alpha) {
  const x = col * LED_CELL_W + 0.5;
  const y = row * LED_CELL_H + 0.5;
  const w = LED_CELL_W - 1;
  const h = LED_CELL_H - 1;
  ctx.globalAlpha = alpha;
  ctx.fillStyle = color;
  ctx.fillRect(x, y, w, h);
  ctx.globalAlpha = 1;
}

function drawTop() {
  const ctx = tctx;
  const mid = LED_ROWS / 2;
  let col, row, t, energy, half, r, bright, color, i;

  ctx.fillStyle = "#060402";
  ctx.fillRect(0, 0, TOP_W, TOP_H);

  for (row = 0; row < LED_ROWS; row++) {
    for (col = 0; col < LED_COLS; col++) {
      drawLedCell(ctx, col, row, "#140c06", 0.92);
    }
  }

  for (col = 0; col < LED_COLS; col++) {
    t = col / (LED_COLS - 1);
    energy = state.spec[col] || 0;
    if (!state.playing) {
      energy *= 0.35;
      energy += 0.06 * Math.sin(Date.now() * 0.002 + col * 0.35);
    }
    energy = Math.min(1, Math.max(0, energy * 1.65 + 0.04));
    half = Math.floor(energy * (mid - 2));
    for (r = 0; r < half; r++) {
      bright = 0.55 + (r / Math.max(1, half)) * 0.45;
      color = warmLedRgb(t, bright);
      drawLedCell(ctx, col, Math.floor(mid) - 1 - r, color, 1);
      drawLedCell(ctx, col, Math.floor(mid) + r, color, 1);
    }
  }

  if (state.playing) {
    const cx = TOP_W * 0.5;
    const cy = TOP_H * 0.5;
    const pulse = 0.55 + state.vuL * 0.45;
    if (!state.sparks) {
      state.sparks = [];
      for (i = 0; i < 48; i++) {
        const ang = (i / 48) * Math.PI * 2;
        state.sparks.push({ ang, dist: 0.12 + (i % 7) * 0.04, phase: i * 0.7 });
      }
    }
    ctx.save();
    ctx.globalCompositeOperation = "lighter";
    for (i = 0; i < state.sparks.length; i++) {
      const sp = state.sparks[i];
      const wobble = Math.sin(Date.now() * 0.004 + sp.phase) * 0.08;
      const rad = (TOP_W * 0.42) * (sp.dist + wobble) * pulse;
      const x = cx + Math.cos(sp.ang) * rad;
      const y = cy + Math.sin(sp.ang) * rad * 0.55;
      const g = ctx.createRadialGradient(x, y, 0, x, y, 3 + pulse * 4);
      g.addColorStop(0, "rgba(255,220,140,0.85)");
      g.addColorStop(0.35, "rgba(255,150,40,0.35)");
      g.addColorStop(1, "rgba(80,30,8,0)");
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(x, y, 3 + pulse * 4, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
  }

  const vig = ctx.createRadialGradient(TOP_W * 0.5, TOP_H * 0.5, TOP_W * 0.15, TOP_W * 0.5, TOP_H * 0.5, TOP_W * 0.72);
  vig.addColorStop(0, "rgba(0,0,0,0)");
  vig.addColorStop(1, "rgba(8,4,2,0.35)");
  ctx.fillStyle = vig;
  ctx.fillRect(0, 0, TOP_W, TOP_H);
}

function drawVu(ctx, cx, cy, r, level, tag) {
  ctx.beginPath();
  ctx.arc(cx, cy, r + 3, 0, Math.PI * 2);
  ctx.fillStyle = "#1c1a16";
  ctx.fill();
  const face = ctx.createRadialGradient(cx - 8, cy - 10, 6, cx, cy, r);
  face.addColorStop(0, "#3a3428");
  face.addColorStop(1, "#12100c");
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fillStyle = face;
  ctx.fill();

  for (let i = 0; i <= 10; i++) {
    const a = (210 + 120 * i / 10) * Math.PI / 180;
    ctx.beginPath();
    ctx.moveTo(cx + Math.cos(a) * (r - 4), cy + Math.sin(a) * (r - 4));
    ctx.lineTo(cx + Math.cos(a) * (r - 12), cy + Math.sin(a) * (r - 12));
    ctx.strokeStyle = i >= 8 ? "#c83020" : "#e6d296";
    ctx.lineWidth = i % 5 === 0 ? 1.6 : 1;
    ctx.stroke();
  }
  const t = Math.min(1, Math.max(0, level));
  const a = (210 + 120 * t) * Math.PI / 180;
  ctx.beginPath();
  ctx.moveTo(cx, cy);
  ctx.lineTo(cx + Math.cos(a) * (r - 14), cy + Math.sin(a) * (r - 14));
  ctx.strokeStyle = "#f0ecdc";
  ctx.lineWidth = 1.6;
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(cx, cy, 3.4, 0, Math.PI * 2);
  ctx.fillStyle = "#d4a848";
  ctx.fill();
  ctx.fillStyle = "#d4a848";
  ctx.font = "9px serif";
  ctx.textAlign = "center";
  ctx.fillText(tag, cx, cy + r - 10);
  ctx.fillStyle = "#8a8070";
  ctx.font = "8px sans-serif";
  ctx.fillText("VU", cx, cy + 8);
  ctx.textAlign = "left";
}

function drawTube(ctx, x, y, e) {
  const glow = ctx.createRadialGradient(x, y - 8, 2, x, y - 6, 22);
  glow.addColorStop(0, `rgba(255,180,60,${0.15 + e * 0.55})`);
  glow.addColorStop(1, "rgba(255,80,0,0)");
  ctx.fillStyle = glow;
  ctx.beginPath();
  ctx.arc(x, y - 8, 22, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.ellipse(x, y - 6, 10, 22, 0, 0, Math.PI * 2);
  ctx.fillStyle = "rgba(40,55,48,0.55)";
  ctx.fill();
  ctx.strokeStyle = "rgba(180,220,200,0.35)";
  ctx.stroke();
  const fire = ctx.createRadialGradient(x, y - 4, 1, x, y - 4, 10);
  fire.addColorStop(0, `rgba(255,230,160,${0.4 + e * 0.6})`);
  fire.addColorStop(0.5, `rgba(255,110,30,${0.25 + e * 0.5})`);
  fire.addColorStop(1, "rgba(80,20,0,0)");
  ctx.fillStyle = fire;
  ctx.beginPath();
  ctx.ellipse(x, y - 4, 6, 12, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#b8b0a0";
  ctx.fillRect(x - 7, y + 14, 14, 5);
  ctx.fillStyle = "#8a6030";
  ctx.fillRect(x - 5, y + 19, 10, 3);
}

function drawCrt(ctx, x, y, w, h) {
  ctx.fillStyle = "#0a120c";
  ctx.fillRect(x - 2, y - 2, w + 4, h + 4);
  ctx.fillStyle = "#07140c";
  ctx.fillRect(x, y, w, h);
  if (!state.persist) {
    state.persist = document.createElement("canvas");
    state.persist.width = w;
    state.persist.height = h;
  }
  const p = state.persist.getContext("2d");
  p.fillStyle = "rgba(7,20,12,0.28)";
  p.fillRect(0, 0, w, h);
  p.beginPath();
  for (let i = 0; i < state.wave.length; i++) {
    const px = i * w / (state.wave.length - 1);
    const py = h * 0.5 - state.wave[i] * h * 0.42;
    if (i === 0) p.moveTo(px, py);
    else p.lineTo(px, py);
  }
  p.strokeStyle = "#ffb040";
  p.lineWidth = 1.2;
  p.stroke();
  ctx.drawImage(state.persist, x, y);
  ctx.strokeStyle = "rgba(40,90,40,0.35)";
  ctx.beginPath();
  ctx.moveTo(x, y + h / 2);
  ctx.lineTo(x + w, y + h / 2);
  ctx.stroke();
}

function btn(ctx, box, hot) {
  ctx.fillStyle = hot ? "#6e4e2a" : "#403022";
  ctx.fillRect(box.x, box.y, box.w, box.h);
  ctx.fillStyle = "#6a5030";
  ctx.fillRect(box.x, box.y, box.w, 1);
  ctx.fillStyle = "#20180e";
  ctx.fillRect(box.x, box.y + box.h - 1, box.w, 1);
}

function drawEqScreen() {
  const ctx = bctx;
  ctx.fillStyle = "#16100c";
  ctx.fillRect(0, 0, BOT_W, BOT_H);
  ctx.fillStyle = "#20160e";
  ctx.fillRect(0, 0, BOT_W, 38);
  btn(ctx, EQ_BACK, false);
  ctx.fillStyle = "#f0e2c4";
  ctx.font = "11px Palatino, serif";
  ctx.textAlign = "center";
  ctx.fillText("BACK", EQ_BACK.x + EQ_BACK.w / 2, EQ_BACK.y + 18);
  ctx.fillStyle = "#d4a848";
  ctx.font = "13px Palatino, serif";
  ctx.fillText("EQUALIZER", 160, 24);
  ctx.textAlign = "left";

  for (let i = 0; i < 3; i++) {
    const cx = EQ_COL0 + i * EQ_COL_GAP;
    const g = (state.eq[i] + 12) / 24;
    const fill = EQ_SLIDER_H;
    const h = g * fill;
    const tx = cx - EQ_SLIDER_W / 2;
    ctx.fillStyle = "#a08c64";
    ctx.font = "9px sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(BANDS[i].name, cx, EQ_SLIDER_Y - 6);
    ctx.fillStyle = "#1c1a16";
    ctx.fillRect(tx, EQ_SLIDER_Y, EQ_SLIDER_W, fill);
    ctx.fillStyle = "#e88c30";
    ctx.fillRect(tx, EQ_SLIDER_Y + (fill - h), EQ_SLIDER_W, h);
    ctx.strokeStyle = "#a08c64";
    ctx.beginPath();
    ctx.moveTo(tx - 5, EQ_SLIDER_Y + fill * 0.5);
    ctx.lineTo(tx + EQ_SLIDER_W + 5, EQ_SLIDER_Y + fill * 0.5);
    ctx.stroke();
    ctx.fillStyle = "#d4a848";
    ctx.fillRect(tx - 4, EQ_SLIDER_Y + (fill - h) - 4, EQ_SLIDER_W + 8, 10);
    ctx.fillStyle = "#d4a848";
    ctx.fillText(`${state.eq[i] >= 0 ? "+" : ""}${Math.round(state.eq[i])} dB`, cx, EQ_SLIDER_Y + fill + 16);
  }
  ctx.textAlign = "left";
}

function drawPlayerScreen() {
  const ctx = bctx;
  ctx.fillStyle = "#16100c";
  ctx.fillRect(0, 0, BOT_W, BOT_H);

  ctx.fillStyle = "#f0e2c4";
  ctx.font = "11px Palatino, serif";
  ctx.fillText(state.title || "Выберите трек в папке", 8, 14);
  ctx.font = "9px sans-serif";
  if (state.error) {
    ctx.fillStyle = "#c83020";
    ctx.fillText(state.error, 8, 26);
  } else {
    ctx.fillStyle = "#a08c64";
    const st = state.playing ? "PLAY" : pauseOffset > 0 ? "PAUSE" : "STOP";
    ctx.fillText(`${state.format}  ${st}`, 8, 26);
  }
  ctx.fillStyle = "#a08c64";
  ctx.fillText(`${clock(currentTime())} / ${clock(duration)}`, 230, 26);

  const prog = progress();
  ctx.fillStyle = "#1c1a16";
  ctx.fillRect(SEEK.x, SEEK.y + 3, SEEK.w, 6);
  ctx.fillStyle = "#e88c30";
  ctx.fillRect(SEEK.x, SEEK.y + 3, SEEK.w * prog, 6);
  ctx.beginPath();
  ctx.arc(SEEK.x + SEEK.w * prog, SEEK.y + 6, 4, 0, Math.PI * 2);
  ctx.fillStyle = "#d4a848";
  ctx.fill();

  btn(ctx, PREV, false);
  triangle(ctx, PREV.x + 8, PREV.y + 15, -1);
  triangle(ctx, PREV.x + 18, PREV.y + 15, -1);
  btn(ctx, PLAY, state.playing);
  if (state.playing) {
    ctx.fillStyle = "#f0e2c4";
    ctx.fillRect(PLAY.x + 16, PLAY.y + 8, 6, 20);
    ctx.fillRect(PLAY.x + 28, PLAY.y + 8, 6, 20);
  } else {
    ctx.fillStyle = "#ffb040";
    ctx.beginPath();
    ctx.moveTo(PLAY.x + 16, PLAY.y + 8);
    ctx.lineTo(PLAY.x + 36, PLAY.y + 18);
    ctx.lineTo(PLAY.x + 16, PLAY.y + 28);
    ctx.fill();
  }
  btn(ctx, NEXT, false);
  triangle(ctx, NEXT.x + 16, NEXT.y + 15, 1);
  triangle(ctx, NEXT.x + 26, NEXT.y + 15, 1);
  btn(ctx, STOP, false);
  ctx.fillStyle = "#f0e2c4";
  ctx.fillRect(STOP.x + (STOP.w - 12) / 2, STOP.y + (STOP.h - 12) / 2, 12, 12);
  btn(ctx, EQBTN, state.screen === "eq");
  ctx.fillStyle = "#f0e2c4";
  ctx.font = "12px Palatino, serif";
  ctx.textAlign = "center";
  ctx.fillText("EQ", EQBTN.x + EQBTN.w / 2, EQBTN.y + 20);
  btn(ctx, FLAT, false);
  ctx.fillText("FLAT", FLAT.x + FLAT.w / 2, FLAT.y + 20);
  ctx.textAlign = "left";

  ctx.fillStyle = "#20160e";
  ctx.fillRect(0, FOLDER_Y, BOT_W, FOLDER_H);
  ctx.fillStyle = "#d4a848";
  ctx.font = "9px sans-serif";
  ctx.fillText(truncLeft(state.cwd, 36), 6, FOLDER_Y + 12);
  ctx.fillStyle = "#e88c30";
  ctx.textAlign = "right";
  ctx.fillText("LOAD", BOT_W - 8, FOLDER_Y + 12);
  ctx.textAlign = "left";

  const vis = LIST_ROWS;
  let anyFile = 0;
  for (let i = 0; i < vis; i++) {
    const idx = state.scroll + i;
    const y = LIST_Y + i * ROW_H;
    if (idx >= state.tracks.length) break;
    const tr = state.tracks[idx];
    if (idx === state.cursor) {
      ctx.fillStyle = "#5a3818";
      ctx.fillRect(0, y, BOT_W, ROW_H);
    }
    if (tr.kind === "file" && tr.path && tr.path === state.currentPath) {
      ctx.fillStyle = "#48dc5a";
      ctx.beginPath();
      ctx.arc(8, y + ROW_H * 0.5, 3, 0, Math.PI * 2);
      ctx.fill();
      anyFile++;
    } else if (tr.kind !== "file") {
      ctx.fillStyle = "#8a6030";
      ctx.fillRect(4, y + 5, 8, 6);
      ctx.fillStyle = "#d4a848";
      ctx.fillRect(6, y + 4, 5, 2);
    }
    ctx.font = "10px Palatino, serif";
    if (tr.kind === "parent") {
      ctx.fillStyle = "#a08c64";
      ctx.fillText("..", 16, y + 12);
    } else if (tr.kind === "dir") {
      ctx.fillStyle = "#e88c30";
      ctx.fillText(`${tr.title}/`, 16, y + 12);
    } else {
      ctx.fillStyle = "#f0e2c4";
      ctx.fillText(tr.title.slice(0, 28), 16, y + 12);
      ctx.fillStyle = "#a08c64";
      ctx.font = "8px sans-serif";
      ctx.fillText(tr.format, 268, y + 12);
      anyFile++;
    }
  }
  const hasFile = state.tracks.some((t) => t.kind === "file");
  if (!state.tracks.length) {
    ctx.fillStyle = "#a08c64";
    ctx.font = "10px sans-serif";
    ctx.fillText("Нет файлов. LOAD или drag-and-drop", 8, LIST_Y + 24);
  } else if (!hasFile) {
    ctx.fillStyle = "#a08c64";
    ctx.font = "10px sans-serif";
    ctx.fillText("Нет песен в этой папке", 8, LIST_Y + state.tracks.length * ROW_H + 14);
  }
}

function drawBottom() {
  const ctx = bctx;
  ctx.clearRect(0, 0, BOT_W, BOT_H);
  if (state.screen === "eq") drawEqScreen();
  else drawPlayerScreen();
}

function triangle(ctx, x, y, dir) {
  ctx.fillStyle = "#f0e2c4";
  ctx.beginPath();
  ctx.moveTo(x, y);
  ctx.lineTo(x - dir * 8, y - 6);
  ctx.lineTo(x - dir * 8, y + 6);
  ctx.fill();
}

function canvasPos(canvas, ev) {
  const r = canvas.getBoundingClientRect();
  const src = ev.touches ? ev.touches[0] : ev;
  return {
    x: (src.clientX - r.left) * (canvas.width / r.width),
    y: (src.clientY - r.top) * (canvas.height / r.height),
  };
}

function handlePointer(px, py, pressed, held) {
  px = Math.floor(px);
  py = Math.floor(py);
  if (state.screen === "eq") {
    if (held) {
      for (let i = 0; i < 3; i++) {
        const cx = EQ_COL0 + i * EQ_COL_GAP;
        if (px >= cx - 28 && px < cx + 28 && py >= EQ_SLIDER_Y - 4 && py < EQ_SLIDER_Y + EQ_SLIDER_H + 4) {
          let g = 1 - (py - EQ_SLIDER_Y) / EQ_SLIDER_H;
          g = Math.min(1, Math.max(0, g));
          state.eq[i] = -12 + g * 24;
          applyEq();
          dragging = "eq";
          return;
        }
      }
    }
    if (pressed && inBox(px, py, EQ_BACK)) state.screen = "player";
    return;
  }
  if (held && inBox(px, py, { x: SEEK.x, y: SEEK.y - 4, w: SEEK.w, h: SEEK.h + 8 })) {
    seekFrac((px - SEEK.x) / SEEK.w);
    dragging = "seek";
    return;
  }
  if (!pressed) return;
  if (inBox(px, py, PLAY)) onPlayButton();
  else if (inBox(px, py, PREV)) {
    if (progress() > 0.04) seekFrac(0);
    else next(-1);
  } else if (inBox(px, py, NEXT)) next(1);
  else if (inBox(px, py, STOP)) stop();
  else if (inBox(px, py, EQBTN)) state.screen = "eq";
  else if (inBox(px, py, FLAT)) { state.eq = [0, 0, 0]; applyEq(); }
  else if (py >= FOLDER_Y && py < FOLDER_Y + FOLDER_H) fileInput.click();
  else if (py >= LIST_Y && py < LIST_Y + LIST_H) {
    const row = Math.floor((py - LIST_Y) / ROW_H);
    const idx = state.scroll + row;
    if (idx >= 0 && idx < state.tracks.length) {
      const tr = state.tracks[idx];
      state.cursor = idx;
      if (tr.kind === "file") openIndex(idx, true);
      else if (tr.path) openFolder(tr.path);
    }
  }
}

botCanvas.addEventListener("pointerdown", (ev) => {
  ev.preventDefault();
  botCanvas.setPointerCapture(ev.pointerId);
  const p = canvasPos(botCanvas, ev);
  dragging = "down";
  handlePointer(p.x, p.y, true, true);
});
botCanvas.addEventListener("pointermove", (ev) => {
  if (!dragging) return;
  const p = canvasPos(botCanvas, ev);
  handlePointer(p.x, p.y, false, true);
});
botCanvas.addEventListener("pointerup", () => { dragging = null; });
botCanvas.addEventListener("pointercancel", () => { dragging = null; });

window.addEventListener("keydown", (ev) => {
  if (ev.code === "Space") { ev.preventDefault(); onPlayButton(); }
  if (ev.code === "Escape" || ev.code === "KeyB") {
    if (state.screen === "eq") state.screen = "player";
    else stop();
  }
  if (ev.code === "KeyE") state.screen = state.screen === "eq" ? "player" : "eq";
  if (ev.code === "ArrowLeft") next(-1);
  if (ev.code === "ArrowRight") next(1);
  if (state.screen !== "eq") {
    if (ev.code === "ArrowUp") {
      state.cursor = Math.max(0, state.cursor - 1);
      if (state.cursor < state.scroll) state.scroll = state.cursor;
    }
    if (ev.code === "ArrowDown") {
      state.cursor = Math.min(state.tracks.length - 1, state.cursor + 1);
      if (state.cursor >= state.scroll + LIST_ROWS) state.scroll = state.cursor - LIST_ROWS + 1;
    }
  }
  if (ev.code === "Enter" && state.cursor >= 0) activate(state.cursor);
});

fileInput.addEventListener("change", () => addFiles(fileInput.files));
const consoleEl = document.getElementById("console");
["dragenter", "dragover"].forEach((t) => {
  consoleEl.addEventListener(t, (e) => { e.preventDefault(); });
});
consoleEl.addEventListener("drop", (e) => {
  e.preventDefault();
  addFiles(e.dataTransfer.files);
});

function loop() {
  analyze();
  drawTop();
  drawBottom();
  requestAnimationFrame(loop);
}

bootDemos().catch((err) => {
  state.error = "Web Audio unavailable";
  console.error(err);
}).finally(() => {
  loop();
});
