# Native lookup uses exact bridge class and sink method names/signatures.
-keep,includedescriptorclasses class io.github.baitian6641.cloudy.desktop.vnc.VncBridge { *; }
-keep interface io.github.baitian6641.cloudy.desktop.vnc.VncBridge$Sink { *; }
-keepclassmembers class * implements io.github.baitian6641.cloudy.desktop.vnc.VncBridge$Sink {
    public void onState(long,int,int);
    public void onFrame(long,int,int,byte[]);
    public void onInputResult(long,long,boolean);
}
