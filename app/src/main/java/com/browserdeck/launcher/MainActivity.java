package com.browserdeck.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.mozilla.geckoview.*;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

public class MainActivity extends Activity {
    private static final String[] LABELS={"A (right)","B (bottom)","X (top)","Y (left)","L1","R1","L2","R2","L3","R3","D-pad left","D-pad up","D-pad right","D-pad down","Start","Select","Left stick up","Left stick down","Left stick left","Left stick right"};
    private static final String[] DEFAULTS={"Mouse0","Space","KeyV","KeyE","","","Mouse2","Mouse0","ShiftLeft","","Digit1","Digit2","Digit3","","Escape","","KeyW","KeyS","KeyA","KeyD"};
    private static final int[] KEYCODES={KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y,KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_R1,KeyEvent.KEYCODE_BUTTON_L2,KeyEvent.KEYCODE_BUTTON_R2,KeyEvent.KEYCODE_BUTTON_THUMBL,KeyEvent.KEYCODE_BUTTON_THUMBR,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BUTTON_SELECT};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final boolean[] pressed=new boolean[LABELS.length], analog=new boolean[LABELS.length];
    private final StringBuilder logs=new StringBuilder();
    private final ArrayList<Profile> library=new ArrayList<>();
    private static GeckoRuntime geckoRuntime;
    private GeckoSession gecko; private GeckoView geckoView; private WebExtension.Port geckoPort;
    private Profile current; private WebView web; private FrameLayout root; private View fullscreenView; private WebChromeClient.CustomViewCallback fullscreenCallback;
    private boolean playing=false, settingsOpen=false, chord=false, start=false, select=false, documentStart=false, ready=false, modeChanged=false;
    private float rx=0, ry=0; private int mode=0; private String lastState=""; private long lastSend=0; private int inputLogs=0;
    private String script="";
    private static class Profile {
        String name,url; String[] binds=DEFAULTS.clone(); int speed=850, deadzone=18; boolean invert=false, desktop=true, fakeLock=true, alternateAxes=false, useGecko=true;
        Profile(String n,String u){name=n;url=u;}
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("name",name);o.put("url",url);o.put("binds",new JSONArray(Arrays.asList(binds)));o.put("speed",speed);o.put("deadzone",deadzone);o.put("invert",invert);o.put("desktop",desktop);o.put("fakeLock",fakeLock);o.put("alternateAxes",alternateAxes);o.put("useGecko",useGecko);}catch(JSONException ignored){}return o;}
        static Profile from(JSONObject o){Profile p=new Profile(o.optString("name","Game"),o.optString("url","https://mana.gs/play/big-ice")); JSONArray b=o.optJSONArray("binds");if(b!=null)for(int i=0;i<p.binds.length;i++)p.binds[i]=b.optString(i,p.binds[i]);p.speed=o.optInt("speed",850);p.deadzone=o.optInt("deadzone",18);p.invert=o.optBoolean("invert");p.desktop=o.optBoolean("desktop",true);p.fakeLock=o.optBoolean("fakeLock",true);p.alternateAxes=o.optBoolean("alternateAxes");p.useGecko=o.optBoolean("useGecko",true);return p;}
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);try(InputStream in=getAssets().open("input.js")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);script=out.toString("UTF-8");}catch(Exception e){log("Asset error: "+e);}load();home();handler.post(ticker);}
    private final Runnable ticker=new Runnable(){public void run(){if(playing&&!settingsOpen)send(false);handler.postDelayed(this,16);}};
    private void log(String s){if(logs.length()>60000)logs.delete(0,Math.min(10000,logs.length()));logs.append(System.currentTimeMillis()).append(" ").append(s).append('\n');}
    private void load(){try{JSONArray a=new JSONArray(getPreferences(0).getString("library","[]"));for(int i=0;i<a.length();i++)library.add(Profile.from(a.getJSONObject(i)));}catch(Exception e){log("Profile read: "+e);}if(library.isEmpty())library.add(new Profile("Big Ice","https://mana.gs/play/big-ice"));}
    private void save(){JSONArray a=new JSONArray();for(Profile p:library)a.put(p.json());getPreferences(0).edit().putString("library",a.toString()).apply();}
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,16,24,16);return l;}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(4,5,4,5);return t;}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());return b;}
    private void home(){stopGame();immersive();ScrollView scroll=new ScrollView(this);LinearLayout l=column();scroll.addView(l);setContentView(scroll);l.addView(text("BrowserDeck  /  0.2.0",26));l.addView(text("Browser games with physical controller mapping",15));for(Profile p:library){LinearLayout row=new LinearLayout(this);Button play=button("Play  ·  "+p.name+"  ["+(p.useGecko?"Gecko":"WebView")+"]",()->launch(p));row.addView(play,new LinearLayout.LayoutParams(0,-2,1));row.addView(button("Profile",()->edit(p,false)));l.addView(row);}l.addView(button("+ Add HTTPS browser game",()->edit(new Profile("","https://"),true)));l.addView(button("Export diagnostics",this::exportLog));l.addView(text("In game: Start + Select = launcher menu. Select = camera/cursor mode. Touch remains enabled. Internet required.",14));}
    private void edit(Profile p,boolean add){ScrollView sc=new ScrollView(this);LinearLayout l=column();sc.addView(l);EditText name=new EditText(this);name.setSingleLine(true);name.setText(p.name);name.setHint("Game name");l.addView(name);EditText url=new EditText(this);url.setSingleLine(true);url.setInputType(17);url.setText(p.url);l.addView(url);CheckBox engine=check(l,"Use GeckoView (Firefox engine)",p.useGecko);AlertDialog d=new AlertDialog.Builder(this).setTitle(add?"New game":"Edit game").setView(sc).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();if(!add)d.setButton(DialogInterface.BUTTON_NEUTRAL,"Remove",(x,y)->{library.remove(p);save();home();});d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{String u=url.getText().toString().trim();Uri uri=Uri.parse(u);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().isEmpty()){url.setError("Enter a valid HTTPS URL");return;}p.name=name.getText().toString().trim();if(p.name.isEmpty())p.name=uri.getHost();p.url=u;p.useGecko=engine.isChecked();if(add)library.add(p);save();d.dismiss();home();}));d.show();}
    private String desktopUA(String ua){Matcher m=Pattern.compile("Chrome/([0-9.]+)").matcher(ua);String version=m.find()?m.group(1):"130.0.0.0";return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"+version+" Safari/537.36";}
    @SuppressWarnings("SetJavaScriptEnabled") private void launch(Profile p){if(p.useGecko){launchGecko(p);return;}stopGame();current=p;playing=true;ready=false;mode=0;modeChanged=false;lastState="";inputLogs=0;clearInputs();root=new FrameLayout(this);web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);immersive();WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMediaPlaybackRequiresUserGesture(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);if(p.desktop)s.setUserAgentString(desktopUA(s.getUserAgentString()));CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);web.setFocusableInTouchMode(true);web.requestFocus();
        JSONObject cfg=new JSONObject();try{cfg.put("desktop",p.desktop);cfg.put("fakeLock",p.fakeLock);}catch(Exception ignored){}final String injection="window.__browserDeckConfig="+cfg+";\n"+script;
        documentStart=WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);if(documentStart)try{WebViewCompat.addDocumentStartJavaScript(web,injection,Collections.singleton("*"));}catch(Exception e){documentStart=false;log("Early injection failed: "+e);}
        log("Launch "+p.name+" host="+Uri.parse(p.url).getHost()+" Android="+Build.VERSION.RELEASE+" WebView="+WebView.getCurrentWebViewPackage()+" earlyInjection="+documentStart);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){if(!"https".equalsIgnoreCase(r.getUrl().getScheme())){log("Blocked non-HTTPS navigation");return true;}return false;}
            @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap icon){ready=false;lastState="";clearInputs();log("Page started host="+Uri.parse(u).getHost());}
            @Override public void onPageCommitVisible(WebView v,String u){if(v==web&&documentStart){ready=true;lastState="";send(true);}}
            @Override public void onPageFinished(WebView v,String u){if(v!=web)return;if(!documentStart)v.evaluateJavascript(injection,null);ready=true;lastState="";log("Page finished host="+Uri.parse(u).getHost());send(true);}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())log("Page error "+e.getErrorCode()+" "+e.getDescription());}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame())log("HTTP error "+e.getStatusCode());}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){log("Renderer gone crashed="+d.didCrash());if(v==web){web=null;if(v.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)v.getParent()).removeView(v);v.destroy();home();Toast.makeText(MainActivity.this,"Browser renderer stopped; see diagnostics",Toast.LENGTH_LONG).show();}return true;}
        });
        web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){String msg=m.message();if(msg.startsWith("[BrowserDeck]")||m.messageLevel()==ConsoleMessage.MessageLevel.ERROR||m.messageLevel()==ConsoleMessage.MessageLevel.WARNING){log("JS "+m.messageLevel()+": "+msg.substring(0,Math.min(400,msg.length())));}return true;}
            @Override public void onShowCustomView(View v,CustomViewCallback cb){if(fullscreenView!=null){cb.onCustomViewHidden();return;}fullscreenView=v;fullscreenCallback=cb;root.addView(v,new FrameLayout.LayoutParams(-1,-1));immersive();}
            @Override public void onHideCustomView(){hideCustom();}
        });
        web.loadUrl(p.url);
    }

    private void launchGecko(Profile p){
        stopGame();current=p;playing=true;ready=false;mode=0;modeChanged=false;lastState="";inputLogs=0;clearInputs();
        root=new FrameLayout(this);setContentView(root);immersive();
        TextView status=text("Starting GeckoView…",20);root.addView(status,new FrameLayout.LayoutParams(-1,-1));
        log("Launch GeckoView "+p.name+" host="+Uri.parse(p.url).getHost()+" Android="+Build.VERSION.RELEASE);
        try{
            if(geckoRuntime==null)geckoRuntime=GeckoRuntime.create(getApplicationContext());
            GeckoSessionSettings opts=new GeckoSessionSettings.Builder()
                .userAgentMode(p.desktop?GeckoSessionSettings.USER_AGENT_MODE_DESKTOP:GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(p.desktop?GeckoSessionSettings.VIEWPORT_MODE_DESKTOP:GeckoSessionSettings.VIEWPORT_MODE_MOBILE).build();
            final GeckoSession session=new GeckoSession(opts);gecko=session;
            session.setProgressDelegate(new GeckoSession.ProgressDelegate(){
                @Override public void onPageStart(GeckoSession s,String u){if(s==gecko){ready=false;geckoPort=null;lastState="";clearInputs();log("Gecko page start "+Uri.parse(u).getHost());}}
                @Override public void onPageStop(GeckoSession s,boolean success){if(s==gecko)log("Gecko page stop success="+success);}
            });
            session.setContentDelegate(new GeckoSession.ContentDelegate(){
                @Override public void onCrash(GeckoSession s){if(s==gecko){log("Gecko content crash");home();Toast.makeText(MainActivity.this,"Gecko crashed; see diagnostics",Toast.LENGTH_LONG).show();}}
            });
            session.open(geckoRuntime);
            geckoRuntime.getWebExtensionController().ensureBuiltIn("resource://android/assets/gecko_input/","input@browserdeck.local").accept(extension->runOnUiThread(()->{
                if(gecko!=session||!playing)return;
                if(extension==null){geckoError("Built-in extension unavailable");return;}
                session.getWebExtensionController().setMessageDelegate(extension,new WebExtension.MessageDelegate(){
                    @Override public void onConnect(WebExtension.Port port){
                        if(session!=gecko){port.disconnect();return;}
                        geckoPort=port;
                        port.setDelegate(new WebExtension.PortDelegate(){
                            @Override public void onPortMessage(Object message,WebExtension.Port source){if(session!=gecko||source!=geckoPort)return;if(message instanceof JSONObject){JSONObject obj=(JSONObject)message;String type=obj.optString("type");if("ready".equals(type)){ready=true;lastState="";send(true);}else if("probe".equals(type)){String report=obj.optString("report");log("Gecko probe "+report.substring(0,Math.min(report.length(),2000)));}}}
                            @Override public void onDisconnect(WebExtension.Port source){if(geckoPort==source){geckoPort=null;ready=false;lastState="";clearInputs();}}
                        });
                        log("Gecko controller bridge connected");ready=true;lastState="";send(true);
                    }
                    @Override public GeckoResult<Object> onMessage(String app,Object message,WebExtension.MessageSender sender){
                        if(session==gecko&&message instanceof JSONObject){JSONObject obj=(JSONObject)message;if("probe".equals(obj.optString("type"))){String report=obj.optString("report");log("Gecko frame probe "+report.substring(0,Math.min(report.length(),2000)));}}return null;
                    }
                },"browserdeck");
                geckoView=new GeckoView(MainActivity.this);root.removeAllViews();root.addView(geckoView,new FrameLayout.LayoutParams(-1,-1));geckoView.setSession(session);geckoView.setFocusableInTouchMode(true);geckoView.requestFocus();session.loadUri(p.url);
            }),error->runOnUiThread(()->{if(gecko==session)geckoError("Extension startup: "+error);}));
        }catch(Throwable e){geckoError("Gecko startup: "+e);}
    }
    private void geckoError(String error){log(error);home();new AlertDialog.Builder(this).setTitle("GeckoView failed to start").setMessage(error+"\nYou can select WebView in the game profile or export diagnostics.").setPositiveButton("OK",null).show();}
    private void geckoCommand(String type,JSONObject state){if(geckoPort==null)return;try{JSONObject msg=new JSONObject();msg.put("type",type);if(state!=null)msg.put("state",state);geckoPort.postMessage(msg);}catch(Exception e){log("Gecko bridge: "+e);}}
    private void stopGecko(){GeckoSession s=gecko;gecko=null;geckoPort=null;if(geckoView!=null){geckoView.releaseSession();if(geckoView.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)geckoView.getParent()).removeView(geckoView);geckoView=null;}if(s!=null&&s.isOpen())s.close();}

    private void hideCustom(){if(fullscreenView!=null){root.removeView(fullscreenView);fullscreenView=null;WebChromeClient.CustomViewCallback cb=fullscreenCallback;fullscreenCallback=null;if(cb!=null)cb.onCustomViewHidden();immersive();}}
    private void clearInputs(){Arrays.fill(pressed,false);Arrays.fill(analog,false);rx=ry=0;start=select=chord=false;}
    private void release(){clearInputs();if(gecko!=null){geckoCommand("release",null);lastState="";}if(web!=null){web.evaluateJavascript("window.__browserDeck && window.__browserDeck.release()",null);lastState="";}}
    private void stopGame(){playing=false;settingsOpen=false;ready=false;release();stopGecko();hideCustom();if(web!=null){web.stopLoading();if(web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}}
    private void send(boolean force){if((web==null&&gecko==null)||!playing||!ready||settingsOpen||current==null)return;LinkedHashSet<String> keys=new LinkedHashSet<>();LinkedHashSet<Integer> mouse=new LinkedHashSet<>();for(int i=0;i<pressed.length;i++)if(pressed[i]||analog[i]){String k=current.binds[i].trim();if(k.matches("Mouse[012]"))mouse.add(k.charAt(5)-'0');else if(!k.isEmpty())keys.add(k);}JSONObject data=new JSONObject();try{data.put("keys",new JSONArray(keys));data.put("mouse",new JSONArray(mouse));data.put("x",Math.round(rx*1000)/1000.0);data.put("y",Math.round(ry*1000)/1000.0);data.put("speed",current.speed);data.put("invert",current.invert);data.put("mode",new String[]{"auto","look","cursor"}[mode]);data.put("fakeLock",current.fakeLock);}catch(Exception ignored){}String json=data.toString();long now=SystemClock.uptimeMillis();if(force||!json.equals(lastState)||now-lastSend>250){lastState=json;lastSend=now;if(gecko!=null)geckoCommand("input",data);else if(web!=null)web.evaluateJavascript("window.__browserDeck && window.__browserDeck.input("+json+")",null);}}
    private void pulse(int i){pressed[i]=true;send(true);WebView active=web;GeckoSession activeGecko=gecko;handler.postDelayed(()->{if(web==active&&gecko==activeGecko){pressed[i]=false;send(true);}},90);}
    private void cycleMode(){mode=(mode+1)%3;modeChanged=true;send(true);Toast.makeText(this,new String[]{"Mouse: automatic","Mouse: look","Mouse: cursor"}[mode],Toast.LENGTH_SHORT).show();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(!playing||settingsOpen)return super.dispatchKeyEvent(e);int code=e.getKeyCode();if(code==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)menu();return true;}int i=-1;for(int j=0;j<KEYCODES.length;j++)if(KEYCODES[j]==code){i=j;break;}if(i<0)return super.dispatchKeyEvent(e);boolean down=e.getAction()==KeyEvent.ACTION_DOWN;if(e.getAction()!=KeyEvent.ACTION_DOWN&&e.getAction()!=KeyEvent.ACTION_UP)return true;if(e.getRepeatCount()>0)return true;if(inputLogs++<60)log("Controller key "+code+" "+(down?"down":"up")+" device="+e.getDeviceId());
        if(i==14||i==15){if(i==14)start=down;else select=down;if(start&&select&&!chord){chord=true;menu();return true;}if(!down&&!chord){if(i==14)pulse(14);else if(current.binds[15].isEmpty())cycleMode();else pulse(15);}if(!start&&!select)chord=false;return true;}
        pressed[i]=down;send(true);return true;
    }
    private float dead(float x){float d=current.deadzone/100f;float a=Math.abs(x);return a<=d?0:Math.copySign(Math.min(1,(a-d)/(1-d)),x);}
    private float axis(MotionEvent e,int a){return e.getAxisValue(a);}
    private boolean has(MotionEvent e,int a){return e.getDevice()!=null&&e.getDevice().getMotionRange(a,e.getSource())!=null;}
    private boolean hysteresis(float v,boolean before){return v>(before?.34f:.48f);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){if(!playing||settingsOpen||(e.getSource()&InputDevice.SOURCE_JOYSTICK)!=InputDevice.SOURCE_JOYSTICK||e.getAction()!=MotionEvent.ACTION_MOVE)return super.dispatchGenericMotionEvent(e);float lx=dead(axis(e,MotionEvent.AXIS_X)),ly=dead(axis(e,MotionEvent.AXIS_Y));analog[16]=hysteresis(-ly,analog[16]);analog[17]=hysteresis(ly,analog[17]);analog[18]=hysteresis(-lx,analog[18]);analog[19]=hysteresis(lx,analog[19]);boolean alt=current.alternateAxes||!has(e,MotionEvent.AXIS_RX)||!has(e,MotionEvent.AXIS_RY);rx=dead(axis(e,alt?MotionEvent.AXIS_Z:MotionEvent.AXIS_RX));ry=dead(axis(e,alt?MotionEvent.AXIS_RZ:MotionEvent.AXIS_RY));float hx=axis(e,MotionEvent.AXIS_HAT_X),hy=axis(e,MotionEvent.AXIS_HAT_Y);analog[10]=hx<-.5;analog[12]=hx>.5;analog[11]=hy<-.5;analog[13]=hy>.5;analog[6]=Math.max(axis(e,MotionEvent.AXIS_LTRIGGER),axis(e,MotionEvent.AXIS_BRAKE))>.45;analog[7]=Math.max(axis(e,MotionEvent.AXIS_RTRIGGER),axis(e,MotionEvent.AXIS_GAS))>.45;if(inputLogs++<60)log("Axes left="+lx+","+ly+" right="+rx+","+ry+" RX/RY="+!alt+" triggers="+analog[6]+","+analog[7]);send(false);return true;}
    private void menu(){if(!playing||settingsOpen)return;release();settingsOpen=true;String[] choices={"Resume","Controller profile & sensitivity","Cycle mouse mode","Run graphics/input probe","Reload game","Export diagnostics","Back to library"};AlertDialog d=new AlertDialog.Builder(this).setTitle("BrowserDeck · "+current.name).setItems(choices,(a,i)->{settingsOpen=false;switch(i){case 1:handler.post(()->{if(playing)settings();});break;case 2:cycleMode();break;case 3:if(gecko!=null)geckoCommand("probe",null);else if(web!=null)web.evaluateJavascript("window.__browserDeck && window.__browserDeck.probe()",null);Toast.makeText(this,"Probe added to diagnostics",Toast.LENGTH_SHORT).show();break;case 4:if(gecko!=null){release();gecko.reload();}else if(web!=null)web.reload();break;case 5:exportLog();break;case 6:home();break;}}).create();d.setOnDismissListener(x->{settingsOpen=false;immersive();if(web!=null)web.requestFocus();if(geckoView!=null)geckoView.requestFocus();});d.show();}
    private CheckBox check(LinearLayout l,String label,boolean value){CheckBox b=new CheckBox(this);b.setText(label);b.setChecked(value);l.addView(b);return b;}
    private void settings(){release();settingsOpen=true;Profile p=current;ScrollView sc=new ScrollView(this);LinearLayout l=column();sc.addView(l);l.addView(text("Browser key codes: KeyW, Digit1, Space, ShiftLeft, Escape, ArrowUp, Mouse0 (left), Mouse1 (middle), Mouse2 (right). Empty = unbound. Select cycles mouse mode if unbound.",14));EditText speed=new EditText(this);speed.setInputType(2);speed.setText(""+p.speed);l.addView(text("Camera speed (pixels/second; 100–3000)",15));l.addView(speed);EditText dz=new EditText(this);dz.setInputType(2);dz.setText(""+p.deadzone);l.addView(text("Stick deadzone (0–50%)",15));l.addView(dz);CheckBox inv=check(l,"Invert camera Y",p.invert), alt=check(l,"Use Z/RZ for right stick instead of RX/RY",p.alternateAxes), desk=check(l,"Desktop browser identity (reload)",p.desktop), shim=check(l,"Emulate Pointer Lock (reload)",p.fakeLock);EditText[] edits=new EditText[LABELS.length];for(int i=0;i<edits.length;i++){LinearLayout row=new LinearLayout(this);row.addView(text(LABELS[i],15),new LinearLayout.LayoutParams(0,-2,1));EditText f=new EditText(this);f.setSingleLine(true);f.setText(p.binds[i]);row.addView(f,new LinearLayout.LayoutParams(0,-2,1));l.addView(row);edits[i]=f;}
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Controller profile").setView(sc).setNegativeButton("Cancel",null).setNeutralButton("Reset",null).setPositiveButton("Save",null).create();d.setOnDismissListener(a->{settingsOpen=false;immersive();if(web!=null)web.requestFocus();if(geckoView!=null)geckoView.requestFocus();});d.setOnShowListener(a->{d.getButton(-3).setOnClickListener(v->{for(int i=0;i<edits.length;i++)edits[i].setText(DEFAULTS[i]);speed.setText("850");dz.setText("18");inv.setChecked(false);alt.setChecked(false);desk.setChecked(true);shim.setChecked(true);});d.getButton(-1).setOnClickListener(v->{int sp,dead;try{sp=Integer.parseInt(speed.getText().toString());dead=Integer.parseInt(dz.getText().toString());if(sp<100||sp>3000||dead<0||dead>50)throw new NumberFormatException();}catch(Exception e){speed.setError("Check numeric ranges");return;}for(EditText f:edits){String k=f.getText().toString().trim();if(!k.isEmpty()&&!k.matches("Key[A-Z]|Digit[0-9]|Mouse[012]|Space|ShiftLeft|ShiftRight|ControlLeft|AltLeft|Escape|Enter|Tab|Backspace|Arrow(Up|Down|Left|Right)")){f.setError("Use a supported browser key code");return;}}boolean reload=p.desktop!=desk.isChecked()||p.fakeLock!=shim.isChecked();p.speed=sp;p.deadzone=dead;p.invert=inv.isChecked();p.alternateAxes=alt.isChecked();p.desktop=desk.isChecked();p.fakeLock=shim.isChecked();for(int i=0;i<edits.length;i++)p.binds[i]=edits[i].getText().toString().trim();save();d.dismiss();if(reload)launch(p);});});d.show();}
    private void exportLog(){String report="BrowserDeck 0.2.0\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\n"+logs;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"BrowserDeck-diagnostics.txt");pendingReport=report;try{startActivityForResult(i,42);}catch(Exception e){new AlertDialog.Builder(this).setTitle("Diagnostics").setMessage(report).setPositiveButton("Copy",(d,w)->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("diagnostics",report))).show();}}
    private String pendingReport="";
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42&&result==RESULT_OK&&data!=null&&data.getData()!=null)try(OutputStream o=getContentResolver().openOutputStream(data.getData())){if(o==null)throw new IOException("No output stream");o.write(pendingReport.getBytes(java.nio.charset.StandardCharsets.UTF_8));Toast.makeText(this,"Diagnostics saved",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Could not save diagnostics: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    @Override protected void onPause(){release();if(web!=null)web.onPause();if(gecko!=null)gecko.setActive(false);CookieManager.getInstance().flush();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();if(gecko!=null)gecko.setActive(true);immersive();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();else release();}
    @Override public void onBackPressed(){if(playing)menu();else super.onBackPressed();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);stopGame();super.onDestroy();}
}
