const $ = (id) => document.getElementById(id);
const SPLASH_STARTED_AT = Date.now();

const Native = {
  get available() {
    return !!window.FlownaNative;
  },
  call(method, ...args) {
    if (!this.available || typeof window.FlownaNative[method] !== "function") {
      return { ok: false, error: "Native köprü hazır değil." };
    }
    try {
      const raw = window.FlownaNative[method](...args);
      return raw ? JSON.parse(raw) : { ok: true };
    } catch (error) {
      return { ok: false, error: error?.message || "Native çağrı başarısız." };
    }
  }
};

const COVER_CACHE_SCHEMA = 3;
migrateCoverCache();

const state = {
  screen: "search",
  permissionGranted: false,
  notificationPermission: false,
  manageStoragePermission: false,
  versionName: "1.0.26",
  versionCode: 26,
  webVersion: "1.0.26-bundled",
  liveUpdateActive: false,
  songs: [],
  downloadedSongs: [],
  search: { query: "", loading: false, results: [], error: "", callbackId: "", suggestions: [], suggestionsLoading: false, suggestionsError: "", suggestionsCallbackId: "" },
  similar: { seedKey: "", query: "", loading: false, results: [], error: "", callbackId: "", fetchedAt: 0 },
  recent: loadJson("flowna_recent_searches", []),
  downloads: loadJson("flowna_downloads", []),
  failedDownloads: loadJson("flowna_failed_downloads", []),
  playStats: loadJson("flowna_play_stats", {}),
  coverCache: loadJson("flowna_cover_cache", {}),
  coverMisses: loadJson("flowna_cover_misses", {}),
  coverPending: new Set(),
  coverRequests: new Map(),
  coverQueue: [],
  coverHydrating: false,
  settings: Object.assign({
    quality: "320K",
    wifiOnly: false,
    bgDl: true,
    autoUp: true,
    notifyDownloads: true
  }, loadJson("flowna_settings", {})),
  libTab: "all",
  dlTab: "active",
  current: null,
  queue: [],
  playing: false,
  position: 0,
  duration: 0,
  liked: loadJson("flowna_likes", {}),
  preview: null,
  actionSheet: null,
  pendingFileActions: {},
  playerDrag: null,
  toastTimer: null,
  searchTimer: null,
  suggestTimer: null,
  searchWatchdog: null,
  pollTimer: null,
  registry: new Map()
};

window.FlownaNativeBridge = {
  onNativeEvent(event) {
    handleNativeEvent(event || {});
  }
};

function loadJson(key, fallback) {
  try {
    const value = localStorage.getItem(key);
    return value ? JSON.parse(value) : fallback;
  } catch (_) {
    return fallback;
  }
}

function saveJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function migrateCoverCache() {
  const current = Number(localStorage.getItem("flowna_cover_cache_schema") || 0);
  if (current >= COVER_CACHE_SCHEMA) return;
  localStorage.removeItem("flowna_cover_cache");
  localStorage.removeItem("flowna_cover_misses");
  localStorage.setItem("flowna_cover_cache_schema", String(COVER_CACHE_SCHEMA));
}

function esc(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function fmt(ms) {
  const total = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

function icon(name, size = 20) {
  const paths = {
    search: `<circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 21 21"/>`,
    download: `<path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/>`,
    library: `<path d="M9 18V5l10-2v13"/><circle cx="7" cy="18" r="3"/><circle cx="17" cy="16" r="3"/>`,
    settings: `<path d="M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z"/><path d="M3 12h2m14 0h2M12 3v2m0 14v2m-6.4-3.6 1.4-1.4m10-10 1.4-1.4m0 12.8-1.4-1.4m-10-10L5.6 4.6"/>`,
    play: `<path d="M8 5v14l11-7Z" fill="currentColor" stroke="none"/>`,
    pause: `<path d="M8 5v14"/><path d="M16 5v14"/>`,
    next: `<path d="m6 6 8 6-8 6V6Z" fill="currentColor" stroke="none"/><path d="M18 6v12"/>`,
    prev: `<path d="M6 6v12"/><path d="m18 6-8 6 8 6V6Z" fill="currentColor" stroke="none"/>`,
    more: `<circle cx="12" cy="5" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="19" r="1.4" fill="currentColor" stroke="none"/>`,
    heart: `<path d="M20.8 8.7c0 5.4-8.8 10.3-8.8 10.3S3.2 14.1 3.2 8.7A4.7 4.7 0 0 1 12 6.3a4.7 4.7 0 0 1 8.8 2.4Z"/>`,
    heartFill: `<path d="M20.8 8.7c0 5.4-8.8 10.3-8.8 10.3S3.2 14.1 3.2 8.7A4.7 4.7 0 0 1 12 6.3a4.7 4.7 0 0 1 8.8 2.4Z" fill="currentColor" stroke="none"/>`,
    close: `<path d="M6 6l12 12"/><path d="M18 6 6 18"/>`,
    down: `<path d="m6 9 6 6 6-6"/>`,
    plus: `<path d="M12 5v14"/><path d="M5 12h14"/>`,
    share: `<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 10.5 6.8-4"/><path d="m8.6 13.5 6.8 4"/>`,
    edit: `<path d="M4 20h4l10.5-10.5a2.8 2.8 0 0 0-4-4L4 16v4Z"/><path d="m13.5 6.5 4 4"/>`,
    trash: `<path d="M4 7h16"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M6 7l1 14h10l1-14"/><path d="M9 7V4h6v3"/>`,
    check: `<path d="m5 13 4 4L19 7"/>`
  };
  return `<svg class="ico" width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.play}</svg>`;
}

function keyOf(track) {
  return track?.uri || track?.videoUrl || track?.id || "";
}

function register(track) {
  const key = keyOf(track);
  if (key) state.registry.set(key, track);
  return esc(key);
}

function findTrack(key) {
  return state.registry.get(key)
    || state.songs.find((song) => keyOf(song) === key)
    || state.search.results.find((song) => keyOf(song) === key)
    || state.similar.results.find((song) => keyOf(song) === key)
    || null;
}

function coverMetaKey(title, artist) {
  return `${String(title || "").trim().toLowerCase()}|${String(artist || "").trim().toLowerCase()}`;
}

function sameTrack(a, b) {
  if (!a || !b) return false;
  if (a.uri && b.uri) return a.uri === b.uri;
  const aKey = keyOf(a);
  const bKey = keyOf(b);
  if (aKey && bKey) return aKey === bKey;
  return coverMetaKey(a.title, a.artist) === coverMetaKey(b.title, b.artist);
}

function cachedCoverFor(track) {
  if (!track) return "";
  const uriKey = track.uri ? `uri:${track.uri}` : "";
  return track.cover
    || (uriKey ? state.coverCache[uriKey] || "" : "")
    || "";
}

function rememberCover(track, cover) {
  if (!track || !cover || !/^https?:\/\//i.test(cover)) return;
  if (track.uri) state.coverCache[`uri:${track.uri}`] = cover;
  delete state.coverMisses[coverLookupKey(track)];
  saveJson("flowna_cover_cache", state.coverCache);
  saveJson("flowna_cover_misses", state.coverMisses);
}

function coverLookupKey(track) {
  return track?.uri ? `uri:${track.uri}` : coverMetaKey(track?.title, track?.artist);
}

function rememberCoverMiss(track) {
  const key = coverLookupKey(track);
  if (!key) return;
  state.coverMisses[key] = Date.now();
  saveJson("flowna_cover_misses", state.coverMisses);
}

function canRetryCover(track) {
  const key = coverLookupKey(track);
  const lastMiss = key ? Number(state.coverMisses[key] || 0) : 0;
  return !lastMiss || (Date.now() - lastMiss) > (6 * 60 * 60 * 1000);
}

function patchTrackCover(matcher, cover) {
  let changed = false;
  const patchList = (list) => list.map((item) => {
    if (!matcher(item) || item.cover === cover) return item;
    changed = true;
    return Object.assign({}, item, { cover });
  });

  state.songs = patchList(state.songs);
  state.downloadedSongs = patchList(state.downloadedSongs);
  state.search.results = patchList(state.search.results);
  state.similar.results = patchList(state.similar.results);
  state.queue = patchList(state.queue);
  state.downloads = state.downloads.map((item) => (
    matcher(item.track) ? Object.assign({}, item, { track: Object.assign({}, item.track, { cover }) }) : item
  ));
  state.failedDownloads = state.failedDownloads.map((item) => (
    matcher(item.track) ? Object.assign({}, item, { track: Object.assign({}, item.track, { cover }) }) : item
  ));
  if (matcher(state.current)) {
    state.current = Object.assign({}, state.current, { cover });
    changed = true;
  }
  return changed;
}

function applyCoverResult(track, cover) {
  if (!track || !cover) return;
  rememberCover(track, cover);
  const changed = patchTrackCover((item) => {
    if (!item) return false;
    if (track.uri && item.uri && item.uri === track.uri) return true;
    if (track.videoUrl && item.videoUrl && item.videoUrl === track.videoUrl) return true;
    const trackKey = keyOf(track);
    return !!trackKey && keyOf(item) === trackKey;
  }, cover);
  if (changed) renderActive();
}

function queueCoverHydration() {
  const toQueue = state.songs
    .filter((track) => track?.uri && !track.cover && canRetryCover(track))
    .slice(0, 12);

  toQueue.forEach((track) => {
    const key = track.uri || keyOf(track);
    if (!key || state.coverPending.has(key) || state.coverQueue.some((item) => sameTrack(item, track))) return;
    state.coverQueue.push({
      uri: track.uri || "",
      title: track.title || "",
      artist: track.artist || ""
    });
  });

  runNextCoverHydration();
}

function runNextCoverHydration() {
  if (state.coverHydrating) return;
  const next = state.coverQueue.shift();
  if (!next) return;

  const pendingKey = next.uri || coverMetaKey(next.title, next.artist);
  if (!pendingKey) {
    runNextCoverHydration();
    return;
  }

  state.coverHydrating = true;
  state.coverPending.add(pendingKey);

  const callbackId = `cover_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  state.coverRequests.set(callbackId, next);

  const query = [next.artist, next.title].filter(Boolean).join(" ").trim();
  if (!query) {
    state.coverRequests.delete(callbackId);
    state.coverPending.delete(pendingKey);
    state.coverHydrating = false;
    runNextCoverHydration();
    return;
  }

  const result = Native.call("refreshArtwork", JSON.stringify(next), callbackId);
  if (!result.ok) {
    state.coverRequests.delete(callbackId);
    state.coverPending.delete(pendingKey);
    state.coverHydrating = false;
    rememberCoverMiss(next);
    setTimeout(runNextCoverHydration, 120);
  }
}

function handleCoverLookup(event) {
  const request = state.coverRequests.get(event.callbackId);
  if (!request) return false;

  state.coverRequests.delete(event.callbackId);
  state.coverPending.delete(request.uri || coverMetaKey(request.title, request.artist));
  state.coverHydrating = false;

  if (event.ok && event.track?.cover) {
    applyCoverResult(request, event.track.cover);
  } else {
    rememberCoverMiss(request);
  }

  setTimeout(runNextCoverHydration, 100);
  return true;
}

function removeTrackLocally(track) {
  state.songs = state.songs.filter((item) => !sameTrack(item, track));
  state.downloadedSongs = state.downloadedSongs.filter((item) => !sameTrack(item, track));
  state.search.results = state.search.results.filter((item) => !sameTrack(item, track));
  state.similar.results = state.similar.results.filter((item) => !sameTrack(item, track));
  state.queue = state.queue.filter((item) => !sameTrack(item, track));
  state.downloads = state.downloads.filter((item) => !sameTrack(item.track, track));
  state.failedDownloads = state.failedDownloads.filter((item) => !sameTrack(item.track, track));
  if (sameTrack(state.current, track)) {
    Native.call("pause");
    state.current = null;
    state.playing = false;
    state.position = 0;
    state.duration = 0;
  }
}

function toast(message, delay = 2400) {
  const el = $("toast");
  if (!el) return;
  el.textContent = message;
  el.classList.remove("off");
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => el.classList.add("off"), delay);
}

function imgD(src, w, h, radius, fallback = "♪") {
  const safeSrc = esc(src || "");
  const icon = esc(fallback);
  const image = src
    ? `<img src="${safeSrc}" alt="" style="width:100%;height:100%;object-fit:cover;display:block" onerror="this.remove();this.parentElement.textContent='${icon}'">`
    : icon;
  return `<div style="width:${w}px;height:${h}px;border-radius:${radius}px;flex-shrink:0;background:linear-gradient(135deg,#ede0ff,#ffd6cc);overflow:hidden;display:flex;align-items:center;justify-content:center;font-size:${Math.max(18, Math.floor(w / 2.6))}px;color:var(--p);box-shadow:var(--sh0)">${image}</div>`;
}

function badge(track) {
  if (track?.source === "online") return `<span class="bdg byt">YouTube</span>`;
  if (track?.downloaded) return `<span class="bdg bdl">İndirildi</span>`;
  return `<span class="bdg bloc">Yerel</span>`;
}

function updateFromNativePayload(payload) {
  if (Array.isArray(payload.songs)) state.songs = normalizeSongs(payload.songs);
  if (Array.isArray(payload.downloadedSongs)) state.downloadedSongs = normalizeSongs(payload.downloadedSongs);
  if (typeof payload.permissionGranted === "boolean") state.permissionGranted = payload.permissionGranted;
  if (typeof payload.notificationPermission === "boolean") state.notificationPermission = payload.notificationPermission;
  if (typeof payload.manageStoragePermission === "boolean") state.manageStoragePermission = payload.manageStoragePermission;
  if (payload.versionName) state.versionName = payload.versionName;
  if (payload.versionCode) state.versionCode = payload.versionCode;
  if (payload.webVersion) state.webVersion = payload.webVersion;
  if (typeof payload.liveUpdateActive === "boolean") state.liveUpdateActive = payload.liveUpdateActive;
  if (payload.settings && typeof payload.settings === "object") {
    state.settings = Object.assign(state.settings, payload.settings);
  }
  queueCoverHydration();
}

function cleanupInterruptedDownloads() {
  const interrupted = state.downloads.filter((item) => item.status === "active");
  if (!interrupted.length) return;
  state.downloads = state.downloads.filter((item) => item.status !== "active");
  state.failedDownloads = [
    ...interrupted.map((item) => Object.assign({}, item, {
      status: "failed",
      progress: 0,
      message: "Yarim kaldi",
      error: "Onceki indirme tamamlanamadi. Tekrar deneyebilirsin."
    })),
    ...state.failedDownloads
  ].slice(0, 20);
  saveJson("flowna_downloads", state.downloads);
  saveJson("flowna_failed_downloads", state.failedDownloads);
}

function normalizeSongs(items) {
  return (items || []).map((song) => ({
    id: String(song.id ?? song.uri ?? song.videoUrl ?? cryptoId()),
    title: song.title || "Bilinmeyen şarkı",
    artist: song.artist || "Bilinmeyen sanatçı",
    album: song.album || "",
    durationMs: Number(song.durationMs || 0),
    duration: song.duration || fmt(song.durationMs || 0),
    uri: song.uri || "",
    cover: cachedCoverFor(song),
    videoUrl: song.videoUrl || (song.source === "online" && String(song.id || "").length === 11 ? `https://www.youtube.com/watch?v=${song.id}` : ""),
    source: song.source || (song.videoUrl ? "online" : "local"),
    downloaded: !!song.downloaded
  }));
}

function cryptoId() {
  return "id_" + Math.random().toString(36).slice(2) + Date.now();
}

let stableAppHeight = 0;
function syncAppHeight(force = false) {
  const activeTag = document.activeElement?.tagName || "";
  const keyboardLikelyOpen = activeTag === "INPUT" || activeTag === "TEXTAREA";
  const height = Math.round(window.innerHeight || document.documentElement.clientHeight || screen.height || 0);
  if (!height) return;
  if (force || !stableAppHeight || (!keyboardLikelyOpen && height > stableAppHeight)) {
    stableAppHeight = height;
  }
  document.documentElement.style.setProperty("--app-h", `${stableAppHeight || height}px`);
}

function init() {
  syncAppHeight(true);
  cleanupInterruptedDownloads();
  const initial = Native.call("getInitialState");
  if (initial.ok) {
    updateFromNativePayload(initial);
  } else {
    state.permissionGranted = false;
  }
  saveSettings(false);
  renderAll();
  startPlaybackPolling();
  setTimeout(() => document.body.classList.add("ui-settled"), 900);
  setTimeout(() => {
    $("splash")?.classList.add("off");
    setTimeout(() => Native.call("warmupDownloadEngine"), 450);
  }, Math.max(3600 - (Date.now() - SPLASH_STARTED_AT), 0));
}

function renderAll() {
  renderNav();
  renderSearch();
  renderLibrary();
  renderDownloads();
  renderSettings();
  renderMini();
}

function renderActive() {
  if (state.screen === "search") renderSearch();
  if (state.screen === "library") renderLibrary();
  if (state.screen === "downloads") renderDownloads();
  if (state.screen === "settings") renderSettings();
  renderMini();
  if (state.current && !$("player").classList.contains("off")) renderPlayer();
}

function goScreen(screen) {
  document.querySelectorAll(".scr").forEach((el) => el.classList.add("off"));
  $(`scr-${screen}`).classList.remove("off");
  state.screen = screen;
  $("player").classList.add("off");
  renderNav();
  renderActive();
}

function handleAndroidBack() {
  if (!$("act").classList.contains("off")) {
    closeActionSheet();
    return true;
  }
  if (!$("pov").classList.contains("off")) {
    closePrev();
    return true;
  }
  if (!$("player").classList.contains("off")) {
    closePlayer();
    return true;
  }
  if (state.screen !== "search") {
    goScreen("search");
    return true;
  }
  toast("Çıkmak için ana ekrandan kapatabilirsin.");
  return true;
}

function renderNav() {
  $("nav").innerHTML = [
    { id: "search", tone: "nav-search", ico: icon("search", 23), lbl: "Ara" },
    { id: "downloads", tone: "nav-downloads", ico: icon("download", 23), lbl: "İndirmeler" },
    { id: "library", tone: "nav-library", ico: icon("library", 23), lbl: "Kütüphane" },
    { id: "settings", tone: "nav-settings", ico: icon("settings", 23), lbl: "Ayarlar" }
  ].map((item) => `<div class="ni ${item.tone} premium ${state.screen === item.id ? "on" : ""}" onclick="goScreen('${item.id}')">
      <div class="npill"></div><div class="nico">${item.ico}</div><div class="nlbl">${item.lbl}</div>
    </div>`).join("");
}

function permissionCard() {
  return `<div class="empty au">
    <div class="eico">${icon("library", 48)}</div>
    <div class="et">Müziklere erişim izni gerekli</div>
    <div class="es">Kütüphaneni göstermek, çalmak, silmek ve ad değiştirmek için Android müzik izni gerekiyor.</div>
    <button class="pdlbtn" style="margin-top:18px;max-width:230px" onclick="requestPermission()">İzin Ver</button>
  </div>`;
}

function requestPermission() {
  const result = Native.call("requestAudioPermission");
  if (!result.ok) toast(result.error || "İzin isteği başlatılamadı.");
}

function requestNotificationPermission() {
  const result = Native.call("requestNotificationPermission");
  if (result.ok) {
    state.notificationPermission = !!result.granted;
    toast(result.granted ? "Bildirim izni hazır." : "Bildirim izni istendi.");
    renderSettings();
  } else {
    toast(result.error || "Bildirim izni başlatılamadı.");
  }
}

function requestManageStoragePermission() {
  const result = Native.call("requestManageStoragePermission");
  if (result.ok) {
    state.manageStoragePermission = !!result.granted;
    toast(result.granted ? "Dosya erişimi hazır." : "Dosya erişimi ekranı açıldı. İzni verip geri dön.");
    renderSettings();
  } else {
    toast(result.error || "Dosya erişimi başlatılamadı.");
  }
}

function refreshLibrary(showToast = true) {
  const result = Native.call("scanLibrary");
  if (result.ok) {
    updateFromNativePayload(result);
    if (showToast) toast(`${state.songs.length} şarkı hazır.`);
    renderActive();
  } else {
    toast(result.error || "Kütüphane okunamadı.");
  }
}

function renderMini() {
  const mini = $("mini");
  if (!state.current) {
    mini.style.display = "none";
    return;
  }
  const track = state.current;
  const progress = state.duration ? Math.min(100, (state.position / state.duration) * 100) : 0;
  mini.style.display = "flex";
  mini.innerHTML = `<div class="mglow"></div>
    <div class="mcover">${imgD(track.cover, 48, 48, 12)}</div>
    <div class="minfo"><div class="mtitle">${esc(track.title)}</div><div class="martist">${esc(track.artist)}</div></div>
    <div class="mctrls" onclick="event.stopPropagation()">
      <button class="mbtn mplay" onclick="togglePlay()">${state.playing ? icon("pause", 18) : icon("play", 18)}</button>
      <button class="mbtn" onclick="nextSong()">${icon("next", 18)}</button>
    </div>
    <div class="mprog"><div class="mpfill" style="width:${progress}%"></div></div>`;
}

function songCard(track, index = 0, options = {}) {
  const key = register(track);
  const isOnline = track.source === "online";
  const primary = isOnline ? `openPreview('${key}')` : `playTrack('${key}')`;
  const menu = isOnline
    ? `<button class="cbtn cdl" onclick="startDownload('${key}')" title="İndir">${icon("download", 17)}</button>`
    : `<button class="cbtn cmore" onclick="openSongActions('${key}')" title="Menü">${icon("more", 19)}</button>`;
  return `<div class="mc au" style="animation-delay:${(index * 0.035).toFixed(2)}s" onclick="${primary}">
    <div class="mcimg">${imgD(track.cover, 56, 56, 12)}</div>
    <div class="mci">
      <div class="mct">${esc(track.title)}</div>
      <div class="mca">${esc(track.artist)}</div>
      <div class="mcm">${badge(track)}<span class="dur">${esc(track.duration || fmt(track.durationMs))}</span></div>
    </div>
    <div class="mcact" onclick="event.stopPropagation()">
      <button class="cbtn cprev" onclick="${isOnline ? `openPreview('${key}')` : `playTrack('${key}')`}" title="${isOnline ? "Önizle" : "Çal"}">${icon("play", 17)}</button>
      ${menu}
    </div>
  </div>`;
}

function playTrack(key, options = {}) {
  const track = findTrack(key);
  if (!track) return;
  if (track.source === "online") {
    openPreview(key);
    return;
  }
  state.current = track;
  state.queue = currentVisibleQueue();
  state.playing = true;
  state.position = 0;
  state.duration = track.durationMs || 0;
  const result = Native.call("playSong", JSON.stringify(track));
  if (!result.ok) {
    state.playing = false;
    toast(result.error || "Şarkı başlatılamadı.");
  } else {
    bumpPlayStat(track);
    // toast(`${track.title} çalıyor`);
  }
  renderMini();
  if (!$("player").classList.contains("off") || !options.keepScreen) renderPlayer();
  if (!options.keepScreen) $("player").classList.remove("off");
}

function currentVisibleQueue() {
  if (state.screen === "library") {
    return state.libTab === "downloaded" ? state.songs.filter((song) => song.downloaded) : state.songs;
  }
  return state.songs;
}

function bumpPlayStat(track) {
  const key = keyOf(track);
  if (!key) return;
  const item = state.playStats[key] || { count: 0, lastPlayed: 0, title: track.title, artist: track.artist };
  item.count += 1;
  item.lastPlayed = Date.now();
  item.title = track.title;
  item.artist = track.artist;
  state.playStats[key] = item;
  saveJson("flowna_play_stats", state.playStats);
}

function togglePlay() {
  if (!state.current && state.songs[0]) {
    playTrack(register(state.songs[0]));
    return;
  }
  if (state.playing) {
    Native.call("pause");
    state.playing = false;
  } else {
    Native.call("resume");
    state.playing = true;
  }
  updatePlaybackChrome();
}

function nextSong(options = {}) {
  if (!state.current || !state.queue.length) return;
  const currentKey = keyOf(state.current);
  const index = state.queue.findIndex((song) => keyOf(song) === currentKey);
  const next = state.queue[(index + 1 + state.queue.length) % state.queue.length];
  playTrack(register(next), options);
}

function playNextAfterCompletion(completedTrack) {
  const completed = normalizeSongs([completedTrack || state.current])[0] || state.current;
  if (!completed || completed.source === "online" || state.preview || state.queue.length < 2) return false;
  const completedKey = keyOf(completed);
  const currentIndex = state.queue.findIndex((song) => keyOf(song) === completedKey);
  const nextIndex = currentIndex >= 0 ? (currentIndex + 1) % state.queue.length : 0;
  const next = state.queue[nextIndex];
  if (!next || keyOf(next) === completedKey) return false;
  playTrack(register(next), { keepScreen: true, autoAdvance: true });
  return true;
}

function prevSong(options = {}) {
  if (!state.current || !state.queue.length) return;
  const currentKey = keyOf(state.current);
  const index = state.queue.findIndex((song) => keyOf(song) === currentKey);
  const prev = state.queue[(index - 1 + state.queue.length) % state.queue.length];
  playTrack(register(prev), options);
}

function seekPlayer(event) {
  if (!state.duration) return;
  const rect = event.currentTarget.getBoundingClientRect();
  const pct = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
  state.position = Math.floor(state.duration * pct);
  Native.call("seekTo", state.position);
  updatePlaybackChrome();
}

function renderPlayer() {
  if (!state.current) return;
  const track = state.current;
  const progress = state.duration ? Math.min(100, (state.position / state.duration) * 100) : 0;
  const liked = !!state.liked[keyOf(track)];
  $("player").innerHTML = `<div class="plbg">
      <div class="plbga">${imgD(track.cover, 420, 420, 0)}</div>
      <div class="plbgov"></div>
    </div>
      <div class="plcon">
      <div class="plhdr">
        <button class="plbtn2" onclick="closePlayer()">${icon("down", 22)}</button>
        <div class="plhdrt">Şimdi Çalıyor</div>
        <button class="plbtn2" onclick="openSongActions('${register(track)}')">${icon("more", 22)}</button>
      </div>
      <div class="plaw">
        <div class="plart" onpointerdown="startPlayerDrag(event)" onpointermove="movePlayerDrag(event)" onpointerup="endPlayerDrag(event)" onpointercancel="endPlayerDrag(event)">${imgD(track.cover, 274, 274, 26)}</div>
        <div class="spec">${spectrumHtml()}</div>
      </div>
      <div class="plrow">
        <div style="flex:1;min-width:0">
          <div class="plsn">${esc(track.title)}</div>
          <div class="plar">${esc(track.artist)}${track.album ? " · " + esc(track.album) : ""}</div>
        </div>
        <button class="plfav" onclick="toggleLike('${register(track)}')">${liked ? icon("heartFill", 24) : icon("heart", 24)}</button>
      </div>
      <div class="plprog">
        <div class="plpb" onclick="seekPlayer(event)">
          <div class="plpbf" style="width:${progress}%"><div class="plpbt"></div></div>
        </div>
        <div class="pltime"><span>${fmt(state.position)}</span><span>${esc(track.duration || fmt(state.duration))}</span></div>
      </div>
      <div class="placts">
        <button class="plact" onclick="toast('Sıraya eklendi')">${icon("plus", 16)} Sıraya Ekle</button>
        <button class="plact" onclick="shareTrack('${register(track)}')">${icon("share", 16)} Paylaş</button>
      </div>
      <div class="plctrls">
        <button class="cc ccpn" onclick="prevSong()">${icon("prev", 24)}</button>
        <button class="cc" onclick="seekRelative(-10000)">−10</button>
        <button class="ccplay" onclick="togglePlay()">${state.playing ? icon("pause", 28) : icon("play", 28)}</button>
        <button class="cc" onclick="seekRelative(10000)">+10</button>
        <button class="cc ccpn" onclick="nextSong()">${icon("next", 24)}</button>
      </div>
    </div>`;
}

function spectrumHtml() {
  const bars = [12, 18, 9, 22, 16, 26, 11, 19, 14, 24, 10, 21, 17, 25, 13, 20];
  return bars.map((height, index) => `<div class="sb2 ${state.playing ? "pl" : ""}" style="height:${state.playing ? height : 4}px;--h:${height}px;--d:${(0.38 + (index % 5) * 0.08).toFixed(2)}s"></div>`).join("");
}

function seekRelative(delta) {
  state.position = Math.max(0, Math.min(state.duration || 0, state.position + delta));
  Native.call("seekTo", state.position);
  updatePlaybackChrome();
}

function closePlayer() {
  const player = $("player");
  player.style.transition = "";
  player.style.transform = "";
  player.style.opacity = "";
  player.classList.add("off");
  state.playerDrag = null;
}

function startPlayerDrag(event) {
  if (event.pointerType === "mouse" && event.button !== 0) return;
  state.playerDrag = { startY: event.clientY, dy: 0 };
  const player = $("player");
  player.style.transition = "none";
  event.currentTarget.setPointerCapture?.(event.pointerId);
}

function movePlayerDrag(event) {
  if (!state.playerDrag) return;
  const dy = Math.max(0, event.clientY - state.playerDrag.startY);
  state.playerDrag.dy = dy;
  const player = $("player");
  player.style.transform = `translateY(${dy}px)`;
  player.style.opacity = String(Math.max(0.35, 1 - dy / 360));
}

function endPlayerDrag() {
  if (!state.playerDrag) return;
  const dy = state.playerDrag.dy || 0;
  const player = $("player");
  player.style.transition = "";
  if (dy > 85) {
    closePlayer();
  } else {
    player.style.transform = "";
    player.style.opacity = "";
  }
  state.playerDrag = null;
}

function toggleLike(key) {
  const track = findTrack(key);
  if (!track) return;
  const id = keyOf(track);
  if (state.liked[id]) delete state.liked[id];
  else state.liked[id] = { title: track.title, artist: track.artist, at: Date.now() };
  saveJson("flowna_likes", state.liked);
  renderPlayer();
}

function shareTrack(key) {
  const track = findTrack(key);
  if (!track) return;
  const text = `${track.title} - ${track.artist}`;
  if (navigator.share) navigator.share({ title: "Flowna", text }).catch(() => {});
  else {
    navigator.clipboard?.writeText(text);
    toast("Şarkı bilgisi kopyalandı.");
  }
}

function openSongActions(key) {
  const track = findTrack(key);
  if (!track) return;
  state.actionSheet = { mode: "menu", key };
  renderActionSheet();
  $("act").classList.remove("off");
}

function closeActionSheet() {
  $("act").classList.add("off");
  state.actionSheet = null;
}

function renderActionSheet() {
  const sheet = state.actionSheet;
  if (!sheet) return;
  const track = findTrack(sheet.key);
  if (!track) return closeActionSheet();
  const canEdit = !!track.uri;
  let content = "";
  if (sheet.mode === "rename") {
    content = `<div class="asheet" onclick="event.stopPropagation()">
      <div class="ashandle"></div>
      <div class="ashead">${imgD(track.cover, 48, 48, 12)}<div class="astxt"><div class="ast">Ad Değiştir</div><div class="asa">${esc(track.artist)}</div></div></div>
      <input id="renameInput" class="ainput" value="${esc(track.title)}" placeholder="Yeni şarkı adı">
      <div class="asplit">
        <button class="pdlbtn" style="background:var(--t3)" onclick="closeActionSheet()">Vazgeç</button>
        <button class="pdlbtn" onclick="confirmRename('${sheet.key}')">${icon("check", 18)} Kaydet</button>
      </div>
    </div>`;
  } else if (sheet.mode === "delete") {
    content = `<div class="asheet" onclick="event.stopPropagation()">
      <div class="ashandle"></div>
      <div class="ashead">${imgD(track.cover, 48, 48, 12)}<div class="astxt"><div class="ast">Şarkı Silinsin mi?</div><div class="asa">${esc(track.title)}</div></div></div>
      <button class="arow danger" onclick="confirmDelete('${sheet.key}')">${icon("trash", 20)} Evet, Sil</button>
      <button class="arow" onclick="closeActionSheet()">${icon("close", 20)} Vazgeç</button>
    </div>`;
  } else {
    content = `<div class="asheet" onclick="event.stopPropagation()">
      <div class="ashandle"></div>
      <div class="ashead">${imgD(track.cover, 48, 48, 12)}<div class="astxt"><div class="ast">${esc(track.title)}</div><div class="asa">${esc(track.artist)}</div></div></div>
      ${track.source === "online" ? `<button class="arow" onclick="openPreview('${sheet.key}');closeActionSheet()">${icon("play", 20)} Önizle</button><button class="arow" onclick="startDownload('${sheet.key}');closeActionSheet()">${icon("download", 20)} İndir</button>` : `<button class="arow" onclick="playTrack('${sheet.key}');closeActionSheet()">${icon("play", 20)} Çal</button>`}
      ${canEdit ? `<button class="arow" onclick="showRenameSheet('${sheet.key}')">${icon("edit", 20)} Ad Değiştir</button><button class="arow danger" onclick="showDeleteSheet('${sheet.key}')">${icon("trash", 20)} Sil</button>` : ""}
      <button class="arow" onclick="shareTrack('${sheet.key}');closeActionSheet()">${icon("share", 20)} Paylaş</button>
    </div>`;
  }
  $("act").innerHTML = content;
  // Apply premium styling to action sheet icons
  $("act").classList.add("premium");
  if (sheet.mode === "rename") {
    setTimeout(() => {
      const input = $("renameInput");
      input?.focus();
      input?.setSelectionRange(0, input.value.length);
    }, 80);
  }
}

function showRenameSheet(key) {
  state.actionSheet = { mode: "rename", key };
  renderActionSheet();
}

function showDeleteSheet(key) {
  state.actionSheet = { mode: "delete", key };
  renderActionSheet();
}

function confirmRename(key) {
  const track = findTrack(key);
  const next = $("renameInput")?.value?.trim();
  if (!track || !next || next === track.title) return closeActionSheet();
  closeActionSheet();
  renameTrack(key, next);
}

function confirmDelete(key) {
  closeActionSheet();
  deleteTrack(key);
}

function renameTrack(key, nextName) {
  const track = findTrack(key);
  if (!track?.uri) return toast("Bu parça için ad değiştirme kullanılamıyor.");
  const next = (nextName || "").trim();
  if (!next || next === track.title) return;
  const result = Native.call("renameSong", track.uri, next);
  if (result.ok) {
    if (result.pendingPermission) {
      state.pendingFileActions[track.uri] = { action: "rename", title: next };
      toast(result.manageStorageRequired ? "Dosya erişimi ekranı açıldı. İzni verip geri dön." : "Android düzenleme onayı açıldı.");
    } else {
      updateFromNativePayload(result);
      toast("Şarkı adı güncellendi.");
      renderActive();
    }
  } else {
    toast(result.error || "Ad değiştirilemedi.");
  }
}

function renderSearch() {
  const el = $("scr-search");
  const hadFocus = document.activeElement?.id === "sinp";
  const cursor = $("sinp")?.selectionStart ?? state.search.query.length;
  const q = state.search.query.trim();
  el.innerHTML = `<div class="shdr">
      <div class="stitle">Ara</div>
      <div class="ssub">Şarkı, sanatçı veya albüm keşfet.</div>
    </div>
    <div class="scroll">
      <div class="swrap">
        <div class="sico">${icon("search", 19)}</div>
        <input class="sinp" id="sinp" placeholder="Şarkı, sanatçı veya albüm ara..." value="${esc(state.search.query)}" oninput="onSearchInput(this.value)" onkeydown="if(event.key==='Enter')runSearch()" autocomplete="off">
        <div id="sclr" class="sclr" style="display:${state.search.query ? "flex" : "none"}" onclick="clearSearch()">×</div>
      </div>
      <div id="sbody">${searchBodyHtml()}</div>
    </div>`;
  const input = $("sinp");
  if (hadFocus) {
    input?.focus();
    input?.setSelectionRange(cursor, cursor);
  }
}

function searchBodyHtml() {
  const q = state.search.query.trim();
  if (!q) return renderSearchHome();
  const suggestions = renderSearchSuggestions(q);
  if (state.search.loading) return `${suggestions}${loadingBlock(`"${esc(q)}" aranıyor`)}`;
  if (state.search.error) {
    return `${suggestions}${emptyBlock("Arama tamamlanamadı", state.search.error, icon("settings", 48), `<button class="pdlbtn" style="margin-top:16px" onclick="runSearch()">Tekrar Dene</button>`)}`;
  }
  return renderSearchResults();
}

function renderSearchSuggestions(query) {
  const q = String(query || "").trim().toLowerCase();
  if (q.length < 1) return "";

  const youtube = state.search.suggestions.length
    ? `<div class="stlbl">YouTube önerileri</div><div class="chips">${state.search.suggestions.map((item) => `<div class="chip" onclick="setSearch('${esc(item)}')">${esc(item)}</div>`).join("")}</div>`
    : (state.search.suggestionsLoading ? `<div class="stlbl">YouTube önerileri</div><div class="chips"><div class="chip">Öneriler alınıyor...</div></div>` : "");

  const recentMatches = state.recent
    .filter((x) => String(x || "").toLowerCase().startsWith(q))
    .slice(0, 6);

  const localMatches = state.songs
    .filter((s) => {
      const t = String(s.title || "").toLowerCase();
      const a = String(s.artist || "").toLowerCase();
      return t.startsWith(q) || a.startsWith(q);
    })
    .slice(0, 6);

  if (!youtube && !recentMatches.length && !localMatches.length) return "";

  const chips = recentMatches.length
    ? `<div class="stlbl">Tahminler</div><div class="chips">${recentMatches.map((item) => `<div class="chip" onclick="setSearch('${esc(item)}')">${esc(item)}</div>`).join("")}</div>`
    : "";

  const locals = localMatches.length
    ? `<div class="stlbl">Yerel eşleşmeler</div>${localMatches.map((song, i) => songCard(song, i)).join("")}`
    : "";

  return `${youtube}${chips}${locals}`;
}

function updateSearchBody() {
  const body = $("sbody");
  if (body) body.innerHTML = searchBodyHtml();
  else renderSearch();
}

function updateSearchClear() {
  const clear = $("sclr");
  if (clear) clear.style.display = state.search.query ? "flex" : "none";
}

function renderSearchHome() {
  const recent = state.recent.length
    ? `<div class="stlbl">Son aramalar</div><div class="chips">${state.recent.map((item) => `<div class="chip" onclick="setSearch('${esc(item)}')">${esc(item)}</div>`).join("")}</div>`
    : emptyBlock("Henüz arama yok", "Arama yaptıkça burada kullanıcı aramaları görünecek.", icon("search", 48));
  const quick = state.songs.length
    ? `<div class="stlbl">Kütüphanenden hızlı başlat</div>${state.songs.slice(0, 6).map(songCard).join("")}`
    : `<div class="stlbl">Çevrim içi arama</div>${emptyBlock("Aramaya başla", "YouTube sonuçları ve önizleme için yukarıya bir şarkı adı yaz.", icon("play", 48))}`;
  return `${recent}${quick}`;
}

function renderSearchResults() {
  if (!state.search.results.length) {
    return emptyBlock("Sonuç bulunamadı", "Farklı bir şarkı, sanatçı veya albüm adı deneyebilirsin.", "∅");
  }
  return `<div class="stlbl">${state.search.results.length} sonuç bulundu</div>${state.search.results.map(songCard).join("")}
    <div style="height:24px"></div>`;
}

function onSearchInput(value) {
  state.search.query = value;
  state.search.error = "";
  clearTimeout(state.searchTimer);
  queueSearchSuggestions(value);
  if (value.trim().length > 1) {
    state.search.loading = true;
    state.searchTimer = setTimeout(runSearch, 450);
  } else {
    state.search.loading = false;
    state.search.results = [];
  }
  updateSearchClear();
  updateSearchBody();
}

function setSearch(value) {
  state.search.query = value;
  const input = $("sinp");
  if (input) input.value = value;
  updateSearchClear();
  runSearch();
}

function clearSearch() {
  clearTimeout(state.suggestTimer);
  state.search = { query: "", loading: false, results: [], error: "", callbackId: "", suggestions: [], suggestionsLoading: false, suggestionsError: "", suggestionsCallbackId: "" };
  const input = $("sinp");
  if (input) {
    input.value = "";
    input.focus();
  }
  updateSearchClear();
  updateSearchBody();
}

function queueSearchSuggestions(value) {
  clearTimeout(state.suggestTimer);
  const q = String(value || "").trim();
  state.search.suggestionsError = "";
  if (q.length < 3) {
    state.search.suggestions = [];
    state.search.suggestionsLoading = false;
    state.search.suggestionsCallbackId = "";
    return;
  }
  state.search.suggestionsLoading = true;
  state.search.suggestionsCallbackId = "suggest_" + Date.now();
  const callbackId = state.search.suggestionsCallbackId;
  state.suggestTimer = setTimeout(() => {
    const result = Native.call("searchSuggestions", q, callbackId);
    if (!result.ok && state.search.suggestionsCallbackId === callbackId) {
      state.search.suggestionsLoading = false;
      state.search.suggestionsError = result.error || "Öneriler alınamadı.";
      updateSearchBody();
    }
  }, 280);
}

function runSearch() {
  const q = state.search.query.trim();
  if (q.length < 2) return;
  clearTimeout(state.searchTimer);
  clearTimeout(state.searchWatchdog);
  rememberSearch(q);
  state.search.loading = true;
  state.search.error = "";
  state.search.callbackId = "search_" + Date.now();
  const callbackId = state.search.callbackId;
  updateSearchClear();
  updateSearchBody();
  state.searchWatchdog = setTimeout(() => {
    if (state.search.callbackId !== callbackId || !state.search.loading) return;
    state.search.loading = false;
    state.search.error = "Arama beklenenden uzun sürdü. İnternet bağlantını kontrol edip tekrar dene.";
    updateSearchBody();
  }, 14000);
  const result = Native.call("search", q, callbackId);
  if (!result.ok) {
    clearTimeout(state.searchWatchdog);
    state.search.loading = false;
    state.search.error = result.error || "Android içinde çalışınca arama aktif olur.";
    updateSearchBody();
  }
}

function rememberSearch(query) {
  state.recent = [query, ...state.recent.filter((item) => item.toLowerCase() !== query.toLowerCase())].slice(0, 8);
  saveJson("flowna_recent_searches", state.recent);
}

function renderLibrary() {
  const el = $("scr-library");
  if (!state.permissionGranted) {
    el.innerHTML = `<div class="shdr"><div class="stitle">Kütüphane</div><div class="ssub">Cihazdaki müzikler</div></div><div class="scroll">${permissionCard()}</div>`;
    return;
  }
  const downloadedCount = state.songs.filter((song) => song.downloaded).length;
  const list = state.libTab === "downloaded" ? state.songs.filter((song) => song.downloaded) : state.songs;
  el.innerHTML = `<div class="shdr">
      <div class="stitle">Kütüphane</div>
      <div class="ssub">${state.songs.length} şarkı hazır</div>
    </div>
    <div class="scroll">
      <div class="sumc au">
        <div class="sumlbl">GENEL BAKIŞ</div>
        <div class="sumst">
          <div><div class="sv">${state.songs.length}</div><div class="sl">Tüm Şarkılar</div></div>
          <div><div class="sv">${downloadedCount}</div><div class="sl">İndirilenler</div></div>
          <div><div class="sv">${Object.keys(state.playStats).length}</div><div class="sl">Dinleme</div></div>
        </div>
        <div class="su2">Son tarama: ${new Date().toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" })}</div>
      </div>
      <div class="tabs">
        <button class="tbtn ${state.libTab === "all" ? "on" : ""}" onclick="setLibTab('all')">Tüm Şarkılar</button>
        <button class="tbtn ${state.libTab === "downloaded" ? "on" : ""}" onclick="setLibTab('downloaded')">İndirilenler</button>
      </div>
      ${state.libTab === "all" ? renderRecommendations() : ""}
      <div class="stlbl">Liste</div>
      ${list.length ? list.map(songCard).join("") : emptyBlock("Bu bölüm boş", "Yeni indirdiğin müzikler burada görünecek.", icon("download", 48))}
    </div>`;
}

function renderRecommendations() {
  const recommended = recommendedSongs();
  const most = mostPlayedSongs();
  requestSimilarSongs(false);
  return `<div class="stlbl">Senin için önerilenler</div>
    ${recommended.length ? `<div class="hs" style="margin-bottom:22px">${recommended.map(featureCard).join("")}</div>` : emptyBlock("Dinledikçe iyileşir", "Birkaç şarkı çaldığında öneriler gerçek dinleme alışkanlığına göre oluşacak.", "✦")}
    ${renderSimilarSongs()}
    <div class="stlbl">En çok dinlenenler</div>
    ${most.length ? most.map((song, i) => songCard(song, i)).join("") : emptyBlock("Henüz dinleme kaydı yok", "Çaldığın şarkılar burada sıralanacak.", "♪")}`;
}

function featureCard(track) {
  const key = register(track);
  return `<div class="fc" onclick="playTrack('${key}')">
    <div class="fcov">${imgD(track.cover, 138, 138, 18)}</div>
    <div class="ft">${esc(track.title)}</div>
    <div class="fa">${esc(track.artist)}</div>
  </div>`;
}

function recommendedSongs() {
  const stats = Object.values(state.playStats).sort((a, b) => b.lastPlayed - a.lastPlayed);
  if (!state.songs.length) return [];
  if (!stats.length) return state.songs.slice(0, Math.min(5, state.songs.length));
  const favoriteArtists = new Set(stats.slice(0, 5).map((item) => item.artist));
  return state.songs
    .filter((song) => favoriteArtists.has(song.artist))
    .concat(state.songs)
    .filter((song, index, arr) => arr.findIndex((other) => keyOf(other) === keyOf(song)) === index)
    .slice(0, 6);
}

function mostPlayedSongs() {
  return state.songs
    .map((song) => ({ song, stat: state.playStats[keyOf(song)] }))
    .filter((item) => item.stat)
    .sort((a, b) => b.stat.count - a.stat.count || b.stat.lastPlayed - a.stat.lastPlayed)
    .map((item) => item.song)
    .slice(0, 8);
}

function similarSeedTrack() {
  if (state.current) return state.current;
  const recentStat = Object.values(state.playStats)
    .sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0))[0];
  if (recentStat) {
    const fromLibrary = state.songs.find((song) => {
      const titleMatch = String(song.title || "").toLowerCase() === String(recentStat.title || "").toLowerCase();
      const artistMatch = String(song.artist || "").toLowerCase() === String(recentStat.artist || "").toLowerCase();
      return titleMatch && artistMatch;
    });
    if (fromLibrary) return fromLibrary;
    return { title: recentStat.title || "", artist: recentStat.artist || "" };
  }
  return state.songs[0] || null;
}

function similarQueryFor(track) {
  const base = [track?.artist, track?.title].filter(Boolean).join(" ").trim();
  return base ? `${base} similar songs` : "";
}

function requestSimilarSongs(force = false) {
  if (!Native.available) return;
  const seed = similarSeedTrack();
  const query = similarQueryFor(seed);
  const seedKey = keyOf(seed) || coverMetaKey(seed?.title, seed?.artist);
  if (!query || !seedKey) return;
  const fresh = state.similar.seedKey === seedKey
    && state.similar.results.length
    && Date.now() - state.similar.fetchedAt < 30 * 60 * 1000;
  if (!force && (state.similar.loading || fresh)) return;

  state.similar.seedKey = seedKey;
  state.similar.query = query;
  state.similar.loading = true;
  state.similar.error = "";
  state.similar.callbackId = "similar_" + Date.now();
  const result = Native.call("search", query, state.similar.callbackId);
  if (!result.ok) {
    state.similar.loading = false;
    state.similar.error = result.error || "Benzer şarkılar alınamadı.";
  }
}

function renderSimilarSongs() {
  const seed = similarSeedTrack();
  const seedText = [seed?.artist, seed?.title].filter(Boolean).join(" - ");
  const refresh = `<button class="sb" onclick="refreshSimilarSongs()" style="margin-left:auto">Yenile</button>`;
  if (state.similar.loading && !state.similar.results.length) {
    return `<div class="stlbl" style="display:flex;align-items:center;gap:10px">Benzer şarkılar ${refresh}</div>${loadingBlock("Benzer şarkılar aranıyor")}`;
  }
  if (state.similar.error && !state.similar.results.length) {
    return `<div class="stlbl" style="display:flex;align-items:center;gap:10px">Benzer şarkılar ${refresh}</div>${emptyBlock("Benzer şarkılar alınamadı", state.similar.error, icon("search", 48))}`;
  }
  if (!state.similar.results.length) {
    return `<div class="stlbl" style="display:flex;align-items:center;gap:10px">Benzer şarkılar ${refresh}</div>${emptyBlock("Dinledikçe önerir", "Bir şarkı çaldığında YouTube'dan benzer müzikler burada görünecek.", icon("search", 48))}`;
  }
  const subtitle = seedText ? `<div style="padding:0 24px 10px;font-size:12px;color:var(--t2)">Kaynak: ${esc(seedText)}</div>` : "";
  return `<div class="stlbl" style="display:flex;align-items:center;gap:10px">Benzer şarkılar ${refresh}</div>
    ${subtitle}
    <div class="hs" style="margin-bottom:22px">${state.similar.results.slice(0, 8).map(featureCard).join("")}</div>`;
}

function refreshSimilarSongs() {
  state.similar.results = [];
  requestSimilarSongs(true);
  renderLibrary();
  toast("Benzer şarkılar yenileniyor.");
}

function setLibTab(tab) {
  state.libTab = tab;
  renderLibrary();
}

function renderDownloads() {
  const el = $("scr-downloads");
  const prevScroll = el?.querySelector?.(".scroll");
  const prevScrollTop = prevScroll ? prevScroll.scrollTop : 0;
  const active = state.downloads.filter((item) => item.status === "active");
  const completed = state.downloads.filter((item) => item.status === "completed");
  const failed = state.failedDownloads;
  const list = state.dlTab === "active" ? active : state.dlTab === "done" ? completed : failed;
  const clearBtnHtml = (state.dlTab === "fail" && failed.length)
    ? `<div style="padding: 0 24px 12px; display: flex; justify-content: flex-end;">
        <button class="dlb dlcancel" onclick="clearFailedDownloads()" style="margin: 0; padding: 6px 14px; font-size: 12px; font-weight: 700; display: flex; align-items: center; gap: 4px;">
          Başarısızları Temizle
        </button>
       </div>`
    : "";
  el.innerHTML = `<div class="shdr">
      <div class="stitle">İndirmeler</div>
      <div class="ssub">${active.length} aktif · ${completed.length} tamamlandı · ${failed.length} başarısız</div>
    </div>
    <div class="scroll">
      <div class="tabs">
        <button class="tbtn ${state.dlTab === "active" ? "on" : ""}" onclick="setDlTab('active')">Aktif</button>
        <button class="tbtn ${state.dlTab === "done" ? "on" : ""}" onclick="setDlTab('done')">Tamamlandı</button>
        <button class="tbtn ${state.dlTab === "fail" ? "on" : ""}" onclick="setDlTab('fail')">Başarısız</button>
      </div>
      ${clearBtnHtml}
      ${list.length ? list.map(downloadCard).join("") : emptyBlock(state.dlTab === "active" ? "Aktif indirme yok" : "Bu bölüm boş", "Arama ekranından bir şarkı indirerek listeyi doldurabilirsin.", icon("download", 48))}
    </div>`;
  const nextScroll = el?.querySelector?.(".scroll");
  if (nextScroll && prevScrollTop) nextScroll.scrollTop = prevScrollTop;
}

function clearFailedDownloads() {
  state.failedDownloads = [];
  saveJson("flowna_failed_downloads", state.failedDownloads);
  renderDownloads();
  toast("Başarısız indirmeler temizlendi.");
}

function downloadCard(item, index) {
  const track = item.track || {};
  if (item.status === "failed") {
    const key = register(track);
    return `<div class="dlc dlfc" data-dlid="${esc(item.id)}" style="animation-delay:${index * 0.04}s">
      <div class="dlct">${imgD(track.cover, 48, 48, 12)}<div class="dli"><div class="dlti">${esc(track.title || "İndirme")}</div><div class="dlar">${esc(track.artist || "")}</div></div></div>
      <div class="errb">${esc(item.error || "İndirme başarısız oldu.")}</div>
      ${track.videoUrl ? `<button class="dlb dlretry" onclick="startDownload('${key}')">Tekrar Dene</button>` : ""}
    </div>`;
  }
  return `<div class="dlc" data-dlid="${esc(item.id)}" style="animation-delay:${index * 0.04}s">
    <div class="dlct">${imgD(track.cover, 48, 48, 12)}<div class="dli"><div class="dlti">${esc(track.title || "İndirme")}</div><div class="dlar">${esc(track.artist || "")}</div></div></div>
    <div class="dlm"><span data-role="dlmsg">${esc(item.message || "")}</span><span data-role="dlpct">${Math.round(item.progress || 0)}%</span></div>
    <div class="dltr"><div class="dlf" data-role="dlbar" style="width:${Math.round(item.progress || 0)}%"></div></div>
  </div>`;
}

function setDlTab(tab) {
  state.dlTab = tab;
  renderDownloads();
}

function isDownloading(sourceKey) {
  return state.downloads.some((item) => item.status === "active" && (item.sourceKey === sourceKey || keyOf(item.track) === sourceKey));
}

function startDownload(key) {
  try {
    const track = findTrack(key);
    if (!track) return;
    if (!track.videoUrl && track.source === "online") {
      toast("Bu sonuç için indirme bağlantısı bulunamadı.");
      return;
    }
    const sourceKey = keyOf(track);
    if (isDownloading(sourceKey)) {
      toast("Bu şarkı zaten indiriliyor.");
      state.dlTab = "active";
      if (state.screen !== "downloads") goScreen("downloads");
      else renderDownloads();
      return;
    }
    const callbackId = "dl_" + Date.now() + "_" + Math.floor(Math.random() * 1000000);
    upsertDownload({ id: callbackId, sourceKey, status: "active", progress: 0, message: "Sıraya alındı", track });
    state.dlTab = "active";
    if (state.screen !== "downloads") goScreen("downloads");
    else renderDownloads();
    const result = Native.call("startDownload", JSON.stringify(track), callbackId);
    if (!result || !result.ok) {
      state.downloads = state.downloads.filter((dl) => dl.id !== callbackId);
      upsertFailed({ id: callbackId, sourceKey, status: "failed", progress: 0, message: "Başarısız", error: (result && result.error) || "Yerel köprü hatası", track });
      renderDownloads();
    } else {
      toast("İndirme başlatıldı.");
    }
  } catch (error) {
    console.error("startDownload error:", error);
    toast("İndirme başlatılırken bir hata oluştu: " + error.message);
  }
}

function renderSettings() {
  const el = $("scr-settings");
  const sw = (key) => `<div class="tog ${state.settings[key] ? "on" : ""}" onclick="toggleSetting('${key}')"><div class="togth"></div></div>`;
  const quality = ["128K", "192K", "320K"].map((q) => `<button class="sb ${state.settings.quality === q ? "on" : ""}" onclick="setQuality('${q}')">${q}</button>`).join("");
  el.innerHTML = `<div class="shdr"><div class="stitle">Ayarlar</div><div class="ssub">İndirme, oynatma ve uygulama tercihleri.</div></div>
    <div class="scroll">
      <div class="aicard au">
        <div class="ailogo"><img src="flowna-logo.png" alt="Flowna logo"></div>
        <div class="ainame">Flowna Music Player</div>
        <div class="aiver">Arayüz ${esc(state.webVersion)}${state.liveUpdateActive ? " · Canlı" : " · Paketli"}</div>
        <div class="aiver">Sürüm ${esc(state.versionName)} · Kod ${esc(state.versionCode)}</div>
        <div class="vbs"><span class="vb vok">Güncel</span><span class="vb vgray">${state.songs.length} şarkı</span></div>
      </div>
      ${state.manageStoragePermission ? "" : `<div class="ss" style="margin-top:14px"><div class="sg"><div class="sr" onclick="requestManageStoragePermission()"><div class="srico" style="background:rgba(255,107,74,.12)">${icon("download", 17)}</div><div class="sri"><div class="srt">Tek Seferlik Dosya Erişimi</div><div class="srs">Sürekli silme onayı çıkmaması için bu izni bir kez ver.</div></div><div class="srr">›</div></div></div></div>`}
      <div class="ss"><div class="ssl">Ses ve İndirme</div><div class="sg">
        <div class="sr"><div class="srico" style="background:rgba(255,107,74,.1)">${icon("library", 17)}</div><div class="sri"><div class="srt">Ses Kalitesi</div><div class="srs">İndirme sırasında kullanılacak kalite</div></div><div class="seg">${quality}</div></div>
        <div class="sr"><div class="srico" style="background:rgba(245,158,11,.1)">${icon("settings", 17)}</div><div class="sri"><div class="srt">Yalnızca Wi‑Fi</div><div class="srs">Mobil veride indirmeyi kapatır</div></div>${sw("wifiOnly")}</div>
        <div class="sr"><div class="srico" style="background:rgba(0,200,150,.1)">${icon("download", 17)}</div><div class="sri"><div class="srt">Arka Planda İndir</div><div class="srs">İndirmeleri uygulama açıkken yönetir</div></div>${sw("bgDl")}</div>
        <div class="sr"><div class="srico" style="background:rgba(79,70,229,.1)">${icon("plus", 17)}</div><div class="sri"><div class="srt">İndirme Bildirimi</div><div class="srs">Tamamlandığında bilgi göster</div></div>${sw("notifyDownloads")}</div>
      </div></div>
      <div class="ss" style="margin-top:14px"><div class="ssl">Güncellemeler</div><div class="sg">
        <div class="sr"><div class="srico" style="background:rgba(79,70,229,.1)">${icon("settings", 17)}</div><div class="sri"><div class="srt">Otomatik Kontrol</div><div class="srs">Açılışta sürüm bilgisi kontrol edilir</div></div>${sw("autoUp")}</div>
        <div class="sr" onclick="checkYtDlpUpdate()"><div class="srico" style="background:rgba(0,200,150,.1)">${icon("download", 17)}</div><div class="sri"><div class="srt">yt-dlp’yi Güncelle</div><div class="srs">İndirme motorunu günceller</div></div><div class="srr">›</div></div>
        <div class="sr" onclick="checkUpdate()"><div class="srico" style="background:rgba(255,107,74,.1)">${icon("search", 17)}</div><div class="sri"><div class="srt">Uygulama Güncellemesi</div><div class="srs">Native değişiklik varsa APK indirir</div></div><div class="srr">›</div></div>
        <div class="sr" onclick="applyLiveUpdate()"><div class="srico" style="background:rgba(138,92,255,.1)">${icon("download", 17)}</div><div class="sri"><div class="srt">Arayüz Güncellemesi</div><div class="srs">APK kurmadan canlı UI paketini indirir</div></div><div class="srr">›</div></div>
        ${state.liveUpdateActive ? `<div class="sr" onclick="resetLiveUpdate()"><div class="srico" style="background:rgba(239,68,68,.1)">${icon("close", 17)}</div><div class="sri"><div class="srt">Paketli Arayüze Dön</div><div class="srs">Canlı UI paketini sıfırlar</div></div><div class="srr">›</div></div>` : ""}
      </div></div>
      <div class="ss" style="margin-top:14px"><div class="ssl">Kütüphane</div><div class="sg">
        <div class="sr" onclick="refreshLibrary(true)"><div class="srico" style="background:rgba(0,200,150,.1)">${icon("library", 17)}</div><div class="sri"><div class="srt">Kütüphaneyi Yeniden Tara</div><div class="srs">Cihazdaki müzikleri günceller</div></div><div class="srr">›</div></div>
        <div class="sr" onclick="clearCache()"><div class="srico" style="background:rgba(239,68,68,.1)">${icon("close", 17)}</div><div class="sri"><div class="srt">Önbelleği Temizle</div><div class="srs">Geçici önizleme ve indirme kalıntılarını siler</div></div><div class="srr">›</div></div>
        <div class="sr" onclick="requestManageStoragePermission()"><div class="srico" style="background:rgba(0,200,150,.1)">${icon("download", 17)}</div><div class="sri"><div class="srt">Dosya Erişimi</div><div class="srs">${state.manageStoragePermission ? "Silme ve ad değiştirme için tam erişim verildi" : "Silme/ad değiştirme için bir kez izin ver"}</div></div><div class="srr">${state.manageStoragePermission ? "OK" : "›"}</div></div>
      </div></div>
      <div class="ss" style="margin-top:14px"><div class="ssl">Tanılama</div><div class="sg">
        ${diagRow("Müzik izni", state.permissionGranted ? "Verildi" : "Bekliyor", state.permissionGranted)}
        <div class="sr" onclick="requestNotificationPermission()"><div class="srico" style="background:rgba(245,158,11,.1)">${icon("settings", 17)}</div><div class="sri"><div class="srt">Bildirim izni</div><div class="srs">${state.notificationPermission ? "Verildi" : "Kilit ekranı ve bildirim kontrolleri için gerekli"}</div></div><div class="srr">${state.notificationPermission ? "OK" : "›"}</div></div>
        ${diagRow("Dosya erişimi", state.manageStoragePermission ? "Tam erişim" : "Tek seferlik izin gerekli", state.manageStoragePermission)}
        ${diagRow("Yerel şarkı", `${state.songs.length} kayıt`, true)}
        ${diagRow("Aktif indirme", `${state.downloads.filter((d) => d.status === "active").length} işlem`, true)}
        ${diagRow("Son hata", state.failedDownloads[0]?.error || "Kayıt yok", !state.failedDownloads.length)}
      </div></div>
      <div style="height:20px"></div>
    </div>`;
}

function diagRow(title, subtitle, ok) {
  return `<div class="dr"><div class="dot ${ok ? "dok" : "dwn"}"></div><div style="flex:1"><div style="font-size:13px;font-weight:500;color:var(--t1)">${esc(title)}</div><div style="font-size:11px;color:var(--t2)">${esc(subtitle)}</div></div><div style="font-size:12px;color:${ok ? "var(--g)" : "#F59E0B"};font-weight:700">${ok ? "OK" : "Dikkat"}</div></div>`;
}

function setQuality(q) {
  state.settings.quality = q;
  saveSettings();
  renderSettings();
}

function toggleSetting(key) {
  state.settings[key] = !state.settings[key];
  saveSettings();
  renderSettings();
}

function saveSettings(show = true) {
  saveJson("flowna_settings", state.settings);
  Native.call("saveSettings", JSON.stringify(state.settings));
  if (show) toast("Ayar kaydedildi.");
}

function checkUpdate() {
  const result = Native.call("checkUpdate");
  if (!result.ok) toast(result.error || "Güncelleme kontrol edilemedi.");
  else toast(result.message || result.status || "Güncelleme kontrol edildi.");
}

function applyLiveUpdate() {
  const check = Native.call("checkLiveUpdate");
  if (!check.ok) {
    toast(check.error || "Arayüz güncellemesi kontrol edilemedi.");
    return;
  }
  if (!check.updateAvailable) {
    toast(check.message || "Arayüz güncel.");
    return;
  }
  toast("Arayüz güncellemesi indiriliyor...");
  const result = Native.call("applyLiveUpdate");
  if (!result.ok) {
    toast(result.error || "Arayüz güncellemesi uygulanamadı.");
    return;
  }
  updateFromNativePayload(result);
  toast(result.message || "Arayüz güncellendi.");
}

function resetLiveUpdate() {
  const result = Native.call("resetLiveUpdate");
  if (!result.ok) {
    toast(result.error || "Arayüz sıfırlanamadı.");
    return;
  }
  updateFromNativePayload(result);
  toast(result.message || "Paketli arayüze dönüldü.");
}

function checkYtDlpUpdate() {
  const result = Native.call("checkYtDlpUpdateNow");
  if (!result.ok) toast(result.error || "yt-dlp güncellemesi başlatılamadı.");
  else toast(result.message || "yt-dlp güncellemesi başlatıldı.");
}

function clearCache() {
  const result = Native.call("clearCache");
  if (result.ok) toast(`Önbellek temizlendi (${Math.round((result.deletedBytes || 0) / 1024 / 1024)} MB).`);
  else toast(result.error || "Önbellek temizlenemedi.");
}

function openPreview(key) {
  try {
    const track = findTrack(key);
    if (!track) return;
    state.preview = { track, status: "loading", progress: 0, playing: true };
    renderPreview();
    $("pov").classList.remove("off");
    if (track.source === "online") {
      const callbackId = "prev_" + Date.now();
      state.preview.callbackId = callbackId;
      const result = Native.call("startPreview", JSON.stringify(track), callbackId);
      if (result && !result.ok) {
        closePrev();
        toast(result.error || "Önizleme başlatılamadı.");
      }
    } else {
      playTrack(key);
    }
  } catch (error) {
    console.error("openPreview error:", error);
    closePrev();
    toast("Önizleme başlatılırken bir hata oluştu: " + error.message);
  }
}

function closePrev() {
  $("pov").classList.add("off");
  state.preview = null;
}

function renderPreview() {
  const preview = state.preview;
  if (!preview) return;
  const track = preview.track;
  $("pov").innerHTML = `<div class="psheet" onclick="event.stopPropagation()">
    <div class="phdl"></div>
    <div class="pcov" style="width:116px;height:116px;margin:0 auto 16px">${imgD(track.cover, 116, 116, 18)}</div>
    <div class="pt">${esc(track.title)}</div>
    <div class="par">${esc(track.artist)}</div>
    <div class="pbarw">
      <div class="pbar"><div class="pbf" style="width:${preview.status === "loading" ? 12 : 45}%"><div class="pbth"></div></div></div>
      <div class="ptm"><span>${preview.status === "loading" ? "Hazırlanıyor" : "Önizleme"}</span><span>${esc(track.duration || "")}</span></div>
    </div>
    <div class="pctrls2">
      <button class="ppbtn" onclick="togglePlay()">${state.playing ? icon("pause", 22) : icon("play", 22)}</button>
      <button class="pdlbtn" onclick="startDownload('${register(track)}');closePrev()">${icon("download", 18)} İndir</button>
    </div>
    <button class="pclbtn" onclick="closePrev()">Kapat</button>
  </div>`;
}

function loadingBlock(text) {
  return `<div class="empty" style="padding:40px 32px"><div style="font-size:26px;margin-bottom:14px"><span class="sldot"></span><span class="sldot"></span><span class="sldot"></span></div><div class="et">${text}</div></div>`;
}

function emptyBlock(title, subtitle, icon = "♪", action = "") {
  return `<div class="empty au"><div class="eico">${icon}</div><div class="et">${esc(title)}</div><div class="es">${esc(subtitle || "")}</div>${action}</div>`;
}

function legacyHandleNativeEvent(event) {
  if (event.type === "permissionChanged") {
    state.permissionGranted = !!event.granted;
    updateFromNativePayload(event);
    renderAll();
    toast(event.granted ? "Müzik izni verildi." : "Müzik izni verilmedi.");
  }
  if (event.type === "libraryChanged") {
    updateFromNativePayload(event);
    renderActive();
  }
  if (event.type === "searchResults" && event.callbackId === state.search.callbackId) {
    clearTimeout(state.searchWatchdog);
    state.search.loading = false;
    if (event.ok) {
      state.search.results = normalizeSongs(event.results || []);
      state.search.error = "";
    } else {
      state.search.results = [];
      state.search.error = event.error || "Arama başarısız.";
    }
    updateSearchBody();
  }
  if (event.type === "previewReady") {
    if (state.preview && event.callbackId === state.preview.callbackId) {
      if (event.ok) {
        state.preview.status = "ready";
        state.preview.track = Object.assign(state.preview.track, event.track || {});
        // toast("Önizleme başladı.");
      } else {
        toast(event.error || "Önizleme başlatılamadı.");
        closePrev();
      }
      renderPreview();
    }
  }
  if (event.type === "playback") {
    if (event.track && event.track !== null) {
      state.current = normalizeSongs([event.track])[0] || state.current;
      state.duration = state.current?.durationMs || state.duration;
    }
    state.playing = event.status === "playing" || event.status === "loading";
    if (event.status === "completed") {
      if (playNextAfterCompletion(event.track)) return;
      state.playing = false;
      state.position = state.duration;
    }
    renderMini();
    if (state.current && !$("player").classList.contains("off")) renderPlayer();
  }
  if (event.type === "download") {
    const eventId = event.callbackId || event.id;
    const previous = state.downloads.find((dl) => dl.id === eventId);
    const track = normalizeSongs([event.track || {}])[0] || event.track;
    const sourceKey = previous?.sourceKey || keyOf(track);
    const item = {
      id: eventId,
      nativeId: event.id,
      sourceKey,
      status: event.status,
      progress: event.progress || 0,
      message: event.message || "",
      error: event.error || "",
      track
    };
    if (event.status === "failed") {
      state.downloads = state.downloads.filter((dl) => dl.id !== item.id);
      saveJson("flowna_downloads", state.downloads);
      upsertFailed(item);
    } else {
      upsertDownload(item);
    }
    if (event.status === "failed") toast(item.error || "İndirme başarısız oldu.");
    if (event.status === "completed") {
      toast("İndirme tamamlandı.");
      refreshLibrary(false);
    }
    if (!patchDownloadUi(item)) renderDownloads();
  }
  if (event.type === "ytDlpUpdate") {
    toast(event.message || "yt-dlp güncelleme durumu güncellendi.");
  }
  if (event.type === "error") {
    toast(event.message || "Bir hata oluştu.");
  }
}

function patchDownloadUi(item) {
  const root = $("scr-downloads");
  if (!root) return false;
  const isVisible = state.screen === "downloads";
  if (!isVisible) return false;
  const header = root.querySelector(".ssub");
  if (header) {
    const activeCount = state.downloads.filter((d) => d.status === "active").length;
    const doneCount = state.downloads.filter((d) => d.status === "completed").length;
    const failCount = state.failedDownloads.length;
    header.textContent = `${activeCount} aktif · ${doneCount} tamamlandı · ${failCount} başarısız`;
  }

  const card = root.querySelector(`.dlc[data-dlid="${cssEsc(item.id)}"]`);
  if (!card) return false;
  if (item.status === "failed") {
    const err = card.querySelector(".errb");
    if (err) err.textContent = item.error || "İndirme başarısız oldu.";
    return true;
  }
  const msg = card.querySelector("[data-role='dlmsg']");
  if (msg) msg.textContent = item.message || "";
  const pct = card.querySelector("[data-role='dlpct']");
  if (pct) pct.textContent = `${Math.round(item.progress || 0)}%`;
  const bar = card.querySelector("[data-role='dlbar']");
  if (bar) bar.style.width = `${Math.round(item.progress || 0)}%`;
  return true;
}

function cssEsc(value) {
  return String(value ?? "").replace(/["\\]/g, "\\$&");
}

function upsertDownload(item) {
  const idx = state.downloads.findIndex((dl) => dl.id === item.id);
  if (idx >= 0) {
    const next = state.downloads.slice();
    next[idx] = Object.assign({}, next[idx], item);
    state.downloads = next;
  } else {
    state.downloads = [item, ...state.downloads].slice(0, 50);
  }
  saveJson("flowna_downloads", state.downloads);
}

function upsertFailed(item) {
  state.failedDownloads = [item, ...state.failedDownloads.filter((dl) => dl.id !== item.id)].slice(0, 20);
  saveJson("flowna_failed_downloads", state.failedDownloads);
}

function updatePlaybackChrome() {
  const progress = state.duration ? Math.min(100, (state.position / state.duration) * 100) : 0;
  document.querySelectorAll(".mpfill").forEach((el) => {
    el.style.width = `${progress}%`;
  });
  document.querySelectorAll(".plpbf").forEach((el) => {
    el.style.width = `${progress}%`;
  });
  const playerTime = document.querySelector(".pltime span:first-child");
  if (playerTime) playerTime.textContent = fmt(state.position);
  document.querySelectorAll(".mplay").forEach((el) => {
    el.innerHTML = state.playing ? icon("pause", 18) : icon("play", 18);
  });
  document.querySelectorAll(".ccplay").forEach((el) => {
    el.innerHTML = state.playing ? icon("pause", 28) : icon("play", 28);
  });
}

function startPlaybackPolling() {
  clearInterval(state.pollTimer);
  state.pollTimer = setInterval(() => {
    if (!state.current) return;
    const playerVisible = !$("player").classList.contains("off");
    if (!state.playing && !playerVisible) return;
    const result = Native.call("getPlaybackState");
    if (!result.ok || !result.hasPlayer) return;
    state.playing = !!result.isPlaying;
    state.position = Number(result.position || 0);
    state.duration = Number(result.duration || state.duration || 0);
    updatePlaybackChrome();
  }, 1200);
}

function deleteTrack(key) {
  try {
    const track = findTrack(key);
    if (!track?.uri) return toast("Bu parça için silme kullanılamıyor.");
    const result = Native.call("deleteSong", track.uri);
    if (result && result.ok) {
      if (result.pendingPermission) {
        state.pendingFileActions[track.uri] = { action: "delete" };
        toast(result.manageStorageRequired ? "Dosya erişimi ekranı açıldı. İzni verip geri dön." : "Android silme onayı açıldı.");
      } else {
        removeTrackLocally(track);
        updateFromNativePayload(result);
        toast("Şarkı silindi.");
        renderAll();
      }
    } else {
      toast((result && result.error) || "Silinemedi.");
    }
  } catch (error) {
    console.error("deleteTrack error:", error);
    toast("Silme işlemi sırasında beklenmedik bir hata oluştu: " + error.message);
  }
}

function retryPendingFileActionsAfterStorageGrant() {
  const entries = Object.entries(state.pendingFileActions);
  if (!entries.length) return false;
  let changed = false;
  for (const [uri, pending] of entries) {
    const track = findTrack(uri) || { uri };
    const result = pending.action === "rename"
      ? Native.call("renameSong", uri, pending.title || "")
      : Native.call("deleteSong", uri);
    if (result.ok && !result.pendingPermission) {
      delete state.pendingFileActions[uri];
      if (pending.action === "delete") removeTrackLocally(track);
      updateFromNativePayload(result);
      if (pending.action === "delete") removeTrackLocally(track);
      changed = true;
    } else if (!result.ok) {
      delete state.pendingFileActions[uri];
      toast(result.error || "Bekleyen dosya işlemi tamamlanamadı.");
    }
  }
  if (changed) {
    toast("Bekleyen dosya işlemi tamamlandı.");
    renderAll();
  }
  return changed;
}

function handleNativeEvent(event) {
  if (event.type === "permissionChanged") {
    state.permissionGranted = !!event.granted;
    updateFromNativePayload(event);
    renderAll();
    toast(event.granted ? "Müzik izni verildi." : "Müzik izni verilmedi.");
    return;
  }

  if (event.type === "libraryChanged") {
    updateFromNativePayload(event);
    renderActive();
    return;
  }

  if (event.type === "notificationPermissionChanged") {
    state.notificationPermission = !!event.granted;
    renderSettings();
    toast(event.granted ? "Bildirim izni verildi." : "Bildirim izni verilmedi.");
    return;
  }

  if (event.type === "storagePermissionChanged") {
    state.manageStoragePermission = !!event.granted;
    updateFromNativePayload(event);
    if (event.granted && retryPendingFileActionsAfterStorageGrant()) return;
    renderSettings();
    toast(event.granted ? "Dosya erişimi verildi." : "Dosya erişimi verilmedi.");
    return;
  }

  if (event.type === "fileActionResult") {
    const pending = event.uri ? state.pendingFileActions[event.uri] : null;
    if (event.uri) delete state.pendingFileActions[event.uri];
    if (event.ok) {
      const track = event.uri ? findTrack(event.uri) : null;
      if (event.action === "delete" && track) removeTrackLocally(track);
      updateFromNativePayload(event);
      if (event.action === "delete" && track) removeTrackLocally(track);
      toast(event.action === "delete" ? "Şarkı silindi." : "Şarkı adı güncellendi.");
      renderAll();
    } else {
      toast(event.error || (pending?.action === "delete" ? "Silme tamamlanamadı." : "Dosya işlemi tamamlanamadı."));
      renderActive();
    }
    return;
  }

  if (event.type === "mediaCommand") {
    if (event.command === "next") nextSong({ keepScreen: true });
    else if (event.command === "previous") prevSong({ keepScreen: true });
    else if (event.command === "play") {
      if (!state.playing) togglePlay();
    } else if (event.command === "pause") {
      if (state.playing) togglePlay();
    } else if (event.command === "toggle") {
      togglePlay();
    }
    return;
  }

  if (event.type === "artworkReady" && handleCoverLookup(event)) {
    return;
  }

  if (event.type === "searchSuggestions" && event.callbackId === state.search.suggestionsCallbackId) {
    state.search.suggestionsLoading = false;
    if (event.ok) {
      state.search.suggestions = Array.isArray(event.suggestions) ? event.suggestions : [];
      state.search.suggestionsError = "";
    } else {
      state.search.suggestions = [];
      state.search.suggestionsError = event.error || "Öneriler alınamadı.";
    }
    updateSearchBody();
    return;
  }

  if (event.type === "searchResults" && event.callbackId === state.similar.callbackId) {
    state.similar.loading = false;
    if (event.ok) {
      const localKeys = new Set(state.songs.map(keyOf).filter(Boolean));
      state.similar.results = normalizeSongs(event.results || [])
        .filter((song) => !localKeys.has(keyOf(song)))
        .slice(0, 8);
      state.similar.error = "";
      state.similar.fetchedAt = Date.now();
    } else {
      state.similar.results = [];
      state.similar.error = event.error || "Benzer şarkılar alınamadı.";
    }
    if (state.screen === "library" && state.libTab === "all") renderLibrary();
    return;
  }

  if (event.type === "searchResults" && event.callbackId === state.search.callbackId) {
    clearTimeout(state.searchWatchdog);
    state.search.loading = false;
    if (event.ok) {
      state.search.results = normalizeSongs(event.results || []);
      state.search.error = "";
    } else {
      state.search.results = [];
      state.search.error = event.error || "Arama başarısız.";
    }
    updateSearchBody();
    return;
  }

  if (event.type === "previewReady") {
    if (state.preview && event.callbackId === state.preview.callbackId) {
      if (event.ok) {
        state.preview.status = "ready";
        state.preview.track = Object.assign(state.preview.track, event.track || {});
        // toast("Önizleme başladı.");
      } else {
        toast(event.error || "Önizleme başlatılamadı.");
        closePrev();
      }
      renderPreview();
    }
    return;
  }

  if (event.type === "playback") {
    if (event.track && event.track !== null) {
      state.current = normalizeSongs([event.track])[0] || state.current;
      state.duration = state.current?.durationMs || state.duration;
    }
    state.playing = event.status === "playing" || event.status === "loading";
    if (event.status === "completed") {
      if (playNextAfterCompletion(event.track)) return;
      state.playing = false;
      state.position = state.duration;
    }
    renderMini();
    if (state.current && !$("player").classList.contains("off")) renderPlayer();
    return;
  }

  if (event.type === "download") {
    const eventId = event.callbackId || event.id;
    const previous = state.downloads.find((dl) => dl.id === eventId);
    const track = normalizeSongs([event.track || {}])[0] || event.track;
    const sourceKey = previous?.sourceKey || keyOf(track);
    const item = {
      id: eventId,
      nativeId: event.id,
      sourceKey,
      status: event.status,
      progress: event.progress || 0,
      message: event.message || "",
      error: event.error || "",
      track
    };
    if (event.status === "failed") {
      state.downloads = state.downloads.filter((dl) => dl.id !== item.id);
      saveJson("flowna_downloads", state.downloads);
      upsertFailed(item);
    } else {
      upsertDownload(item);
    }
    if (event.status === "failed") toast(item.error || "İndirme başarısız oldu.");
    if (event.status === "completed") {
      toast("İndirme tamamlandı.");
      refreshLibrary(false);
    }
    if (!patchDownloadUi(item)) renderDownloads();
    return;
  }

  if (event.type === "ytDlpUpdate") {
    toast(event.message || "yt-dlp güncelleme durumu güncellendi.");
    return;
  }

  if (event.type === "error") {
    toast(event.message || "Bir hata oluştu.");
  }
}

window.goScreen = goScreen;
window.requestPermission = requestPermission;
window.requestNotificationPermission = requestNotificationPermission;
window.requestManageStoragePermission = requestManageStoragePermission;
window.refreshLibrary = refreshLibrary;
window.playTrack = playTrack;
window.togglePlay = togglePlay;
window.nextSong = nextSong;
window.prevSong = prevSong;
window.seekPlayer = seekPlayer;
window.seekRelative = seekRelative;
window.closePlayer = closePlayer;
window.openPlayer = () => { if (state.current) { renderPlayer(); $("player").classList.remove("off"); } };
window.startPlayerDrag = startPlayerDrag;
window.movePlayerDrag = movePlayerDrag;
window.endPlayerDrag = endPlayerDrag;
window.openSongActions = openSongActions;
window.closeActionSheet = closeActionSheet;
window.showRenameSheet = showRenameSheet;
window.showDeleteSheet = showDeleteSheet;
window.confirmRename = confirmRename;
window.confirmDelete = confirmDelete;
window.toggleLike = toggleLike;
window.shareTrack = shareTrack;
window.onSearchInput = onSearchInput;
window.setSearch = setSearch;
window.clearSearch = clearSearch;
window.runSearch = runSearch;
window.setLibTab = setLibTab;
window.setDlTab = setDlTab;
window.startDownload = startDownload;
window.setQuality = setQuality;
window.toggleSetting = toggleSetting;
window.checkUpdate = checkUpdate;
window.applyLiveUpdate = applyLiveUpdate;
window.resetLiveUpdate = resetLiveUpdate;
window.refreshSimilarSongs = refreshSimilarSongs;
window.checkYtDlpUpdate = checkYtDlpUpdate;
window.clearCache = clearCache;
window.openPreview = openPreview;
window.closePrev = closePrev;
window.handleAndroidBack = handleAndroidBack;

syncAppHeight(true);
window.addEventListener("resize", () => syncAppHeight(false), { passive: true });
window.visualViewport?.addEventListener("resize", () => syncAppHeight(false), { passive: true });

setTimeout(init, 80);
