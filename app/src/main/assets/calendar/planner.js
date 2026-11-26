(function () {
  'use strict';

  const Style = window.PooftieCalendarStyle || null;
  const Events = window.PooftieCalendarEvents || null;
  const Holidays = window.LP_Holidays || null;

  const NOTE_KEY = 'lp_cal_notes';
  const REACTION_KEY = 'lp_cal_reactions';
  const PHOTO_KEY = 'lp_cal_photos';
  const STICKER_KEY = 'lp_cal_stickers';
  const ZOOM_KEY = 'lp_cal_zoom';
  const HOLIDAY_KEY = 'lp_cal_holidays';
  const EMOJI_KEY = 'lp_cal_emoji';
  const CUSTOM_FONT_KEY = 'lp_cal_custom_font';
  const CUSTOM_FONT_FAMILY = 'LP Cal Font';
  const TASKS_MIGRATED_KEY = 'lp_cal_tasks_migrated';
  const HOUR = 46;
  const MAX_LINES = 3;

  const EMOJI_FAMILIES = {
    iphone: "'Pooftie Apple Emoji', 'Apple Color Emoji', 'Segoe UI Emoji', 'Noto Color Emoji', sans-serif",
    aparelho: "'Apple Color Emoji', 'Segoe UI Emoji', 'Noto Color Emoji', 'Android Emoji', sans-serif"
  };

  const MONTHS = [
    'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
    'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'
  ];
  const WEEKDAYS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
  const WEEKDAYS_LONG = ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'];
  const REACTIONS = ['🍰', '🌸', '🎀', '🧸', '☕', '🌧️'];
  const FUNDO_NOMES = {
    nenhum: 'lisinho', bolinhas: 'bolinhas', nuvens: 'nuvens', coracoes: 'corações',
    xadrez: 'xadrez', listras: 'listras', estrelinhas: 'estrelinhas', papel: 'papel', foto: 'minha foto'
  };
  const QUADRO_NOMES = { grade: 'grade', cartoes: 'cartões', redondos: 'redondos', limpos: 'limpos' };
  const FONTE_NOMES = {
    jorte: 'Jorte (limpa e apertadinha)', batang: 'Gowun Batang (romance)', nunito: 'Nunito (limpa)',
    patrick: 'Patrick Hand (gostosa)', caveat: 'Caveat (caligrafia)', gloria: 'Gloria (giz de cera)',
    indie: 'Indie Flower (despojada)', vt323: 'VT323 (retrô)', custom: 'minha fonte ♡'
  };
  const MINI_NOMES = {
    nada: 'sem numerinho', dia: 'dia do ano (1…366)', semana: 'semana do ano (1…53)',
    mesdia: 'data curtinha (10.1)'
  };
  const PALETA_NOMES = {
    romantica: 'romântica (rosa, creme…)', terrosa: 'terrosa (areia, cacau…)',
    pastel: 'pastel (azul, rosa…)', neutra: 'neutra (cinzinhas)'
  };
  const STICKER_CATEGORY_LABELS = {
    agere: 'fofos', animais: 'bichinhos', canetinhas: 'canetinhas', comidinhas: 'comidinhas',
    coracaozinhos: 'corações', emoticons: 'emoticons', lacinhos: 'lacinhos', natureza: 'natureza',
    pelucias: 'pelúcias', pooftie: 'pooftie', random: 'variados', 'rendinhas-e-bordas': 'rendinhas'
  };

  const state = {
    view: 'month',
    today: todayKey(),
    cursor: new Date(),
    month: new Date(new Date().getFullYear(), new Date().getMonth(), 1),
    selected: todayKey(),
    notes: readObject(NOTE_KEY),
    reactions: readObject(REACTION_KEY),
    photos: readObject(PHOTO_KEY),
    stickers: [],
    zoom: readZoom(),
    holidays: readHolidays(),
    emoji: readEmojiMode(),
    style: Style ? Style.ler(localStorage) : null,
    customFont: null,
    customFace: null,
    eventColor: (Events && Events.CORES[0]) || '#f9c8d7',
    draft: null,
    drag: null,
    selectedSticker: null,
    zCounter: 20,
    stickersLoaded: false,
    stickersLoading: false,
    stickerAll: [],
    stickerCategories: ['all'],
    stickerCategory: 'all',
    stickerQuery: '',
    stickerLimit: 120,
    stickerOpen: false,
    pendingScroll: false,
    scrollMinute: 480,
    resizeRaf: 0,
    savedOnce: false
  };

  let root = null;

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, Number(value) || 0));
  }

  function uid(prefix) {
    return prefix + '-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 7);
  }

  function escapeHTML(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (ch) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
    });
  }

  function escape(value) {
    return escapeHTML(value);
  }

  let toastTimer = 0;
  function toast(message, kind) {
    if (!message) return;
    let box = document.getElementById('lp-toast');
    if (!box) {
      box = document.createElement('div');
      box.id = 'lp-toast';
      box.className = 'lp-toast';
      document.body.appendChild(box);
    }
    box.textContent = String(message);
    box.className = 'lp-toast is-visible' + (kind === 'error' ? ' is-error' : '');
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () { box.className = 'lp-toast'; }, 3400);
  }

  function dateKey(date) {
    return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
  }

  function parseDate(value) {
    const parts = String(value || '').split('-').map(Number);
    if (parts.length !== 3 || !parts.every(Number.isFinite)) return null;
    return new Date(parts[0], parts[1] - 1, parts[2]);
  }

  function todayKey() {
    return dateKey(new Date());
  }

  function addDays(date, days) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
  }

  function startOfWeek(date) {
    return addDays(date, -date.getDay());
  }

  function startOfMonth(date) {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }

  function minutesNow() {
    const now = new Date();
    return now.getHours() * 60 + now.getMinutes();
  }

  function readObject(key) {
    try {
      const value = JSON.parse(localStorage.getItem(key) || '{}');
      return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
    } catch (error) {
      return {};
    }
  }

  function saveObject(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (error) {
      return false;
    }
  }

  function readArray(key) {
    try {
      const value = JSON.parse(localStorage.getItem(key) || '[]');
      return Array.isArray(value) ? value : [];
    } catch (error) {
      return [];
    }
  }

  function readZoom() {
    try {
      return clamp(Number(localStorage.getItem(ZOOM_KEY)) || 1, 0.75, 1.25);
    } catch (error) {
      return 1;
    }
  }

  function readHolidays() {
    try {
      const raw = localStorage.getItem(HOLIDAY_KEY);
      if (raw == null) return ['br'];
      const list = JSON.parse(raw);
      if (!Array.isArray(list)) return ['br'];
      if (!Holidays) return [];
      return list.filter(function (id) {
        return Holidays.PAISES.some(function (pais) { return pais.id === id; });
      });
    } catch (error) {
      return ['br'];
    }
  }

  function readEmojiMode() {
    try {
      return localStorage.getItem(EMOJI_KEY) === 'aparelho' ? 'aparelho' : 'iphone';
    } catch (error) {
      return 'iphone';
    }
  }

  function applyEmojiStyle() {
    const familia = EMOJI_FAMILIES[state.emoji] || EMOJI_FAMILIES.iphone;
    document.documentElement.style.setProperty('--fonte-emoji', familia);
  }

  function noteLines(text) {
    return Style ? Style.linhasDaNota(text) : String(text || '').split(/\r?\n/).map(function (line) { return line.trim(); }).filter(Boolean);
  }

  function applyStyle() {
    if (!Style || !root) return;
    Style.aplicar(root, state.style);
    root.style.setProperty('--cal-zoom', String(state.zoom));
  }

  function events() {
    return Events ? Events.ler(localStorage) : [];
  }

  function saveEvents(list) {
    return Events ? Events.guardar(localStorage, list) : false;
  }

  function holidayName(key) {
    return Holidays ? Holidays.nome(key, state.holidays) : '';
  }

  function shortDate(key) {
    const date = parseDate(key);
    if (!date) return '';
    return date.getDate() + ' ' + MONTHS[date.getMonth()].slice(0, 3);
  }

  function rangeLabel(evento) {
    if (!evento) return '';
    if (!evento.hora) {
      return evento.inicio === evento.fim
        ? 'dia inteiro'
        : 'dia inteiro · ' + shortDate(evento.inicio) + ' – ' + shortDate(evento.fim);
    }
    const horas = evento.fimHora && evento.fimHora !== evento.hora ? evento.hora + ' – ' + evento.fimHora : evento.hora;
    const dias = evento.inicio === evento.fim
      ? shortDate(evento.inicio)
      : shortDate(evento.inicio) + ' – ' + shortDate(evento.fim);
    return horas + ' · ' + dias;
  }

  function shell() {
    const weekdays = WEEKDAYS.map(function (day, index) {
      return '<span class="' + (index === 0 ? 'is-sun' : index === 6 ? 'is-sat' : '') + '">' + day + '</span>';
    }).join('');

    return ''
      + '<div class="cal-app">'
      + '  <header class="cal-top">'
      + '    <button class="cal-arrow" type="button" data-cal="prev" aria-label="anterior">‹</button>'
      + '    <h1 class="cal-title" id="cal-title"></h1>'
      + '    <button class="cal-arrow" type="button" data-cal="next" aria-label="próximo">›</button>'
      + '    <div class="cal-top-actions">'
      + '      <button class="cal-chip" type="button" data-cal="today">hoje</button>'
      + '      <button class="cal-icon" type="button" data-cal="stickers" aria-label="adesivos" title="adesivos">🎀</button>'
      + '      <button class="cal-icon" type="button" data-cal="theme" aria-label="personalizar" title="personalizar">🎨</button>'
      + '    </div>'
      + '  </header>'
      + '  <nav class="cal-tabs" aria-label="modo de visualização">'
      + '    <div class="cal-tabs-group">'
      + '      <button class="cal-tab" type="button" data-view="month">mês</button>'
      + '      <button class="cal-tab" type="button" data-view="week">semana</button>'
      + '      <button class="cal-tab" type="button" data-view="day">dia</button>'
      + '    </div>'
      + '    <div class="cal-zoom" id="cal-zoom">'
      + '      <button type="button" data-cal="zoom-out" aria-label="diminuir">−</button>'
      + '      <button type="button" data-cal="zoom-in" aria-label="aumentar">＋</button>'
      + '    </div>'
      + '  </nav>'
      + '  <div class="cal-stage">'
      + '    <section class="cal-view" data-view-panel="month">'
      + '      <div class="cal-month" id="cal-card">'
      + '        <div class="cal-weekdays">' + weekdays + '</div>'
      + '        <div class="cal-grid" id="cal-grid"></div>'
      + '        <div class="cal-stickers" id="cal-stickers-overlay"></div>'
      + '      </div>'
      + '    </section>'
      + '    <section class="cal-view" data-view-panel="week">'
      + '      <div class="cal-week">'
      + '        <div class="cal-week-head" id="cal-week-head"></div>'
      + '        <div class="cal-week-allday" id="cal-week-allday"></div>'
      + '        <div class="cal-timeline" id="cal-week-timeline"></div>'
      + '      </div>'
      + '    </section>'
      + '    <section class="cal-view" data-view-panel="day">'
      + '      <div class="cal-day">'
      + '        <div class="cal-day-head" id="cal-day-head"></div>'
      + '        <div class="cal-day-allday" id="cal-day-allday"></div>'
      + '        <div class="cal-timeline" id="cal-day-timeline"></div>'
      + '      </div>'
      + '    </section>'
      + '  </div>'
      + '  <button class="cal-fab" type="button" data-cal="add" aria-label="novo evento">+</button>'
      + '</div>'

      + '<div class="cal-modal" id="cal-day-modal">'
      + '  <div class="cal-modal-backdrop" data-cal="close-day"></div>'
      + '  <div class="cal-modal-card cal-day-card" role="dialog" aria-modal="true" aria-labelledby="cal-day-title">'
      + '    <button class="cal-modal-x" type="button" data-cal="close-day" aria-label="fechar">×</button>'
      + '    <h2 class="cal-day-title" id="cal-day-title">tarefinhas do dia</h2>'
      + '    <p class="cal-day-date" id="cal-day-date"></p>'
      + '    <p class="cal-day-holiday" id="cal-day-holiday" hidden></p>'
      + '    <p class="cal-day-hint">cada linha vira uma tarefinha colorida no dia ⋆</p>'
      + '    <textarea id="cal-day-notes" class="cal-day-notes" maxlength="1200" placeholder="ex.: regar as plantinhas&#10;tomar meu chá…"></textarea>'
      + '    <div class="cal-day-preview" id="cal-day-preview"></div>'
      + '    <section class="cal-section">'
      + '      <p class="cal-section-title">a carinha do dia ⋆</p>'
      + '      <div class="cal-reactions" id="cal-reactions"></div>'
      + '    </section>'
      + '    <section class="cal-section">'
      + '      <p class="cal-section-title">evento ⋆</p>'
      + '      <form class="cal-event-form" id="cal-event-form">'
      + '        <input id="cal-event-title" class="cal-event-input" type="text" maxlength="60" placeholder="ex.: viagem, prova, aniversário…" aria-label="nome do evento">'
      + '        <div class="cal-event-row">'
      + '          <label class="cal-switch"><input type="checkbox" id="cal-event-allday" checked><span>dia inteiro</span></label>'
      + '          <label class="cal-time-field">de <input type="time" id="cal-event-hora" value="09:00"></label>'
      + '          <label class="cal-time-field">até <input type="time" id="cal-event-fim-hora" value="10:00"></label>'
      + '        </div>'
      + '        <div class="cal-event-row">'
      + '          <label class="cal-event-label" for="cal-event-end">até o dia</label>'
      + '          <input id="cal-event-end" class="cal-event-date" type="date" aria-label="último dia do evento">'
      + '          <div class="cal-event-colors" id="cal-event-colors" role="group" aria-label="cor do evento"></div>'
      + '          <button class="cal-mini-btn" type="submit">+ adicionar</button>'
      + '        </div>'
      + '      </form>'
      + '      <div class="cal-event-list" id="cal-event-list"></div>'
      + '      <p class="cal-event-hint">escolha o dia, a hora e a cor ⋆</p>'
      + '    </section>'
      + '    <section class="cal-section">'
      + '      <p class="cal-section-title">foto do dia ⋆</p>'
      + '      <div class="cal-photo-box">'
      + '        <div class="cal-photo-preview" id="cal-photo-preview"></div>'
      + '        <div class="cal-photo-actions">'
      + '          <label class="cal-mini-btn" for="cal-photo-file">🖼️ escolher foto</label>'
      + '          <button class="cal-mini-btn" type="button" data-cal="clear-photo" id="cal-photo-clear" hidden>tirar</button>'
      + '          <label class="cal-switch"><input type="checkbox" id="cal-photo-front"><span>na frente</span></label>'
      + '        </div>'
      + '      </div>'
      + '    </section>'
      + '    <div class="cal-day-actions">'
      + '      <button class="cal-btn ghost" type="button" data-cal="close-day">deixar pra lá</button>'
      + '      <button class="cal-btn ghost danger" type="button" data-cal="clear-day">apagar</button>'
      + '      <button class="cal-btn primary" type="button" data-cal="save-day">guardar ♡</button>'
      + '    </div>'
      + '  </div>'
      + '</div>'

      + '<div class="cal-modal" id="cal-theme-modal">'
      + '  <div class="cal-modal-backdrop" data-cal="close-theme"></div>'
      + '  <div class="cal-modal-card cal-theme-card" role="dialog" aria-modal="true" aria-labelledby="cal-theme-title">'
      + '    <div class="cal-theme-head">'
      + '      <div><span class="cal-kicker">meu calendário</span><h2 id="cal-theme-title">🎨 do seu jeitinho</h2></div>'
      + '      <button class="cal-modal-x" type="button" data-cal="close-theme" aria-label="fechar">×</button>'
      + '    </div>'
      + '    <div class="cal-theme-body">'
      + '      <p class="cal-theme-label">fundo</p>'
      + '      <div class="cal-fundo-grid" id="cal-fundo-grid" role="radiogroup" aria-label="fundo do calendário"></div>'
      + '      <div class="cal-theme-row">'
      + '        <label class="cal-theme-field" for="cal-fundo-cor">fundo <input type="color" id="cal-fundo-cor" value="#ffffff"></label>'
      + '        <label class="cal-theme-field" for="cal-detalhe-cor">detalhe <input type="color" id="cal-detalhe-cor" value="#f9dbe5"></label>'
      + '        <button class="cal-mini-btn" type="button" data-cal="theme-photo">🖼️ minha foto</button>'
      + '        <button class="cal-mini-btn" type="button" data-cal="theme-photo-clear" id="cal-bg-clear" hidden>tirar foto</button>'
      + '      </div>'
      + '      <p class="cal-theme-label">quadradinhos</p>'
      + '      <div class="cal-quadros-grid" id="cal-quadros-grid" role="radiogroup" aria-label="estilo dos quadradinhos"></div>'
      + '      <div class="cal-theme-row">'
      + '        <label class="cal-theme-field" for="cal-celula-cor">quadrado <input type="color" id="cal-celula-cor" value="#ffffff"></label>'
      + '        <label class="cal-theme-field" for="cal-linha-cor">linha <input type="color" id="cal-linha-cor" value="#eee1e7"></label>'
      + '        <label class="cal-theme-field" for="cal-texto-cor">número <input type="color" id="cal-texto-cor" value="#2f2a2c"></label>'
      + '      </div>'
      + '      <div class="cal-theme-row">'
      + '        <label class="cal-theme-field wide" for="cal-fonte-select">letra'
      + '          <select id="cal-fonte-select">' + Object.keys(FONTE_NOMES).map(function (key) { return '<option value="' + key + '">' + FONTE_NOMES[key] + '</option>'; }).join('') + '</select>'
      + '        </label>'
      + '        <label class="cal-mini-btn cal-font-upload" for="cal-font-file" title="escolher uma fonte do celular">🔤 minha fonte</label>'
      + '        <input type="file" id="cal-font-file" accept=".ttf,.otf,.woff,.woff2,font/ttf,font/otf,font/woff,font/woff2,application/x-font-ttf,application/x-font-otf,application/font-woff" hidden>'
      + '      </div>'
      + '      <div class="cal-theme-row">'
      + '        <label class="cal-theme-field wide" for="cal-mini-select">numerinho do lado'
      + '          <select id="cal-mini-select">' + Object.keys(MINI_NOMES).map(function (key) { return '<option value="' + key + '">' + MINI_NOMES[key] + '</option>'; }).join('') + '</select>'
      + '        </label>'
      + '      </div>'
      + '      <div class="cal-theme-row">'
      + '        <label class="cal-theme-field wide" for="cal-paleta-select">cor das tarefinhas'
      + '          <select id="cal-paleta-select">' + Object.keys(PALETA_NOMES).map(function (key) { return '<option value="' + key + '">' + PALETA_NOMES[key] + '</option>'; }).join('') + '</select>'
      + '        </label>'
      + '        <label class="cal-switch"><input type="checkbox" id="cal-domingo-vermelho"><span>domingo vermelho</span></label>'
      + '      </div>'
      + '      <p class="cal-theme-label">feriados</p>'
      + '      <div class="cal-theme-chips" id="cal-holiday-grid" role="group" aria-label="feriados"></div>'
      + '      <p class="cal-theme-label">carinhas</p>'
      + '      <div class="cal-theme-chips" id="cal-emoji-grid" role="radiogroup" aria-label="estilo das carinhas"></div>'
      + '      <p class="cal-theme-hint">a foto do fundo fica guardada dentro do app (bem pequenininha). os adesivinhos continuam soltos por cima, do jeitinho que você colar ♡</p>'
      + '    </div>'
      + '    <div class="cal-theme-foot">'
      + '      <button class="cal-mini-btn" type="button" data-cal="reset-theme">voltar ao padrão</button>'
      + '      <button class="cal-btn primary" type="button" data-cal="close-theme">pronto ♡</button>'
      + '    </div>'
      + '  </div>'
      + '</div>'

      + '<div class="cal-sticker-drawer" id="cal-sticker-drawer">'
      + '  <div class="cal-sticker-head">'
      + '    <div><span class="cal-kicker">adesivos</span><h2>🎀 colar no calendário</h2></div>'
      + '    <button class="cal-modal-x" type="button" data-cal="close-stickers" aria-label="fechar adesivos">×</button>'
      + '  </div>'
      + '  <div class="cal-sticker-cats" id="cal-sticker-cats"></div>'
      + '  <input class="cal-sticker-search" id="cal-sticker-search" type="search" placeholder="buscar adesivo..." aria-label="buscar adesivo">'
      + '  <div class="cal-sticker-grid" id="cal-sticker-grid"></div>'
      + '  <p class="cal-sticker-hint">toca num adesivo pra colar; arrasta ele, gira na bolinha de cima e aumenta na bolinha do canto ♡</p>'
      + '</div>'

      + '<input type="file" id="cal-photo-file" accept="image/*" hidden>'
      + '<input type="file" id="cal-bg-file" accept="image/*" hidden>';
  }

  function init() {
    root = document.getElementById('planner-root');
    if (!root || root.dataset.ready) return;
    if (!Style || !Events) {
      root.innerHTML = '<div class="planner-error"><strong>não consegui abrir Calendar</strong><p>recarregue a página. se continuar, me avise.</p></div>';
      return;
    }
    state.selected = todayKey();
    state.today = todayKey();
    state.style = Style.ler(localStorage);
    applyEmojiStyle();
    state.stickers = readArray(STICKER_KEY).map(migrateSticker).map(normalizeSticker).filter(Boolean);
    state.zCounter = state.stickers.reduce(function (max, item) { return Math.max(max, item.z || 0); }, 20);
    root.classList.add('planner-cal');
    root.innerHTML = shell();
    applyStyle();
    bind();
    render();
    restoreStickers();
    restoreCustomFont();
    root.dataset.ready = '1';
    try {
      if (typeof window.__littlePauseCalendarReady === 'function') {
        window.__littlePauseCalendarReady(root.querySelectorAll('.cal-cell').length);
      }
    } catch (error) { }
    if (typeof ResizeObserver === 'function') {
      const stage = root.querySelector('.cal-stage');
      if (stage) {
        const observer = new ResizeObserver(function () {
          if (state.resizeRaf) return;
          state.resizeRaf = requestAnimationFrame(function () {
            state.resizeRaf = 0;
            positionMonthBars();
            clampStickers();
          });
        });
        observer.observe(stage);
      }
    }
    if (document.fonts && document.fonts.ready) {
      document.fonts.ready.then(function () { window.setTimeout(function () { positionMonthBars(); clampStickers(); }, 0); }).catch(function () {});
    }
    window.addEventListener('resize', onViewportChange);
    if (window.visualViewport) window.visualViewport.addEventListener('resize', onViewportChange);
    window.addEventListener('orientationchange', onViewportChange);
    window.setInterval(refreshNow, 60000);
  }

  function onViewportChange() {
    if (state.resizeRaf) cancelAnimationFrame(state.resizeRaf);
    state.resizeRaf = requestAnimationFrame(function () {
      state.resizeRaf = 0;
      positionMonthBars();
      clampStickers();
    });
  }

  function refreshNow() {
    const nextToday = todayKey();
    if (nextToday !== state.today) {
      state.today = nextToday;
      render();
      return;
    }
    if (state.view === 'month' || !root) return;
    const box = root.querySelector(state.view === 'week' ? '#cal-week-timeline .cal-cols' : '#cal-day-timeline .cal-cols');
    if (!box) return;
    const line = box.querySelector('.cal-now');
    if (line) line.style.top = String((minutesNow() / 60) * HOUR) + 'px';
  }

  function bind() {
    root.addEventListener('click', onClick);
    root.addEventListener('submit', onSubmit);
    root.addEventListener('input', onInput);
    root.addEventListener('change', onChange);
    root.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('pointermove', onPointerMove, { passive: false });
    window.addEventListener('pointerup', onPointerUp);
    window.addEventListener('pointercancel', onPointerUp);
    document.addEventListener('pointerdown', function (event) {
      if (!state.selectedSticker) return;
      const sticker = event.target.closest ? event.target.closest('.placed-sticker') : null;
      const drawer = event.target.closest ? event.target.closest('#cal-sticker-drawer') : null;
      if (!sticker && !drawer) selectSticker(null);
    });
    document.addEventListener('keydown', function (event) {
      if (event.key !== 'Escape') return;
      if (root.querySelector('#cal-theme-modal').classList.contains('is-open')) return closeTheme();
      if (root.querySelector('#cal-day-modal').classList.contains('is-open')) return closeDay();
      if (state.stickerOpen) return toggleStickers(false);
    });
  }

  function onClick(event) {
    const action = event.target.closest('[data-cal]');
    if (action && action.dataset.cal) {
      const name = action.dataset.cal;
      if (name === 'prev') return shift(-1);
      if (name === 'next') return shift(1);
      if (name === 'today') return goToday();
      if (name === 'zoom-in') return setZoom(state.zoom + 0.1);
      if (name === 'zoom-out') return setZoom(state.zoom - 0.1);
      if (name === 'theme') return openTheme();
      if (name === 'close-theme') return closeTheme();
      if (name === 'stickers') return toggleStickers();
      if (name === 'close-stickers') return toggleStickers(false);
      if (name === 'theme-photo') return root.querySelector('#cal-bg-file').click();
      if (name === 'theme-photo-clear') return clearThemePhoto();
      if (name === 'reset-theme') return resetTheme();
      if (name === 'close-day') return closeDay();
      if (name === 'save-day') return saveDay();
      if (name === 'clear-day') return clearDay();
      if (name === 'clear-photo') return clearDayPhoto();
      if (name === 'add') return openDay(dateKey(state.cursor));
    }

    const tab = event.target.closest('.cal-tab[data-view]');
    if (tab) return setView(tab.dataset.view);

    const jump = event.target.closest('[data-jump]');
    if (jump) {
      state.cursor = parseDate(jump.dataset.jump) || state.cursor;
      state.selected = jump.dataset.jump;
      state.view = 'day';
      state.pendingScroll = true;
      state.scrollMinute = Math.max(0, minutesNow() - 90);
      return render();
    }

    const cell = event.target.closest('.cal-cell');
    if (cell) return openDay(cell.dataset.date);

    const block = event.target.closest('.cal-event');
    if (block) return openDay(block.dataset.date);

    const bar = event.target.closest('.cal-bar');
    if (bar) return openDay(bar.dataset.date);

    const monthBar = event.target.closest('.cal-month-bar');
    if (monthBar) return openDay(monthBar.dataset.date);

    const col = event.target.closest('.cal-col');
    if (col) {
      const rect = col.getBoundingClientRect();
      const y = Math.max(0, Math.min(rect.height, event.clientY - rect.top));
      const minutes = clamp(Math.round((y / HOUR) * 2) * 30, 0, 1410);
      return openDay(col.dataset.date, Events.horaDe(minutes));
    }

    const fundo = event.target.closest('[data-fundo]');
    if (fundo) {
      changeTheme({ fundo: fundo.dataset.fundo });
      renderTheme();
      return;
    }
    const quadro = event.target.closest('[data-quadro]');
    if (quadro) {
      changeTheme({ quadros: quadro.dataset.quadro });
      renderTheme();
      return;
    }
    const holiday = event.target.closest('[data-holiday]');
    if (holiday) {
      toggleHoliday(holiday.dataset.holiday);
      return;
    }
    const emojiMode = event.target.closest('[data-emoji-mode]');
    if (emojiMode) {
      setEmojiMode(emojiMode.dataset.emojiMode);
      return;
    }
    const cat = event.target.closest('[data-st-cat]');
    if (cat) {
      state.stickerCategory = cat.dataset.stCat;
      state.stickerLimit = 120;
      renderStickerDrawer();
      return;
    }
    const more = event.target.closest('[data-st-more]');
    if (more) {
      state.stickerLimit += 120;
      renderStickerGrid();
      return;
    }
    const stickerItem = event.target.closest('[data-st-src]');
    if (stickerItem) {
      placeSticker(stickerItem.dataset.stSrc);
      return;
    }
    const eventColor = event.target.closest('[data-event-color]');
    if (eventColor) {
      state.eventColor = eventColor.dataset.eventColor;
      renderEventColors();
      return;
    }
    const reaction = event.target.closest('[data-reaction]');
    if (reaction) {
      if (!state.draft) return;
      const value = reaction.dataset.reaction || '';
      state.draft.reaction = state.draft.reaction === value ? '' : value;
      renderReactions();
      return;
    }
    const deleteEvent = event.target.closest('[data-delete-event]');
    if (deleteEvent) {
      const id = deleteEvent.dataset.deleteEvent;
      saveEvents(Events.semEvento(events(), id));
      renderEventList();
      render();
      return;
    }
    const deleteSticker = event.target.closest('.sticker-handle-del');
    if (deleteSticker) {
      const el = deleteSticker.closest('.placed-sticker');
      removeSticker(el ? el.dataset.id : '');
    }
  }

  function onSubmit(event) {
    if (event.target.id !== 'cal-event-form') return;
    event.preventDefault();
    if (!state.draft) return;
    const title = root.querySelector('#cal-event-title').value.trim();
    if (!title) {
      toast('dê um nome pro evento ♡');
      return;
    }
    let end = root.querySelector('#cal-event-end').value || state.draft.date;
    if (end < state.draft.date) end = state.draft.date;
    const allDay = root.querySelector('#cal-event-allday').checked;
    let hora = '';
    let fimHora = '';
    if (!allDay) {
      hora = root.querySelector('#cal-event-hora').value || '09:00';
      fimHora = root.querySelector('#cal-event-fim-hora').value || hora;
      if (end === state.draft.date && fimHora < hora) fimHora = Events.horaDe(Events.emMinutos(hora) + 60);
    }
    const evento = Events.criar({ titulo: title, inicio: state.draft.date, fim: end, cor: state.eventColor, hora: hora, fimHora: fimHora });
    if (!evento) return;
    saveEvents(Events.comEvento(events(), evento));
    root.querySelector('#cal-event-title').value = '';
    root.querySelector('#cal-event-end').value = state.draft.date;
    renderEventList();
    render();
    toast('evento guardado ♡');
  }

  function onInput(event) {
    if (event.target.id === 'cal-day-notes') {
      renderDayPreview();
      return;
    }
    if (event.target.id === 'cal-sticker-search') {
      state.stickerQuery = event.target.value;
      state.stickerLimit = 120;
      renderStickerGrid();
    }
  }

  function onChange(event) {
    const target = event.target;
    if (target.id === 'cal-photo-file') return useDayPhoto(target);
    if (target.id === 'cal-bg-file') return useThemePhoto(target);
    if (target.id === 'cal-font-file') return useCustomFont(target);
    if (target.id === 'cal-photo-front') {
      if (state.draft && state.draft.photo) state.draft.photo.frente = target.checked;
      return;
    }
    if (target.id === 'cal-event-allday') return syncEventForm();
    if (target.id === 'cal-event-hora') {
      const start = Events.emMinutos(target.value);
      const end = root.querySelector('#cal-event-fim-hora');
      if (end && Events.emMinutos(end.value) <= start) end.value = Events.horaDe(start + 60);
      return;
    }
    if (target.id === 'cal-fundo-cor') return changeTheme({ fundoCor: target.value }, false);
    if (target.id === 'cal-detalhe-cor') return changeTheme({ detalheCor: target.value }, false);
    if (target.id === 'cal-celula-cor') return changeTheme({ celulaCor: target.value }, false);
    if (target.id === 'cal-linha-cor') return changeTheme({ linhaCor: target.value }, false);
    if (target.id === 'cal-texto-cor') return changeTheme({ textoCor: target.value }, false);
    if (target.id === 'cal-fonte-select') return changeTheme({ fonte: target.value });
    if (target.id === 'cal-mini-select') return changeTheme({ mini: target.value });
    if (target.id === 'cal-paleta-select') return changeTheme({ paleta: target.value });
    if (target.id === 'cal-domingo-vermelho') return changeTheme({ domingoVermelho: target.checked });
  }

  function render() {
    if (!root || !state.style) return;
    state.today = todayKey();
    applyStyle();
    root.dataset.view = state.view;
    updateTitle();
    root.querySelectorAll('[data-view]').forEach(function (button) {
      button.classList.toggle('is-active', button.dataset.view === state.view);
    });
    const zoom = root.querySelector('#cal-zoom');
    if (zoom) zoom.hidden = state.view !== 'month';
    if (state.view === 'month') renderMonth();
    else if (state.view === 'week') renderWeek();
    else renderDay();
    if (state.pendingScroll) {
      state.pendingScroll = false;
      requestAnimationFrame(scrollTimeline);
    }
  }

  function updateTitle() {
    const title = root.querySelector('#cal-title');
    if (!title) return;
    if (state.view === 'month') {
      title.textContent = MONTHS[state.month.getMonth()] + ' ' + state.month.getFullYear();
      return;
    }
    if (state.view === 'week') {
      const start = startOfWeek(state.cursor);
      const end = addDays(start, 6);
      const ano = end.getFullYear() !== new Date().getFullYear() ? ' ' + end.getFullYear() : '';
      if (start.getMonth() === end.getMonth()) {
        title.textContent = start.getDate() + ' – ' + end.getDate() + ' de ' + MONTHS[end.getMonth()] + ano;
      } else {
        title.textContent = start.getDate() + ' ' + MONTHS[start.getMonth()].slice(0, 3)
          + ' – ' + end.getDate() + ' ' + MONTHS[end.getMonth()].slice(0, 3) + ano;
      }
      return;
    }
    title.textContent = state.cursor.getDate() + ' de ' + MONTHS[state.cursor.getMonth()];
  }

  function setView(view) {
    if (['month', 'week', 'day'].indexOf(view) === -1) return;
    state.view = view;
    if (view === 'month') state.month = startOfMonth(state.cursor);
    if (view !== 'month') {
      state.pendingScroll = true;
      state.scrollMinute = Math.max(0, minutesNow() - 90);
    }
    render();
    if (view === 'month') clampStickers();
  }

  function shift(step) {
    if (state.view === 'month') {
      const next = new Date(state.month.getFullYear(), state.month.getMonth() + step, 1);
      state.month = next;
      state.cursor = next;
    } else if (state.view === 'week') {
      state.cursor = addDays(state.cursor, step * 7);
      state.month = startOfMonth(state.cursor);
      state.pendingScroll = true;
      state.scrollMinute = Math.max(0, minutesNow() - 90);
    } else {
      state.cursor = addDays(state.cursor, step);
      state.month = startOfMonth(state.cursor);
      state.pendingScroll = true;
      state.scrollMinute = Math.max(0, minutesNow() - 90);
    }
    render();
  }

  function goToday() {
    state.cursor = new Date();
    state.month = startOfMonth(state.cursor);
    state.selected = todayKey();
    state.pendingScroll = true;
    state.scrollMinute = Math.max(0, minutesNow() - 90);
    render();
  }

  function dayEntries(key, max) {
    const list = [];
    const doDia = Events.eventosDoDia(events(), key);
    doDia.filter(function (evento) { return evento.hora; })
      .sort(function (a, b) { return a.hora.localeCompare(b.hora) || a.titulo.localeCompare(b.titulo); })
      .forEach(function (evento) {
        list.push({ titulo: evento.titulo, cor: evento.cor, hora: evento.hora, tipo: 'evento' });
      });
    noteLines(state.notes[key]).forEach(function (title, index) {
      list.push({ titulo: title, cor: Style.corDaTarefa(state.style, index), hora: '', tipo: 'tarefa' });
    });
    return { visible: list.slice(0, max), rest: Math.max(0, list.length - max), total: list.length };
  }

  function renderMonth() {
    const grid = root.querySelector('#cal-grid');
    if (!grid) return;
    const year = state.month.getFullYear();
    const month = state.month.getMonth();
    const first = new Date(year, month, 1);
    const start = addDays(first, -first.getDay());
    const fragment = document.createDocumentFragment();

    for (let index = 0; index < 42; index += 1) {
      const date = addDays(start, index);
      const key = dateKey(date);
      const weekday = index % 7;
      const holiday = holidayName(key);
      const cell = document.createElement('button');
      cell.type = 'button';
      cell.className = 'cal-cell'
        + (date.getMonth() !== month ? ' is-out' : '')
        + (key === state.today ? ' is-today' : '')
        + (weekday === 0 ? ' is-sun' : '')
        + (weekday === 6 ? ' is-sat' : '')
        + (key === state.selected ? ' is-selected' : '')
        + (holiday ? ' is-holiday' : '');
      cell.dataset.date = key;

      const photo = state.photos[key];
      if (photo && photo.src) {
        cell.classList.add('has-photo');
        if (photo.frente) cell.classList.add('photo-front');
        const image = document.createElement('img');
        image.className = 'cal-cell-photo';
        image.src = photo.src;
        image.alt = '';
        image.decoding = 'async';
        cell.appendChild(image);
      }

      const top = document.createElement('span');
      top.className = 'cal-num-row';
      const number = document.createElement('span');
      number.className = 'cal-num';
      number.textContent = String(date.getDate());
      top.appendChild(number);
      const mini = Style.miniDoDia(key, state.style.mini);
      if (mini) {
        const tiny = document.createElement('span');
        tiny.className = 'cal-mini';
        tiny.textContent = mini;
        top.appendChild(tiny);
      }

      const topBox = document.createElement('span');
      topBox.className = 'cal-cell-head';
      topBox.appendChild(top);

      if (holiday) {
        const label = document.createElement('span');
        label.className = 'cal-holiday';
        label.textContent = holiday;
        topBox.appendChild(label);
      }
      cell.appendChild(topBox);

      const entries = dayEntries(key, holiday ? 2 : MAX_LINES);
      if (entries.visible.length) {
        const lines = document.createElement('span');
        lines.className = 'cal-lines';
        entries.visible.forEach(function (entry) {
          const line = document.createElement('span');
          line.className = 'cal-line';
          const dot = document.createElement('i');
          dot.style.background = entry.cor;
          line.appendChild(dot);
          const text = document.createElement('span');
          text.textContent = entry.titulo;
          line.appendChild(text);
          line.title = entry.hora ? entry.hora + ' · ' + entry.titulo : entry.titulo;
          lines.appendChild(line);
        });
        if (entries.rest) {
          const more = document.createElement('span');
          more.className = 'cal-line cal-more';
          more.textContent = '+' + entries.rest;
          lines.appendChild(more);
        }
        cell.appendChild(lines);
      }

      const reaction = state.reactions[key];
      if (reaction) {
        const badge = document.createElement('span');
        badge.className = 'cal-emoji';
        badge.textContent = reaction;
        cell.appendChild(badge);
      }

      const aria = date.getDate() + ' de ' + MONTHS[date.getMonth()]
        + (holiday ? ', ' + holiday : '')
        + (entries.total ? ', ' + entries.total + ' itens' : '');
      cell.setAttribute('aria-label', aria);
      fragment.appendChild(cell);
    }

    grid.innerHTML = '';
    grid.appendChild(fragment);
    positionMonthBars();
  }

  function monthFaixas() {
    return events().filter(function (evento) {
      return !evento.hora;
    }).map(function (evento, index) {
      return {
        id: 'evento:' + evento.id,
        idEvento: evento.id,
        titulo: evento.titulo,
        cor: evento.cor,
        textoCor: Events.corDeTexto(evento.cor),
        inicio: evento.inicio,
        fim: evento.fim,
        tipo: 'evento',
        ordem: index
      };
    });
  }

  function positionMonthBars() {
    const grid = root.querySelector('#cal-grid');
    if (!grid) return;
    const old = grid.querySelector('.cal-bars-layer');
    if (old) old.remove();
    const cells = Array.prototype.slice.call(grid.querySelectorAll('.cal-cell'));
    if (!cells.length) return;

    const dates = cells.map(function (cell) { return cell.dataset.date; });
    const grade = Events.montarGrade({ dias: dates, faixas: monthFaixas() });
    const step = Math.max(12, Math.min(18, Math.round((grid.clientWidth || 380) * 0.032)));
    const altura = Math.max(8, step - 3);
    grid.style.setProperty('--bar-step', step + 'px');
    cells.forEach(function (cell) { cell.style.setProperty('--bars', '0'); });
    if (!grade || !grade.semanas) return;

    const layer = document.createElement('div');
    layer.className = 'cal-bars-layer';

    grade.semanas.forEach(function (week, weekIndex) {
      const lanes = week.totalFaixas || 0;
      for (let coluna = 0; coluna < 7; coluna += 1) {
        const cell = cells[weekIndex * 7 + coluna];
        if (cell) cell.style.setProperty('--bars', String(lanes));
      }

      const grupos = [];
      const grupoDaFaixa = new Map();
      for (let coluna = 0; coluna < 7; coluna += 1) {
        (week.celulas[coluna] || []).forEach(function (segmento) {
          const anterior = grupoDaFaixa.get(segmento.id);
          if (anterior && anterior.colFim === coluna - 1) {
            anterior.colFim = coluna;
          } else {
            const grupo = { segmento: segmento, colInicio: coluna, colFim: coluna };
            grupos.push(grupo);
            grupoDaFaixa.set(segmento.id, grupo);
          }
        });
      }

      grupos.forEach(function (grupo) {
        const celulaInicio = cells[weekIndex * 7 + grupo.colInicio];
        const celulaFim = cells[weekIndex * 7 + grupo.colFim];
        if (!celulaInicio || !celulaFim) return;
        const segmento = grupo.segmento;
        const topo = celulaInicio.querySelector('.cal-cell-head');
        const base = (topo ? topo.offsetTop + topo.offsetHeight : 18) + 2;
        const top = celulaInicio.offsetTop + base + (segmento.lane - 1) * step;
        const esquerda = Math.max(0, celulaInicio.offsetLeft + (segmento.comecaAqui ? 1 : 0));
        const direita = celulaFim.offsetLeft + celulaFim.offsetWidth - (segmento.terminaAqui ? 1 : 0);

        const barra = document.createElement('button');
        barra.type = 'button';
        barra.className = 'cal-month-bar'
          + (segmento.comecaAqui ? ' is-start' : '')
          + (segmento.terminaAqui ? ' is-end' : '');
        barra.dataset.date = segmento.inicio;
        barra.style.setProperty('--c', segmento.cor);
        barra.style.setProperty('--t', segmento.textoCor);
        barra.style.top = top + 'px';
        barra.style.left = esquerda + 'px';
        barra.style.width = Math.max(6, direita - esquerda) + 'px';
        barra.style.height = altura + 'px';
        barra.textContent = segmento.mostrarTitulo ? segmento.titulo : '';
        barra.title = segmento.inicio === segmento.fim
          ? segmento.titulo
          : segmento.titulo + ' · ' + shortDate(segmento.inicio) + ' – ' + shortDate(segmento.fim);
        barra.setAttribute('aria-label', barra.title);
        layer.appendChild(barra);
      });
    });

    grid.appendChild(layer);
  }

  function renderWeek() {
    const start = startOfWeek(state.cursor);
    const dates = [];
    for (let index = 0; index < 7; index += 1) dates.push(dateKey(addDays(start, index)));
    const head = root.querySelector('#cal-week-head');
    if (head) {
      head.innerHTML = '<span class="cal-gutter"></span>' + dates.map(function (key, index) {
        const date = parseDate(key);
        const holiday = holidayName(key);
        return '<button type="button" class="cal-week-day'
          + (key === state.today ? ' is-today' : '')
          + (index === 0 ? ' is-sun' : '')
          + (index === 6 ? ' is-sat' : '')
          + (holiday ? ' is-holiday' : '')
          + '" data-jump="' + key + '">'
          + '<small>' + WEEKDAYS[index] + '</small><b>' + date.getDate() + '</b></button>';
      }).join('');
    }
    renderWeekAllDay(dates);
    renderTimeline(root.querySelector('#cal-week-timeline'), dates, true);
  }

  function allDayBoxes(dates) {
    const faixas = [];
    events().forEach(function (evento) {
      if (evento.hora) return;
      faixas.push({
        id: 'evento:' + evento.id,
        titulo: evento.titulo,
        cor: evento.cor,
        inicio: evento.inicio,
        fim: evento.fim
      });
    });
    Object.keys(state.notes).forEach(function (key) {
      noteLines(state.notes[key]).forEach(function (title, index) {
        faixas.push({
          id: 'tarefa:' + key + ':' + index,
          titulo: title,
          cor: Style.corDaTarefa(state.style, index),
          inicio: key,
          fim: key
        });
      });
    });
    const boxes = [];
    faixas.forEach(function (faixa) {
      let colStart = 0;
      while (colStart < dates.length && dates[colStart] < faixa.inicio) colStart += 1;
      let colEnd = dates.length - 1;
      while (colEnd >= 0 && dates[colEnd] > faixa.fim) colEnd -= 1;
      if (colStart > colEnd || colStart >= dates.length) return;
      boxes.push({
        titulo: faixa.titulo,
        cor: faixa.cor,
        texto: Events.corDeTexto(faixa.cor),
        colStart: colStart,
        colEnd: colEnd,
        deAntes: faixa.inicio < dates[0],
        vaiDepois: faixa.fim > dates[6]
      });
    });
    boxes.sort(function (a, b) {
      return a.colStart - b.colStart
        || (b.colEnd - b.colStart) - (a.colEnd - a.colStart)
        || a.titulo.localeCompare(b.titulo);
    });
    const lanes = [];
    boxes.forEach(function (box) {
      let lane = 0;
      while (true) {
        if (!lanes[lane]) lanes[lane] = [];
        let busy = false;
        for (let col = box.colStart; col <= box.colEnd; col += 1) {
          if (lanes[lane][col]) { busy = true; break; }
        }
        if (!busy) break;
        lane += 1;
      }
      for (let col = box.colStart; col <= box.colEnd; col += 1) lanes[lane][col] = true;
      box.lane = lane;
    });
    return boxes;
  }

  function renderWeekAllDay(dates) {
    const box = root.querySelector('#cal-week-allday');
    if (!box) return;
    const boxes = allDayBoxes(dates);
    const rows = boxes.reduce(function (max, item) { return Math.max(max, item.lane + 1); }, 0);
    const bars = boxes.map(function (item) {
      const label = (item.deAntes ? '◀ ' : '') + item.titulo + (item.vaiDepois ? ' ▶' : '');
      return '<button type="button" class="cal-bar" data-date="' + dates[item.colStart] + '"'
        + ' style="grid-column:' + (item.colStart + 1) + ' / ' + (item.colEnd + 2) + ';grid-row:' + (item.lane + 1)
        + ';--c:' + item.cor + ';--t:' + item.texto + '">' + escape(label) + '</button>';
    }).join('');
    box.innerHTML = '<span class="cal-allday-label">todo</span>'
      + '<div class="cal-allday-bars" style="grid-template-rows:repeat(' + Math.max(1, rows) + ',16px)">' + bars + '</div>';
  }

  function renderDay() {
    const key = dateKey(state.cursor);
    const date = state.cursor;
    const head = root.querySelector('#cal-day-head');
    if (head) {
      const holiday = holidayName(key);
      head.innerHTML = '<div class="cal-day-date"><b>' + WEEKDAYS_LONG[date.getDay()] + '</b>'
        + '<span>' + date.getDate() + ' de ' + MONTHS[date.getMonth()] + '</span></div>'
        + (holiday ? '<p class="cal-day-feriado">' + escape(holiday) + '</p>' : '');
    }
    renderDayAllDay(key);
    renderTimeline(root.querySelector('#cal-day-timeline'), [key], false);
  }

  function renderDayAllDay(key) {
    const box = root.querySelector('#cal-day-allday');
    if (!box) return;
    const chips = [];
    Events.eventosDoDia(events(), key).forEach(function (evento) {
      if (evento.hora) return;
      chips.push({ titulo: evento.titulo, cor: evento.cor, texto: Events.corDeTexto(evento.cor) });
    });
    noteLines(state.notes[key]).forEach(function (title, index) {
      chips.push({ titulo: title, cor: Style.corDaTarefa(state.style, index), texto: '#4a3a34' });
    });
    box.innerHTML = '<span class="cal-allday-label">todo</span>'
      + '<div class="cal-day-allday-list">'
      + (chips.length ? chips.map(function (chip) {
        return '<button type="button" class="cal-chip-bar" data-date="' + key + '" style="--c:' + chip.cor + ';--t:' + chip.texto + '">' + escape(chip.titulo) + '</button>';
      }).join('') : '<span class="cal-day-empty">nada marcado o dia todo</span>')
      + '</div>';
  }

  function dayTimed(key) {
    const items = Events.eventosDoDia(events(), key).filter(function (evento) {
      return Boolean(evento.hora);
    }).map(function (evento) {
      let start = evento.inicio === key ? Events.emMinutos(evento.hora) : 0;
      let end = evento.fim === key ? Events.emMinutos(evento.fimHora || evento.hora) : 1440;
      if (end <= start) end = Math.min(1440, start + 30);
      return {
        id: evento.id,
        titulo: evento.titulo,
        cor: evento.cor,
        texto: Events.corDeTexto(evento.cor),
        hora: evento.hora,
        start: start,
        end: end
      };
    }).sort(function (a, b) { return a.start - b.start || b.end - a.end; });

    let cluster = [];
    let clusterEnd = 0;

    function flush() {
      if (!cluster.length) return;
      const lanes = [];
      cluster.forEach(function (item) {
        let lane = 0;
        while (lanes[lane] != null && lanes[lane] > item.start) lane += 1;
        lanes[lane] = item.end;
        item.lane = lane;
      });
      const total = lanes.length;
      cluster.forEach(function (item) { item.lanes = total; });
      cluster = [];
    }

    items.forEach(function (item) {
      if (cluster.length && item.start >= clusterEnd) flush();
      cluster.push(item);
      clusterEnd = cluster.length === 1 ? item.end : Math.max(clusterEnd, item.end);
    });
    flush();
    return items;
  }

  function renderTimeline(box, dates, week) {
    if (!box) return;
    const labels = [];
    for (let hour = 0; hour < 24; hour += 1) {
      labels.push('<span style="top:' + (hour * HOUR) + 'px">' + String(hour).padStart(2, '0') + ':00</span>');
    }
    const cols = dates.map(dayColumn).join('');
    const rows = 24 * HOUR;
    const nowIndex = dates.indexOf(state.today);
    const now = nowIndex !== -1
      ? '<span class="cal-now" style="top:' + ((minutesNow() / 60) * HOUR) + 'px"></span>'
      : '';
    box.innerHTML = '<div class="cal-timeline-inner">'
      + '<div class="cal-hours" style="height:' + rows + 'px">' + labels.join('') + '</div>'
      + '<div class="cal-cols' + (week ? '' : ' is-day') + '" style="height:' + rows + 'px">' + cols + now + '</div>'
      + '</div>';
  }

  function dayColumn(key) {
    const items = dayTimed(key);
    const blocks = items.map(function (item) {
      const total = item.lanes || 1;
      const width = 100 / total;
      const top = (item.start / 60) * HOUR;
      const height = Math.max(17, ((item.end - item.start) / 60) * HOUR - 1);
      return '<button type="button" class="cal-event" data-date="' + key + '"'
        + ' style="--c:' + item.cor + ';--t:' + item.texto
        + ';top:' + top + 'px;height:' + height + 'px'
        + ';left:calc(' + (item.lane * width) + '% + 1px);width:calc(' + width + '% - 2px)">'
        + '<b>' + item.hora + '</b><span>' + escape(item.titulo) + '</span></button>';
    }).join('');
    return '<div class="cal-col" data-date="' + key + '">' + blocks + '</div>';
  }

  function scrollTimeline() {
    if (state.view !== 'week' && state.view !== 'day') return;
    const box = root.querySelector(state.view === 'week' ? '#cal-week-timeline' : '#cal-day-timeline');
    if (!box) return;
    box.scrollTop = (state.scrollMinute / 60) * HOUR;
  }

  function openDay(key, hora) {
    if (!state.style) return;
    const date = parseDate(key);
    if (!date) return;
    state.selected = key;
    state.cursor = date;
    state.month = startOfMonth(date);
    try {
      if (window.LittlePauseCalendar && window.LittlePauseCalendar.onDateSelected) {
        window.LittlePauseCalendar.onDateSelected(key);
      }
    } catch (error) { }
    markSelected();
    const photo = state.photos[key];
    state.draft = {
      date: key,
      notes: state.notes[key] || '',
      reaction: state.reactions[key] || '',
      photo: photo && photo.src ? { src: photo.src, frente: Boolean(photo.frente) } : null
    };
    const modal = root.querySelector('#cal-day-modal');
    modal.querySelector('#cal-day-date').textContent = date.toLocaleDateString('pt-BR', {
      weekday: 'long', day: 'numeric', month: 'long'
    });
    const holiday = holidayName(key);
    const holidayBox = modal.querySelector('#cal-day-holiday');
    holidayBox.textContent = holiday;
    holidayBox.hidden = !holiday;
    modal.querySelector('#cal-day-notes').value = state.draft.notes;
    root.querySelector('#cal-photo-file').value = '';
    resetEventForm(hora || '');
    modal.classList.add('is-open');
    renderDayPreview();
    renderReactions();
    renderEventColors();
    renderEventList();
    renderDayPhoto();
    if (window.innerWidth > 700) {
      window.setTimeout(function () { modal.querySelector('#cal-day-notes').focus(); }, 60);
    }
  }

  function markSelected() {
    root.querySelectorAll('.cal-cell.is-selected').forEach(function (cell) {
      cell.classList.remove('is-selected');
    });
    const cell = root.querySelector('.cal-cell[data-date="' + state.selected + '"]');
    if (cell) cell.classList.add('is-selected');
  }

  function resetEventForm(hora) {
    const allDay = root.querySelector('#cal-event-allday');
    const start = root.querySelector('#cal-event-hora');
    const end = root.querySelector('#cal-event-fim-hora');
    const endDate = root.querySelector('#cal-event-end');
    const title = root.querySelector('#cal-event-title');
    if (!allDay || !start || !end || !endDate) return;
    allDay.checked = !hora;
    start.value = hora || '09:00';
    end.value = Events.horaDe(Events.emMinutos(start.value) + 60);
    endDate.value = state.draft ? state.draft.date : todayKey();
    endDate.min = endDate.value;
    if (title) title.value = '';
    syncEventForm();
  }

  function syncEventForm() {
    const allDay = root.querySelector('#cal-event-allday');
    const off = !allDay || allDay.checked;
    root.querySelectorAll('.cal-time-field').forEach(function (field) {
      field.classList.toggle('is-off', off);
      const input = field.querySelector('input');
      if (input) input.disabled = off;
    });
  }

  function closeDay() {
    const modal = root.querySelector('#cal-day-modal');
    if (modal) modal.classList.remove('is-open');
    state.draft = null;
  }

  function renderDayPreview() {
    const box = root.querySelector('#cal-day-preview');
    if (!box || !state.draft) return;
    const textarea = root.querySelector('#cal-day-notes');
    const lines = noteLines(textarea ? textarea.value : '');
    box.innerHTML = lines.length
      ? lines.map(function (line, index) {
        return '<span class="cal-day-task" style="--cal-tarefa-cor:' + Style.corDaTarefa(state.style, index) + '">' + escape(line) + '</span>';
      }).join('')
      : '<span class="cal-day-preview-empty">as tarefinhas aparecem aqui ⋆</span>';
  }

  function renderReactions() {
    const box = root.querySelector('#cal-reactions');
    if (!box || !state.draft) return;
    box.innerHTML = REACTIONS.map(function (emoji) {
      const chosen = state.draft.reaction === emoji;
      return '<button class="cal-reaction' + (chosen ? ' is-chosen' : '') + '" type="button" data-reaction="' + emoji + '" aria-label="marcar o dia com ' + emoji + '" aria-pressed="' + (chosen ? 'true' : 'false') + '">' + emoji + '</button>';
    }).join('') + '<button class="cal-reaction cal-reaction-clear' + (state.draft.reaction ? '' : ' is-chosen') + '" type="button" data-reaction="" aria-label="sem carinha">sem carinha</button>';
  }

  function renderEventColors() {
    const box = root.querySelector('#cal-event-colors');
    if (!box) return;
    box.innerHTML = Events.CORES.map(function (color) {
      return '<button class="cal-color' + (color === state.eventColor ? ' is-chosen' : '') + '" type="button" data-event-color="' + color + '" style="--cal-color:' + color + '" aria-label="cor ' + color + '"></button>';
    }).join('');
  }

  function renderEventList() {
    const box = root.querySelector('#cal-event-list');
    if (!box || !state.draft) return;
    const list = Events.eventosDoDia(events(), state.draft.date).sort(function (a, b) {
      return (a.hora || '99:99').localeCompare(b.hora || '99:99') || a.titulo.localeCompare(b.titulo);
    });
    box.innerHTML = list.length ? list.map(function (evento) {
      return '<div class="cal-event-item">'
        + '<i style="--cal-color:' + evento.cor + '"></i>'
        + '<div><strong>' + escape(evento.titulo) + '</strong><small>' + escape(rangeLabel(evento)) + '</small></div>'
        + '<button type="button" data-delete-event="' + escape(evento.id) + '" aria-label="apagar evento">×</button>'
        + '</div>';
    }).join('') : '<p class="cal-event-empty">nenhum evento por aqui ♡</p>';
  }

  function renderDayPhoto() {
    const box = root.querySelector('#cal-photo-preview');
    const clear = root.querySelector('#cal-photo-clear');
    const front = root.querySelector('#cal-photo-front');
    if (!box) return;
    const photo = state.draft && state.draft.photo;
    box.innerHTML = photo && photo.src ? '<img src="' + escape(photo.src) + '" alt="foto do dia">' : '<span class="cal-photo-empty">nenhuma fotinha ainda ⋆</span>';
    if (clear) clear.hidden = !photo;
    if (front) front.checked = Boolean(photo && photo.frente);
  }

  async function useDayPhoto(input) {
    const file = input.files && input.files[0];
    input.value = '';
    if (!file || !state.draft) return;
    const data = await shrinkImage(file, 520, 190 * 1024);
    if (!data) {
      toast('essa foto é grande demais 🌧️ tenta outra?', 'error');
      return;
    }
    state.draft.photo = { src: data, frente: false };
    renderDayPhoto();
  }

  function clearDayPhoto() {
    if (!state.draft) return;
    state.draft.photo = null;
    renderDayPhoto();
  }

  function saveDay() {
    if (!state.draft) return;
    const key = state.draft.date;
    const text = root.querySelector('#cal-day-notes').value.trim();
    if (text) state.notes[key] = text;
    else delete state.notes[key];
    if (state.draft.reaction) state.reactions[key] = state.draft.reaction;
    else delete state.reactions[key];
    if (state.draft.photo && state.draft.photo.src) state.photos[key] = state.draft.photo;
    else delete state.photos[key];
    const savedNotes = saveObject(NOTE_KEY, state.notes);
    const savedReactions = saveObject(REACTION_KEY, state.reactions);
    const savedPhotos = saveObject(PHOTO_KEY, state.photos);
    const saved = savedNotes && savedReactions && savedPhotos;
    closeDay();
    render();
    toast(saved ? 'dia guardado ♡' : 'não consegui guardar tudo. o armazenamento pode estar cheio.', saved ? undefined : 'error');
  }

  function clearDay() {
    if (!state.draft) return;
    const key = state.draft.date;
    delete state.notes[key];
    delete state.reactions[key];
    delete state.photos[key];
    saveObject(NOTE_KEY, state.notes);
    saveObject(REACTION_KEY, state.reactions);
    saveObject(PHOTO_KEY, state.photos);
    closeDay();
    render();
    toast('dia limpinho ♡');
  }

  function setZoom(value) {
    state.zoom = clamp(Math.round(value * 100) / 100, 0.75, 1.25);
    applyStyle();
    try { localStorage.setItem(ZOOM_KEY, String(state.zoom)); } catch (error) { }
    requestAnimationFrame(positionMonthBars);
  }

  function openTheme() {
    renderTheme();
    root.querySelector('#cal-theme-modal').classList.add('is-open');
  }

  function closeTheme() {
    root.querySelector('#cal-theme-modal').classList.remove('is-open');
  }

  function changeTheme(patch, redraw) {
    state.style = Style.normalizar(Object.assign({}, state.style, patch));
    Style.guardar(localStorage, state.style);
    applyStyle();
    if (redraw !== false) render();
  }

  function toggleHoliday(id) {
    if (!Holidays) return;
    const existe = Holidays.PAISES.some(function (pais) { return pais.id === id; });
    if (!existe) return;
    const index = state.holidays.indexOf(id);
    if (index === -1) state.holidays.push(id);
    else state.holidays.splice(index, 1);
    state.holidays = Holidays.PAISES.filter(function (pais) {
      return state.holidays.indexOf(pais.id) !== -1;
    }).map(function (pais) { return pais.id; });
    try { localStorage.setItem(HOLIDAY_KEY, JSON.stringify(state.holidays)); } catch (error) { }
    render();
    renderTheme();
  }

  function setEmojiMode(mode) {
    state.emoji = mode === 'aparelho' ? 'aparelho' : 'iphone';
    applyEmojiStyle();
    try { localStorage.setItem(EMOJI_KEY, state.emoji); } catch (error) { }
    renderTheme();
  }

  function renderTheme() {
    const style = state.style;
    const fundos = Style.FUNDOS.filter(function (fundo) { return fundo !== 'foto' || style.foto; });
    const fundoGrid = root.querySelector('#cal-fundo-grid');
    fundoGrid.innerHTML = fundos.map(function (fundo) {
      const sample = Style.fundoCss(Object.assign({}, style, { fundo: fundo }));
      return '<button class="cal-fundo-option' + (style.fundo === fundo ? ' is-chosen' : '') + '" type="button" data-fundo="' + fundo + '" title="fundo ' + (FUNDO_NOMES[fundo] || fundo) + '" aria-label="fundo ' + (FUNDO_NOMES[fundo] || fundo) + '" style="background:' + escape(sample) + '"><span>' + (FUNDO_NOMES[fundo] || fundo) + '</span></button>';
    }).join('');

    root.querySelector('#cal-quadros-grid').innerHTML = Style.QUADROS.map(function (quadro) {
      return '<button class="cal-quadro-option' + (style.quadros === quadro ? ' is-chosen' : '') + '" type="button" data-quadro="' + quadro + '" title="quadradinhos ' + (QUADRO_NOMES[quadro] || quadro) + '">'
        + '<span class="cal-quadro-preview q-' + quadro + '"><i></i><i></i><i></i><i></i></span>'
        + '<span>' + (QUADRO_NOMES[quadro] || quadro) + '</span></button>';
    }).join('');

    root.querySelector('#cal-fundo-cor').value = style.fundoCor;
    root.querySelector('#cal-detalhe-cor').value = style.detalheCor;
    root.querySelector('#cal-celula-cor').value = style.celulaCor;
    root.querySelector('#cal-linha-cor').value = style.linhaCor;
    root.querySelector('#cal-texto-cor').value = style.textoCor;
    const fonteSelect = root.querySelector('#cal-fonte-select');
    if (fonteSelect) {
      const fontes = Object.keys(FONTE_NOMES).filter(function (key) {
        return key !== 'custom' || state.customFont;
      });
      fonteSelect.innerHTML = fontes.map(function (key) {
        const label = key === 'custom' && state.customFont
          ? '★ ' + state.customFont.name
          : FONTE_NOMES[key];
        return '<option value="' + key + '">' + escape(label) + '</option>';
      }).join('');
      fonteSelect.value = fontes.indexOf(style.fonte) !== -1 ? style.fonte : 'jorte';
    }
    root.querySelector('#cal-mini-select').value = style.mini;
    root.querySelector('#cal-paleta-select').value = style.paleta;
    root.querySelector('#cal-domingo-vermelho').checked = style.domingoVermelho;
    root.querySelector('#cal-bg-clear').hidden = !style.foto;

    const holidayGrid = root.querySelector('#cal-holiday-grid');
    if (holidayGrid) {
      holidayGrid.innerHTML = (Holidays ? Holidays.PAISES : []).map(function (pais) {
        const chosen = state.holidays.indexOf(pais.id) !== -1;
        return '<button class="cal-theme-chip' + (chosen ? ' is-chosen' : '') + '" type="button" data-holiday="' + pais.id + '" aria-pressed="' + (chosen ? 'true' : 'false') + '">' + pais.nome + '</button>';
      }).join('');
    }

    const emojiGrid = root.querySelector('#cal-emoji-grid');
    if (emojiGrid) {
      emojiGrid.innerHTML = [
        { id: 'iphone', nome: 'iPhone' },
        { id: 'aparelho', nome: 'do aparelho' }
      ].map(function (modo) {
        const chosen = state.emoji === modo.id;
        return '<button class="cal-theme-chip' + (chosen ? ' is-chosen' : '') + '" type="button" data-emoji-mode="' + modo.id + '" aria-pressed="' + (chosen ? 'true' : 'false') + '">' + modo.nome + '</button>';
      }).join('');
    }
  }

  async function useThemePhoto(input) {
    const file = input.files && input.files[0];
    input.value = '';
    if (!file) return;
    const data = await shrinkImage(file, 900, Style.LIMITE_DA_FOTO);
    if (!data) {
      toast('essa foto é grande demais 🌧️ tenta outra?', 'error');
      return;
    }
    changeTheme({ fundo: 'foto', foto: data });
    renderTheme();
  }

  function clearThemePhoto() {
    changeTheme({ foto: '', fundo: state.style.fundo === 'foto' ? 'nuvens' : state.style.fundo });
    renderTheme();
  }

  function readFontData(file) {
    return new Promise(function (resolve, reject) {
      const reader = new FileReader();
      reader.onload = function () { resolve(String(reader.result || '')); };
      reader.onerror = function () { reject(reader.error || new Error('fonte inválida')); };
      reader.readAsDataURL(file);
    });
  }

  async function registrarFonteCustom(data, name) {
    if (!data || typeof FontFace !== 'function' || !document.fonts) return false;
    try {
      const face = new FontFace(CUSTOM_FONT_FAMILY, 'url(' + data + ')');
      await face.load();
      if (state.customFace) {
        try { document.fonts.delete(state.customFace); } catch (error) { }
      }
      document.fonts.add(face);
      state.customFace = face;
      state.customFont = { name: name || 'minha fonte', data: data };
      Style.FAMILIAS.custom = "'" + CUSTOM_FONT_FAMILY + "', 'Nunito', sans-serif";
      return true;
    } catch (error) {
      return false;
    }
  }

  async function useCustomFont(input) {
    const file = input.files && input.files[0];
    input.value = '';
    if (!file) return;
    if (file.size > 2.5 * 1024 * 1024) {
      toast('essa fonte é grande demais (até 2,5 MB) 🌧️', 'error');
      return;
    }
    let data = '';
    try {
      data = await readFontData(file);
    } catch (error) {
      toast('não consegui ler essa fonte 🌧️', 'error');
      return;
    }
    const name = String(file.name || '').replace(/\.[^.]+$/, '') || 'minha fonte';
    if (!(await registrarFonteCustom(data, name))) {
      toast('não consegui carregar essa fonte 🌧️ tenta outra?', 'error');
      return;
    }
    let saved = true;
    try {
      localStorage.setItem(CUSTOM_FONT_KEY, JSON.stringify({ name: name, data: data }));
    } catch (error) {
      saved = false;
    }
    changeTheme({ fonte: 'custom' }, false);
    render();
    renderTheme();
    toast(saved ? 'fonte "' + name + '" carregada ♡' : 'a fonte vale só até fechar o app (grande demais pra guardar)', saved ? undefined : 'error');
  }

  async function restoreCustomFont() {
    if (!Style) return;
    let raw = null;
    try { raw = JSON.parse(localStorage.getItem(CUSTOM_FONT_KEY) || 'null'); } catch (error) { }
    if (!raw || !raw.data) {
      if (state.style && state.style.fonte === 'custom') {
        state.style = Style.normalizar(Object.assign({}, state.style, { fonte: 'jorte' }));
        Style.guardar(localStorage, state.style);
        applyStyle();
      }
      return;
    }
    if (await registrarFonteCustom(raw.data, raw.name)) {
      if (state.style && state.style.fonte === 'custom') {
        applyStyle();
        render();
      }
      renderTheme();
    } else if (state.style && state.style.fonte === 'custom') {
      state.style = Style.normalizar(Object.assign({}, state.style, { fonte: 'jorte' }));
      Style.guardar(localStorage, state.style);
      applyStyle();
      render();
    }
  }

  function resetTheme() {
    state.style = Style.padrao();
    Style.guardar(localStorage, state.style);
    applyStyle();
    render();
    renderTheme();
    toast('calendário de volta ao padrão ♡');
  }

  async function shrinkImage(file, maxSide, maxBytes) {
    let source = null;
    try {
      source = await loadBitmap(file);
    } catch (error) {
      return '';
    }
    if (!source) return '';
    const width = source.width || source.naturalWidth || 1;
    const height = source.height || source.naturalHeight || 1;
    let scale = Math.min(1, maxSide / Math.max(width, height));
    const canvas = document.createElement('canvas');
    for (let attempt = 0; attempt < 6; attempt += 1) {
      canvas.width = Math.max(1, Math.round(width * scale));
      canvas.height = Math.max(1, Math.round(height * scale));
      const context = canvas.getContext('2d');
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.drawImage(source, 0, 0, canvas.width, canvas.height);
      const url = canvas.toDataURL('image/webp', attempt < 2 ? 0.76 : 0.6);
      if (!url || url === 'data:,') continue;
      if (url.length <= maxBytes || attempt === 5) return url;
      scale *= 0.78;
    }
    return '';
  }

  function loadBitmap(file) {
    if (window.createImageBitmap) {
      return window.createImageBitmap(file).catch(function () { return loadImageElement(file); });
    }
    return loadImageElement(file);
  }

  function loadImageElement(file) {
    return new Promise(function (resolve, reject) {
      const image = new Image();
      const url = URL.createObjectURL(file);
      image.onload = function () {
        URL.revokeObjectURL(url);
        resolve(image);
      };
      image.onerror = function () {
        URL.revokeObjectURL(url);
        reject(new Error('imagem inválida'));
      };
      image.src = url;
    });
  }

  function normalizeSticker(raw, index) {
    if (!raw || typeof raw !== 'object') return null;
    const src = String(raw.src || '').trim();
    if (!src) return null;
    const item = {
      id: String(raw.id || uid('st')),
      src: src,
      x: clamp(raw.x, -400, 4000),
      y: clamp(raw.y, -400, 4000),
      width: clamp(raw.width || 85, 24, 600),
      rotation: clamp(raw.rotation, -180, 180),
      flip: Boolean(raw.flip),
      border: Boolean(raw.border),
      z: Number(raw.z) || 21 + (index || 0)
    };
    if (raw.pctX != null && raw.pctY != null) {
      item.pctX = clamp(raw.pctX, 0, 100);
      item.pctY = clamp(raw.pctY, 0, 100);
    }
    return item;
  }

  function saveStickers() {
    if (saveObject(STICKER_KEY, state.stickers)) return;
    if (state.savedOnce) return;
    state.savedOnce = true;
    toast('não consegui guardar os adesivos. o armazenamento pode estar cheio.', 'error');
  }

  function overlay() {
    return root.querySelector('#cal-stickers-overlay');
  }

  function restoreStickers() {
    const layer = overlay();
    if (!layer) return;
    const width = layer.clientWidth || 600;
    const height = layer.clientHeight || 520;
    let moved = false;
    state.stickers.forEach(function (item) {
      if (item.pctX == null) return;
      item.x = clamp((item.pctX / 100) * width, 0, width);
      item.y = clamp((item.pctY / 100) * height, 0, height);
      delete item.pctX;
      delete item.pctY;
      moved = true;
    });
    state.stickers.forEach(function (item) { createStickerEl(item); });
    clampStickers();
    if (moved) saveStickers();
  }

  function migrateSticker(raw) {
    if (!raw || typeof raw !== 'object') return null;
    if (raw.src) {
      const legacy = raw.width == null && Number(raw.x) <= 100 && Number(raw.y) <= 100;
      if (!legacy) return raw;
      return Object.assign({}, raw, {
        width: clamp(Number(raw.size) * 1.6 || 110, 24, 620),
        pctX: clamp(Number(raw.x), 0, 100),
        pctY: clamp(Number(raw.y), 0, 100)
      });
    }
    if (!raw.emoji) return null;
    return {
      id: String(raw.id || uid('st')),
      src: emojiSticker(String(raw.emoji)),
      width: clamp((Number(raw.size) || 34) * 1.8, 24, 240),
      rotation: clamp(Number(raw.rotation) || 0, -180, 180),
      pctX: clamp(Number(raw.x), 0, 100),
      pctY: clamp(Number(raw.y), 0, 100)
    };
  }

  function emojiSticker(emoji) {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96">'
      + '<text x="48" y="74" font-size="68" text-anchor="middle">' + emoji + '</text></svg>';
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  }

  function createStickerEl(item) {
    const layer = overlay();
    if (!layer) return null;
    const el = document.createElement('div');
    el.className = 'placed-sticker' + (item.border ? ' has-border' : '');
    el.dataset.id = item.id;
    el.style.left = item.x + 'px';
    el.style.top = item.y + 'px';
    el.style.width = item.width + 'px';
    el.style.height = item.width + 'px';
    el.style.zIndex = String(item.z);
    const image = document.createElement('img');
    image.src = item.src;
    image.alt = 'adesivo';
    image.draggable = false;
    image.decoding = 'async';
    image.addEventListener('load', function () { clampStickerEl(el, item); });
    image.addEventListener('error', function () { el.classList.add('is-broken'); });
    el.appendChild(image);
    el.insertAdjacentHTML('beforeend', ''
      + '<span class="sticker-handle-rotate" title="girar">🔄</span>'
      + '<span class="sticker-handle-resize" title="aumentar">↘️</span>'
      + '<span class="sticker-handle-del" title="tirar">✕</span>');
    applyStickerTransform(el, item);
    layer.appendChild(el);
    return el;
  }

  function applyStickerTransform(el, item) {
    el.style.transform = 'rotate(' + item.rotation + 'deg)' + (item.flip ? ' scaleX(-1)' : '');
    el.style.width = item.width + 'px';
    el.style.height = item.width + 'px';
  }

  function placeSticker(src) {
    if (!src) return;
    if (state.view !== 'month') {
      state.view = 'month';
      state.month = startOfMonth(state.cursor);
      render();
    }
    const layer = overlay();
    if (!layer) return;
    const width = layer.clientWidth || 600;
    const height = layer.clientHeight || 520;
    const size = 85;
    const item = {
      id: uid('st'),
      src: src,
      x: Math.max(4, Math.min(width - size - 4, width * 0.55 + (state.stickers.length % 5) * 14)),
      y: Math.max(4, Math.min(height - size - 4, height * 0.3 + (state.stickers.length % 5) * 14)),
      width: size,
      rotation: 0,
      flip: false,
      border: false,
      z: ++state.zCounter
    };
    state.stickers.push(item);
    const el = createStickerEl(item);
    clampStickerEl(el, item);
    saveStickers();
    selectSticker(el);
  }

  function selectSticker(el) {
    if (state.selectedSticker && state.selectedSticker !== el) {
      state.selectedSticker.classList.remove('selected');
    }
    state.selectedSticker = el || null;
    if (el) el.classList.add('selected');
  }

  function stickerItem(id) {
    return state.stickers.find(function (item) { return item.id === id; }) || null;
  }

  function removeSticker(id) {
    const item = stickerItem(id);
    const el = overlay() ? overlay().querySelector('.placed-sticker[data-id="' + String(id).replace(/"/g, '\\"') + '"]') : null;
    state.stickers = state.stickers.filter(function (entry) { return entry.id !== id; });
    if (el && el.parentElement) el.parentElement.removeChild(el);
    if (state.selectedSticker === el) state.selectedSticker = null;
    if (item) saveStickers();
  }

  function onPointerDown(event) {
    const el = event.target.closest ? event.target.closest('.placed-sticker') : null;
    if (!el || !overlay() || !overlay().contains(el)) return;
    const item = stickerItem(el.dataset.id);
    if (!item) return;
    if (event.target.closest('.sticker-handle-del')) return;
    event.preventDefault();
    selectSticker(el);
    const elRect = el.getBoundingClientRect();
    const rotate = Boolean(event.target.closest('.sticker-handle-rotate'));
    const resize = Boolean(event.target.closest('.sticker-handle-resize'));
    state.drag = {
      type: rotate ? 'rotate' : (resize ? 'resize' : 'move'),
      el: el,
      item: item,
      startX: event.clientX,
      startY: event.clientY,
      origX: item.x,
      origY: item.y,
      origWidth: item.width,
      origRotation: item.rotation,
      centerX: elRect.left + elRect.width / 2,
      centerY: elRect.top + elRect.height / 2,
      startAngle: Math.atan2(event.clientY - (elRect.top + elRect.height / 2), event.clientX - (elRect.left + elRect.width / 2)) * 180 / Math.PI
    };
    el.classList.add('is-dragging');
  }

  function onPointerMove(event) {
    const drag = state.drag;
    if (!drag) return;
    event.preventDefault();
    const dx = event.clientX - drag.startX;
    const dy = event.clientY - drag.startY;
    const item = drag.item;
    const el = drag.el;

    if (drag.type === 'move') {
      item.x = drag.origX + dx;
      item.y = drag.origY + dy;
      el.style.left = item.x + 'px';
      el.style.top = item.y + 'px';
      clampStickerEl(el, item);
      return;
    }

    if (drag.type === 'rotate') {
      const angle = Math.atan2(event.clientY - drag.centerY, event.clientX - drag.centerX) * 180 / Math.PI;
      item.rotation = normalizeAngle(drag.origRotation + (angle - drag.startAngle));
      applyStickerTransform(el, item);
      return;
    }

    const radians = item.rotation * Math.PI / 180;
    const delta = dx * Math.cos(radians) + dy * Math.sin(radians);
    item.width = clamp(drag.origWidth + delta, 24, 620);
    applyStickerTransform(el, item);
  }

  function onPointerUp() {
    if (!state.drag) return;
    state.drag.el.classList.remove('is-dragging');
    state.drag = null;
    saveStickers();
  }

  function normalizeAngle(value) {
    let angle = Number(value) || 0;
    while (angle > 180) angle -= 360;
    while (angle < -180) angle += 360;
    return angle;
  }

  function clampStickerEl(el, item) {
    const layer = overlay();
    if (!layer || !el) return;
    const width = layer.clientWidth;
    const height = layer.clientHeight;
    if (!width || !height) return;
    const size = item.width || 85;
    const minX = 0;
    const maxX = width - size - 2;
    const minY = 4;
    const maxY = height - size - 4;
    const nextX = clamp(item.x, minX, Math.max(minX, maxX));
    const nextY = clamp(item.y, minY, Math.max(minY, maxY));
    if (nextX !== item.x || nextY !== item.y) {
      item.x = nextX;
      item.y = nextY;
      el.style.left = nextX + 'px';
      el.style.top = nextY + 'px';
    }
  }

  function clampStickers() {
    const layer = overlay();
    if (!layer) return;
    layer.querySelectorAll('.placed-sticker').forEach(function (el) {
      const item = stickerItem(el.dataset.id);
      if (item) clampStickerEl(el, item);
    });
  }

  function toggleStickers(force) {
    state.stickerOpen = typeof force === 'boolean' ? force : !state.stickerOpen;
    const drawer = root.querySelector('#cal-sticker-drawer');
    if (!drawer) return;
    if (state.stickerOpen && state.view !== 'month') {
      state.view = 'month';
      state.month = startOfMonth(state.cursor);
      render();
      clampStickers();
    }
    drawer.classList.toggle('is-open', state.stickerOpen);
    if (state.stickerOpen) {
      renderStickerDrawer();
      if (!state.stickersLoaded && !state.stickersLoading) loadStickers();
      const search = root.querySelector('#cal-sticker-search');
      if (search && window.innerWidth > 700) window.setTimeout(function () { search.focus(); }, 120);
    }
  }

  async function loadStickers() {
    state.stickersLoading = true;
    renderStickerGrid();
    try {
      const list = Array.isArray(window.LP_STICKER_MANIFEST) ? window.LP_STICKER_MANIFEST : [];
      const categories = new Set(['all']);
      state.stickerAll = list.map(function (rel) {
        const parts = String(rel).split('/');
        const category = parts.length >= 3 ? parts[parts.length - 2] : 'random';
        categories.add(category);
        return {
          src: String(rel),
          category: category,
          name: (parts[parts.length - 1] || '').replace(/\.[^.]+$/, '').toLowerCase()
        };
      }).filter(function (item) { return item.src; });
      state.stickerCategories = ['all'].concat(Array.from(categories).filter(function (cat) { return cat !== 'all'; }).sort());
      state.stickersLoaded = true;
    } catch (error) {
      state.stickerAll = [];
      state.stickersLoaded = true;
    } finally {
      state.stickersLoading = false;
      renderStickerDrawer();
    }
  }

  function renderStickerDrawer() {
    if (!state.stickerOpen) return;
    const cats = root.querySelector('#cal-sticker-cats');
    if (cats) {
      cats.innerHTML = state.stickerCategories.map(function (cat) {
        const label = cat === 'all' ? 'todos' : (STICKER_CATEGORY_LABELS[cat] || cat);
        return '<button class="cal-st-cat' + (state.stickerCategory === cat ? ' is-active' : '') + '" type="button" data-st-cat="' + escape(cat) + '">' + escape(label) + '</button>';
      }).join('');
    }
    const search = root.querySelector('#cal-sticker-search');
    if (search && search.value !== state.stickerQuery) search.value = state.stickerQuery;
    renderStickerGrid();
  }

  function renderStickerGrid() {
    const grid = root.querySelector('#cal-sticker-grid');
    if (!grid) return;
    if (state.stickersLoading) {
      grid.innerHTML = '<p class="cal-st-empty">carregando adesivos...</p>';
      return;
    }
    if (!state.stickersLoaded) {
      grid.innerHTML = '<p class="cal-st-empty">abrindo a gavetinha...</p>';
      return;
    }
    const query = state.stickerQuery.trim().toLowerCase();
    const list = state.stickerAll.filter(function (item) {
      if (state.stickerCategory !== 'all' && item.category !== state.stickerCategory) return false;
      return !query || item.name.indexOf(query) !== -1;
    });
    const visible = list.slice(0, state.stickerLimit);
    const more = list.length > visible.length
      ? '<button class="cal-st-more" type="button" data-st-more>mostrar mais (' + (list.length - visible.length) + ')</button>'
      : '';
    grid.innerHTML = visible.length ? visible.map(function (item) {
      return '<button class="cal-st-item" type="button" data-st-src="' + escape(item.src) + '" title="' + escape(item.name) + '">'
        + '<img src="' + escape(item.src) + '" alt="' + escape(item.name) + '">'
        + '</button>';
    }).join('') + more : '<p class="cal-st-empty">nenhum adesivo encontrado</p>';
  }

  let pendingTaskImport = null;

  function importTasks(map) {
    if (!map || typeof map !== 'object') return;
    if (!root || !root.dataset.ready) {
      pendingTaskImport = map;
      return;
    }
    applyTaskImport(map);
  }

  function applyTaskImport(map) {
    try {
      if (localStorage.getItem(TASKS_MIGRATED_KEY) === '1') return;
      let changed = false;
      Object.keys(map).forEach(function (key) {
        if (!parseDate(key)) return;
        const incoming = String(map[key] || '').split(/\r?\n/).map(function (line) {
          return line.trim();
        }).filter(Boolean);
        if (!incoming.length) return;
        const current = String(state.notes[key] || '').split(/\r?\n/).map(function (line) {
          return line.trim();
        }).filter(Boolean);
        const merged = current.concat(incoming.filter(function (line) {
          return current.indexOf(line) === -1;
        }));
        state.notes[key] = merged.join('\n');
        changed = true;
      });
      if (changed) saveObject(NOTE_KEY, state.notes);
      localStorage.setItem(TASKS_MIGRATED_KEY, '1');
      if (changed) render();
    } catch (error) { }
  }

  function closeTop() {
    if (!root) return false;
    const theme = root.querySelector('#cal-theme-modal');
    if (theme && theme.classList.contains('is-open')) {
      closeTheme();
      return true;
    }
    const day = root.querySelector('#cal-day-modal');
    if (day && day.classList.contains('is-open')) {
      closeDay();
      return true;
    }
    if (state.stickerOpen) {
      toggleStickers(false);
      return true;
    }
    if (state.selectedSticker) {
      selectSticker(null);
      return true;
    }
    if (state.view !== 'month') {
      setView('month');
      return true;
    }
    return false;
  }

  window.LittlePausePlanner = {
    init: init,
    closeTop: closeTop,
    importTasks: importTasks,
    redraw: function () {
      render();
      clampStickers();
    }
  };

  function boot() {
    try {
      init();
      if (pendingTaskImport) {
        applyTaskImport(pendingTaskImport);
        pendingTaskImport = null;
      }
      window.setTimeout(clampStickers, 80);
    } catch (error) {
      console.error('Little Pause calendar boot failed:', error);
      const target = document.getElementById('planner-root');
      if (target) {
        target.innerHTML = '<div class="planner-error"><strong>não consegui abrir o calendário</strong><p>houve um erro ao montar a tela. feche e abra o calendário novamente ♡</p></div>';
      }
      if (typeof window.__littlePauseCalendarReady === 'function') {
        window.__littlePauseCalendarReady(0);
      }
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
