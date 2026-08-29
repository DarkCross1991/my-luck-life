/* 3DSong 0.02 web preview — layout constants match 3dsong/source/ui.c */
"use strict";

const TOP_W = 400, TOP_H = 240, BOT_W = 320, BOT_H = 240;
const HDR_H = 18;
const PLAY = { x: 52, y: 66, w: 50, h: 36 };
const PREV = { x: 10, y: 70, w: 36, h: 30 };
const NEXT = { x: 108, y: 70, w: 36, h: 30 };
const STOP = { x: 150, y: 70, w: 28, h: 30 };
const VOL = { x: 186, y: 76, w: 124, h: 18 };
const SEEK = { x: 10, y: 50, w: 300, h: 12 };
const EQ_Y = 110, EQ_H = 48, EQ_X0 = 18, EQ_SLOT = 70;
const FLAT = { x: 246, y: 118, w: 62, h: 32 };
const LIST_Y = 164, LIST_H = 72, ROW_H = 18;
const LOAD = { x: 150, y: 1, w: 52, h: 16 };
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
  volume: 0.8,
  eq: [0, 0, 0],
  title: "NO SIGNAL",
  format: "FILE",
  error: "",
  vuL: 0,
  vuR: 0,
  tubes: [0.18, 0.18, 0.18, 0.18],
  wave: new Float32Array(128),
  bins: new Float32Array(16),
  persist: null,
};

let audioCtx = null;
let nodes = null;
let sourceNode = null;
let mediaEl = null;
let demoBuffers = [];
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
  gain.gain.value = state.volume;
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

function applyVolume() {
  if (nodes) nodes.gain.gain.value = state.volume;
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

function onEnded() {
  if (state.repeat === 1) {
    seekFrac(0);
    play();
    return;
  }
  next(1);
}

async function openIndex(i, autoplay) {
  if (i < 0 || i >= state.tracks.length) return;
  ensureAudio();
  if (audioCtx.state === "suspended") await audioCtx.resume();
  disconnectSource();
  const tr = state.tracks[i];
  state.index = i;
  state.cursor = i;
  if (i < state.scroll) state.scroll = i;
  if (i >= state.scroll + 4) state.scroll = i - 3;
  state.title = tr.title;
  state.format = tr.format;
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
  if (state.index < 0) {
    if (state.tracks.length) openIndex(state.cursor, true);
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

function next(dir) {
  if (!state.tracks.length) return;
  let i;
  if (state.shuffle && state.tracks.length > 1) {
    i = state.index;
    while (i === state.index) i = Math.floor(Math.random() * state.tracks.length);
  } else {
    i = state.index + dir;
    if (i < 0) i = state.repeat === 2 ? state.tracks.length - 1 : 0;
    if (i >= state.tracks.length) i = state.repeat === 2 ? 0 : state.tracks.length - 1;
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
  demoBuffers = [a, b];
  state.tracks = [
    { title: "Filament Warm-up", format: "DEMO", buffer: a, duration: a.duration },
    { title: "Amber Trio", format: "DEMO", buffer: b, duration: b.duration },
  ];
}

function addFiles(fileList) {
  ensureAudio();
  const files = Array.from(fileList || []);
  files.forEach((file) => {
    const url = URL.createObjectURL(file);
    state.tracks.push({
      title: file.name.replace(/\.[^.]+$/, ""),
      format: fmtName(file.name),
      url,
      duration: 0,
    });
  });
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
  }
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function drawTop() {
  const ctx = tctx;
  ctx.clearRect(0, 0, TOP_W, TOP_H);

  const wood = ctx.createLinearGradient(0, 0, 0, TOP_H);
  wood.addColorStop(0, "#5a3418");
  wood.addColorStop(0.5, "#3a1e0c");
  wood.addColorStop(1, "#241208");
  ctx.fillStyle = wood;
  ctx.fillRect(0, 0, TOP_W, TOP_H);
  ctx.fillStyle = "rgba(255,200,120,0.04)";
  for (let y = 8; y < TOP_H; y += 5) ctx.fillRect(0, y, TOP_W, 1);

  const plate = ctx.createLinearGradient(0, 14, 0, TOP_H - 20);
  plate.addColorStop(0, "#8a8680");
  plate.addColorStop(0.12, "#5c5852");
  plate.addColorStop(0.5, "#3e3c38");
  plate.addColorStop(1, "#2a2824");
  roundRect(ctx, 14, 12, TOP_W - 28, TOP_H - 30, 4);
  ctx.fillStyle = plate;
  ctx.fill();

  ctx.fillStyle = "#d4a848";
  ctx.font = "bold 13px Palatino, serif";
  ctx.fillText("3DSong", 24, 32);
  ctx.fillStyle = "#b8aa88";
  ctx.font = "8px sans-serif";
  ctx.fillText("STEREO INTEGRATED AMPLIFIER", 92, 31);
  ctx.fillStyle = "#9a7a38";
  ctx.fillText("TYPE 0.02", 318, 31);

  ctx.beginPath();
  ctx.arc(372, 28, 6, 0, Math.PI * 2);
  ctx.fillStyle = "#1a1208";
  ctx.fill();
  const g = ctx.createRadialGradient(371, 26, 0.5, 372, 28, 7);
  g.addColorStop(0, state.playing ? "#ffe8a0" : "#6a3a18");
  g.addColorStop(0.4, state.playing ? "#ff9a28" : "#401808");
  g.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = g;
  ctx.beginPath();
  ctx.arc(372, 28, 7, 0, Math.PI * 2);
  ctx.fill();

  drawVu(ctx, 78, 118, 54, state.vuL, "L");
  drawVu(ctx, 322, 118, 54, state.vuR, "R");

  for (let i = 0; i < 4; i++) drawTube(ctx, 150 + i * 26, 88, state.tubes[i]);
  ctx.fillStyle = "#8a8070";
  ctx.font = "7px sans-serif";
  ctx.textAlign = "center";
  ctx.fillText("EL34", 189, 122);
  ctx.textAlign = "left";

  drawCrt(ctx, 138, 128, 126, 48);

  ctx.fillStyle = "#1a0e08";
  ctx.fillRect(18, 196, TOP_W - 36, 22);
  ctx.fillStyle = "#f0e2c4";
  ctx.font = "11px Palatino, serif";
  ctx.fillText(state.title || "NO SIGNAL", 24, 211);
  ctx.fillStyle = "#e88c30";
  ctx.font = "9px sans-serif";
  ctx.fillText(state.format, 308, 211);
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

function drawBottom() {
  const ctx = bctx;
  ctx.clearRect(0, 0, BOT_W, BOT_H);
  ctx.fillStyle = "#16100c";
  ctx.fillRect(0, 0, BOT_W, BOT_H);
  ctx.fillStyle = "#20160e";
  ctx.fillRect(0, 0, BOT_W, HDR_H);
  ctx.fillStyle = "#d4a848";
  ctx.font = "11px Palatino, serif";
  ctx.fillText("3DSong 0.02", 6, 13);
  ctx.fillStyle = state.shuffle ? "#e88c30" : "#a08c64";
  ctx.font = "9px sans-serif";
  ctx.fillText(state.shuffle ? "SHUF" : "SEQ", 210, 13);
  ctx.fillStyle = state.repeat ? "#e88c30" : "#a08c64";
  ctx.fillText(state.repeat === 1 ? "R1" : state.repeat === 2 ? "R*" : "R-", 258, 13);
  ctx.fillStyle = "#d4a848";
  ctx.fillText("LOAD", LOAD.x + 6, 13);
  ctx.fillStyle = "#8a8070";
  ctx.fillText("O3DS", 286, 13);

  ctx.fillStyle = "#f0e2c4";
  ctx.font = "11px Palatino, serif";
  ctx.fillText(state.title || "Выберите трек в списке", 8, 32);
  ctx.font = "9px sans-serif";
  if (state.error) {
    ctx.fillStyle = "#c83020";
    ctx.fillText(state.error, 8, 44);
  } else {
    ctx.fillStyle = "#a08c64";
    const st = state.playing ? "PLAY" : pauseOffset > 0 ? "PAUSE" : "STOP";
    ctx.fillText(`${state.format}  ${Math.round(state.volume * 100)}%  ${st}`, 8, 44);
  }
  ctx.fillStyle = "#a08c64";
  ctx.fillText(`${clock(currentTime())} / ${clock(duration)}`, 230, 44);

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
  ctx.fillRect(STOP.x + 8, STOP.y + 9, 12, 12);

  ctx.fillStyle = "#a08c64";
  ctx.font = "8px sans-serif";
  ctx.fillText("VOL", VOL.x, VOL.y - 2);
  ctx.fillStyle = "#1c1a16";
  ctx.fillRect(VOL.x, VOL.y + 4, VOL.w, 8);
  ctx.fillStyle = "#d4a848";
  ctx.fillRect(VOL.x, VOL.y + 4, VOL.w * state.volume, 8);
  ctx.beginPath();
  ctx.arc(VOL.x + VOL.w * state.volume, VOL.y + 8, 5, 0, Math.PI * 2);
  ctx.fillStyle = "#ff9a28";
  ctx.fill();

  for (let i = 0; i < 3; i++) {
    const x = EQ_X0 + i * EQ_SLOT;
    const g = (state.eq[i] + 12) / 24;
    const fill = EQ_H - 14;
    const h = g * fill;
    ctx.fillStyle = "#1c1a16";
    ctx.fillRect(x + 14, EQ_Y, 10, fill);
    ctx.fillStyle = "#e88c30";
    ctx.fillRect(x + 14, EQ_Y + (fill - h), 10, h);
    ctx.fillStyle = "#d4a848";
    ctx.fillRect(x + 11, EQ_Y + (fill - h) - 3, 16, 8);
    ctx.fillStyle = "#a08c64";
    ctx.font = "8px sans-serif";
    ctx.fillText(BANDS[i].name, x, EQ_Y + fill + 10);
  }
  btn(ctx, FLAT, false);
  ctx.fillStyle = "#f0e2c4";
  ctx.font = "11px Palatino, serif";
  ctx.fillText("FLAT", FLAT.x + 14, FLAT.y + 20);

  ctx.fillStyle = "#3a2a18";
  ctx.fillRect(0, LIST_Y - 2, BOT_W, 1);
  const vis = Math.floor(LIST_H / ROW_H);
  for (let i = 0; i < vis; i++) {
    const idx = state.scroll + i;
    const y = LIST_Y + i * ROW_H;
    if (idx >= state.tracks.length) break;
    if (idx === state.cursor) {
      ctx.fillStyle = "#5a3818";
      ctx.fillRect(0, y, BOT_W, ROW_H);
    }
    if (idx === state.index) {
      ctx.fillStyle = "#48dc5a";
      ctx.beginPath();
      ctx.arc(8, y + ROW_H * 0.5, 3, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.fillStyle = "#f0e2c4";
    ctx.font = "10px Palatino, serif";
    const name = state.tracks[idx].title.slice(0, 28);
    ctx.fillText(name, 16, y + 13);
    ctx.fillStyle = "#a08c64";
    ctx.font = "8px sans-serif";
    ctx.fillText(state.tracks[idx].format, 268, y + 13);
  }
  if (!state.tracks.length) {
    ctx.fillStyle = "#a08c64";
    ctx.font = "10px sans-serif";
    ctx.fillText("Нет файлов. LOAD или drag-and-drop", 8, LIST_Y + 24);
  }
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
  if (held && inBox(px, py, { x: SEEK.x, y: SEEK.y - 4, w: SEEK.w, h: SEEK.h + 8 })) {
    seekFrac((px - SEEK.x) / SEEK.w);
    dragging = "seek";
    return;
  }
  if (held && inBox(px, py, { x: VOL.x, y: VOL.y - 4, w: VOL.w, h: VOL.h + 10 })) {
    state.volume = Math.min(1, Math.max(0, (px - VOL.x) / VOL.w));
    applyVolume();
    dragging = "vol";
    return;
  }
  if (held && py >= EQ_Y && py < EQ_Y + EQ_H - 10) {
    for (let i = 0; i < 3; i++) {
      const x = EQ_X0 + i * EQ_SLOT;
      if (px >= x && px < x + 42) {
        const fill = EQ_H - 14;
        let g = 1 - (py - EQ_Y) / fill;
        g = Math.min(1, Math.max(0, g));
        state.eq[i] = -12 + g * 24;
        applyEq();
        dragging = "eq";
        return;
      }
    }
  }
  if (!pressed) return;
  if (inBox(px, py, PLAY)) toggle();
  else if (inBox(px, py, PREV)) {
    if (progress() > 0.04) seekFrac(0);
    else next(-1);
  } else if (inBox(px, py, NEXT)) next(1);
  else if (inBox(px, py, STOP)) stop();
  else if (inBox(px, py, FLAT)) { state.eq = [0, 0, 0]; applyEq(); }
  else if (inBox(px, py, LOAD)) fileInput.click();
  else if (px >= 200 && py < HDR_H) {
    if (px < 250) state.shuffle = !state.shuffle;
    else if (px < 286) state.repeat = (state.repeat + 1) % 3;
  } else if (py >= LIST_Y) {
    const row = Math.floor((py - LIST_Y) / ROW_H);
    const idx = state.scroll + row;
    if (idx >= 0 && idx < state.tracks.length) openIndex(idx, true);
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
  if (ev.code === "Space") { ev.preventDefault(); toggle(); }
  if (ev.code === "ArrowLeft") next(-1);
  if (ev.code === "ArrowRight") next(1);
  if (ev.code === "ArrowUp") {
    state.cursor = Math.max(0, state.cursor - 1);
    if (state.cursor < state.scroll) state.scroll = state.cursor;
  }
  if (ev.code === "ArrowDown") {
    state.cursor = Math.min(state.tracks.length - 1, state.cursor + 1);
    if (state.cursor >= state.scroll + 4) state.scroll = state.cursor - 3;
  }
  if (ev.code === "KeyL" || ev.code === "Equal") {
    state.volume = Math.min(1, state.volume + 0.05);
    applyVolume();
  }
  if (ev.code === "KeyR" || ev.code === "Minus") {
    state.volume = Math.max(0, state.volume - 0.05);
    applyVolume();
  }
  if (ev.code === "Enter" && state.cursor >= 0) openIndex(state.cursor, true);
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
