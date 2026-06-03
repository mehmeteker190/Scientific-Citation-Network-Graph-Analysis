package com.prolab.project.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bilimsel makale verilerini temsil eden model sınıfı.
 * 
 * Her makale bir graf düğümüne karşılık gelir.
 * JSON dosyasından okunan makale bilgilerini saklar.
 * 
 * @author Prolab Projesi
 * @version 1.0
 */
public class Article {
    
    /** Makalenin benzersiz kimlik numarası (OpenAlex ID) */
    private Long id;
    
    /** Makalenin yazarlarının listesi */
    private List<String> authors = new ArrayList<>();
    
    /** Makalenin başlığı */
    private String title;
    
    /** Makalenin yayınlanma yılı */
    private Integer year;
    
    /** Bu makalenin referans verdiği diğer makalelerin ID listesi */
    private List<Long> referencedWorks = new ArrayList<>();

    /**
     * Varsayılan yapıcı metod.
     * Boş bir Article nesnesi oluşturur.
     */
    public Article() {
    }

    /**
     * Parametreli yapıcı metod.
     * Tüm alanları belirtilen değerlerle başlatır.
     * 
     * @param id              Makale ID'si
     * @param authors         Yazar listesi
     * @param title           Makale başlığı
     * @param year            Yayın yılı
     * @param referencedWorks Referans verilen makale ID'leri
     */
    public Article(Long id, List<String> authors, String title, Integer year, List<Long> referencedWorks) {
        this.id = id;
        this.authors = authors;
        this.title = title;
        this.year = year;
        this.referencedWorks = referencedWorks;
    }

    // ==================== GETTER VE SETTER METODLARI ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public List<Long> getReferencedWorks() {
        return referencedWorks;
    }

    public void setReferencedWorks(List<Long> referencedWorks) {
        this.referencedWorks = referencedWorks;
    }

    // ==================== YARDIMCI METODLAR ====================

    /**
     * İki Article nesnesinin eşitliğini ID'ye göre kontrol eder.
     * Aynı ID'ye sahip iki makale eşit kabul edilir.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Article)) return false;
        Article article = (Article) o;
        return Objects.equals(id, article.id);
    }

    /**
     * Article nesnesinin hash kodunu ID'ye göre hesaplar.
     * HashMap ve HashSet gibi yapılarda kullanılır.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
