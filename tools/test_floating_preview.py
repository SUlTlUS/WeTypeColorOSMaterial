"""Exercise the production preview coordinator against a resettable Android/OEM boundary.

Checks ownership, capture selection, reuse and window lifecycle; does not emulate GPU rendering.
"""
import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = {
    'android/graphics/ColorFilter.java': 'package android.graphics; public class ColorFilter {}',
    'android/graphics/Matrix.java': 'package android.graphics; public class Matrix {public boolean invert(Matrix m){return true;}}',
    'android/graphics/RectF.java': 'package android.graphics; public class RectF {public float left,top,right,bottom;public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}',
    'android/graphics/Path.java': 'package android.graphics; public class Path {public enum Direction {CW} public RectF rect;public int arcs;public boolean rounded;public void reset(){arcs=0;}public void addArc(float l,float t,float r,float b,float start,float sweep){arcs++;}public void addRoundRect(RectF r,float x,float y,Direction d){rect=r;}public void transform(Matrix m){}}',
    'android/graphics/Paint.java': 'package android.graphics; public class Paint {public static final int ANTI_ALIAS_FLAG=1;public enum Style{STROKE}public enum Cap{ROUND}public Paint(int f){}public void setStyle(Style s){}public void setStrokeWidth(float w){}public void setStrokeCap(Cap c){}public void getFillPath(Path a,Path b){b.arcs=a.arcs;b.rounded=true;}}',
    'android/graphics/Canvas.java': 'package android.graphics; public class Canvas {public Path clipped,mask;public boolean restored;public int save(){return 1;}public void clipOutPath(Path p){clipped=p;}public void clipPath(Path p){mask=p;}public void restoreToCount(int s){restored=true;}}',
    'android/content/res/Resources.java': 'package android.content.res; public class Resources {public static class Metrics{public float density=3;}public Metrics getDisplayMetrics(){return new Metrics();}}',
    'android/graphics/drawable/InsetDrawable.java': 'package android.graphics.drawable; public class InsetDrawable extends Drawable {public InsetDrawable(Drawable d,int i){}public void draw(android.graphics.Canvas c){}public int getOpacity(){return 0;}}',
    'android/graphics/ColorMatrix.java': 'package android.graphics; public class ColorMatrix {public ColorMatrix(float[] f){}}',
    'android/graphics/ColorMatrixColorFilter.java': 'package android.graphics; public class ColorMatrixColorFilter extends ColorFilter {public ColorMatrixColorFilter(ColorMatrix m){}}',
    'android/graphics/Bitmap.java': '''package android.graphics;
public class Bitmap {
 public enum Config {ARGB_8888}
 public boolean alpha=true,recycled;
 public static Bitmap wrapHardwareBuffer(android.hardware.HardwareBuffer b,ColorSpace c){return new Bitmap();}
 public Bitmap copy(Config c,boolean mutable){Bitmap b=new Bitmap();b.alpha=alpha;return b;}
 public boolean hasAlpha(){return alpha;} public void setHasAlpha(boolean b){alpha=b;}
 public boolean isRecycled(){return recycled;} public void recycle(){recycled=true;}
}''',
    'android/graphics/RecordingCanvas.java': 'package android.graphics; public class RecordingCanvas {}',
    'android/graphics/RenderNode.java': '''package android.graphics;
public class RenderNode {
 public static int ended,discarded;
 public RenderNode(String n){} public void setPosition(int l,int t,int r,int b){}
 public RecordingCanvas beginRecording(int w,int h){return new RecordingCanvas();}
 public void endRecording(){ended++;} public void discardDisplayList(){discarded++;}
}''',
    'android/graphics/HardwareRenderer.java': '''package android.graphics;
public class HardwareRenderer {
 public static final int SYNC_LOST_SURFACE_REWARD_IF_FOUND=2,SYNC_CONTEXT_IS_STOPPED=4,SYNC_FRAME_DROPPED=8;
 public static boolean fail;
 public void setSurface(Object s){}public void setOpaque(boolean b){}public void setContentRoot(RenderNode n){}
 public Request createRenderRequest(){return new Request();}public void destroy(){}
 public static class Request {
  public Request setWaitForPresent(boolean b){return this;}
  public int syncAndDraw(){if(fail)throw new IllegalStateException("renderer lost");return 0;}
 }
}''',
    'android/graphics/ColorSpace.java': '''package android.graphics;
public class ColorSpace {public enum Named {SRGB} public static ColorSpace get(Named n){return new ColorSpace();}}''',
    'android/graphics/PixelFormat.java': 'package android.graphics; public class PixelFormat {public static final int RGBA_8888=1,TRANSLUCENT=-3;}',
    'android/hardware/HardwareBuffer.java': '''package android.hardware;
public class HardwareBuffer {public static final long USAGE_GPU_SAMPLED_IMAGE=1,USAGE_GPU_COLOR_OUTPUT=2;
 public void close(){}}''',
    'android/media/Image.java': '''package android.media;
public class Image {public android.hardware.HardwareBuffer getHardwareBuffer(){return new android.hardware.HardwareBuffer();}
 public void close(){}}''',
    'android/media/ImageReader.java': '''package android.media;
public class ImageReader {
 public static ImageReader newInstance(int w,int h,int f,int n,long u){return new ImageReader();}
 public Object getSurface(){return new Object();}public Image acquireLatestImage(){return new Image();}
 public void close(){}
}''',
    'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable; public class Drawable {public void draw(android.graphics.Canvas c){}}',
    'android/graphics/drawable/BitmapDrawable.java': '''package android.graphics.drawable;
public class BitmapDrawable extends Drawable {
 final android.graphics.Bitmap bitmap; public BitmapDrawable(android.graphics.Bitmap b){bitmap=b;}
 public android.graphics.Bitmap getBitmap(){return bitmap;}
}''',
    'android/view/ViewTreeObserver.java': '''package android.view;
import java.util.*;
public class ViewTreeObserver {
 public interface OnPreDrawListener {boolean onPreDraw();}
 public interface OnGlobalLayoutListener {void onGlobalLayout();}
 public final List<OnPreDrawListener> before=new ArrayList<>();
 public final List<OnGlobalLayoutListener> layouts=new ArrayList<>();
 public boolean isAlive(){return true;}
 public void addOnPreDrawListener(OnPreDrawListener l){before.add(l);}
 public void removeOnPreDrawListener(OnPreDrawListener l){before.remove(l);}
 public void addOnGlobalLayoutListener(OnGlobalLayoutListener l){layouts.add(l);}
 public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){layouts.remove(l);}
 public void frame(){for(var l:new ArrayList<>(before))l.onPreDraw();}
}''',
    'android/view/View.java': '''package android.view;
import android.graphics.*;import android.graphics.drawable.*;
public class View {
 public static final int VISIBLE=0;
 public int width=640,height=480,windowVisibility=0,draws;
 public View parent;public int x,y;
 public Object getParent(){return parent;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public void getLocationOnScreen(int[] out){out[0]=x;out[1]=y;}
 public boolean attached=true,hardware=true,shown=true;
 public Drawable background,backgroundDuringDraw; public float alpha=1;
 public final ViewTreeObserver observer=new ViewTreeObserver();
 public boolean isAttachedToWindow(){return attached;} public boolean isHardwareAccelerated(){return hardware;}
 public boolean isShown(){return shown;} public int getWindowVisibility(){return windowVisibility;}
 public int getWidth(){return width;}public int getHeight(){return height;}
 public void draw(RecordingCanvas canvas){draws++;backgroundDuringDraw=background;}
 public Drawable getBackground(){return background;} public void setBackground(Drawable d){background=d;}
 public int getPaddingLeft(){return 1;}public int getPaddingTop(){return 2;}
 public int getPaddingRight(){return 3;}public int getPaddingBottom(){return 4;}
 public void setPadding(int l,int t,int r,int b){}
 public void setAlpha(float a){alpha=a;}public float getAlpha(){return alpha;}
 public void invalidate(){}public ViewTreeObserver getViewTreeObserver(){return observer;}
}''',
    'android/view/ViewGroup.java': '''package android.view;
public class ViewGroup extends View {
 public final java.util.List<View> children=new java.util.ArrayList<>();
 public int getChildCount(){return children.size();}public View getChildAt(int n){return children.get(n);}
}''',
    'android/widget/ImageView.java': '''package android.widget;
public class ImageView extends android.view.View {
 public android.graphics.ColorFilter filter;
 public android.graphics.ColorFilter getColorFilter(){return filter;}
 public void setColorFilter(android.graphics.ColorFilter f){filter=f;}
 public android.graphics.drawable.Drawable drawable;
 public android.graphics.drawable.Drawable getDrawable(){return drawable;}
 public void setImageDrawable(android.graphics.drawable.Drawable d){drawable=d;}
 public android.graphics.Matrix getImageMatrix(){return new android.graphics.Matrix();}
}''',
    'dev/coloros/wetype/material/WeTypeTheme.java': '''package dev.coloros.wetype.material;
public class WeTypeTheme {public boolean dark;public boolean isDark(){return dark;}}''',
    'dev/coloros/wetype/material/ColorOsMaterial.java': '''package dev.coloros.wetype.material;
public class ColorOsMaterial {
 public static boolean available=true; public static int refreshes;
 boolean active; public ColorOsMaterial(ClassLoader l,WeTypeMaterialModule m,WeTypeTheme t,boolean f){}
 public void setFloatingCorner(float r){}public boolean prepare(android.view.View v){return available;}
 public boolean activate(Object w){return active=available;} public boolean isActive(){return active;}
 public boolean needsRebind(){return false;} public void refresh(){refreshes++;active=available;}
 public void release(){active=false;}
}''',
    'dev/coloros/wetype/material/WeTypeMaterialModule.java': '''package dev.coloros.wetype.material;
import java.lang.reflect.*;import java.util.*;
public class WeTypeMaterialModule {
 public interface Chain {Object proceed() throws Throwable;}
 public interface Hooker {Object intercept(Chain c) throws Throwable;}
 public final Map<Method,Hooker> hooks=new HashMap<>();
 public void add(Method m,Hooker h){hooks.put(m,h);}public void deoptimize(Method m){}
 public void info(String s){}public void error(String s,Throwable t){}
}''',
    'com/tencent/wetype/plugin/hld/model/N.java': '''package com.tencent.wetype.plugin.hld.model;
public class N {public static final N a=new N();public android.view.View root=new android.view.View();
 public android.view.View C0(){return root;}}
''',
    'fixtures/Popup.java': '''package fixtures;
public class Popup extends android.view.View {
 public static class Panel extends android.view.ViewGroup {public int getRadius(){return 30;}}
 public final Panel panel=new Panel();public final android.view.View body=new android.view.View();
 public final android.widget.ImageView U=new android.widget.ImageView();
 public Popup(android.graphics.Bitmap b){
  panel.children.add(new android.widget.ImageView());panel.children.add(new android.widget.ImageView());
  ((android.widget.ImageView)panel.getChildAt(1)).drawable=new android.graphics.drawable.Drawable();
  body.parent=panel;body.x=20;body.y=24;body.width=600;body.height=430;
  body.background=new android.graphics.drawable.Drawable();
  U.drawable=new android.graphics.drawable.BitmapDrawable(b);
 }
 public android.view.View getRootView(){return panel;}public android.view.View getContent(){return body;}
}''',
    'fixtures/f.java': '''package fixtures;
public class f {public static class g {
 public android.graphics.Bitmap g(){return new android.graphics.Bitmap();}
 public Object invoke(){return g();}
}}''',
    'dev/coloros/wetype/material/PreviewTest.java': '''package dev.coloros.wetype.material;
import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import fixtures.Popup;
public class PreviewTest {
 static int checks,originals;
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);checks++;}
 public static void main(String[] args)throws Throwable {
  WeTypeMaterialModule module=new WeTypeMaterialModule();WeTypeTheme theme=new WeTypeTheme();
  boolean[] active={true};var type=Popup.class;
  var manager=new FloatingPreviewMaterial(type.getClassLoader(),module,theme,()->active[0],type,
    type.getMethod("getRootView"),type.getMethod("getContent"));
  var capture=module.hooks.get(fixtures.f.g.class.getDeclaredMethod("g"));
  WeTypeMaterialModule.Chain original=()->{originals++;return new Bitmap();};
  var root=com.tencent.wetype.plugin.hld.model.N.a.root;
  Drawable sourceFill=new Drawable();root.background=sourceFill;
  Bitmap bitmap=(Bitmap)capture.intercept(original);
  check(root.draws==1&&originals==0&&bitmap.hasAlpha()&&root.backgroundDuringDraw==null
    &&root.background==sourceFill,"hardware capture excludes fill and restores live background");
  check(RenderNode.ended==1&&RenderNode.discarded==1,"capture recording released");
  bitmap.setHasAlpha(false);Popup popup=new Popup(bitmap);
  android.widget.ImageView frame=(android.widget.ImageView)popup.panel.getChildAt(1);
  android.graphics.ColorFilter originalFilter=new android.graphics.ColorFilter();frame.filter=originalFilter;
  Drawable old=popup.body.background;manager.attach(popup);
  check(manager.isActive()&&bitmap.hasAlpha()&&popup.body.background==null,"preview alpha and independent material");
  check(popup.panel.getChildAt(0).alpha==0&&frame.alpha==1&&frame.filter!=originalFilter,
    "filter frame fill while preserving stock resize marker alpha and layout");
  Canvas canvas=new Canvas();frame.drawable.draw(canvas);
  check(canvas.clipped!=null&&canvas.clipped.rect.left==19&&canvas.clipped.rect.top==22&&canvas.restored,
    "resize corners clip out actual content bounds and restore canvas");
  check(canvas.mask!=null&&canvas.mask.arcs==4&&canvas.mask.rounded,"four rounded arc masks preserve green marker geometry");
  bitmap.setHasAlpha(false);popup.observer.frame();
  check(bitmap.hasAlpha(),"restore alpha on cached screenshot reuse");
  int refreshed=ColorOsMaterial.refreshes;popup.observer.frame();
  check(ColorOsMaterial.refreshes==refreshed,"stable predraw avoids native reconfiguration");
  manager.suspend();popup.observer.frame();
  check(!manager.isActive()&&popup.body.background==old&&popup.panel.getChildAt(0).alpha==1&&frame.filter==originalFilter,
    "suspend restores and predraw cannot reactivate");
  manager.resume();check(manager.isActive(),"resume rebinds visible preview");
  popup.shown=false;popup.observer.frame();check(!manager.isActive(),"hidden preview releases material");
  popup.shown=true;popup.observer.frame();check(manager.isActive(),"shown preview recovers without attach");
  Popup second=new Popup(bitmap);manager.attach(second);manager.detach(popup);
  check(manager.isActive()&&popup.observer.before.isEmpty(),"one popup detach preserves other popup");
  Drawable replacement=new Drawable();second.body.background=replacement;manager.detach(second);
  check(second.body.background==replacement,"detach preserves newer background owner");
  Bitmap foreign=new Bitmap();foreign.setHasAlpha(false);Popup fallback=new Popup(foreign);manager.attach(fallback);
  check(!manager.isActive()&&!foreign.hasAlpha()&&fallback.observer.before.isEmpty(),"ignore foreign snapshots");
  active[0]=false;capture.intercept(original);check(originals==1,"inactive material uses original capture");
  active[0]=true;root.width=0;capture.intercept(original);check(originals==2,"empty capture uses original");
  root.width=640;HardwareRenderer.fail=true;capture.intercept(original);
  check(originals==3&&RenderNode.ended==2&&RenderNode.discarded==2,"renderer exception restores original and releases node");
  HardwareRenderer.fail=false;ColorOsMaterial.available=false;
  Popup unavailable=new Popup(bitmap);manager.attach(unavailable);
  check(!manager.isActive()&&unavailable.body.background!=null,"native failure keeps original panel");
  manager.clear();check(unavailable.observer.before.isEmpty(),"clear removes listeners");
  System.out.println("PASS: "+checks+" floating preview checks");
 }
}''',
}


def main():
    java_home = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Java\jdk-21.0.10'))
    production = ROOT / 'app/src/main/java/dev/coloros/wetype/material/FloatingPreviewMaterial.java'
    with tempfile.TemporaryDirectory(prefix='wetype-floating-preview-') as folder:
        work = Path(folder)
        paths = []
        for name, source in SOURCES.items():
            path = work / 'src' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding='utf-8')
            paths.append(path)
        classes = work / 'classes'
        classes.mkdir()
        subprocess.run([str(java_home / 'bin/javac.exe'), '-encoding', 'UTF-8', '-d', str(classes),
                        *map(str, paths), str(production),
                        str(production.with_name('FloatingResizeFrame.java'))], check=True)
        result = subprocess.run([str(java_home / 'bin/java.exe'), '-cp', str(classes),
                                 'dev.coloros.wetype.material.PreviewTest'], check=True,
                                capture_output=True, text=True)
    print(result.stdout.strip())
    (ROOT / 'evidence/floating-preview-validation-1.3.20.json').write_text(json.dumps({
        'production_source': str(production.relative_to(ROOT)), 'result': 'pass',
        'checks': 20, 'output': result.stdout.strip(),
        'runner': 'host JVM, simulated Android/OEM boundary',
        'gpu_and_device_visual_acceptance': 'not assessed',
    }, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
