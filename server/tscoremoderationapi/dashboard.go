package tscoremoderationapi

import "net/http"

func (h *Handler) dashboard(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "text/html; charset=utf-8")
    w.Header().Set("Cache-Control", "no-store, private")
    w.Header().Set("X-Frame-Options", "DENY")
    w.Header().Set("X-Content-Type-Options", "nosniff")
    w.Header().Set("Referrer-Policy", "no-referrer")
    w.Header().Set("Content-Security-Policy", "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'")
    _, _ = w.Write([]byte(moderationDashboardHTML))
}

const moderationDashboardHTML = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>T-Score Moderation</title>
<style>
:root{font-family:Inter,system-ui,sans-serif;color:#172322;background:#f6f8f8}*{box-sizing:border-box}body{margin:0}.wrap{max-width:1180px;margin:auto;padding:24px}.top{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:20px}h1{margin:0;font-size:24px}.badge{background:#e0f2f1;color:#00695c;padding:6px 10px;border-radius:999px;font-weight:700}.controls{display:flex;gap:8px;flex-wrap:wrap;margin-left:auto}input,select,button,textarea{font:inherit;border:1px solid #d8dfde;border-radius:10px;padding:10px 12px;background:white}input{min-width:260px}button{cursor:pointer;font-weight:700}.primary{background:#00897b;color:white;border-color:#00897b}.card{background:#fff;border:1px solid #e1e6e5;border-radius:16px;padding:16px;margin:12px 0;box-shadow:0 2px 8px #0000000a}.row{display:flex;gap:14px;justify-content:space-between;align-items:flex-start;flex-wrap:wrap}.meta{color:#667674;font-size:13px}.reason{font-weight:800}.status{font-weight:800;padding:5px 9px;border-radius:999px;background:#eef3f2}.details{white-space:pre-wrap;margin:12px 0;color:#334441}.actions{display:flex;gap:8px;flex-wrap:wrap}.note{width:100%;min-height:70px;margin-top:10px}.empty{padding:40px;text-align:center;color:#71807e}.err{color:#b71c1c;margin:10px 0}.ok{color:#2e7d32;margin:10px 0}@media(max-width:700px){.wrap{padding:14px}.controls{margin-left:0;width:100%}input,select{min-width:0;flex:1}}
</style>
</head>
<body><div class="wrap">
<div class="top"><h1>T-Score Moderation</h1><span class="badge">Admin</span><div class="controls">
<input id="key" type="password" placeholder="Admin key" autocomplete="off">
<select id="status"><option value="">All</option><option>OPEN</option><option>REVIEWING</option><option>RESOLVED</option><option>DISMISSED</option></select>
<button class="primary" id="load">Load reports</button></div></div>
<div id="msg"></div><div id="list" class="empty">Enter the admin key and load reports.</div>
</div>
<script>
const $=id=>document.getElementById(id); const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
$('key').value=sessionStorage.getItem('tscore_admin_key')||'';
$('load').onclick=load; $('status').onchange=()=>{if($('key').value)load()};
async function api(path,opt={}){const key=$('key').value.trim();if(!key)throw new Error('Admin key required');sessionStorage.setItem('tscore_admin_key',key);opt.headers={...(opt.headers||{}),'X-TScore-Admin-Key':key,'Accept':'application/json'};const r=await fetch(path,opt);const t=await r.text();let j={};try{j=JSON.parse(t)}catch(e){}if(!r.ok)throw new Error(j.error||('Request failed: '+r.status));return j}
async function load(){msg('Loading…','');try{const s=$('status').value;const j=await api('/internal/v1/reports'+(s?'?status='+encodeURIComponent(s):''));render(j.reports||[]);msg((j.reports||[]).length+' report(s) loaded','ok')}catch(e){$('list').innerHTML='';msg(e.message,'err')}}
function msg(t,c){$('msg').className=c;$('msg').textContent=t}
function render(items){if(!items.length){$('list').className='empty';$('list').textContent='No reports found.';return}$('list').className='';$('list').innerHTML=items.map(r=>`<div class="card" data-id="${esc(r.id)}"><div class="row"><div><div class="reason">${esc(r.reason)}</div><div class="meta">${esc(r.id)} • ${esc(r.created_at)}</div></div><span class="status">${esc(r.status)}</span></div><div class="meta" style="margin-top:10px">Reporter: ${esc(r.reporter_id)}<br>Target: ${esc(r.target_id)}${r.topic_name?'<br>Topic: '+esc(r.topic_name):''}</div><div class="details">${esc(r.details||'No details provided')}</div>${r.admin_note?'<div class="meta"><b>Admin note:</b> '+esc(r.admin_note)+'</div>':''}<textarea class="note" placeholder="Admin note"></textarea><div class="actions"><button onclick="decide(this,'REVIEWING')">Reviewing</button><button class="primary" onclick="decide(this,'RESOLVED')">Resolved</button><button onclick="decide(this,'DISMISSED')">Dismiss</button></div></div>`).join('')}
async function decide(btn,status){const card=btn.closest('.card');const id=card.dataset.id;const note=card.querySelector('.note').value.trim();btn.disabled=true;try{await api('/internal/v1/reports/'+encodeURIComponent(id)+'/decision',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({status,note})});msg('Report '+id+' updated to '+status,'ok');await load()}catch(e){msg(e.message,'err')}finally{btn.disabled=false}}
</script></body></html>`
