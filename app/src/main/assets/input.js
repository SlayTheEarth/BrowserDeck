(function () {
  'use strict';
  if (window.__browserDeck) return;
  const tag = 'browserdeck-input-v1';
  let cfg = {desktop:true, fakeLock:true}, locked = null, state = {keys:[], mouse:[], x:0,y:0, mode:'auto', speed:850, invert:false};
  let remainderX=0,remainderY=0;
  const originalLock = new Map(); let shimInstalled=false;
  let held = new Set(), buttons = new Set(), child = null, px = innerWidth/2, py = innerHeight/2, last = performance.now(), cursor = null;
  const log = (...v) => console.info('[BrowserDeck]', ...v);
  function install(config) {
    cfg = Object.assign(cfg,config || {});
    if (cfg.desktop) {
      for (const [p,v] of [['maxTouchPoints',0],['platform','Linux x86_64']]) {
        try { Object.defineProperty(navigator,p,{get:()=>v,configurable:true}); } catch(e) { log(p,e.message); }
      }
    }
    if (!cfg.fakeLock && shimInstalled) { for(const [obj,descs] of originalLock) for(const [name,d] of descs) try { if(d)Object.defineProperty(obj,name,d); else delete obj[name]; } catch(e){} locked=null;shimInstalled=false; }
    if (cfg.fakeLock && !shimInstalled) {
      for (const [obj,names] of [[Document.prototype,['pointerLockElement','webkitPointerLockElement','mozPointerLockElement','exitPointerLock','webkitExitPointerLock','mozExitPointerLock']],[Element.prototype,['requestPointerLock','webkitRequestPointerLock','mozRequestPointerLock']]]) if(!originalLock.has(obj)) originalLock.set(obj,names.map(n=>[n,Object.getOwnPropertyDescriptor(obj,n)]));
      shimInstalled=true;
      function request() { locked = this; document.dispatchEvent(new Event('pointerlockchange')); return Promise.resolve(); }
      function exit() { if (locked) { locked=null; document.dispatchEvent(new Event('pointerlockchange')); } }
      for (const p of ['pointerLockElement','webkitPointerLockElement','mozPointerLockElement']) {
        try { Object.defineProperty(Document.prototype,p,{get(){return locked;},configurable:true}); } catch(e) {log(p,e.message);}
      }
      for (const p of ['requestPointerLock','webkitRequestPointerLock','mozRequestPointerLock']) {
        try { Object.defineProperty(Element.prototype,p,{value:request,writable:true,configurable:true}); } catch(e) {log(p,e.message);}
      }
      for (const p of ['exitPointerLock','webkitExitPointerLock','mozExitPointerLock']) {
        try { Object.defineProperty(Document.prototype,p,{value:exit,writable:true,configurable:true}); } catch(e) {log(p,e.message);}
      }
    }
  }
  install(window.__browserDeckConfig);
  function largest(selector) {
    let best=null, area=0;
    for(const el of document.querySelectorAll(selector)) {
      const r=el.getBoundingClientRect(), a=Math.max(0,Math.min(r.right,innerWidth)-Math.max(r.left,0))*Math.max(0,Math.min(r.bottom,innerHeight)-Math.max(r.top,0));
      if(a>area && getComputedStyle(el).visibility!=='hidden' && getComputedStyle(el).display!=='none'){best=el;area=a;}
    }
    return {el:best,area};
  }
  function gameFrame() {
    const f=largest('iframe'), c=largest('canvas');
    return f.el && f.area>innerWidth*innerHeight*.28 && f.area>c.area ? f.el.contentWindow:null;
  }
  function keyInfo(code) {
    if(/^Key[A-Z]$/.test(code)) return [code.slice(3).toLowerCase(),code.charCodeAt(3)];
    if(/^Digit[0-9]$/.test(code)) return [code.slice(5),code.charCodeAt(5)];
    const map={Space:[' ',32],ShiftLeft:['Shift',16],ShiftRight:['Shift',16],ControlLeft:['Control',17],AltLeft:['Alt',18],Escape:['Escape',27],Enter:['Enter',13],Tab:['Tab',9],Backspace:['Backspace',8],ArrowLeft:['ArrowLeft',37],ArrowUp:['ArrowUp',38],ArrowRight:['ArrowRight',39],ArrowDown:['ArrowDown',40]};
    return map[code] || [code,0];
  }
  function key(code,down) {
    const [k,n]=keyInfo(code), target=document.activeElement || document.body || document;
    const ev=new KeyboardEvent(down?'keydown':'keyup',{key:k,code,bubbles:true,cancelable:true,composed:true,shiftKey:held.has('ShiftLeft')||held.has('ShiftRight'),ctrlKey:held.has('ControlLeft'),altKey:held.has('AltLeft')});
    for(const p of ['keyCode','which']) try{Object.defineProperty(ev,p,{get:()=>n});}catch(e){}
    target.dispatchEvent(ev);
    if(down && code==='Escape' && locked) document.exitPointerLock();
  }
  function mask() { let b=0;for(const n of buttons)b|=(n===0?1:n===1?4:2);return b; }
  function isLocked(){return !!(locked||document.pointerLockElement);}
  function mouse(type,button,dx=0,dy=0) {
    const lock=state.mode!=='cursor' && isLocked(), x=lock?innerWidth/2:px,y=lock?innerHeight/2:py;
    const target=(lock&&(locked||document.pointerLockElement))||(state.mode==='look'&&largest('canvas').el)||document.elementFromPoint(x,y)||document.body||document;
    const init={bubbles:true,cancelable:true,composed:true,view:window,clientX:x,clientY:y,screenX:x,screenY:y,button,buttons:mask(),movementX:dx,movementY:dy};
    const pt={mousedown:'pointerdown',mouseup:'pointerup',mousemove:'pointermove'}[type];
    function send(e){for(const [p,v] of [['movementX',dx],['movementY',dy]]) if(e[p]!==v)try{Object.defineProperty(e,p,{get:()=>v});}catch(err){}return target.dispatchEvent(e);}
    let ok=true;
    if(pt && window.PointerEvent)ok=send(new PointerEvent(pt,{...init,pointerId:1,pointerType:'mouse',isPrimary:true,pressure:mask()?0.5:0}));
    if(ok || type==='mousemove')send(new MouseEvent(type,init));
  }
  function apply(s, suppressClick=false) {
    state=Object.assign(state,s);
    if (state.mode==='cursor' && isLocked() && document.exitPointerLock) document.exitPointerLock();
    const nk=new Set(state.keys||[]), nb=new Set(state.mouse||[]);
    for(const k of [...held]) if(!nk.has(k)){held.delete(k);key(k,false);}
    for(const k of nk) if(!held.has(k)){held.add(k);key(k,true);}
    for(const b of [...buttons]) if(!nb.has(b)){buttons.delete(b);mouse('mouseup',b);if(b===0&&!suppressClick)mouse('click',b);}
    for(const b of nb) if(!buttons.has(b)){buttons.add(b);mouse('mousedown',b);}
  }
  function zero(){return {keys:[],mouse:[],x:0,y:0};}
  function release(){remainderX=remainderY=0;if(child){try{child.postMessage({tag,state:zero()},'*');}catch(e){}child=null;}apply(zero(),true);}
  function route(s) {
    if(typeof s.fakeLock==='boolean' && s.fakeLock!==cfg.fakeLock)install({fakeLock:s.fakeLock});
    state=Object.assign({},state,s);
    const next=gameFrame();
    if(next!==child){const saved={...state};release();state=saved;child=next;}
    if(child){try{child.postMessage({tag,state},'*');}catch(e){log(e.message);}}
    else apply(state);
  }
  function drawCursor(show){
    if(!show){if(cursor)cursor.style.display='none';return;}
    if(!cursor||!cursor.isConnected){if(!document.documentElement)return;cursor=document.createElement('div');cursor.style.cssText='position:fixed;z-index:2147483647;width:16px;height:16px;border:2px solid white;border-radius:50%;box-shadow:0 0 2px 2px black;pointer-events:none;transform:translate(-50%,-50%);';document.documentElement.appendChild(cursor);}
    cursor.style.display='block';cursor.style.left=px+'px';cursor.style.top=py+'px';
  }
  function tick(t){
    const dt=Math.min(.05,Math.max(0,(t-last)/1000));last=t;
    if(!child){
      const dx=(state.x||0)*state.speed*dt, dy=(state.y||0)*state.speed*dt*(state.invert?-1:1);
      const look=state.mode==='look'||(state.mode!=='cursor'&&isLocked());
      if(!look){px=Math.min(Math.max(0,innerWidth-1),Math.max(0,px+dx));py=Math.min(Math.max(0,innerHeight-1),Math.max(0,py+dy));}
      remainderX+=dx;remainderY+=dy;const mx=Math.trunc(remainderX),my=Math.trunc(remainderY);remainderX-=mx;remainderY-=my;if(mx||my)mouse('mousemove',0,mx,my);
      drawCursor(!look && (Math.abs(state.x)+Math.abs(state.y)>0 || state.mode==='cursor'));
    }else drawCursor(false);
    requestAnimationFrame(tick);
  }
  window.addEventListener('message',e=>{if(window!==window.top && e.source===window.parent && e.data && e.data.tag===tag)route(e.data.state||zero());});
  window.addEventListener('pagehide',release);
  document.addEventListener('visibilitychange',()=>{if(document.hidden)release();});
  window.__browserDeck={input:route,release,probe:async()=>{
    let gpu='not exposed';if(navigator.gpu)try{gpu=(await navigator.gpu.requestAdapter())?'adapter available':'adapter unavailable';}catch(e){gpu=String(e);}
    let gl='unavailable';try{gl=document.createElement('canvas').getContext('webgl2')?'available':'unavailable';}catch(e){}
    let sharedWasm=false;try{const m=new WebAssembly.Memory({initial:1,maximum:1,shared:true});sharedWasm=typeof SharedArrayBuffer==='function'&&m.buffer instanceof SharedArrayBuffer;}catch(e){} log('probe',JSON.stringify({crossOriginIsolated:window.crossOriginIsolated,sharedArrayBuffer:typeof SharedArrayBuffer,sharedWasm,gpu,webgl2:gl,lock:!!document.pointerLockElement,shim:cfg.fakeLock,canvas:document.querySelectorAll('canvas').length,frames:document.querySelectorAll('iframe').length,ua:navigator.userAgent}));
  }};
  requestAnimationFrame(tick);log('input ready',window===window.top?'top':'frame');
})();
