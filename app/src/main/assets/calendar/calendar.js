(() => {
'use strict';
const $=id=>document.getElementById(id), C=window.PooftieCalendarStyle, E=window.PooftieCalendarEvents;
const view=$('view-calendar'),grid=$('calendar-days-grid'),card=$('calendar-card');
const months=['janeiro','fevereiro','março','abril','maio','junho','julho','agosto','setembro','outubro','novembro','dezembro'];
const today=new Date();let shown=new Date(today.getFullYear(),today.getMonth(),1),selected='',zoom=1,theme=C.ler(localStorage)||C.padrao();
let notes=read('lp_cal_notes',{}),reactions=read('lp_cal_reactions',{}),photos=read('lp_cal_photos',{}),events=E.ler(localStorage),stickers=read('lp_cal_stickers',[]);
let eventColor=E.CORES[0],activeReaction='';
function read(k,f){try{return JSON.parse(localStorage.getItem(k)||'null')??f}catch{return f}}
function save(k,v){try{localStorage.setItem(k,JSON.stringify(v))}catch{}}
function iso(d){return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0')}
function parse(s){const [y,m,d]=s.split('-').map(Number);return new Date(y,m-1,d)}
function safe(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function applyTheme(){C.aplicar(view,theme)}
function saveEvents(){E.guardar(localStorage,events)}
function taskLines(s){return C.linhasDaNota(s||'')}
function render(){
 applyTheme();grid.innerHTML='';
 const y=shown.getFullYear(),m=shown.getMonth(),first=new Date(y,m,1).getDay(),count=new Date(y,m+1,0).getDate(),prev=new Date(y,m,0).getDate();
 $('calendar-month-year').textContent=months[m]+' '+y;
 const dates=[];
 for(let i=first-1;i>=0;i--)dates.push(new Date(y,m-1,prev-i));
 for(let d=1;d<=count;d++)dates.push(new Date(y,m,d));
 while(dates.length%7)dates.push(new Date(y,m+1,dates.length-first-count+1));
 const bands=[];
 events.forEach((e,i)=>bands.push({id:'event:'+e.id,idEvento:e.id,titulo:e.titulo,cor:e.cor,inicio:e.inicio,fim:e.fim,tipo:'evento',ordem:i}));
 Object.keys(notes).forEach(date=>taskLines(notes[date]).forEach((title,i)=>bands.push({id:'task:'+date+':'+i,titulo:title,cor:C.corDaTarefa(theme,i),inicio:date,fim:date,tipo:'tarefa',ordem:i})));
 const layout=E.montarGrade({dias:dates.map(iso),faixas:bands}),max=Math.max(0,...layout.semanas.map(w=>w.totalFaixas));
 dates.forEach((d,i)=>{
  const date=iso(d),cell=document.createElement('div');cell.className='cal-day-cell';
  if(d.getMonth()!==m)cell.classList.add('other-month');if(d.getDay()===0)cell.classList.add('is-domingo');if(date===iso(today))cell.classList.add('today');
  if(photos[date]?.src){cell.classList.add('tem-foto');if(photos[date].front)cell.classList.add('foto-na-frente');const img=document.createElement('img');img.className='cal-day-photo';img.src=photos[date].src;cell.appendChild(img)}
  const top=document.createElement('div');top.className='cal-day-top';const num=document.createElement('span');num.className='cal-day-num';num.textContent=d.getDate();top.appendChild(num);
  const mini=C.miniDoDia(date,theme.mini);if(mini){const s=document.createElement('span');s.className='cal-day-mini';s.textContent=mini;top.appendChild(s)}cell.appendChild(top);
  if(reactions[date]){const badge=document.createElement('span');badge.className='cal-day-reaction';badge.textContent=reactions[date];cell.appendChild(badge)}
  if(taskLines(notes[date]).length)cell.style.setProperty('--cal-linhas',String(max));
  cell.addEventListener('click',()=>openDay(date));grid.appendChild(cell);
 });
 renderBands(layout,dates);renderStickers();
}
function renderBands(layout,dates){
 const old=grid.querySelector('.cal-days-overlay');if(old)old.remove();
 const overlay=document.createElement('div');overlay.className='cal-days-overlay';
 const cells=[...grid.querySelectorAll('.cal-day-cell')],step=Math.max(13,Math.min(24,(grid.clientWidth||380)*.032)),height=Math.max(9,step-3);
 grid.style.setProperty('--cal-faixa-passo',step+'px');
 layout.semanas.forEach((week,w)=>{for(let col=0;col<7;col++){(week.celulas[col]||[]).forEach(seg=>{
  const key=seg.id.replace(/"/g,'&quot;'),prevBar=[...overlay.querySelectorAll('.cal-faixa')].find(b=>b.dataset.band===key&&b.dataset.week===String(w));
  if(prevBar){const end=cells[w*7+col];prevBar.style.width=(end.offsetLeft+end.offsetWidth-parseFloat(prevBar.style.left)-(seg.terminaAqui?4:-1))+'px';prevBar.classList.toggle('termina-aqui',seg.terminaAqui);return}
  const start=cells[w*7+col];if(!start)return;const top=start.querySelector('.cal-day-top'),bar=document.createElement('button');bar.type='button';bar.className='cal-faixa '+(seg.tipo==='evento'?'is-evento':'is-tarefa')+(seg.comecaAqui?' comeca-aqui':'')+(seg.terminaAqui?' termina-aqui':'');
  bar.dataset.band=key;bar.dataset.week=String(w);bar.style.setProperty('--faixa-cor',seg.cor);bar.style.setProperty('--faixa-texto',seg.textoCor);
  bar.style.top=(start.offsetTop+(top?top.offsetTop+top.offsetHeight:0)+3+(seg.lane-1)*step)+'px';const left=Math.max(0,start.offsetLeft+(seg.comecaAqui?4:-1));bar.style.left=left+'px';bar.style.width=Math.max(6,start.offsetWidth-(seg.terminaAqui?4:-1))+'px';bar.style.height=height+'px';bar.textContent=seg.mostrarTitulo?seg.titulo:'';bar.title=seg.titulo;
  bar.addEventListener('click',ev=>{ev.stopPropagation();openDay(seg.tipo==='evento'?seg.inicio:iso(dates[w*7+col]))});overlay.appendChild(bar);
 })}});grid.appendChild(overlay);
}
function openDay(date){
 selected=date;try{window.LittlePauseCalendar?.onDateSelected?.(date)}catch{}const d=parse(date);$('day-title').textContent='tarefinhas do dia';$('day-date-label').textContent=String(d.getDate()).padStart(2,'0')+'/'+String(d.getMonth()+1).padStart(2,'0')+'/'+d.getFullYear()+' ⋆';
 $('day-tasks').value=notes[date]||'';$('event-title').value='';$('event-end').value=date;activeReaction=reactions[date]||'';renderReactions();renderEventColors();renderEventList();$('photo-front').checked=!!photos[date]?.front;$('day-modal').classList.add('open');renderTaskPreview();
}
function renderTaskPreview(){const box=$('task-preview');box.innerHTML='';taskLines($('day-tasks').value).forEach((t,i)=>{const e=document.createElement('span');e.className='cal-day-task';e.style.setProperty('--cal-tarefa-cor',C.corDaTarefa(theme,i));e.textContent=t;box.appendChild(e)})}
function renderReactions(){const box=$('day-reactions');box.innerHTML='';['🍰','🌸','🎀','🧸','☕','🌧️','⭐','🩷','📚'].forEach(emoji=>{const b=document.createElement('button');b.textContent=emoji;b.classList.toggle('selected',activeReaction===emoji);b.onclick=()=>{activeReaction=activeReaction===emoji?'':emoji;renderReactions()};box.appendChild(b)})}
function renderEventColors(){const box=$('event-colors');box.innerHTML='';E.CORES.forEach(color=>{const b=document.createElement('button');b.style.background=color;b.classList.toggle('selected',eventColor===color);b.onclick=()=>{eventColor=color;renderEventColors()};box.appendChild(b)})}
function prettyRange(a,b){const f=s=>{const d=parse(s);return d.getDate()+'/'+(d.getMonth()+1)};return a===b?f(a):f(a)+' até '+f(b)}
function renderEventList(){const box=$('event-list');box.innerHTML='';E.eventosDoDia(events,selected).forEach(e=>{const row=document.createElement('div');row.className='event-list-item';const dot=document.createElement('i');dot.className='dot';dot.style.background=e.cor;const label=document.createElement('span');label.innerHTML='<b>'+safe(e.titulo)+'</b><small>'+prettyRange(e.inicio,e.fim)+'</small>';const del=document.createElement('button');del.textContent='✕';del.onclick=()=>{events=E.semEvento(events,e.id);saveEvents();renderEventList();render()};row.append(dot,label,del);box.appendChild(row)})}
$('btn-event-add').onclick=()=>{const title=$('event-title').value.trim();if(!title)return;const end=E.normalizarData($('event-end').value)||selected;const event=E.criar({titulo:title,inicio:selected,fim:end<selected?selected:end,cor:eventColor});events=E.comEvento(events,event);saveEvents();$('event-title').value='';renderEventList();render()};
$('day-save').onclick=()=>{const value=$('day-tasks').value.trim();if(value)notes[selected]=value;else delete notes[selected];if(activeReaction)reactions[selected]=activeReaction;else delete reactions[selected];if(photos[selected])photos[selected].front=$('photo-front').checked;save('lp_cal_notes',notes);save('lp_cal_reactions',reactions);save('lp_cal_photos',photos);$('day-modal').classList.remove('open');render()};
$('day-tasks').addEventListener('input',renderTaskPreview);
$('day-photo-upload').onchange=async ev=>{const file=ev.target.files?.[0];if(!file)return;const data=await fileToData(file,130000);if(data){photos[selected]={src:data,front:$('photo-front').checked};save('lp_cal_photos',photos);render()}ev.target.value=''};
$('day-photo-clear').onclick=()=>{delete photos[selected];save('lp_cal_photos',photos);render();$('photo-front').checked=false};
$('btn-prev-month').onclick=()=>{shown.setMonth(shown.getMonth()-1);render()};$('btn-next-month').onclick=()=>{shown.setMonth(shown.getMonth()+1);render()};
$('btn-zoom-in-cal').onclick=()=>{zoom=Math.min(1.25,zoom+.1);card.style.transform='scale('+zoom+')'};$('btn-zoom-out-cal').onclick=()=>{zoom=Math.max(.75,zoom-.1);card.style.transform='scale('+zoom+')'};
document.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>$(b.dataset.close).classList.remove('open'));
$('btn-calendar-theme').onclick=openTheme;$('theme-reset').onclick=()=>{theme=C.padrao();saveTheme();render();renderTheme()};
function saveTheme(){C.guardar(localStorage,theme)}function changeTheme(p){theme=C.normalizar({...theme,...p});saveTheme();render()}
function openTheme(){renderTheme();$('theme-modal').classList.add('open')}
const names={nenhum:'lisinho',bolinhas:'bolinhas',nuvens:'nuvens',coracoes:'corações',xadrez:'xadrez',listras:'listras',estrelinhas:'estrelinhas',papel:'papel',foto:'minha foto'};
function renderTheme(){
 const fg=$('cal-fundo-grid');fg.innerHTML='';C.FUNDOS.forEach(f=>{if(f==='foto'&&!theme.foto)return;const b=document.createElement('button');b.textContent=names[f]||f;b.style.background=C.fundoCss({...theme,fundo:f});b.classList.toggle('selected',theme.fundo===f);b.onclick=()=>{changeTheme({fundo:f});renderTheme()};fg.appendChild(b)});
 const qg=$('cal-quadros-grid');qg.innerHTML='';C.QUADROS.forEach(q=>{const b=document.createElement('button');b.textContent=q;b.classList.toggle('selected',theme.quadros===q);b.onclick=()=>{changeTheme({quadros:q});renderTheme()};qg.appendChild(b)});
 [['cal-fundo-cor','fundoCor'],['cal-detalhe-cor','detalheCor'],['cal-celula-cor','celulaCor'],['cal-linha-cor','linhaCor'],['cal-texto-cor','textoCor']].forEach(([id,k])=>$(id).value=theme[k]);
 $('cal-fonte-select').value=theme.fonte;$('cal-mini-select').value=theme.mini;$('cal-paleta-select').value=theme.paleta;$('cal-domingo-vermelho').checked=theme.domingoVermelho;
}
[['cal-fundo-cor','fundoCor'],['cal-detalhe-cor','detalheCor'],['cal-celula-cor','celulaCor'],['cal-linha-cor','linhaCor'],['cal-texto-cor','textoCor']].forEach(([id,k])=>$(id).oninput=()=>changeTheme({[k]:$(id).value}));
$('cal-fonte-select').onchange=()=>changeTheme({fonte:$('cal-fonte-select').value});$('cal-mini-select').onchange=()=>changeTheme({mini:$('cal-mini-select').value});$('cal-paleta-select').onchange=()=>changeTheme({paleta:$('cal-paleta-select').value});$('cal-domingo-vermelho').onchange=()=>changeTheme({domingoVermelho:$('cal-domingo-vermelho').checked});
$('cal-foto-upload').onchange=async ev=>{const f=ev.target.files?.[0];if(!f)return;const src=await fileToData(f,300000);if(src)changeTheme({fundo:'foto',foto:src});ev.target.value=''};$('cal-foto-clear').onclick=()=>{changeTheme({foto:'',fundo:theme.fundo==='foto'?'nuvens':theme.fundo});renderTheme()};
async function fileToData(file,max){return new Promise(resolve=>{const img=new Image(),reader=new FileReader();reader.onload=()=>{img.onload=()=>{const canvas=document.createElement('canvas'),scale=Math.min(1,900/Math.max(img.width,img.height));canvas.width=Math.round(img.width*scale);canvas.height=Math.round(img.height*scale);canvas.getContext('2d').drawImage(img,0,0,canvas.width,canvas.height);let q=.75,out=canvas.toDataURL('image/webp',q);while(out.length>max&&q>.35){q-=.1;out=canvas.toDataURL('image/webp',q)}resolve(out.length<=max?out:'')};img.onerror=()=>resolve('');img.src=reader.result};reader.onerror=()=>resolve('');reader.readAsDataURL(file)})}
const stickerEmojis=['🎀','🌸','🧸','🍰','🍓','🩷','⭐','🌷','🪽','🐇','🐈','☁️','🦢','🕯️','🫖','🍒','🧁','💌','🪞','🎧','🪻','🦋','🐚','🧺','🪷','🪄','🩰','🍡','🌙','♡','✧'];
$('btn-add-sticker').onclick=()=>$('sticker-picker').classList.add('open');$('btn-add-event').onclick=()=>openDay(iso(new Date()));$('sticker-close').onclick=()=>$('sticker-picker').classList.remove('open');
function renderStickerPicker(){const box=$('sticker-emoji-grid');box.innerHTML='';stickerEmojis.forEach(e=>{const b=document.createElement('button');b.textContent=e;b.onclick=()=>{stickers.push({id:Date.now()+Math.random(),emoji:e,x:42+Math.random()*16,y:35+Math.random()*20,size:34,rotation:Math.random()*18-9});save('lp_cal_stickers',stickers);renderStickers();$('sticker-picker').classList.remove('open')};box.appendChild(b)})}
$('sticker-upload').onchange=async ev=>{const f=ev.target.files?.[0];if(!f)return;const src=await fileToData(f,180000);if(src){stickers.push({id:Date.now()+Math.random(),src,x:42,y:35,size:56,rotation:0});save('lp_cal_stickers',stickers);renderStickers()}ev.target.value='';$('sticker-picker').classList.remove('open')};
function renderStickers(){const layer=$('calendar-stickers-overlay');layer.innerHTML='';stickers.forEach((s,i)=>{const el=document.createElement('div');el.className='calendar-sticker';el.style.left=s.x+'%';el.style.top=s.y+'%';el.style.fontSize=s.size+'px';el.style.transform='rotate('+s.rotation+'deg)';if(s.src){const img=document.createElement('img');img.src=s.src;el.appendChild(img)}else el.textContent=s.emoji||'♡';let start=null;el.onpointerdown=e=>{start={x:e.clientX,y:e.clientY,left:s.x,top:s.y};el.setPointerCapture(e.pointerId)};el.onpointermove=e=>{if(!start)return;const r=layer.getBoundingClientRect();s.x=Math.max(0,Math.min(92,start.left+(e.clientX-start.x)/r.width*100));s.y=Math.max(0,Math.min(90,start.top+(e.clientY-start.y)/r.height*100));el.style.left=s.x+'%';el.style.top=s.y+'%'};el.onpointerup=()=>{if(start){start=null;save('lp_cal_stickers',stickers)}};el.ondblclick=()=>{stickers.splice(i,1);save('lp_cal_stickers',stickers);renderStickers()};layer.appendChild(el)})}
renderStickerPicker();window.addEventListener('resize',()=>render());renderTheme();render();
})();