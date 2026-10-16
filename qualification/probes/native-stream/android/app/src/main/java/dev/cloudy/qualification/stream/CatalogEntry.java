package dev.cloudy.qualification.stream;

/** Only bounded native text and the actual positive upstream application ID. */
public final class CatalogEntry {
    public final int id;
    public final String title;
    CatalogEntry(int id,String title) {
        if (!CatalogPolicy.validId(id)) throw new IllegalArgumentException("catalog-id-rejected");
        this.id=id;this.title=CatalogPolicy.title(title);
    }
}
