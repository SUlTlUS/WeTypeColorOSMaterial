"""Exercise production dispatch, IME drawChild containers and state observation on a JVM."""
import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = {
    'android/util/Log.java': '''package android.util;
public class Log {
 public static int println(int l,String t,String m){return 0;}
 public static String getStackTraceString(Throwable t){return t.toString();}
 public static int e(String t,String m,Throwable e){return 0;}
}''',
    'android/graphics/Rect.java': '''package android.graphics;
public class Rect {
 public int left,top,right,bottom;
 public Rect(){}
 public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public Rect(Rect r){this(r.left,r.top,r.right,r.bottom);}
 public boolean isEmpty(){return left>=right||top>=bottom;}
 public boolean equals(Object o){if(!(o instanceof Rect))return false;Rect r=(Rect)o;
 return left==r.left&&top==r.top&&right==r.right&&bottom==r.bottom;}
}''',
    'android/view/ViewTreeObserver.java': '''package android.view;
import java.util.*;
public class ViewTreeObserver {
 public interface OnPreDrawListener {boolean onPreDraw();}
 public final List<OnPreDrawListener> listeners=new ArrayList<>();
 public boolean isAlive(){return true;}
 public void addOnPreDrawListener(OnPreDrawListener l){listeners.add(l);}
 public void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}
 public void frame(){for(var l:new ArrayList<>(listeners))l.onPreDraw();}
}''',
    'android/view/View.java': '''package android.view;
import android.graphics.Rect;import java.util.*;
public class View {
 public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 private Rect clip; public boolean pressed,attached=true;public int visibility;
 public final ViewTreeObserver observer=new ViewTreeObserver();
 public final List<OnAttachStateChangeListener> listeners=new ArrayList<>();
 public Rect getClipBounds(){return clip==null?null:new Rect(clip);}
 public void setClipBounds(Rect r){clip=r==null?null:new Rect(r);}
 public boolean isPressed(){return pressed;} public void setPressed(boolean b){pressed=b;}
 public int getVisibility(){return visibility;}public void setVisibility(int i){visibility=i;}
 public boolean isAttachedToWindow(){return attached;}
 public ViewTreeObserver getViewTreeObserver(){return observer;}
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.add(l);}
 public void detach(){attached=false;for(var l:listeners)l.onViewDetachedFromWindow(this);}
}''',
    'android/view/ViewGroup.java': '''package android.view;
import java.util.*;
public class ViewGroup extends View {
 public final List<View> children=new ArrayList<>();
 public int draws;public android.graphics.Canvas lastCanvas;public View lastChild;public long lastTime;
 public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}
 protected boolean drawChild(android.graphics.Canvas c,View v,long time){draws++;lastCanvas=c;lastChild=v;lastTime=time;return true;}
}''',
    'android/content/Context.java': 'package android.content;public class Context {}',
    'android/util/AttributeSet.java': 'package android.util;public interface AttributeSet {}',
    'android/widget/FrameLayout.java': '''package android.widget;
public class FrameLayout extends android.view.ViewGroup {
 public FrameLayout(android.content.Context c){}
 public FrameLayout(android.content.Context c,android.util.AttributeSet a){}
 public FrameLayout(android.content.Context c,android.util.AttributeSet a,int s){}
 public FrameLayout(android.content.Context c,android.util.AttributeSet a,int s,int r){}
}''',
    'android/graphics/Canvas.java': 'package android.graphics; public class Canvas {}',
    'dev/coloros/wetype/material/WeTypeMaterialModule.java': '''package dev.coloros.wetype.material;
public class WeTypeMaterialModule extends dev.coloros.wetype.direct.DirectModule {}''',
    'dev/coloros/wetype/direct/RuntimeTest.java': '''package dev.coloros.wetype.direct;
import java.lang.reflect.*;import java.util.*;import android.view.*;import android.graphics.*;
public class RuntimeTest {
 static int passed;
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);passed++;System.out.println("PASS "+message);}
 public int sum(int a,int b){return -99;}
 public int $wetype$original$sum(int a,int b){return a+b;}
 public static String array(String[] a){return null;}
 public static String $wetype$original$array(String[] a){return a==null?"null":String.join(",",a);}
 public void fail(){}
 public void $wetype$original$fail(){throw new IllegalArgumentException("original failure");}
 public long wide(long l,double d,boolean b){return 0;}
 public long $wetype$original$wide(long l,double d,boolean b){return l+(long)d+(b?1:0);}
 public static void main(String[] args)throws Throwable {
  RuntimeTest host=new RuntimeTest();Class<?> type=RuntimeTest.class;
  Method sum=type.getMethod("sum",int.class,int.class);String key=DirectRuntime.key(sum);
  check((int)DirectRuntime.dispatch(key,type,host,new Object[]{2,3})==5,"unhooked original call");
  StringBuilder order=new StringBuilder();
  var first=DirectRuntime.add(sum,c->{order.append("A");int n=(int)c.proceed();order.append("D");return n+1;});
  var second=DirectRuntime.add(sum,c->{order.append("B");int n=(int)c.proceed();order.append("C");return n*2;});
  check((int)DirectRuntime.dispatch(key,type,host,new Object[]{2,3})==11&&order.toString().equals("ABCD"),"ordered callback chain and primitive return");
  check((int)new DirectModule().getInvoker(sum).invoke(host,2,3)==5,"origin bypass avoids callback recursion");
  first.unhook();second.unhook();
  var stop=DirectRuntime.add(sum,c->42);
  check((int)DirectRuntime.dispatch(key,type,host,new Object[]{2,3})==42,"callback short circuit");stop.unhook();
  check((int)DirectRuntime.dispatch(key,type,host,new Object[]{2,3})==5,"unhook restores original");
  Method array=type.getMethod("array",String[].class);
  check(DirectRuntime.dispatch(DirectRuntime.key(array),type,null,new Object[]{new String[]{"a","b"}}).equals("a,b"),"static array descriptor");
  check(DirectRuntime.dispatch(DirectRuntime.key(array),type,null,new Object[]{null}).equals("null"),"null argument preservation");
  Method wide=type.getMethod("wide",long.class,double.class,boolean.class);
  check((long)DirectRuntime.dispatch(DirectRuntime.key(wide),type,host,new Object[]{8L,2.5,true})==11L,"wide primitive argument preservation");
  boolean failed=false;try{Method m=type.getMethod("fail");DirectRuntime.dispatch(DirectRuntime.key(m),type,host,new Object[]{});}
  catch(IllegalArgumentException error){failed=error.getMessage().equals("original failure");}
  check(failed,"original exception unwrapped");
  MaterialFrameLayout root=new MaterialFrameLayout(null);View child=new View();root.children.add(child);
  Rect original=new Rect(1,2,80,90);child.setClipBounds(original);boolean[] hide={true};
  Method draw=ViewGroup.class.getDeclaredMethod("drawChild",Canvas.class,View.class,long.class);
  Canvas canvas=new Canvas();int[] calls={0};
  var drawing=DirectRuntime.add(draw,c->{
   calls[0]++;if(c.getThisObject()!=root)return c.proceed();
   if(c.getArg(0)!=canvas||(long)c.getArg(2)!=123L)throw new AssertionError("real drawing arguments lost");
   return hide[0]&&c.getArg(1)==child?false:c.proceed();
  });DrawGuard.watch(root);DrawGuard.watch(root);
  root.observer.frame();
  check(root.observer.listeners.size()==1&&calls[0]==0&&child.getClipBounds().equals(original),"pre-draw never simulates drawChild or clips children");
  check(!root.drawChild(canvas,child,123L)&&root.draws==0,"covered child omitted at actual drawing entry");
  check(child.getClipBounds().equals(original),"suppression preserves child clip and descendant geometry");
  View footer=new View();root.children.add(footer);
  check(root.drawChild(canvas,footer,123L)&&root.draws==1&&root.lastChild==footer,"newer footer remains drawable");
  hide[0]=false;
  check(root.drawChild(canvas,child,123L)&&root.draws==2&&root.lastCanvas==canvas&&root.lastTime==123L,"hide-to-show transition takes effect in same draw call");
  Rect external=new Rect(3,4,30,40);child.setClipBounds(external);hide[0]=true;
  root.drawChild(canvas,child,123L);root.observer.frame();
  check(child.getClipBounds().equals(external),"host-owned clipping never modified");
  MaterialFrameLayout settings=new MaterialFrameLayout(null);
  check(settings.drawChild(canvas,child,7L)&&settings.draws==1,"suppression limited to owning container");
  int[] secondCalls={0};var secondDraw=DirectRuntime.add(draw,c->{secondCalls[0]++;return c.proceed();});
  hide[0]=false;root.drawChild(canvas,child,123L);
  check(secondCalls[0]==1&&root.draws==3,"multiple module drawing callbacks share one original call");secondDraw.unhook();
  root.detach();check(child.getClipBounds().equals(external)&&root.observer.listeners.isEmpty(),"detach removes observer without geometry restoration");drawing.unhook();
  ViewGroup next=new ViewGroup();View button=new View();next.children.add(button);DrawGuard.watch(next);next.observer.frame();
  int[] presses={0},visibility={0};
  var press=DirectRuntime.add(View.class.getMethod("setPressed",boolean.class),c->{presses[0]++;return c.proceed();});
  var visible=DirectRuntime.add(View.class.getMethod("setVisibility",int.class),c->{visibility[0]++;return c.proceed();});
  button.setPressed(true);button.setVisibility(8);next.observer.frame();next.observer.frame();
  check(presses[0]==1&&visibility[0]==0,"pressed state observed once without delayed visibility emulation");press.unhook();visible.unhook();
  var broken=DirectRuntime.add(View.class.getMethod("setPressed",boolean.class),c->{throw new IllegalStateException();});
  button.setPressed(false);next.observer.frame();
  check(button.getClipBounds()==null,"state observer failure leaves geometry untouched");broken.unhook();
  System.out.println("TOTAL "+passed);
 }
}''',
}


def main():
    jdk = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Java\jdk-21.0.10'))
    with tempfile.TemporaryDirectory(prefix='wetype-direct-runtime-') as directory:
        scratch = Path(directory)
        for name, source in SOURCES.items():
            path = scratch / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding='utf-8')
        sources = list(scratch.rglob('*.java')) + list((ROOT / 'direct/src').rglob('*.java'))
        subprocess.run([str(jdk / 'bin/javac.exe'), '-encoding', 'UTF-8', '-d', str(scratch / 'classes'),
                        *map(str, sources)], check=True)
        result = subprocess.run([str(jdk / 'bin/java.exe'), '-cp', str(scratch / 'classes'),
                                 'dev.coloros.wetype.direct.RuntimeTest'], check=True, capture_output=True, text=True)
        print(result.stdout)
        (ROOT / 'evidence/direct-runtime-validation.json').write_text(json.dumps({
            'host_jvm': True, 'device_rendering': 'not tested',
            'passed': [line[5:] for line in result.stdout.splitlines() if line.startswith('PASS ')],
        }, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
