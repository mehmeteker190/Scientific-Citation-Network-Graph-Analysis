package com.prolab.project.service;

import com.prolab.project.model.Article;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Graf Servis Sınıfı - Atıf Ağı Analizi
 * 
 * Bu sınıf, bilimsel makaleler arasındaki atıf ilişkilerini
 * yönlü graf yapısında modelleyen ve analiz eden tüm işlevleri içerir.
 * 
 * Özellikler:
 * - JSON dosyasından graf oluşturma (kütüphanesiz, manuel parser)
 * - H-Index, H-Core ve H-Median hesaplama
 * - Betweenness Centrality hesaplama (Brandes algoritması)
 * - K-Core Decomposition
 * 
 * @author Prolab Projesi
 * @version 1.0
 */
public class GraphService {

    // ==================== VERİ YAPILARI ====================
    
    /** 
     * Makale verileri - ID'ye göre Article nesnelerini tutar.
     * Key: Makale ID, Value: Article nesnesi
     */
    private final Map<Long, Article> articles = new HashMap<>();

    /** 
     * Yönlü Graf - Komşuluk Listesi (Adjacency List)
     * Her makale hangi makalelere referans veriyor?
     * Key: Kaynak Makale ID, Value: Hedef Makale ID'leri
     * Örnek: A -> {B, C} ise A makalesi B ve C'ye referans veriyor
     */
    private Map<Long, Set<Long>> adjList = new HashMap<>();

    /** 
     * Ters Yönlü Graf - Gelen Kenarlar (Reverse Adjacency List)
     * Her makaleye kim referans veriyor?
     * Key: Hedef Makale ID, Value: Kaynak Makale ID'leri
     * Örnek: A <- {B, C} ise A makalesine B ve C referans veriyor
     * Bu yapı H-Index hesaplamasında hız kazandırır.
     */
    private Map<Long, Set<Long>> reverseAdjList = new HashMap<>();

    // ==================== JSON OKUMA ====================

    /**
     * JSON dosyasını okuyarak graf yapısını oluşturur.
     * Harici kütüphane kullanmadan manuel parsing yapar.
     * 
     * İşlem Adımları:
     * 1. JSON içeriğini String olarak oku
     * 2. JSON array'ini parse et
     * 3. Her makale için düğüm oluştur
     * 4. Referans ilişkilerinden kenarları oluştur
     * 
     * @param in JSON dosyasının InputStream'i
     * @throws IOException Dosya okuma hatası
     */
    public void loadFromJson(InputStream in) throws IOException {
        // Mevcut verileri temizle (yeni dosya yüklendiğinde)
        articles.clear();
        adjList.clear();
        reverseAdjList.clear();

        // Adım 1: Dosyayı String olarak oku
        String jsonContent = readInputStreamToString(in);
        
        // Adım 2: JSON array'ini parse et
        List<Map<String, Object>> articlesData = parseJsonArray(jsonContent);
        
        // Adım 3: Her makale için düğüm oluştur
        for (Map<String, Object> articleMap : articlesData) {
            Long id = extractIdFromValue(articleMap.get("id"));
            if (id == null) continue; // Geçersiz ID'leri atla

            // Makale bilgilerini çıkar
            @SuppressWarnings("unchecked")
            List<String> authors = (List<String>) articleMap.getOrDefault("authors", new ArrayList<>());
            String title = (String) articleMap.get("title");
            Integer year = articleMap.get("year") != null ? ((Number) articleMap.get("year")).intValue() : null;
            
            // Referans listesini çıkar
            List<Long> referenced = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Object> refWorks = (List<Object>) articleMap.getOrDefault("referenced_works", new ArrayList<>());
            for (Object ref : refWorks) {
                Long refId = extractIdFromValue(ref);
                if (refId != null) {
                    referenced.add(refId);
                }
            }

            // Article nesnesini oluştur ve kaydet
            Article article = new Article(id, authors, title, year, referenced);
            articles.put(id, article);

            // Graf yapısında bu düğümü başlat
            adjList.putIfAbsent(id, new HashSet<>());
            reverseAdjList.putIfAbsent(id, new HashSet<>());
        }

        // Adım 4: Kenarları (referans ilişkilerini) ekle
        for (Article a : articles.values()) {
            Long sourceId = a.getId();
            for (Long targetId : a.getReferencedWorks()) {
                // Hedef düğüm veri setinde yoksa bile ekle
                adjList.putIfAbsent(targetId, new HashSet<>());
                reverseAdjList.putIfAbsent(targetId, new HashSet<>());

                // Yönlü kenar ekle: Kaynak -> Hedef
                adjList.get(sourceId).add(targetId);

                // Ters kenar ekle: Hedef <- Kaynak (H-Index için)
                reverseAdjList.get(targetId).add(sourceId);
            }
        }
    }

    /**
     * InputStream'i String'e dönüştürür.
     * UTF-8 encoding kullanarak dosyayı okur.
     * 
     * @param in Okunacak InputStream
     * @return Dosya içeriği String olarak
     */
    private String readInputStreamToString(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        char[] buffer = new char[8192]; // 8KB buffer
        int charsRead;
        while ((charsRead = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, charsRead);
        }
        reader.close();
        return sb.toString();
    }

    /**
     * JSON array'ini parse eder.
     * "[{...}, {...}, ...]" formatındaki string'i ayrıştırır.
     * 
     * @param json JSON array string'i
     * @return Parse edilmiş objelerin listesi
     */
    private List<Map<String, Object>> parseJsonArray(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        json = json.trim();
        
        // Array formatı kontrolü
        if (!json.startsWith("[") || !json.endsWith("]")) {
            return result;
        }
        
        // Dış köşeli parantezleri kaldır
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return result;

        // İç içe yapıları takip etmek için değişkenler
        int depth = 0;           // Süslü/köşeli parantez derinliği
        int start = 0;           // Obje başlangıç indeksi
        boolean inString = false; // String içinde miyiz?
        boolean escaped = false;  // Escape karakteri mi?

        // Her karakteri tara
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            // Escape karakterini işle
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            // String başlangıç/bitiş
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            // String içindeyken diğer karakterleri atla
            if (inString) continue;
            
            // Obje başlangıcı
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } 
            // Obje bitişi
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    // Tam bir obje bulundu, parse et
                    String objectStr = json.substring(start, i + 1);
                    Map<String, Object> obj = parseJsonObject(objectStr);
                    result.add(obj);
                }
            }
        }
        
        return result;
    }

    /**
     * Tek bir JSON objesini parse eder.
     * "{key: value, ...}" formatındaki string'i ayrıştırır.
     * 
     * @param json JSON obje string'i
     * @return Key-Value çiftleri içeren Map
     */
    private Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> result = new HashMap<>();
        json = json.trim();
        
        // Obje formatı kontrolü
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return result;
        }
        
        // Süslü parantezleri kaldır
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return result;

        // Key-value çiftlerini ayır
        int depth = 0;
        int start = 0;
        boolean inString = false;
        boolean escaped = false;
        List<String> pairs = new ArrayList<>();

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                pairs.add(json.substring(start, i).trim());
                start = i + 1;
            }
        }
        // Son çifti ekle
        if (start < json.length()) {
            pairs.add(json.substring(start).trim());
        }

        // Her key-value çiftini işle
        for (String pair : pairs) {
            int colonIndex = findColonIndex(pair);
            if (colonIndex == -1) continue;
            
            String key = pair.substring(0, colonIndex).trim();
            String value = pair.substring(colonIndex + 1).trim();
            
            // Key'den tırnak işaretlerini kaldır
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }
            
            // Sadece gerekli alanları parse et (performans için)
            if (key.equals("id") || key.equals("title") || key.equals("year") || 
                key.equals("authors") || key.equals("referenced_works")) {
                result.put(key, parseValue(value));
            }
        }
        
        return result;
    }

    /**
     * JSON key:value ayracı olan ':' karakterinin indeksini bulur.
     * String içindeki ':' karakterlerini atlar.
     */
    private int findColonIndex(String pair) {
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString && c == ':') return i;
        }
        return -1;
    }

    /**
     * JSON değerini uygun Java tipine dönüştürür.
     * Desteklenen tipler: null, String, Boolean, Number, Array, Object
     */
    private Object parseValue(String value) {
        value = value.trim();
        
        if (value.equals("null")) return null;
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return unescapeString(value.substring(1, value.length() - 1));
        }
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;
        if (value.startsWith("[")) return parseArray(value);
        if (value.startsWith("{")) return parseJsonObject(value);
        
        // Sayı olarak parse etmeyi dene
        try {
            if (value.contains(".")) return Double.parseDouble(value);
            else return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /**
     * JSON array'ini parse eder (basit değerler için).
     * "[item1, item2, ...]" formatını ayrıştırır.
     */
    private List<Object> parseArray(String json) {
        List<Object> result = new ArrayList<>();
        json = json.trim();
        
        if (!json.startsWith("[") || !json.endsWith("]")) return result;
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return result;

        int depth = 0;
        int start = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                String element = json.substring(start, i).trim();
                if (!element.isEmpty()) result.add(parseValue(element));
                start = i + 1;
            }
        }
        
        // Son elemanı ekle
        if (start < json.length()) {
            String element = json.substring(start).trim();
            if (!element.isEmpty()) result.add(parseValue(element));
        }
        
        return result;
    }

    /**
     * JSON escape karakterlerini çözer.
     * \n, \t, \", \\ gibi karakterleri dönüştürür.
     */
    private String unescapeString(String s) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            
            if (escaped) {
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    default: sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
    }

    /**
     * ID değerini Long tipine dönüştürür.
     * OpenAlex URL formatını veya doğrudan sayıyı destekler.
     * 
     * Örnek: "https://openalex.org/W2756105776" -> 2756105776
     */
    private Long extractIdFromValue(Object value) {
        if (value == null) return null;
        
        // Zaten sayıysa doğrudan dönüştür
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        
        String text = value.toString();
        try {
            // OpenAlex URL formatı: "https://openalex.org/W2756105776"
            if (text.startsWith("http") && text.contains("/W")) {
                int wIndex = text.lastIndexOf("/W");
                if (wIndex != -1) {
                    return Long.parseLong(text.substring(wIndex + 2));
                }
            }
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            // Fallback: String'in hash kodunu kullan
            return (long) text.hashCode();
        }
    }

    // ==================== İSTATİSTİK METODLARI ====================

    /**
     * Graf istatistiklerini hesaplar.
     * 
     * @return İstatistik bilgilerini içeren Map:
     *         - nodeCount: Toplam düğüm (makale) sayısı
     *         - totalReferences: Toplam kenar (referans) sayısı
     *         - mostReceivedId: En çok atıf alan makale ID'si
     *         - mostReceivedCount: En çok atıf alan makalenin atıf sayısı
     *         - mostGivenId: En çok referans veren makale ID'si
     *         - mostGivenCount: En çok referans veren makalenin referans sayısı
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // Toplam düğüm ve kenar sayıları
        long nodeCount = adjList.size();
        long edgeCount = adjList.values().stream().mapToLong(Set::size).sum();

        stats.put("nodeCount", nodeCount);
        stats.put("totalReferences", edgeCount);

        // En çok atıf alan makale (In-Degree maksimum)
        Optional<Map.Entry<Long, Set<Long>>> mostReceived = reverseAdjList.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()));

        if (mostReceived.isPresent()) {
            stats.put("mostReceivedId", mostReceived.get().getKey());
            stats.put("mostReceivedCount", mostReceived.get().getValue().size());
        }

        // En çok referans veren makale (Out-Degree maksimum)
        Optional<Map.Entry<Long, Set<Long>>> mostGiven = adjList.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()));

        if (mostGiven.isPresent()) {
            stats.put("mostGivenId", mostGiven.get().getKey());
            stats.put("mostGivenCount", mostGiven.get().getValue().size());
        }

        stats.put("totalGivenReferences", edgeCount);
        stats.put("totalReceivedReferences", edgeCount);

        return stats;
    }

    /**
     * Tüm düğümlerin bilgilerini döndürür.
     */
    public List<Map<String, Object>> getNodes() {
        return adjList.keySet().stream().map(this::nodeToMap).collect(Collectors.toList());
    }

    /**
     * Düğüm bilgilerini Map formatına dönüştürür.
     */
    private Map<String, Object> nodeToMap(Long id) {
        Article a = articles.getOrDefault(id, null);
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("title", a != null ? a.getTitle() : "Unknown");
        m.put("authors", a != null ? a.getAuthors() : Collections.emptyList());
        m.put("year", a != null ? a.getYear() : null);

        // Derece hesaplama
        int inDegree = reverseAdjList.containsKey(id) ? reverseAdjList.get(id).size() : 0;
        int outDegree = adjList.containsKey(id) ? adjList.get(id).size() : 0;

        m.put("inDegree", inDegree);
        m.put("outDegree", outDegree);
        return m;
    }

    /**
     * Tüm kenarları döndürür.
     * İki tip kenar vardır:
     * - reference: Atıf ilişkisi (siyah)
     * - sequential: ID sırasına göre bağlantı (yeşil)
     */
    public List<Map<String, Object>> getEdges() {
        List<Map<String, Object>> edges = new ArrayList<>();

        // Referans kenarları
        for (Map.Entry<Long, Set<Long>> entry : adjList.entrySet()) {
            Long from = entry.getKey();
            for (Long to : entry.getValue()) {
                Map<String, Object> m = new HashMap<>();
                m.put("from", from);
                m.put("to", to);
                m.put("type", "reference");
                edges.add(m);
            }
        }

        // Sıralı kenarlar (artan ID'ye göre)
        List<Long> sortedIds = adjList.keySet().stream().sorted().collect(Collectors.toList());
        for (int i = 0; i < sortedIds.size() - 1; i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("from", sortedIds.get(i));
            m.put("to", sortedIds.get(i + 1));
            m.put("type", "sequential");
            edges.add(m);
        }
        return edges;
    }

    // ==================== H-INDEX HESAPLAMA ====================

    /**
     * Belirtilen makale için H-Index, H-Core ve H-Median hesaplar.
     * 
     * H-Index Tanımı:
     * Bir makalenin h-indeksi, bu makaleye atıf yapan makaleler içerisinden
     * en az h atıfa sahip minimum h makalenin bulunma koşulunu sağlayan
     * en büyük h sayısıdır.
     * 
     * H-Core: H-Index şartını sağlayan makalelerin kümesi
     * H-Median: H-Core'daki makalelerin atıf sayılarının ortanca değeri
     * 
     * @param id Analiz edilecek makale ID'si
     * @return hIndex, hCore ve hMedian değerlerini içeren Map
     */
    public Map<String, Object> computeHIndex(Long id) {
        Map<String, Object> result = new HashMap<>();
        
        // Düğüm kontrolü
        if (!adjList.containsKey(id)) {
            result.put("error", "Makale bulunamadı");
            return result;
        }

        // Adım 1: Bu makaleye atıf yapan makaleleri bul
        Set<Long> citingArticles = reverseAdjList.getOrDefault(id, Collections.emptySet());

        // Adım 2: Atıf yapan makalelerin kendi atıf sayılarını al ve sırala
        List<Integer> citationCounts = citingArticles.stream()
                .map(citeId -> reverseAdjList.getOrDefault(citeId, Collections.emptySet()).size())
                .sorted(Comparator.reverseOrder()) // Büyükten küçüğe
                .collect(Collectors.toList());

        // Adım 3: H-Index hesapla
        // i+1 numaralı makalenin atıf sayısı >= i+1 olduğu sürece h'yi artır
        int h = 0;
        for (int i = 0; i < citationCounts.size(); i++) {
            if (citationCounts.get(i) >= i + 1) {
                h = i + 1;
            } else {
                break; // Koşul sağlanmadığında dur
            }
        }
        result.put("hIndex", h);

        // Adım 4: H-Core hesapla (en çok atıf alan h makale)
        int finalH = h;
        List<Long> hCore = citingArticles.stream()
                .filter(citeId -> reverseAdjList.getOrDefault(citeId, Collections.emptySet()).size() >= finalH)
                .sorted((a, b) -> Integer.compare(
                        reverseAdjList.getOrDefault(b, Collections.emptySet()).size(),
                        reverseAdjList.getOrDefault(a, Collections.emptySet()).size()))
                .limit(h)
                .collect(Collectors.toList());
        result.put("hCore", hCore);

        // Adım 5: H-Median hesapla (H-Core'un atıf sayılarının ortancası)
        List<Integer> hCounts = hCore.stream()
                .map(v -> reverseAdjList.getOrDefault(v, Collections.emptySet()).size())
                .sorted()
                .collect(Collectors.toList());

        if (!hCounts.isEmpty()) {
            int mid = hCounts.size() / 2;
            if (hCounts.size() % 2 == 1) {
                // Tek sayıda eleman: ortadaki değer
                result.put("hMedian", hCounts.get(mid));
            } else {
                // Çift sayıda eleman: ortadaki iki değerin ortalaması
                result.put("hMedian", (hCounts.get(mid - 1) + hCounts.get(mid)) / 2.0);
            }
        } else {
            result.put("hMedian", 0);
        }

        return result;
    }

    /**
     * H-Core düğümlerini döndürür (graf genişletme için).
     */
    public Map<String, Object> expandByHCore(Long id) {
        Map<String, Object> hData = computeHIndex(id);
        @SuppressWarnings("unchecked")
        List<Long> hCore = (List<Long>) hData.get("hCore");

        Set<Long> newlyAdded = new HashSet<>();
        if (hCore != null) {
            newlyAdded.addAll(hCore);
        }

        Map<String, Object> out = new HashMap<>();
        out.putAll(hData);
        out.put("newlyAdded", newlyAdded);
        return out;
    }

    // ==================== BETWEENNESS CENTRALITY ====================

    /**
     * Tüm düğümler için Betweenness Centrality hesaplar.
     * 
     * Betweenness Centrality Tanımı:
     * Bir düğümün, graftaki diğer düğümlerin olası tüm ikili düğümleri için
     * bulunan en kısa yollardan kaç tanesinin üzerinde bulunduğunu hesaplar.
     * 
     * Algoritma: Brandes Algoritması (BFS tabanlı)
     * Zaman Karmaşıklığı: O(V * E)
     * 
     * NOT: Proje gereksinimlerine göre önce yönsüz grafa dönüştürülür.
     * 
     * @return Her düğüm için betweenness değerini içeren Map
     */
    public Map<Long, Double> computeBetweennessCentrality() {
        Map<Long, Double> betweenness = new HashMap<>();
        
        // Başlangıçta tüm değerler 0
        for (Long v : adjList.keySet()) {
            betweenness.put(v, 0.0);
        }

        // Adım 1: Yönlü grafı yönsüz grafa dönüştür
        Map<Long, Set<Long>> undirectedGraph = new HashMap<>();
        for (Long u : adjList.keySet()) {
            undirectedGraph.putIfAbsent(u, new HashSet<>());
            for (Long v : adjList.get(u)) {
                undirectedGraph.get(u).add(v);
                undirectedGraph.putIfAbsent(v, new HashSet<>());
                undirectedGraph.get(v).add(u);
            }
        }

        // Adım 2: Her düğümden BFS başlat (Brandes Algoritması)
        for (Long s : undirectedGraph.keySet()) {
            Stack<Long> stack = new Stack<>();                    // Keşif sırası
            Map<Long, List<Long>> P = new HashMap<>();            // Predecessors (öncüller)
            Map<Long, Integer> sigma = new HashMap<>();           // En kısa yol sayısı
            Map<Long, Integer> dist = new HashMap<>();            // Mesafe

            // Başlangıç değerleri
            for (Long v : undirectedGraph.keySet()) {
                P.put(v, new ArrayList<>());
                sigma.put(v, 0);
                dist.put(v, -1); // -1 = henüz ziyaret edilmedi
            }
            sigma.put(s, 1);
            dist.put(s, 0);

            // BFS
            Queue<Long> Q = new LinkedList<>();
            Q.add(s);

            while (!Q.isEmpty()) {
                Long v = Q.poll();
                stack.push(v);

                for (Long w : undirectedGraph.getOrDefault(v, Collections.emptySet())) {
                    // w ilk kez keşfedildi
                    if (dist.get(w) < 0) {
                        Q.add(w);
                        dist.put(w, dist.get(v) + 1);
                    }
                    // w'ye en kısa yol v üzerinden geçiyor
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        P.get(w).add(v);
                    }
                }
            }

            // Geriye doğru bağımlılık hesaplama
            Map<Long, Double> delta = new HashMap<>();
            for (Long v : undirectedGraph.keySet()) {
                delta.put(v, 0.0);
            }

            while (!stack.isEmpty()) {
                Long w = stack.pop();
                for (Long v : P.get(w)) {
                    // Bağımlılık formülü
                    double c = ((double) sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w));
                    delta.put(v, delta.get(v) + c);
                }
                if (!w.equals(s)) {
                    betweenness.put(w, betweenness.get(w) + delta.get(w));
                }
            }
        }

        // Yönsüz graf için düzeltme (her yol iki kez sayıldı)
        for (Long v : betweenness.keySet()) {
            betweenness.put(v, betweenness.get(v) / 2.0);
        }

        return betweenness;
    }

    // ==================== K-CORE DECOMPOSITION ====================

    /**
     * K-Core alt grafını bulur.
     * 
     * K-Core Tanımı:
     * Ana grafiğin her bir düğümünün en az k derecesine sahip olduğu
     * maksimum alt grafıdır. Yani, oluşan alt grafta her düğümün
     * en az k komşusu vardır.
     * 
     * Algoritma: Soğan kabuğu soyma (Peeling)
     * - Derecesi k'dan küçük düğümleri sırayla sil
     * - Silme sonrası komşuların derecesini güncelle
     * - Kalan düğümler k-core'u oluşturur
     * 
     * @param k Minimum derece eşiği
     * @return K-Core'u oluşturan düğümlerin kümesi
     */
    public Set<Long> computeKCore(int k) {
        // Adım 1: Yönsüz graf kopyası oluştur
        Map<Long, Set<Long>> tempGraph = new HashMap<>();
        Map<Long, Integer> degrees = new HashMap<>();

        for (Long u : adjList.keySet()) {
            tempGraph.putIfAbsent(u, new HashSet<>());
            degrees.put(u, 0);
        }

        // Kenarları çift yönlü ekle ve dereceleri hesapla
        for (Long u : adjList.keySet()) {
            for (Long v : adjList.get(u)) {
                tempGraph.putIfAbsent(v, new HashSet<>());
                if (tempGraph.get(u).add(v)) {
                    degrees.put(u, degrees.getOrDefault(u, 0) + 1);
                }
                if (tempGraph.get(v).add(u)) {
                    degrees.put(v, degrees.getOrDefault(v, 0) + 1);
                }
            }
        }

        // Adım 2: Derecesi k'dan küçük düğümleri kuyruğa ekle
        Queue<Long> queue = new LinkedList<>();
        for (Map.Entry<Long, Integer> entry : degrees.entrySet()) {
            if (entry.getValue() < k) {
                queue.add(entry.getKey());
            }
        }

        // Adım 3: Düğümleri sırayla sil (Peeling)
        Set<Long> removed = new HashSet<>();

        while (!queue.isEmpty()) {
            Long v = queue.poll();
            if (removed.contains(v)) continue;

            removed.add(v);

            // Komşuların derecesini güncelle
            if (tempGraph.containsKey(v)) {
                for (Long neighbor : tempGraph.get(v)) {
                    if (!removed.contains(neighbor)) {
                        int currentDeg = degrees.get(neighbor);
                        degrees.put(neighbor, currentDeg - 1);
                        
                        // Derece k'nın altına düştüyse kuyruğa ekle
                        if (degrees.get(neighbor) < k && currentDeg >= k) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        // Adım 4: Silinmeyen düğümler K-Core'u oluşturur
        Set<Long> kCoreNodes = new HashSet<>(tempGraph.keySet());
        kCoreNodes.removeAll(removed);
        return kCoreNodes;
    }

    // ==================== GÖRSELLEŞTIRME İÇİN GETTER'LAR ====================

    /**
     * Komşuluk listesini döndürür (görselleştirme için).
     */
    public Map<Long, Set<Long>> getAdjListForDrawing() {
        return this.adjList;
    }

    /**
     * Ters komşuluk listesini döndürür (görselleştirme için).
     */
    public Map<Long, Set<Long>> getReverseAdjListForDrawing() {
        return this.reverseAdjList;
    }

    /**
     * Belirtilen ID'ye sahip makaleyi döndürür.
     */
    public Article getArticle(Long id) {
        return articles.get(id);
    }
}
