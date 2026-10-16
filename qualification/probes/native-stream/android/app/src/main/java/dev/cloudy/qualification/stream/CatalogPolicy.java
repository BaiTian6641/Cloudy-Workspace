package dev.cloudy.qualification.stream;

/** Finite catalog metadata is UI text, never a command or durable peer-body log. */
public final class CatalogPolicy {
    public static final int MAX_APPS=128;
    private CatalogPolicy() {}
    public static boolean validId(int id) { return id>0; }
    public static String title(String value) {
        if (value==null || value.length()>1024) throw new IllegalArgumentException("catalog-title-rejected");
        StringBuilder result=new StringBuilder();
        value.codePoints().limit(96).forEach(code -> {
            int type=Character.getType(code);
            if (type!=Character.CONTROL && type!=Character.FORMAT && type!=Character.SURROGATE) result.appendCodePoint(code);
        });
        return result.toString();
    }
    static java.util.List<CatalogEntry> entries(java.util.List<com.limelight.nvstream.http.NvApp> parsed) {
        if (parsed.size()>MAX_APPS) throw new IllegalArgumentException("catalog-limit");
        java.util.ArrayList<CatalogEntry> result=new java.util.ArrayList<>();
        java.util.Set<Integer> ids=new java.util.HashSet<>();
        for (com.limelight.nvstream.http.NvApp app:parsed) {
            if (!app.isInitialized() || !validId(app.getAppId()) || !ids.add(app.getAppId())) throw new IllegalArgumentException("catalog-id-rejected");
            result.add(new CatalogEntry(app.getAppId(),app.getAppName()));
        }
        return java.util.List.copyOf(result);
    }
    public static boolean selected(long currentGeneration,long suppliedGeneration,int id,boolean contained) {
        return currentGeneration>0 && currentGeneration==suppliedGeneration && validId(id) && contained;
    }
}
