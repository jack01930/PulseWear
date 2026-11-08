'use strict';

const deviceName = 'Pulse Band S1';
const readings = [66, 70, 68, 78, 73, 76, 71, 74];
const demoSteps = 6842;
const storageKey = 'pulsewear.web.history.v1';
const panel = document.querySelector('#panel');
const announcement = document.querySelector('#announcement');
const tabs = document.querySelector('.tabs');
const clock = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const number = new Intl.NumberFormat('zh-CN');
document.body.classList.toggle('app-only', new URLSearchParams(location.search).get('view') === 'app');
const glyphs = {
  band: '<rect x="6" y="6" width="12" height="12" rx="4"/><path d="m9 6 1-4h4l1 4m-6 12 1 4h4l1-4m-3-9v3l2 1"/>',
  heart: '<path d="M20.8 8.7c0 4.3-8.8 9.8-8.8 9.8S3.2 13 3.2 8.7a4.6 4.6 0 0 1 8.8-1.8 4.6 4.6 0 0 1 8.8 1.8Z"/>',
  steps: '<ellipse cx="7" cy="7" rx="3" ry="4"/><path d="M6 13h2m8 7h2"/><ellipse cx="17" cy="13" rx="3" ry="4"/>',
  history: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5v4.8l3.5 2"/>',
};

let storageAvailable = true;
const state = {
  tab: 'device', link: 'idle', sync: 'idle', timeoutNext: false,
  history: loadHistory(), detail: null, timer: null,
};

function dayKey(date) {
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function loadHistory() {
  try {
    const stored = JSON.parse(localStorage.getItem(storageKey) ?? '[]');
    if (!Array.isArray(stored)) return [];
    return stored.filter((record) => record && typeof record.day === 'string' &&
      /^\d{4}-\d{2}-\d{2}$/.test(record.day) && Number.isSafeInteger(record.syncedAt) &&
      record.syncedAt > 0 && record.syncedAt <= 8.64e15 && Number.isSafeInteger(record.steps) &&
      record.steps >= 0 && Array.isArray(record.samples) && record.samples.length === readings.length &&
      record.samples.every((value) => Number.isInteger(value) && value >= 60 && value <= 80));
  } catch (error) {
    if (!(error instanceof SyntaxError)) storageAvailable = false;
    return [];
  }
}

function saveToday() {
  const now = new Date();
  const record = { day: dayKey(now), syncedAt: now.getTime(), steps: demoSteps, samples: readings };
  const records = new Map([...state.history, ...loadHistory(), record].map((item) => [item.day, item]));
  state.history = [...records.values()].sort((a, b) => b.syncedAt - a.syncedAt);
  try {
    localStorage.setItem(storageKey, JSON.stringify(state.history));
    storageAvailable = true;
  } catch {
    storageAvailable = false;
  }
}

function tile(icon, large = false) {
  return `<span class="tile${large ? ' large' : ''}"><svg viewBox="0 0 24 24" aria-hidden="true">${glyphs[icon]}</svg></span>`;
}

function button(action, label, options = '') {
  return `<button class="action${options === 'secondary' ? ' secondary' : ''}" type="button" data-action="${action}"${options === 'disabled' ? ' disabled' : ''}>${label}</button>`;
}

function heading(title, text) {
  return `<h2 tabindex="-1">${title}</h2><p class="subhead">${text}</p>`;
}

function deviceCard(connected) {
  const last = state.history[0];
  const fields = [
    ['设备型号', 'PB-S1'], ['固件版本', '1.4.2'], ['电池电量', '82%'], ['信号强度', '−52 dBm'],
    ['上次同步', last ? `${last.day} ${clock.format(last.syncedAt)}` : '尚未同步'],
  ];
  return `<div class="card"><div class="row">${tile('band')}<div class="fill"><p class="device-title">${deviceName}</p><p class="note">模拟 BLE 手环</p></div><span class="chip${connected ? ' connected' : ''}">${connected ? '已连接' : '可连接'}</span></div>${connected
    ? `<dl class="details">${fields.map(([label, value]) => `<div><dt>${label}</dt><dd>${value}</dd></div>`).join('')}</dl>`
    : button('connect', '连接手环')}</div>`;
}

function devicePage() {
  const header = heading('设备', '发现、连接并查看穿戴设备信息');
  if (state.link === 'ready') {
    return header + deviceCard(true) + button('view-health', '查看健康概览') +
      `<label class="card controls"><span><strong>模拟下次同步超时</strong><span class="note">触发一次超时，随后可以重试。</span></span><input type="checkbox" ${state.timeoutNext ? 'checked' : ''}></label>` +
      button('disconnect', '断开连接', 'secondary');
  }

  const descriptions = {
    idle: ['发现附近设备', '扫描模拟手环，开启健康数据同步。'],
    scanning: ['正在扫描', '正在发现附近的模拟 BLE 设备。'],
    found: ['找到 1 台手环', '选择下方的设备进行连接。'],
    connecting: ['正在连接', `正在与 ${deviceName} 建立模拟连接。`],
    preparing: ['准备数据通道', '正在模拟服务发现与回包订阅。'],
  };
  const [title, description] = descriptions[state.link];
  const action = state.link === 'idle' ? button('scan', '扫描设备') :
    state.link === 'found' ? deviceCard(false) : button('', '请稍候…', 'disabled');
  return header + `<div class="card discover">${tile('band', true)}<h3>${title}</h3><p class="note">${description}</p></div>` +
    action + '<p class="note">浏览器演示 · 可直接扫描模拟设备。</p>';
}

function trend(record) {
  const points = record.samples.map((bpm, index) => [10 + index * 45, 73 - (bpm - 60) * 3]);
  const line = points.map(([x, y], index) => `${index ? 'L' : 'M'}${x} ${y}`).join(' ');
  const last = new Date(record.syncedAt);
  const earliest = new Date(Math.max(new Date(last.getFullYear(), last.getMonth(), last.getDate()).getTime(), last.getTime() - 7 * 3600000));
  return `<svg class="chart" viewBox="0 0 354 96" role="img" aria-label="心率趋势，最低 ${Math.min(...record.samples)}，最高 ${Math.max(...record.samples)} 次每分钟">
    <defs><linearGradient id="trend-fill" x1="0" y1="0" x2="0" y2="100%"><stop stop-color="#b5daff"/><stop offset="1" stop-color="#edf6ff"/></linearGradient></defs>
    <path d="M10 13H325 M10 43H325 M10 73H325" stroke="#dce8f5" fill="none"/>
    <path d="${line} L325 73 H10 Z" fill="url(#trend-fill)"/>
    <path d="${line}" stroke="#086bd6" stroke-width="2.4" fill="none" stroke-linejoin="round"/>
    <circle cx="325" cy="${points.at(-1)[1]}" r="3.4" fill="#086bd6"/>
    <text x="333" y="16">80</text><text x="333" y="46">70</text><text x="333" y="76">60</text>
    <text x="10" y="90">${clock.format(earliest)}</text><text x="325" y="90" text-anchor="end">${clock.format(last)}</text>
  </svg>`;
}

function metrics(record, label) {
  return `<div class="card"><div class="row">${tile('heart')}<strong class="fill">心率</strong><span class="chip">${record ? `${record.samples.length} 个样本` : '待同步'}</span></div>
    <p class="metric-value">${record?.samples.at(-1) ?? '—'} <span class="unit">bpm</span></p>
    <p class="note">${record ? '最近读数 · 心率趋势' : '同步后显示读数和趋势'}</p>${record ? trend(record) : ''}</div>
    <div class="card row">${tile('steps')}<strong class="fill">${label}</strong><strong class="steps-value">${record ? number.format(record.steps) : '—'} <span class="unit">步</span></strong></div>`;
}

function feedback(title, detail, type = '') {
  return `<div class="feedback ${type}"><strong>${title}</strong><p>${detail}</p></div>`;
}

function healthPage() {
  const record = state.history.find((item) => item.day === dayKey(new Date()));
  const connected = state.link === 'ready';
  const content = heading('健康概览', connected ? `${deviceName} · 已连接` : '连接模拟手环，查看健康数据') + metrics(record, '今日步数');
  if (!connected) return content + button('view-device', '前往设备页');

  const label = state.sync === 'waiting' ? '正在同步…' : state.sync === 'timeout' ? '重试同步' : '同步健康数据';
  const messages = {
    waiting: () => feedback('正在等待手环回包', '收到数据后将保存到历史。'),
    saved: () => feedback('同步成功', storageAvailable ? '心率和步数已保存到此浏览器。' : '浏览器存储不可用，数据暂时保存在本页。', 'success'),
    timeout: () => feedback('同步超时', '请点击重试。此前同步的数据仍可查看。', 'error'),
  };
  return content + button('sync', label, state.sync === 'waiting' ? 'disabled' : '') + (messages[state.sync]?.() ?? '');
}

function historyPage() {
  const record = state.history.find((item) => item.day === state.detail);
  if (record) {
    return heading('记录详情', `${record.day} · ${deviceName}`) + metrics(record, '当日步数') +
      `<p class="subhead">保存于 ${clock.format(record.syncedAt)} · 模拟数据</p>` + button('history-back', '返回历史列表', 'secondary');
  }
  const header = heading('健康历史', '每台设备每天保留一条记录');
  if (!state.history.length) {
    return header + `<div class="card discover">${tile('history', true)}<h3>暂无同步记录</h3><p class="note">成功同步后，记录会显示在这里。</p></div>` + button('view-health', '前往健康概览', 'secondary');
  }
  return header + state.history.map((item) => `<article class="card"><div class="row"><strong class="fill">${item.day}</strong><span class="chip connected">${storageAvailable ? '本地保存' : '本页记录'}</span></div>
    <p class="note">${deviceName} · 同步于 ${clock.format(item.syncedAt)}</p><p class="device-title">${item.samples.at(-1)} bpm · ${number.format(item.steps)} 步</p>
    <button class="action secondary" type="button" data-record="${item.day}">查看记录</button></article>`).join('');
}

function render() {
  const action = panel.contains(document.activeElement) ? document.activeElement.dataset.action : null;
  panel.innerHTML = { device: devicePage, health: healthPage, history: historyPage }[state.tab]();
  for (const tab of tabs.querySelectorAll('button')) {
    if (tab.dataset.tab === state.tab) tab.setAttribute('aria-current', 'page');
    else tab.removeAttribute('aria-current');
  }
  if (action) {
    const replacement = panel.querySelector(`[data-action="${action}"]:not(:disabled)`) ?? panel.querySelector('h2');
    replacement.focus({ preventScroll: true });
  }
}

function update(message) {
  render();
  announcement.textContent = message;
}

function navigate(tab) {
  state.tab = tab;
  state.detail = null;
  render();
  panel.scrollTop = 0;
  panel.querySelector('h2').focus({ preventScroll: true });
}

function after(delay, action) {
  clearTimeout(state.timer);
  state.timer = setTimeout(action, delay);
}

const actions = {
  scan() {
    if (state.link !== 'idle') return;
    state.link = 'scanning';
    update('正在扫描');
    after(850, () => { state.link = 'found'; update('找到 1 台手环'); });
  },
  connect() {
    if (state.link !== 'found') return;
    state.link = 'connecting';
    update('正在连接');
    after(470, () => {
      state.link = 'preparing';
      update('准备数据通道');
      after(680, () => { state.link = 'ready'; update('手环已连接'); });
    });
  },
  disconnect() {
    clearTimeout(state.timer);
    state.link = 'idle';
    state.sync = 'idle';
    state.timeoutNext = false;
    update('连接已断开');
  },
  sync() {
    if (state.link !== 'ready' || state.sync === 'waiting') return;
    const timeout = state.timeoutNext;
    state.timeoutNext = false;
    state.sync = 'waiting';
    update('正在同步');
    after(timeout ? 2400 : 1080, () => {
      if (!timeout) saveToday();
      state.sync = timeout ? 'timeout' : 'saved';
      update(timeout ? '同步超时，可以重试' : '同步成功');
      if (state.tab === 'health') panel.querySelector('.feedback')?.scrollIntoView({ block: 'nearest' });
    });
  },
  'view-health': () => navigate('health'),
  'view-device': () => navigate('device'),
  'history-back': () => navigate('history'),
};

tabs.addEventListener('click', (event) => {
  const button = event.target.closest('button[data-tab]');
  if (button) navigate(button.dataset.tab);
});
panel.addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button) return;
  if (button.dataset.record) {
    state.detail = button.dataset.record;
    render();
    panel.scrollTop = 0;
    panel.querySelector('h2').focus({ preventScroll: true });
  } else {
    actions[button.dataset.action]?.();
  }
});
panel.addEventListener('change', (event) => {
  if (event.target.matches('input[type="checkbox"]')) state.timeoutNext = event.target.checked;
});

render();
