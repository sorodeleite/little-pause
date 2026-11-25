(function (root, factory) {
  const api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root) root.LP_Holidays = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const PAISES = [
    { id: 'br', nome: 'Brasil' },
    { id: 'jp', nome: 'Japão' },
    { id: 'ru', nome: 'Rússia' }
  ];

  const BR_FIXOS = {
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

  const RU_FIXOS = {
    '01-01': 'Ano Novo',
    '01-02': 'Férias de Ano Novo',
    '01-03': 'Férias de Ano Novo',
    '01-04': 'Férias de Ano Novo',
    '01-05': 'Férias de Ano Novo',
    '01-06': 'Férias de Ano Novo',
    '01-07': 'Natal Ortodoxo',
    '01-08': 'Férias de Ano Novo',
    '02-23': 'Dia do Defensor da Pátria',
    '03-08': 'Dia Internacional da Mulher',
    '05-01': 'Primavera e Dia do Trabalho',
    '05-09': 'Dia da Vitória',
    '06-12': 'Dia da Rússia',
    '11-04': 'Dia da Unidade Nacional'
  };

  const JP_FIXOS = {
    '01-01': 'Ano Novo',
    '02-11': 'Fundação da Nação',
    '02-23': 'Aniversário do Imperador',
    '04-29': 'Dia de Showa',
    '05-03': 'Dia da Constituição',
    '05-04': 'Dia do Verde',
    '05-05': 'Dia das Crianças',
    '08-11': 'Dia da Montanha',
    '11-03': 'Dia da Cultura',
    '11-23': 'Dia do Trabalho e Gratidão'
  };

  const DATA = /^(\d{4})-(\d{2})-(\d{2})$/;
  const cache = new Map();

  function chave(data) {
    return data.getFullYear() + '-' + String(data.getMonth() + 1).padStart(2, '0') + '-' + String(data.getDate()).padStart(2, '0');
  }

  function dataDe(valor) {
    const partes = DATA.exec(String(valor || ''));
    if (!partes) return null;
    const data = new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3]));
    return Number.isNaN(data.getTime()) ? null : data;
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

  function enesimoDiaDaSemana(ano, mes, diaDaSemana, numero) {
    const primeiro = new Date(ano, mes, 1);
    const ajuste = (diaDaSemana - primeiro.getDay() + 7) % 7;
    return new Date(ano, mes, 1 + ajuste + (numero - 1) * 7);
  }

  function equinocio(ano, outono) {
    const base = outono ? 23.2488 : 20.8431;
    const dia = Math.floor(base + 0.242194 * (ano - 1980) - Math.floor((ano - 1980) / 4));
    return new Date(ano, outono ? 8 : 2, dia);
  }

  function mapaBrasil(ano) {
    const mapa = {};
    Object.keys(BR_FIXOS).forEach(function (mesDia) {
      mapa[ano + '-' + mesDia] = BR_FIXOS[mesDia];
    });
    const domingo = pascoa(ano);
    mapa[deslocar(domingo, -47)] = 'Carnaval';
    mapa[deslocar(domingo, -2)] = 'Sexta-feira Santa';
    mapa[deslocar(domingo, 0)] = 'Páscoa';
    mapa[deslocar(domingo, 60)] = 'Corpus Christi';
    return mapa;
  }

  function mapaRussia(ano) {
    const mapa = {};
    Object.keys(RU_FIXOS).forEach(function (mesDia) {
      mapa[ano + '-' + mesDia] = RU_FIXOS[mesDia];
    });
    return mapa;
  }

  function mapaJapao(ano) {
    const mapa = {};
    function por(mesDia, nome) {
      mapa[ano + '-' + mesDia] = nome;
    }
    Object.keys(JP_FIXOS).forEach(function (mesDia) {
      por(mesDia, JP_FIXOS[mesDia]);
    });
    mapa[chave(enesimoDiaDaSemana(ano, 0, 1, 2))] = 'Dia da Maioridade';
    mapa[chave(equinocio(ano, false))] = 'Equinócio de Primavera';
    mapa[chave(enesimoDiaDaSemana(ano, 6, 1, 3))] = 'Dia do Mar';
    mapa[chave(enesimoDiaDaSemana(ano, 8, 1, 3))] = 'Dia do Respeito ao Idoso';
    mapa[chave(equinocio(ano, true))] = 'Equinócio de Outono';
    mapa[chave(enesimoDiaDaSemana(ano, 9, 1, 2))] = 'Dia do Esporte';

    Object.keys(mapa).sort().forEach(function (dia) {
      const data = dataDe(dia);
      if (!data || data.getDay() !== 0) return;
      let seguinte = new Date(data.getFullYear(), data.getMonth(), data.getDate() + 1);
      while (mapa[chave(seguinte)]) {
        seguinte = new Date(seguinte.getFullYear(), seguinte.getMonth(), seguinte.getDate() + 1);
      }
      mapa[chave(seguinte)] = 'Feriado substituto';
    });

    const totalDoAno = new Date(ano, 11, 31);
    for (let cursor = new Date(ano, 0, 2); cursor <= totalDoAno; cursor = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() + 1)) {
      const dia = chave(cursor);
      if (mapa[dia]) continue;
      const antes = chave(new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() - 1));
      const depois = chave(new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() + 1));
      if (mapa[antes] && mapa[depois]) mapa[dia] = 'Dia Nacional';
    }
    return mapa;
  }

  function mapaDoPais(id, ano) {
    const chaveDoCache = id + ':' + ano;
    if (cache.has(chaveDoCache)) return cache.get(chaveDoCache);
    let mapa = {};
    if (id === 'br') mapa = mapaBrasil(ano);
    else if (id === 'jp') mapa = mapaJapao(ano);
    else if (id === 'ru') mapa = mapaRussia(ano);
    cache.set(chaveDoCache, mapa);
    return mapa;
  }

  function nomes(valor, paises) {
    const partes = DATA.exec(String(valor || ''));
    if (!partes) return [];
    const ano = Number(partes[1]);
    if (ano < 1900 || ano > 2200) return [];
    const escolhidos = Array.isArray(paises) ? paises : ['br'];
    const lista = [];
    escolhidos.forEach(function (id) {
      const nome = mapaDoPais(id, ano)[String(valor)];
      if (nome && lista.indexOf(nome) === -1) lista.push(nome);
    });
    return lista;
  }

  function nome(valor, paises) {
    return nomes(valor, paises).join(' · ');
  }

  return { PAISES: PAISES, nome: nome, nomes: nomes };
});
