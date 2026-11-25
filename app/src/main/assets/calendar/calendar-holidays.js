(function (root, factory) {
  const api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root) root.LP_Holidays = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const FIXOS = {
    '01-01': 'Confraternização Universal',
    '04-21': 'Tiradentes',
    '05-01': 'Dia do Trabalho',
    '09-07': 'Independência do Brasil',
    '10-12': 'Nossa Senhora Aparecida',
    '11-02': 'Finados',
    '11-15': 'Proclamação da República',
    '11-20': 'Consciência Negra',
    '12-25': 'Natal'
  };

  const cache = new Map();
  const DATA = /^(\d{4})-(\d{2})-(\d{2})$/;

  function chave(data) {
    return data.getFullYear() + '-' + String(data.getMonth() + 1).padStart(2, '0') + '-' + String(data.getDate()).padStart(2, '0');
  }

  function deslocar(base, dias) {
    return chave(new Date(base.getFullYear(), base.getMonth(), base.getDate() + dias));
  }

  function pascoa(ano) {
    const a = ano % 19;
    const b = Math.floor(ano / 100);
    const c = ano % 100;
    const d = Math.floor(b / 4);
    const e = b % 4;
    const f = Math.floor((b + 8) / 25);
    const g = Math.floor((b - f + 1) / 3);
    const h = (19 * a + b - d - g + 15) % 30;
    const i = Math.floor(c / 4);
    const k = c % 4;
    const l = (32 + 2 * e + 2 * i - h - k) % 7;
    const m = Math.floor((a + 11 * h + 22 * l) / 451);
    const mes = Math.floor((h + l - 7 * m + 114) / 31);
    const dia = ((h + l - 7 * m + 114) % 31) + 1;
    return new Date(ano, mes - 1, dia);
  }

  function doAno(ano) {
    if (cache.has(ano)) return cache.get(ano);
    const mapa = {};
    Object.keys(FIXOS).forEach(function (mesDia) {
      mapa[ano + '-' + mesDia] = FIXOS[mesDia];
    });
    const domingo = pascoa(ano);
    mapa[deslocar(domingo, -47)] = 'Carnaval';
    mapa[deslocar(domingo, -2)] = 'Sexta-feira Santa';
    mapa[deslocar(domingo, 0)] = 'Páscoa';
    mapa[deslocar(domingo, 60)] = 'Corpus Christi';
    cache.set(ano, mapa);
    return mapa;
  }

  function nome(chaveDaData) {
    const partes = DATA.exec(String(chaveDaData || ''));
    if (!partes) return '';
    const ano = Number(partes[1]);
    if (ano < 1900 || ano > 2200) return '';
    const mapa = doAno(ano);
    return mapa[String(chaveDaData)] || '';
  }

  function doMes(ano, mes) {
    const mapa = doAno(Number(ano));
    const prefixo = Number(ano) + '-' + String(Number(mes)).padStart(2, '0') + '-';
    const dias = {};
    Object.keys(mapa).forEach(function (dia) {
      if (dia.indexOf(prefixo) === 0) dias[dia] = mapa[dia];
    });
    return dias;
  }

  return { nome: nome, doAno: doAno, doMes: doMes };
});
