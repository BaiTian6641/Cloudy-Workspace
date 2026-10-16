package dev.cloudy.qualification.stream;

/** Pixel geometry only; no density assumptions and no decoder or input coordinate changes. */
public final class PresentationLayout {
    private PresentationLayout() {}
    public static final class Size {
        public final int width,height;
        Size(int width,int height) { this.width=width;this.height=height; }
    }
    public static Size contain(int width,int height) {
        if (width<=0 || height<=0) return new Size(0,0);
        long fitWidth=Math.min((long)width,(long)height*16/9);
        long fitHeight=fitWidth*9/16;
        // Tiny windows cannot contain one whole 16:9 pixel unit; remain unavailable.
        return fitHeight>0 ? new Size((int)fitWidth,(int)fitHeight) : new Size(0,0);
    }
    public static int paneHeight(int rootHeight,int fixedChromeHeight,int minimumControlsHeight) {
        if (rootHeight<=0 || fixedChromeHeight<0 || minimumControlsHeight<0) return 0;
        long remaining=(long)rootHeight-fixedChromeHeight-minimumControlsHeight;
        return (int)Math.max(0,Math.min(remaining,(long)rootHeight*45/100));
    }
    public static long remainingSeconds(long now,long deadline) {
        if (now<0 || deadline<0 || deadline<=now) return 0;
        long remaining=deadline-now;
        return remaining/1000+(remaining%1000==0 ? 0 : 1);
    }
}
