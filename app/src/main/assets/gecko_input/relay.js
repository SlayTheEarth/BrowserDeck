(() => {
  function capabilities() {
    let sharedWasm=false, wasmError="";
    try { const m=new WebAssembly.Memory({initial:1,maximum:1,shared:true});sharedWasm=typeof SharedArrayBuffer==='function'&&m.buffer instanceof SharedArrayBuffer; } catch(e){wasmError=String(e);}
    let webgl2=false;try{webgl2=!!document.createElement("canvas").getContext("webgl2");}catch(e){}
    return JSON.stringify({origin:location.origin,top:window===window.top,crossOriginIsolated:window.crossOriginIsolated,sharedArrayBuffer:typeof SharedArrayBuffer,sharedWasm,wasmError,webgl2,webgpu:!!navigator.gpu,ua:navigator.userAgent});
  }
  if(window!==window.top){
    if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",()=>browser.runtime.sendNativeMessage("browserdeck",{type:"probe",report:capabilities()}).catch(()=>{}),{once:true});
    else browser.runtime.sendNativeMessage("browserdeck",{type:"probe",report:capabilities()}).catch(()=>{});
    return;
  }
  const port=browser.runtime.connectNative("browserdeck");
  let pending=null, closed=false;
  const dispatch=msg=>window.dispatchEvent(new CustomEvent("BrowserDeckNativeInput",{detail:JSON.stringify(msg)}));
  port.onMessage.addListener(msg=>{
    if(!msg||typeof msg.type!=="string")return;
    if(msg.type==="probe"){port.postMessage({type:"probe",report:capabilities()});}
    if(msg.type==="input"||msg.type==="release"){pending=msg;dispatch(msg);}
  });
  port.onDisconnect.addListener(()=>{closed=true;pending=null;dispatch({type:"release"});});
  function ready(){if(closed)return;port.postMessage({type:"ready"});port.postMessage({type:"probe",report:capabilities()});if(pending)dispatch(pending);}
  if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",ready,{once:true});else ready();
  window.addEventListener("pagehide",()=>{dispatch({type:"release"});if(!closed)port.disconnect();},{once:true});
})();
