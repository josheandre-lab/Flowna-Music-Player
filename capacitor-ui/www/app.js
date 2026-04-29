const $ = (id) => document.getElementById(id);

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

const state = {
  screen: "search",
  permissionGranted: false,
  versionName: "1.0.8",
  versionCode: 9,
  songs: [],
  downloadedSongs: [],
  search: { query: "", loading: false, results: [], error: "", callbackId: "" },
  recent: loadJson("flowna_recent_searches", []),
  downloads: loadJson("flowna_downloads", []),
  failedDownloads: loadJson("flowna_failed_downloads", []),
  playStats: loadJson("flowna_play_stats", {}),
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
  toastTimer: null,
  searchTimer: null,
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
    || null;
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
  if (payload.versionName) state.versionName = payload.versionName;
  if (payload.versionCode) state.versionCode = payload.versionCode;
  if (payload.settings && typeof payload.settings === "object") {
    state.settings = Object.assign(state.settings, payload.settings);
  }
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
    cover: song.cover || "",
    videoUrl: song.videoUrl || "",
    source: song.source || (song.videoUrl ? "online" : "local"),
    downloaded: !!song.downloaded
  }));
}

function cryptoId() {
  return "id_" + Math.random().toString(36).slice(2) + Date.now();
}

function init() {
  const initial = Native.call("getInitialState");
  if (initial.ok) {
    updateFromNativePayload(initial);
  } else {
    state.permissionGranted = false;
  }
  saveSettings(false);
  renderAll();
  startPlaybackPolling();
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

function renderNav() {
  $("nav").innerHTML = [
    { id: "search", ico: "⌕", lbl: "Ara" },
    { id: "downloads", ico: "↓", lbl: "İndirmeler" },
    { id: "library", ico: "♫", lbl: "Kütüphane" },
    { id: "settings", ico: "⚙", lbl: "Ayarlar" }
  ].map((item) => `<div class="ni ${state.screen === item.id ? "on" : ""}" onclick="goScreen('${item.id}')">
      <div class="npill"></div><div class="nico">${item.ico}</div><div class="nlbl">${item.lbl}</div>
    </div>`).join("");
}

function permissionCard() {
  return `<div class="empty au">
    <div class="eico">♫</div>
    <div class="et">Müziklere erişim izni gerekli</div>
    <div class="es">Kütüphaneni göstermek, çalmak, silmek ve ad değiştirmek için Android müzik izni gerekiyor.</div>
    <button class="pdlbtn" style="margin-top:18px;max-width:230px" onclick="requestPermission()">İzin Ver</button>
  </div>`;
}

function requestPermission() {
  const result = Native.call("requestAudioPermission");
  if (!result.ok) toast(result.error || "İzin isteği başlatılamadı.");
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
      <button class="mbtn mplay" onclick="togglePlay()">${state.playing ? "Ⅱ" : "▶"}</button>
      <button class="mbtn" onclick="nextSong()">⏭</button>
    </div>
    <div class="mprog"><div class="mpfill" style="width:${progress}%"></div></div>`;
}

function songCard(track, index = 0, options = {}) {
  const key = register(track);
  const isOnline = track.source === "online";
  const primary = isOnline ? `openPreview('${key}')` : `playTrack('${key}')`;
  const menu = isOnline
    ? `<button class="cbtn cdl" onclick="startDownload('${key}')" title="İndir">↓</button>`
    : `<button class="cbtn cmore" onclick="openSongActions('${key}')" title="Menü">⋮</button>`;
  return `<div class="mc au" style="animation-delay:${(index * 0.035).toFixed(2)}s" onclick="${primary}">
    <div class="mcimg">${imgD(track.cover, 56, 56, 12)}</div>
    <div class="mci">
      <div class="mct">${esc(track.title)}</div>
      <div class="mca">${esc(track.artist)}</div>
      <div class="mcm">${badge(track)}<span class="dur">${esc(track.duration || fmt(track.durationMs))}</span></div>
    </div>
    <div class="mcact" onclick="event.stopPropagation()">
      <button class="cbtn cprev" onclick="${isOnline ? `openPreview('${key}')` : `playTrack('${key}')`}" title="${isOnline ? "Önizle" : "Çal"}">▶</button>
      ${menu}
    </div>
  </div>`;
}

function playTrack(key) {
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
    toast(`${track.title} çalıyor`);
  }
  renderMini();
  renderPlayer();
  $("player").classList.remove("off");
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
  renderMini();
  renderPlayer();
}

function nextSong() {
  if (!state.current || !state.queue.length) return;
  const currentKey = keyOf(state.current);
  const index = state.queue.findIndex((song) => keyOf(song) === currentKey);
  const next = state.queue[(index + 1 + state.queue.length) % state.queue.length];
  playTrack(register(next));
}

function prevSong() {
  if (!state.current || !state.queue.length) return;
  const currentKey = keyOf(state.current);
  const index = state.queue.findIndex((song) => keyOf(song) === currentKey);
  const prev = state.queue[(index - 1 + state.queue.length) % state.queue.length];
  playTrack(register(prev));
}

function seekPlayer(event) {
  if (!state.duration) return;
  const rect = event.currentTarget.getBoundingClientRect();
  const pct = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
  state.position = Math.floor(state.duration * pct);
  Native.call("seekTo", state.position);
  renderMini();
  renderPlayer();
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
        <button class="plbtn2" onclick="closePlayer()">⌄</button>
        <div class="plhdrt">Şimdi Çalıyor</div>
        <button class="plbtn2" onclick="openSongActions('${register(track)}')">⋮</button>
      </div>
      <div class="plaw">
        <div class="plart">${imgD(track.cover, 274, 274, 26)}</div>
        <div class="spec">${spectrumHtml()}</div>
      </div>
      <div class="plrow">
        <div style="flex:1;min-width:0">
          <div class="plsn">${esc(track.title)}</div>
          <div class="plar">${esc(track.artist)}${track.album ? " · " + esc(track.album) : ""}</div>
        </div>
        <button class="plfav" onclick="toggleLike('${register(track)}')">${liked ? "♥" : "♡"}</button>
      </div>
      <div class="plprog">
        <div class="plpb" onclick="seekPlayer(event)">
          <div class="plpbf" style="width:${progress}%"><div class="plpbt"></div></div>
        </div>
        <div class="pltime"><span>${fmt(state.position)}</span><span>${esc(track.duration || fmt(state.duration))}</span></div>
      </div>
      <div class="placts">
        <button class="plact" onclick="toast('Sıraya eklendi')">＋ Sıraya Ekle</button>
        <button class="plact" onclick="shareTrack('${register(track)}')">↗ Paylaş</button>
      </div>
      <div class="plctrls">
        <button class="cc ccpn" onclick="prevSong()">⏮</button>
        <button class="cc" onclick="seekRelative(-10000)">−10</button>
        <button class="ccplay" onclick="togglePlay()">${state.playing ? "Ⅱ" : "▶"}</button>
        <button class="cc" onclick="seekRelative(10000)">+10</button>
        <button class="cc ccpn" onclick="nextSong()">⏭</button>
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
  renderPlayer();
}

function closePlayer() {
  $("player").classList.add("off");
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
  const choice = prompt(`${track.title}\n\n1: Ad değiştir\n2: Sil\n3: Paylaş`, "1");
  if (choice === "1") renameTrack(key);
  if (choice === "2") deleteTrack(key);
  if (choice === "3") shareTrack(key);
}

function renameTrack(key) {
  const track = findTrack(key);
  if (!track?.uri) return toast("Bu parça için ad değiştirme kullanılamıyor.");
  const next = prompt("Yeni şarkı adı", track.title);
  if (!next || next.trim() === track.title) return;
  const result = Native.call("renameSong", track.uri, next.trim());
  if (result.ok) {
    updateFromNativePayload(result);
    toast(result.pendingPermission ? "Android düzenleme onayı açıldı." : "Şarkı adı güncellendi.");
    renderActive();
  } else {
    toast(result.error || "Ad değiştirilemedi.");
  }
}

function deleteTrack(key) {
  const track = findTrack(key);
  if (!track?.uri) return toast("Bu parça için silme kullanılamıyor.");
  if (!confirm(`${track.title} silinsin mi?`)) return;
  const result = Native.call("deleteSong", track.uri);
  if (result.ok) {
    updateFromNativePayload(result);
    toast(result.pendingPermission ? "Android silme onayı açıldı." : "Şarkı silindi.");
    if (state.current && keyOf(state.current) === keyOf(track)) {
      state.current = null;
      Native.call("pause");
    }
    renderActive();
  } else {
    toast(result.error || "Silinemedi.");
  }
}

function renderSearch() {
  const el = $("scr-search");
  const q = state.search.query.trim();
  const body = !q
    ? renderSearchHome()
    : state.search.loading
      ? loadingBlock(`"${esc(q)}" aranıyor`)
      : state.search.error
        ? emptyBlock("Arama tamamlanamadı", state.search.error, "↻", `<button class="pdlbtn" style="margin-top:16px" onclick="runSearch()">Tekrar Dene</button>`)
        : renderSearchResults();
  el.innerHTML = `<div class="shdr">
      <div class="stitle">Ara</div>
      <div class="ssub">Şarkı, sanatçı veya albüm keşfet.</div>
    </div>
    <div class="scroll">
      <div class="swrap">
        <div class="sico">⌕</div>
        <input class="sinp" id="sinp" placeholder="Şarkı, sanatçı veya albüm ara..." value="${esc(state.search.query)}" oninput="onSearchInput(this.value)" onkeydown="if(event.key==='Enter')runSearch()" autocomplete="off">
        ${state.search.query ? `<div class="sclr" onclick="clearSearch()">×</div>` : ""}
      </div>
      ${body}
    </div>`;
  const input = $("sinp");
  if (document.activeElement?.id === "sinp") {
    input?.focus();
    input?.setSelectionRange(input.value.length, input.value.length);
  }
}

function renderSearchHome() {
  const recent = state.recent.length
    ? `<div class="stlbl">Son aramalar</div><div class="chips">${state.recent.map((item) => `<div class="chip" onclick="setSearch('${esc(item)}')">${esc(item)}</div>`).join("")}</div>`
    : emptyBlock("Henüz arama yok", "Arama yaptıkça burada kullanıcı aramaları görünecek.", "⌕");
  const quick = state.songs.length
    ? `<div class="stlbl">Kütüphanenden hızlı başlat</div>${state.songs.slice(0, 6).map(songCard).join("")}`
    : `<div class="stlbl">Çevrim içi arama</div>${emptyBlock("Aramaya başla", "YouTube sonuçları ve önizleme için yukarıya bir şarkı adı yaz.", "▶")}`;
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
  if (value.trim().length > 1) {
    state.search.loading = true;
    state.searchTimer = setTimeout(runSearch, 450);
  } else {
    state.search.loading = false;
    state.search.results = [];
  }
  renderSearch();
}

function setSearch(value) {
  state.search.query = value;
  runSearch();
}

function clearSearch() {
  state.search = { query: "", loading: false, results: [], error: "", callbackId: "" };
  renderSearch();
}

function runSearch() {
  const q = state.search.query.trim();
  if (q.length < 2) return;
  rememberSearch(q);
  state.search.loading = true;
  state.search.error = "";
  state.search.callbackId = "search_" + Date.now();
  renderSearch();
  const result = Native.call("search", q, state.search.callbackId);
  if (!result.ok) {
    state.search.loading = false;
    state.search.error = result.error || "Android içinde çalışınca arama aktif olur.";
    renderSearch();
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
      ${list.length ? list.map(songCard).join("") : emptyBlock("Bu bölüm boş", "Yeni indirdiğin müzikler burada görünecek.", "↓")}
    </div>`;
}

function renderRecommendations() {
  const recommended = recommendedSongs();
  const most = mostPlayedSongs();
  return `<div class="stlbl">Senin için önerilenler</div>
    ${recommended.length ? `<div class="hs" style="margin-bottom:22px">${recommended.map(featureCard).join("")}</div>` : emptyBlock("Dinledikçe iyileşir", "Birkaç şarkı çaldığında öneriler gerçek dinleme alışkanlığına göre oluşacak.", "✦")}
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

function setLibTab(tab) {
  state.libTab = tab;
  renderLibrary();
}

function renderDownloads() {
  const el = $("scr-downloads");
  const active = state.downloads.filter((item) => item.status === "active");
  const completed = state.downloads.filter((item) => item.status === "completed");
  const failed = state.failedDownloads;
  const list = state.dlTab === "active" ? active : state.dlTab === "done" ? completed : failed;
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
      ${list.length ? list.map(downloadCard).join("") : emptyBlock(state.dlTab === "active" ? "Aktif indirme yok" : "Bu bölüm boş", "Arama ekranından bir şarkı indirerek listeyi doldurabilirsin.", "↓")}
    </div>`;
}

function downloadCard(item, index) {
  const track = item.track || {};
  if (item.status === "failed") {
    const key = register(track);
    return `<div class="dlc dlfc au" style="animation-delay:${index * 0.04}s">
      <div class="dlct">${imgD(track.cover, 48, 48, 12)}<div class="dli"><div class="dlti">${esc(track.title || "İndirme")}</div><div class="dlar">${esc(track.artist || "")}</div></div></div>
      <div class="errb">${esc(item.error || "İndirme başarısız oldu.")}</div>
      ${track.videoUrl ? `<button class="dlb dlretry" onclick="startDownload('${key}')">Tekrar Dene</button>` : ""}
    </div>`;
  }
  return `<div class="dlc au" style="animation-delay:${index * 0.04}s">
    <div class="dlct">${imgD(track.cover, 48, 48, 12)}<div class="dli"><div class="dlti">${esc(track.title || "İndirme")}</div><div class="dlar">${esc(track.artist || "")}</div></div></div>
    <div class="dlm"><span>${esc(item.message || "")}</span><span>${Math.round(item.progress || 0)}%</span></div>
    <div class="dltr"><div class="dlf" style="width:${Math.round(item.progress || 0)}%"></div></div>
  </div>`;
}

function setDlTab(tab) {
  state.dlTab = tab;
  renderDownloads();
}

function startDownload(key) {
  const track = findTrack(key);
  if (!track) return;
  const callbackId = "dl_" + Date.now();
  upsertDownload({ id: callbackId, status: "active", progress: 0, message: "Sıraya alındı", track });
  state.dlTab = "active";
  renderDownloads();
  const result = Native.call("startDownload", JSON.stringify(track), callbackId);
  if (!result.ok) {
    upsertFailed({ id: callbackId, status: "failed", progress: 0, message: "Başarısız", error: result.error, track });
    renderDownloads();
  } else {
    toast("İndirme başlatıldı.");
  }
}

function renderSettings() {
  const el = $("scr-settings");
  const sw = (key) => `<div class="tog ${state.settings[key] ? "on" : ""}" onclick="toggleSetting('${key}')"><div class="togth"></div></div>`;
  const quality = ["128K", "192K", "320K"].map((q) => `<button class="sb ${state.settings.quality === q ? "on" : ""}" onclick="setQuality('${q}')">${q}</button>`).join("");
  el.innerHTML = `<div class="shdr"><div class="stitle">Ayarlar</div><div class="ssub">İndirme, oynatma ve uygulama tercihleri.</div></div>
    <div class="scroll">
      <div class="aicard au">
        <div class="ailogo">♫</div>
        <div class="ainame">Flowna Music Player</div>
        <div class="aiver">Sürüm ${esc(state.versionName)} · Kod ${esc(state.versionCode)}</div>
        <div class="vbs"><span class="vb vok">Güncel</span><span class="vb vgray">${state.songs.length} şarkı</span></div>
      </div>
      <div class="ss"><div class="ssl">Ses ve İndirme</div><div class="sg">
        <div class="sr"><div class="srico" style="background:rgba(255,107,74,.1)">♫</div><div class="sri"><div class="srt">Ses Kalitesi</div><div class="srs">İndirme sırasında kullanılacak kalite</div></div><div class="seg">${quality}</div></div>
        <div class="sr"><div class="srico" style="background:rgba(245,158,11,.1)">⌁</div><div class="sri"><div class="srt">Yalnızca Wi‑Fi</div><div class="srs">Mobil veride indirmeyi kapatır</div></div>${sw("wifiOnly")}</div>
        <div class="sr"><div class="srico" style="background:rgba(0,200,150,.1)">↻</div><div class="sri"><div class="srt">Arka Planda İndir</div><div class="srs">İndirmeleri uygulama açıkken yönetir</div></div>${sw("bgDl")}</div>
        <div class="sr"><div class="srico" style="background:rgba(79,70,229,.1)">🔔</div><div class="sri"><div class="srt">İndirme Bildirimi</div><div class="srs">Tamamlandığında bilgi göster</div></div>${sw("notifyDownloads")}</div>
      </div></div>
      <div class="ss" style="margin-top:14px"><div class="ssl">Güncellemeler</div><div class="sg">
        <div class="sr"><div class="srico" style="background:rgba(79,70,229,.1)">↥</div><div class="sri"><div class="srt">Otomatik Kontrol</div><div class="srs">Açılışta sürüm bilgisi kontrol edilir</div></div>${sw("autoUp")}</div>
        <div class="sr" onclick="checkUpdate()"><div class="srico" style="background:rgba(255,107,74,.1)">⌕</div><div class="sri"><div class="srt">Güncellemeyi Kontrol Et</div><div class="srs">Kullanıcıya repo adresi gösterilmez</div></div><div class="srr">›</div></div>
      </div></div>
      <div class="ss" style="margin-top:14px"><div class="ssl">Kütüphane</div><div class="sg">
        <div class="sr" onclick="refreshLibrary(true)"><div class="srico" style="background:rgba(0,200,150,.1)">▣</div><div class="sri"><div class="srt">Kütüphaneyi Yeniden Tara</div><div class="srs">Cihazdaki müzikleri günceller</div></div><div class="srr">›</div></div>
        <div class="sr" onclick="clearCache()"><div class="srico" style="background:rgba(239,68,68,.1)">⌫</div><div class="sri"><div class="srt">Önbelleği Temizle</div><div class="srs">Geçici önizleme ve indirme kalıntılarını siler</div></div><div class="srr">›</div></div>
      </div></div>
      <div class="ss" style="margin-top:14px"><div class="ssl">Tanılama</div><div class="sg">
        ${diagRow("Müzik izni", state.permissionGranted ? "Verildi" : "Bekliyor", state.permissionGranted)}
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
  toast(result.message || result.status || "Güncelleme kontrol edildi.");
}

function clearCache() {
  const result = Native.call("clearCache");
  if (result.ok) toast(`Önbellek temizlendi (${Math.round((result.deletedBytes || 0) / 1024 / 1024)} MB).`);
  else toast(result.error || "Önbellek temizlenemedi.");
}

function openPreview(key) {
  const track = findTrack(key);
  if (!track) return;
  state.preview = { track, status: "loading", progress: 0, playing: true };
  renderPreview();
  $("pov").classList.remove("off");
  if (track.source === "online") {
    const callbackId = "prev_" + Date.now();
    state.preview.callbackId = callbackId;
    Native.call("startPreview", JSON.stringify(track), callbackId);
  } else {
    playTrack(key);
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
      <button class="ppbtn" onclick="togglePlay()">${state.playing ? "Ⅱ" : "▶"}</button>
      <button class="pdlbtn" onclick="startDownload('${register(track)}');closePrev()">↓ İndir</button>
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

function handleNativeEvent(event) {
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
    state.search.loading = false;
    if (event.ok) {
      state.search.results = normalizeSongs(event.results || []);
      state.search.error = "";
    } else {
      state.search.results = [];
      state.search.error = event.error || "Arama başarısız.";
    }
    renderSearch();
  }
  if (event.type === "previewReady") {
    if (state.preview && event.callbackId === state.preview.callbackId) {
      if (event.ok) {
        state.preview.status = "ready";
        state.preview.track = Object.assign(state.preview.track, event.track || {});
        toast("Önizleme başladı.");
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
      state.playing = false;
      state.position = state.duration;
    }
    renderMini();
    if (state.current && !$("player").classList.contains("off")) renderPlayer();
  }
  if (event.type === "download") {
    const item = {
      id: event.callbackId || event.id,
      nativeId: event.id,
      status: event.status,
      progress: event.progress || 0,
      message: event.message || "",
      error: event.error || "",
      track: normalizeSongs([event.track || {}])[0] || event.track
    };
    if (event.status === "failed") upsertFailed(item);
    else upsertDownload(item);
    if (event.status === "completed") refreshLibrary(false);
    renderDownloads();
  }
  if (event.type === "error") {
    toast(event.message || "Bir hata oluştu.");
  }
}

function upsertDownload(item) {
  state.downloads = [item, ...state.downloads.filter((dl) => dl.id !== item.id)].slice(0, 50);
  saveJson("flowna_downloads", state.downloads);
}

function upsertFailed(item) {
  state.failedDownloads = [item, ...state.failedDownloads.filter((dl) => dl.id !== item.id)].slice(0, 20);
  saveJson("flowna_failed_downloads", state.failedDownloads);
}

function startPlaybackPolling() {
  clearInterval(state.pollTimer);
  state.pollTimer = setInterval(() => {
    if (!state.current) return;
    const result = Native.call("getPlaybackState");
    if (!result.ok || !result.hasPlayer) return;
    state.playing = !!result.isPlaying;
    state.position = Number(result.position || 0);
    state.duration = Number(result.duration || state.duration || 0);
    renderMini();
    if (!$("player").classList.contains("off")) renderPlayer();
  }, 800);
}

window.goScreen = goScreen;
window.requestPermission = requestPermission;
window.refreshLibrary = refreshLibrary;
window.playTrack = playTrack;
window.togglePlay = togglePlay;
window.nextSong = nextSong;
window.prevSong = prevSong;
window.seekPlayer = seekPlayer;
window.seekRelative = seekRelative;
window.closePlayer = closePlayer;
window.openPlayer = () => { if (state.current) { renderPlayer(); $("player").classList.remove("off"); } };
window.openSongActions = openSongActions;
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
window.clearCache = clearCache;
window.openPreview = openPreview;
window.closePrev = closePrev;

init();
