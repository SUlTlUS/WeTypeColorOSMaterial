"""Run the production material coordinator on a host JVM with a resettable OEM boundary.

Exercises state loss and rollback; this does not replace Android visual acceptance.
Fixtures deliberately contain no Android drawing implementation.
"""
import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = {
    'android/graphics/Color.java': '''package android.graphics;
public class Color {
 public static final int TRANSPARENT=0;
 public static int red(int c){return (c>>>16)&255;}
 public static int green(int c){return (c>>>8)&255;}
 public static int blue(int c){return c&255;}
 public static int alpha(int c){return c>>>24;}
}''',
    'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;
public class Drawable {}''',
    'android/graphics/drawable/ColorDrawable.java': '''package android.graphics.drawable;
public class ColorDrawable extends Drawable { public ColorDrawable(int value){} }''',
    'android/content/res/Resources.java': '''package android.content.res;
public class Resources {
 public static class Metrics {public float density=3.5f;}
 public Metrics getDisplayMetrics(){return new Metrics();}
}''',
    'android/content/Context.java': '''package android.content;
public class Context {
 public final android.view.WindowManager wm=new android.view.WindowManager();
 public <T> T getSystemService(Class<T> type){return type.cast(wm);}
}''',
    'android/view/WindowManager.java': '''package android.view;
public class WindowManager {public boolean enabled=true;
 public boolean isCrossWindowBlurEnabled(){return enabled;}
}''',
    'android/view/View.java': '''package android.view;
public class View {
 public static final int VISIBLE=0;
 public boolean attached=true,hardware=true; public int visibility=0;
 public Object surface=new Object();
 public int x,y; public Runnable delayed;
 public void getLocationOnScreen(int[] out){out[0]=x;out[1]=y;}
 public boolean postDelayed(Runnable task,long delay){delayed=task;return true;}
 public boolean removeCallbacks(Runnable task){if(delayed==task)delayed=null;return true;}
 public void settle(){Runnable task=delayed;delayed=null;if(task!=null)task.run();}
 public final android.content.Context context=new android.content.Context();
 public android.graphics.drawable.Drawable background=new android.graphics.drawable.Drawable();
 public boolean isAttachedToWindow(){return attached;}
 public boolean isHardwareAccelerated(){return hardware;}
 public int getWindowVisibility(){return visibility;}
 public Object getRootSurfaceControl(){return surface;}
 public android.content.Context getContext(){return context;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public android.graphics.drawable.Drawable getBackground(){return background;}
 public void setBackground(android.graphics.drawable.Drawable value){background=value;}
 public void invalidate(){}
}''',
    'android/view/Window.java': '''package android.view;
public class Window {
 public final View decor=new View();
 public View getDecorView(){return decor;}
 public void setBackgroundBlurRadius(int radius){}
 public void setBackgroundDrawable(android.graphics.drawable.Drawable value){decor.background=value;}
}''',
    'android/os/SystemProperties.java': '''package android.os;
public class SystemProperties {public static String get(String key,String fallback){return "V17.0.0";}}''',
    'com/oplus/graphics/OplusBlurParam.java': '''package com.oplus.graphics;
public class OplusBlurParam {
 public int type; public float[] rgba;
 public void setBlurType(int value){type=value;}
 public void setMaterialParams(int mode,float[] mult,float[] value){rgba=value;}
 public void setSmoothCornerType(int value){}
 public void setSmoothCornerWeight(float value){}
}''',
    'com/oplus/view/ViewRootManager.java': '''package com.oplus.view;
public class ViewRootManager {
 public static final java.util.List<ViewRootManager> instances=new java.util.ArrayList<>();
 public static boolean failRadius;
 public int radius; public float corner,bottomCorner;
 public com.oplus.graphics.OplusBlurParam params;
 public final android.graphics.drawable.Drawable background=new android.graphics.drawable.Drawable();
 public ViewRootManager(android.view.View view){instances.add(this);}
 public android.graphics.drawable.Drawable getBackgroundBlurDrawable(){return background;}
 public void setBlurParams(com.oplus.graphics.OplusBlurParam value){params=value;}
 public void setBlurRadius(int value){
  if(failRadius&&value>0)throw new IllegalStateException("simulated renderer failure"); radius=value;
 }
 public void setCornerRadius(float tl,float tr,float bl,float br){corner=tl;bottomCorner=bl;}
 public void resetRenderer(){params=null;radius=0;corner=0;}
}''',
    'dev/coloros/wetype/material/WeTypeTheme.java': '''package dev.coloros.wetype.material;
class WeTypeTheme {boolean dark; void refresh(){} boolean isDark(){return dark;}}''',
    'dev/coloros/wetype/material/WeTypeMaterialModule.java': '''package dev.coloros.wetype.material;
class WeTypeMaterialModule {void info(String message){} void error(String message,Throwable error){}}''',
    'dev/coloros/wetype/material/PanelEdgeMaterial.java': '''package dev.coloros.wetype.material;
class PanelEdgeMaterial {
 PanelEdgeMaterial(WeTypeMaterialModule module){}
 void apply(android.view.View view,float corner,boolean dark,boolean floating){} void release(){}
}''',
    'dev/coloros/wetype/material/MaterialLifecycleTest.java': '''package dev.coloros.wetype.material;
import android.view.*;
import android.graphics.drawable.Drawable;
import com.oplus.view.ViewRootManager;
public class MaterialLifecycleTest {
 static int count;
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static ViewRootManager latest(){return ViewRootManager.instances.get(ViewRootManager.instances.size()-1);}
 static ColorOsMaterial material()throws Exception{
  return new ColorOsMaterial(MaterialLifecycleTest.class.getClassLoader(),new WeTypeMaterialModule(),new WeTypeTheme());
 }
 static void begin(ColorOsMaterial material,View view,Window window){
  check(material.prepare(view),"prepare");check(material.activate(window),"activate");
 }
 public static void main(String[]args)throws Exception{
  ColorOsMaterial m=material();View v=new View();Window w=new Window();
  Drawable rootBg=v.background,windowBg=w.decor.background;
  begin(m,v,w);ViewRootManager first=latest();
  first.resetRenderer();m.refresh();
  check(first.radius==150&&first.params!=null&&first.params.type==2&&first.corner>0,
        "unchanged theme must rebind lost renderer parameters and radius");count++;
  m.setChooserSheet(true);m.refresh();
  check(first.params.rgba[3]>.49f&&first.params.rgba[3]<.51f,
        "chooser must retain native blur with reduced tint, not flat opaque fill");
  m.setChooserSheet(false);m.refresh();
  check(first.params.rgba[3]>.79f&&first.params.rgba[3]<.81f,
        "leaving chooser must restore stock keyboard tint");count++;
  m.release();check(v.background==rootBg&&w.decor.background==windowBg&&!m.isActive(),
        "release must restore both owned backgrounds");count++;
  for(int i=0;i<8;i++){
   begin(m,v,w);m.release();
   check(v.background==rootBg&&w.decor.background==windowBg,"repeated show/hide retained transparent background");
  }count++;
  begin(m,v,w);v.context.wm.enabled=false;
  check(!m.prepare(v)&&!m.isActive()&&v.background==rootBg&&w.decor.background==windowBg,
        "blur-disabled preparation must release active transparency");count++;
  v.context.wm.enabled=true;begin(m,v,w);int managers=ViewRootManager.instances.size();
  v.surface=new Object();check(m.prepare(v),"new surface prepare");
  check(ViewRootManager.instances.size()==managers+1&&first!=latest(),"new root must get a new manager");count++;
  check(m.activate(w),"new root activate");ViewRootManager.failRadius=true;m.refresh();
  check(!m.isActive()&&v.background==rootBg&&w.decor.background==windowBg,
        "native exception must roll back both backgrounds");
  check(!m.prepare(v),"failed renderer should not spin retries on the same surface");
  ViewRootManager.failRadius=false;m.release();begin(m,v,w);count++;
  v.visibility=4;check(!m.prepare(v)&&!m.isActive()&&w.decor.background==windowBg,
        "hidden window must not keep transparency active");count++;
  v.visibility=0;begin(m,v,w);Drawable newerWindowBg=new Drawable();w.decor.background=newerWindowBg;
  m.release();check(w.decor.background==newerWindowBg,"do not overwrite a newer window background");count++;
  ColorOsMaterial repair=material();View root=new View();Window host=new Window();begin(repair,root,host);
  check(!repair.needsRebind(),"healthy background should not need per-frame reconfiguration");
  Drawable newRootBg=new Drawable();root.background=newRootBg;
  check(repair.needsRebind(),"background replacement without layout must be detectable");repair.refresh();
  check(!repair.needsRebind()&&root.background==latest().background,"repair lost background before draw");
  repair.release();check(root.background==newRootBg,"retain replacement as restore target");count++;
  begin(repair,root,host);Drawable newHostBg=new Drawable();host.decor.background=newHostBg;
  check(repair.needsRebind(),"window background replacement must be detectable");repair.refresh();
  check(!repair.needsRebind(),"repair changed window background");repair.release();
  check(host.decor.background==newHostBg,"restore latest window background after repair");count++;
  ColorOsMaterial docked=material();View dockedRoot=new View();Window dockedHost=new Window();
  begin(docked,dockedRoot,dockedHost);check(latest().bottomCorner==0,"docked bottom remains square");
  ColorOsMaterial floating=new ColorOsMaterial(MaterialLifecycleTest.class.getClassLoader(),
        new WeTypeMaterialModule(),new WeTypeTheme(),true);
  View floatingRoot=new View();Drawable floatingBg=floatingRoot.background;floating.setFloatingCorner(36f);
  check(floating.prepare(floatingRoot)&&floating.activate(null),"floating popup does not require an IME Window");
  ViewRootManager floatingManager=latest();
  check(floatingManager.corner==36f&&floatingManager.bottomCorner==36f,"floating corners follow real panel radius");
  docked.release();check(floating.isActive()&&floatingManager.radius==150,"docked release must not clear popup blur");count++;
  floatingManager.resetRenderer();floating.refresh();
  check(floatingManager.radius==150&&floatingManager.bottomCorner==36f,"floating renderer reset restores all corners");
  floating.setChooserSheet(true);floating.refresh();
  check(floatingManager.params.rgba[3]>.79f&&floatingManager.params.rgba[3]<.81f,
        "chooser tint must not affect independent floating keyboard window");count++;
  floatingRoot.x=200;floatingRoot.y=100;floatingManager.resetRenderer();
  check(floating.needsRebind(),"moving unchanged-size popup must request repair");floating.refresh();
  check(!floating.needsRebind()&&floatingManager.radius==150,"movement repair records current position");count++;
  floatingManager.resetRenderer();floatingRoot.settle();
  check(floatingManager.radius==150&&floatingManager.params!=null,"repair reset after final popup traversal");
  check(floatingRoot.delayed==null,"settled repair must not reschedule itself");count++;
  floating.refresh();floating.release();
  check(floatingRoot.delayed==null&&floatingRoot.background==floatingBg,"dismiss cancels repair and restores original background");count++;
  System.out.println("PASS: "+count+" material lifecycle scenarios");
 }
}''',
}


def main():
    java_home = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Java\jdk-21.0.10'))
    production = ROOT / 'app/src/main/java/dev/coloros/wetype/material/ColorOsMaterial.java'
    with tempfile.TemporaryDirectory(prefix='wetype-material-lifecycle-') as temp_dir:
        work = Path(temp_dir)
        paths = []
        for name, source in SOURCES.items():
            path = work / 'src' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding='utf-8')
            paths.append(path)
        classes = work / 'classes'
        classes.mkdir()
        subprocess.run([str(java_home / 'bin/javac.exe'), '-encoding', 'UTF-8', '-d', str(classes),
                        *map(str, paths), str(production)], check=True)
        result = subprocess.run([str(java_home / 'bin/java.exe'), '-cp', str(classes),
                                 'dev.coloros.wetype.material.MaterialLifecycleTest'],
                                check=True, capture_output=True, text=True)
    output = result.stdout.strip()
    print(output)
    (ROOT / 'evidence/material-lifecycle-validation-1.3.18.json').write_text(json.dumps({
        'production_source': str(production.relative_to(ROOT)), 'result': 'pass', 'scenarios': 16,
        'runner': 'host JVM with resettable OEM boundary',
        'android_visual_acceptance': 'not assessed', 'output': output,
    }, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
