"""Run production toolbar sizing against portrait/landscape layout metrics on a host JVM."""
import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = {
    'android/util/TypedValue.java': 'package android.util; public class TypedValue {public static final int COMPLEX_UNIT_PX=0;}',
    'android/view/View.java': '''package android.view;
public class View {
 public ViewGroup.LayoutParams params;public int changes;public int[] padding={0,0,0,0};
 public int left,width; public float translationX;
 public ViewGroup.LayoutParams getLayoutParams(){return params;}
 public void setLayoutParams(ViewGroup.LayoutParams p){params=p;changes++;width=p.width;}
 public int getLeft(){return left;}public int getWidth(){return width;}
 public float getTranslationX(){return translationX;}
 public void setTranslationX(float x){translationX=x;changes++;}
 public int getPaddingLeft(){return padding[0];}public int getPaddingTop(){return padding[1];}
 public int getPaddingRight(){return padding[2];}public int getPaddingBottom(){return padding[3];}
 public void setPadding(int l,int t,int r,int b){padding=new int[]{l,t,r,b};changes++;}
}''',
    'android/view/ViewGroup.java': '''package android.view;
public class ViewGroup extends View {
 public static class LayoutParams {public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}
 public static class MarginLayoutParams extends LayoutParams {public int topMargin;public MarginLayoutParams(){super(-2,-2);}}
}''',
    'android/graphics/Paint.java': '''package android.graphics;
public class Paint {public float size;public int getFontMetricsInt(Object f){return Math.round(size*1.2f);}}''',
    'android/widget/TextView.java': '''package android.widget;
public class TextView extends android.view.View {
 public final android.graphics.Paint paint=new android.graphics.Paint();public TextView(){paint.size=36;}
 public float getTextSize(){return paint.size;}public android.graphics.Paint getPaint(){return paint;}
 public void setTextSize(int unit,float size){paint.size=size;changes++;}
}''',
    'dev/coloros/wetype/material/SizingTest.java': '''package dev.coloros.wetype.material;
import android.view.*;import android.widget.*;
public class SizingTest {
 static int passed;static void check(boolean b,String m){if(!b)throw new AssertionError(m);passed++;}
 public static void main(String[] args){
  View icon=new View();icon.params=new ViewGroup.LayoutParams(168,168);icon.setPadding(32,32,32,32);
  TextView text=new TextView();var margins=new ViewGroup.MarginLayoutParams();margins.topMargin=24;text.params=margins;
  ToolbarItemSizing fit=new ToolbarItemSizing(icon,text);
  fit.apply(300,3.5f);
  check(icon.params.height==168&&text.getTextSize()==36&&margins.topMargin==24,"roomy portrait keeps stock metrics");
  fit.apply(140,3.5f);
  check(icon.params.height+margins.topMargin+text.getPaint().getFontMetricsInt(null)<=112,"landscape content fits inside row");
  check(icon.getPaddingLeft()<32&&icon.getPaddingLeft()*2<icon.params.width,"icon padding shrinks with its face");
  int changes=icon.changes+text.changes;fit.apply(140,3.5f);
  check(changes==icon.changes+text.changes,"stable metrics do not request another layout");
  fit.apply(300,3.5f);
  check(icon.params.height==168&&icon.getPaddingLeft()==32&&text.getTextSize()==36&&margins.topMargin==24,
    "rotating back restores original icon font padding and gap");
  fit.apply(140,3.5f);fit.restore();
  check(icon.params.width==168&&icon.params.height==168&&icon.getPaddingLeft()==32,"closing sheet restores metrics");
  fit.apply(140,3.5f);icon.params=new ViewGroup.LayoutParams(190,190);icon.setPadding(40,40,40,40);
  text.setTextSize(0,40);margins.topMargin=30;fit.apply(400,3.5f);
  check(icon.params.height==190&&text.getTextSize()==40&&margins.topMargin==30,"stock configuration metrics supersede prior cache");
  fit.apply(140,3.5f);icon.params=new ViewGroup.LayoutParams(77,79);fit.restore();
  check(icon.params.width==77&&icon.params.height==79,"restore preserves a newer layout owner");
  View face=new View(),glyph=new View();TextView caption=new TextView();
  face.params=new ViewGroup.LayoutParams(168,168);face.width=168;face.left=30;
  glyph.params=new ViewGroup.LayoutParams(96,96);glyph.width=96;
  caption.params=new ViewGroup.MarginLayoutParams();caption.width=110;caption.left=14;
  ToolbarItemSizing fixed=new ToolbarItemSizing(face,glyph,caption);
  fixed.apply(140,3.5f);
  check(face.params.height<168&&glyph.params.height<96,
      "small row shrinks the circular face and child glyph together");
  check(Math.abs(caption.getTranslationX()-(face.getLeft()+face.getWidth()*.5f-caption.getLeft()-caption.getWidth()*.5f))<.01f,
      "label centered on actual circular face");
  float offset=caption.getTranslationX();fixed.apply(140,3.5f);
  check(caption.getTranslationX()==offset,"repeated pre-draw alignment remains stable");
  fixed.restore();
  check(face.params.width==168&&glyph.params.width==96&&caption.getTranslationX()==0f,
      "closing editor restores circle glyph and caption placement");
  System.out.println("PASS: "+passed+" toolbar sizing scenarios");
 }
}''',
}


def main():
    java = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Java\jdk-21.0.10')) / 'bin'
    source = ROOT / 'app/src/main/java/dev/coloros/wetype/material/ToolbarItemSizing.java'
    with tempfile.TemporaryDirectory(prefix='wetype-toolbar-sizing-') as directory:
        work = Path(directory)
        paths = []
        for name, content in SOURCES.items():
            path = work / 'src' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf-8')
            paths.append(path)
        classes = work / 'classes'
        classes.mkdir()
        subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(classes),
                        *map(str, paths), str(source)], check=True)
        result = subprocess.run([str(java / 'java.exe'), '-cp', str(classes),
                                 'dev.coloros.wetype.material.SizingTest'], capture_output=True,
                                text=True, check=True)
    print(result.stdout.strip())
    (ROOT / 'evidence/toolbar-sizing-validation-1.3.17.json').write_text(json.dumps({
        'result': 'pass', 'scenarios': 8, 'output': result.stdout.strip(),
        'runner': 'production Java with host JVM layout boundary', 'device_rendering': 'not assessed',
    }, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
