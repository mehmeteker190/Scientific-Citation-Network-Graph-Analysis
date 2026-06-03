package com.prolab.project;

import com.prolab.project.model.Article;
import com.prolab.project.service.GraphService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Graf Görselleştirme Uygulaması - JavaFX
 * 
 * Bu sınıf, atıf ağı graf yapısını interaktif olarak görselleştirir.
 * Kullanıcı düğümlere tıklayarak H-Index analizi yapabilir,
 * K-Core ve Betweenness Centrality sonuçlarını görebilir.
 * 
 * Görsel Özellikler:
 * - Akıllı radyal düzen algoritması (önemli düğümler merkezde)
 * - Hiyerarşik renk kodlaması (atıf sayısına göre)
 * - Dinamik graf genişletme (H-Core tıklamaları ile)
 * - Zoom ve pan özellikleri
 * 
 * @author Prolab Projesi
 * @version 1.0
 */
public class GraphVisualizationApp extends Application {

    // ==================== SERVİS VE BİLEŞENLER ====================
    
    /** Graf servis nesnesi - tüm algoritmalar burada çalışır */
    private final GraphService graphService = new GraphService();
    
    /** Graf çizim alanı - düğümler ve kenarlar buraya eklenir */
    private Pane graphPane = new Pane();
    
    /** Kaydırılabilir panel - büyük graflar için */
    private ScrollPane scrollPane = new ScrollPane();
    
    /** İstatistik etiketi - sağ panelde gösterilir */
    private Label statsLabel = new Label("İstatistikler: Dosya bekleniyor...");

    // ==================== DÜĞÜM VE KENAR TAKİBİ ====================
    
    /** Düğüm sprite'ları - ID'ye göre görsel nesneler */
    private Map<Long, NodeSprite> nodeSprites = new HashMap<>();
    
    /** Kenar sprite listesi */
    private List<EdgeSprite> edgeSprites = new ArrayList<>();
    
    /** Genişletme modunda görünür düğümler */
    private Set<Long> visibleNodes = new HashSet<>();
    
    /** Önceki tıklamalarda eklenen düğümler (renk ayrımı için) */
    private Set<Long> previouslyAddedNodes = new HashSet<>();

    // ==================== RENK PALETİ ====================
    // Göz yormayan, hiyerarşiyi belli eden mat renkler
    
    /** Merkez düğüm rengi - En önemli makaleler (Koyu Kırmızı) */
    private final Color CENTER_COLOR = Color.web("#C0392B");
    
    /** Orta düğüm rengi - Orta seviye önem (Koyu Mavi) */
    private final Color MID_COLOR = Color.web("#2980B9");
    
    /** Dış düğüm rengi - Düşük önem (Gri) */
    private final Color OUTER_COLOR = Color.web("#7F8C8D");
    
    /** H-Core düğüm rengi (Turuncu) */
    private final Color HCORE_COLOR = Color.web("#F39C12");
    
    /** K-Core düğüm rengi (Mor) */
    private final Color KCORE_COLOR = Color.web("#8E44AD");
    
    /** Yeni eklenen düğüm rengi (Yeşil) */
    private final Color NEW_NODE_COLOR = Color.web("#27AE60");
    
    /** Tıklanan düğüm rengi (Parlak Kırmızı) */
    private final Color CLICKED_NODE_COLOR = Color.web("#E74C3C");
    
    /** Düğüm üzerindeki yazı rengi */
    private final Color TEXT_COLOR = Color.WHITE;

    // ==================== UYGULAMA BAŞLANGICI ====================

    /**
     * JavaFX uygulama başlangıç noktası.
     * Ana pencere ve tüm bileşenler burada oluşturulur.
     * 
     * @param primaryStage Ana pencere stage'i
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("PROLAB Graf Analiz Uygulaması");

        // Ana düzen: BorderPane (üst, orta, sağ paneller)
        BorderPane root = new BorderPane();
        
        // ÜST PANEL: Kontrol butonları
        root.setTop(createControls(primaryStage));

        // ORTA PANEL: Graf çizim alanı
        graphPane.setStyle("-fx-background-color: white;");
        
        // Grafı ortalamak için StackPane kullan
        StackPane centerContainer = new StackPane(graphPane);
        centerContainer.setAlignment(Pos.CENTER);
        
        // Kaydırılabilir panel ayarları
        scrollPane.setContent(centerContainer);
        scrollPane.setPannable(true);       // Sürükleyerek kaydırma
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: #ecf0f1;");
        
        // Zoom özelliğini ekle
        addZoomFunctionality();
        root.setCenter(scrollPane);

        // SAĞ PANEL: İstatistikler
        VBox statsPane = new VBox(10);
        statsPane.setStyle("-fx-padding: 10; -fx-background-color: rgba(255,255,255,0.95); " +
                          "-fx-border-color: #bdc3c7; -fx-min-width: 250;");
        
        Label headerLabel = new Label("İSTATİSTİKLER");
        headerLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        headerLabel.setTextFill(Color.web("#2c3e50"));
        
        statsPane.getChildren().addAll(headerLabel, new Separator(), statsLabel);
        root.setRight(statsPane);

        // Sahneyi oluştur ve göster
        Scene scene = new Scene(root, 1200, 900);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Ana metod - uygulamayı başlatır.
     */
    public static void main(String[] args) {
        launch(args);
    }

    // ==================== RADYAL DÜZEN ALGORİTMASI ====================

    /**
     * Akıllı Radyal Graf Düzeni Algoritması
     * 
     * Bu algoritma düğümleri önem sırasına göre yerleştirir:
     * 1. En çok atıf alan makaleler (yüksek in-degree) merkeze yakın
     * 2. Referans verdikleri makalelerin arkasına doğru yayılım
     * 3. Ebeveyn-çocuk ilişkisine göre kümeleme
     * 
     * Sonuç: Önemli makaleler merkezde, daha az önemliler dışta
     */
    private void buildSmartRadialGraph() {
        // Temizlik - önceki görsel öğeleri kaldır
        graphPane.getChildren().clear();
        nodeSprites.clear();
        edgeSprites.clear();
        graphPane.setScaleX(1.0);
        graphPane.setScaleY(1.0);

        Map<Long, Set<Long>> adj = graphService.getAdjListForDrawing();
        if (adj.isEmpty()) return;

        // ADIM 1: Düğümleri önem sırasına göre sırala (In-Degree)
        Map<Long, Set<Long>> reverseAdj = graphService.getReverseAdjListForDrawing();
        List<Long> sortedNodes = new ArrayList<>(adj.keySet());
        
        // Büyükten küçüğe sırala (en çok atıf alan en başta)
        sortedNodes.sort((id1, id2) -> {
            int d1 = reverseAdj.containsKey(id1) ? reverseAdj.get(id1).size() : 0;
            int d2 = reverseAdj.containsKey(id2) ? reverseAdj.get(id2).size() : 0;
            return Integer.compare(d2, d1); 
        });

        int nodeCount = sortedNodes.size();
        
        // ADIM 2: Alan boyutunu hesapla
        double spreadFactor = 45.0; 
        double maxRadius = Math.sqrt(nodeCount) * spreadFactor; 
        double paneSize = (maxRadius * 2) + 800;
        double centerX = paneSize / 2;
        double centerY = paneSize / 2;

        graphPane.setPrefSize(paneSize, paneSize);
        graphPane.setMinSize(paneSize, paneSize);

        // Yerleştirilen düğümlerin polar koordinatları
        Map<Long, PolarCoord> placedNodes = new HashMap<>();
        Random rand = new Random();

        // ADIM 3: Her düğümü polar koordinatlarla yerleştir
        for (int i = 0; i < nodeCount; i++) {
            Long currentId = sortedNodes.get(i);
            
            double myRadius;
            double myAngle;

            // En önemli %3'lük kesim merkeze yerleştirilir
            if (i < Math.max(10, nodeCount * 0.03)) {
                myRadius = rand.nextDouble() * 150; 
                myAngle = rand.nextDouble() * 2 * Math.PI;
            } else {
                // Diğer düğümler ebeveynlerine göre yerleştirilir
                Set<Long> references = adj.get(currentId);
                Long parentId = null;

                // Atıf verdiği (referans gösterdiği) makaleler arasından
                // zaten yerleştirilmiş olanı bul
                if (references != null) {
                    for (Long refId : references) {
                        if (placedNodes.containsKey(refId)) {
                            parentId = refId;
                            break;
                        }
                    }
                }

                if (parentId != null) {
                    // Ebeveyn bulundu - onun arkasına yerleştir
                    PolarCoord parentPos = placedNodes.get(parentId);
                    myRadius = parentPos.radius + 80 + (rand.nextDouble() * 40); 
                    double angleVariation = Math.toRadians(rand.nextDouble() * 40 - 20);
                    myAngle = parentPos.angle + angleVariation;
                } else {
                    // Ebeveyn yok - dış halkaya yerleştir
                    myRadius = 250 + (Math.sqrt(i) * 35);
                    myAngle = rand.nextDouble() * 2 * Math.PI;
                }
            }

            // Polar koordinatı kaydet
            placedNodes.put(currentId, new PolarCoord(myRadius, myAngle));

            // Kartezyen koordinatlara dönüştür
            double x = centerX + myRadius * Math.cos(myAngle);
            double y = centerY + myRadius * Math.sin(myAngle);

            // Düğüm sprite'ını oluştur
            NodeSprite sprite = new NodeSprite(currentId, x, y);
            
            // RENK BELİRLEME: Atıf sayısına göre hiyerarşik renklendirme
            int inDegree = reverseAdj.containsKey(currentId) ? reverseAdj.get(currentId).size() : 0;
            
            if (inDegree > 15) {
                // Çok önemli makale: Kırmızı, büyük
                sprite.setColor(CENTER_COLOR);
                sprite.circle.setRadius(18);
            } else if (inDegree > 5) {
                // Orta önemde: Mavi, normal
                sprite.setColor(MID_COLOR);
                sprite.circle.setRadius(14);
            } else {
                // Düşük önem: Gri, küçük
                sprite.setColor(OUTER_COLOR);
                sprite.circle.setRadius(12);
            }

            nodeSprites.put(currentId, sprite);
        }

        // ADIM 4: KENAR ÇİZİMİ - Referans bağlantıları (siyah)
        for (Map.Entry<Long, Set<Long>> entry : adj.entrySet()) {
            NodeSprite source = nodeSprites.get(entry.getKey());
            if (entry.getValue() != null) {
                for (Long targetId : entry.getValue()) {
                    NodeSprite target = nodeSprites.get(targetId);
                    if (source != null && target != null && !source.id.equals(target.id)) {
                        EdgeSprite edge = new EdgeSprite(source, target, false);
                        edgeSprites.add(edge);
                        graphPane.getChildren().addAll(edge.line, edge.arrowhead);
                    }
                }
            }
        }
        
        // ADIM 5: SIRALAMA KENARLARI (Yeşil) - Artan ID sırasına göre
        // Proje gereksinimi: ID sırasına göre sıralı bağlantılar
        List<Long> sortedByIdForSequential = new ArrayList<>(nodeSprites.keySet());
        Collections.sort(sortedByIdForSequential);
        for (int i = 0; i < sortedByIdForSequential.size() - 1; i++) {
            Long fromId = sortedByIdForSequential.get(i);
            Long toId = sortedByIdForSequential.get(i + 1);
            NodeSprite source = nodeSprites.get(fromId);
            NodeSprite target = nodeSprites.get(toId);
            if (source != null && target != null) {
                EdgeSprite sequentialEdge = new EdgeSprite(source, target, true);
                edgeSprites.add(sequentialEdge);
                graphPane.getChildren().addAll(sequentialEdge.line, sequentialEdge.arrowhead);
            }
        }

        // ADIM 6: Düğümleri en üstte göster (kenarların üstünde)
        for (NodeSprite n : nodeSprites.values()) {
            graphPane.getChildren().add(n.stack);
        }

        // ADIM 7: Kamerayı merkeze odakla
        Platform.runLater(() -> {
            scrollPane.setHvalue(0.5);
            scrollPane.setVvalue(0.5);
            graphPane.setScaleX(0.9);
            graphPane.setScaleY(0.9);
        });
    }

    /**
     * Polar Koordinat Yardımcı Sınıfı
     * Radyal düzen algoritmasında kullanılır.
     */
    private static class PolarCoord {
        double radius; // Merkezden uzaklık
        double angle;  // Açı (radyan)
        
        public PolarCoord(double r, double a) { 
            this.radius = r; 
            this.angle = a; 
        }
    }

    // ==================== DÜĞÜM SPRİTE SINIFI ====================

    /**
     * Düğüm Görsel Sınıfı
     * 
     * Her makaleyi temsil eden görsel öğe.
     * İçerir: Daire, ID yazısı, tooltip, tıklama olayı
     */
    private class NodeSprite {
        Long id;                    // Makale ID
        StackPane stack;            // Ana konteyner (daire + yazı)
        Circle circle;              // Görsel daire
        Text text;                  // ID yazısı
        Color originalColor;        // Orijinal renk (sıfırlama için)

        /**
         * Yeni düğüm sprite oluşturur.
         * @param id Makale ID
         * @param x X koordinatı
         * @param y Y koordinatı
         */
        public NodeSprite(Long id, double x, double y) {
            this.id = id;
            
            // Daire oluştur
            circle = new Circle(12); 
            circle.setFill(OUTER_COLOR);
            circle.setStroke(Color.WHITE);
            circle.setStrokeWidth(1.5);
            circle.setEffect(new DropShadow(3, Color.rgb(0,0,0,0.3)));

            // ID yazısı
            text = new Text(String.valueOf(id));
            text.setFill(TEXT_COLOR);
            text.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            text.setMouseTransparent(true);

            // Daire ve yazıyı üst üste koy
            stack = new StackPane(circle, text);
            stack.setLayoutX(x - 12);
            stack.setLayoutY(y - 12);

            // Tıklama olayı - H-Index hesaplama
            stack.setOnMouseClicked(e -> handleNodeClick(id));
            
            // Tooltip ekle
            setupTooltip();
        }

        /**
         * Düğüm için bilgi tooltip'i oluşturur.
         * Gösterir: ID, Başlık, Yazarlar, Yıl, Atıf Sayısı
         */
        private void setupTooltip() {
            Tooltip tooltip = new Tooltip();
            Article article = graphService.getArticle(id);
            
            if (article != null) {
                // Atıf sayısını hesapla
                int citationCount = 0;
                if (graphService.getReverseAdjListForDrawing().containsKey(id)) {
                    citationCount = graphService.getReverseAdjListForDrawing().get(id).size();
                }
                
                // Yazar listesini virgülle birleştir
                String authors = article.getAuthors() != null ? 
                    String.join(", ", article.getAuthors()) : "Bilinmiyor";
                Integer year = article.getYear();
                
                tooltip.setText(String.format(
                    "ID: %d\nBaşlık: %s\nYazarlar: %s\nYıl: %s\nAtıf Sayısı: %d", 
                    id, 
                    article.getTitle() != null ? article.getTitle() : "Bilinmiyor",
                    authors,
                    year != null ? year.toString() : "Bilinmiyor",
                    citationCount));
            } else {
                tooltip.setText("ID: " + id);
            }
            
            // Tooltip stilini ayarla
            tooltip.setShowDelay(Duration.ZERO);
            tooltip.setShowDuration(Duration.INDEFINITE);
            tooltip.setStyle("-fx-font-size: 13px; -fx-background-color: #2c3e50; -fx-text-fill: white;");
            Tooltip.install(stack, tooltip);
        }

        /**
         * Düğüm rengini ayarlar.
         */
        void setColor(Color c) {
            circle.setFill(c);
            this.originalColor = c;
        }
        
        /**
         * Düğümü orijinal rengine sıfırlar.
         */
        void resetColor() {
            if(this.originalColor != null) circle.setFill(this.originalColor);
            else circle.setFill(OUTER_COLOR);
        }
    }

    // ==================== KENAR SPRİTE SINIFI ====================

    /**
     * Kenar Görsel Sınıfı
     * 
     * İki düğüm arasındaki bağlantıyı temsil eder.
     * İki tip kenar vardır:
     * - Referans kenarı (siyah): Atıf ilişkisi
     * - Sıralama kenarı (yeşil): ID sırasına göre bağlantı
     */
    private class EdgeSprite {
        Line line;              // Çizgi
        Polygon arrowhead;      // Ok başı
        Long sourceId;          // Kaynak düğüm ID
        Long targetId;          // Hedef düğüm ID
        boolean isGreen;        // Sıralama kenarı mı?
        Color originalColor;    // Orijinal renk

        /**
         * Yeni kenar sprite oluşturur.
         * @param source Kaynak düğüm
         * @param target Hedef düğüm
         * @param isGreen Yeşil (sıralama) kenar mı?
         */
        public EdgeSprite(NodeSprite source, NodeSprite target, boolean isGreen) {
            this.sourceId = source.id;
            this.targetId = target.id;
            this.isGreen = isGreen;
            
            // Koordinatları hesapla (düğüm merkezinden)
            double sx = source.stack.getLayoutX() + 12;
            double sy = source.stack.getLayoutY() + 12;
            double tx = target.stack.getLayoutX() + 12;
            double ty = target.stack.getLayoutY() + 12;

            // Çizgi oluştur
            line = new Line(sx, sy, tx, ty);
            line.setStrokeWidth(isGreen ? 1.5 : 0.7); 
            
            if (isGreen) {
                // Sıralama kenarı: Yeşil, kesikli çizgi
                line.setStroke(Color.web("#27ae60"));
                line.getStrokeDashArray().addAll(5d, 5d);
                originalColor = Color.web("#27ae60");
            } else {
                // Referans kenarı: Gri, saydam
                line.setStroke(Color.web("#bdc3c7"));
                line.setOpacity(0.6);
                originalColor = Color.web("#bdc3c7");
            }

            // Ok başı oluştur
            arrowhead = new Polygon();
            arrowhead.getPoints().addAll(0.0, 0.0, -5.0, -2.5, -5.0, 2.5);
            arrowhead.setFill(isGreen ? Color.web("#27ae60") : Color.web("#95a5a6"));
            
            // Oku hedef düğümün kenarına konumlandır
            double angle = Math.atan2(ty - sy, tx - sx);
            double offset = 15.0;
            arrowhead.setLayoutX(tx - offset * Math.cos(angle));
            arrowhead.setLayoutY(ty - offset * Math.sin(angle));
            arrowhead.setRotate(Math.toDegrees(angle));
        }
        
        /**
         * Kenarı orijinal rengine sıfırlar.
         */
        void resetColor() {
            line.setStroke(originalColor);
            line.setStrokeWidth(isGreen ? 1.5 : 0.7);
            line.setOpacity(isGreen ? 1.0 : 0.6);
            arrowhead.setFill(isGreen ? Color.web("#27ae60") : Color.web("#95a5a6"));
        }
    }

    // ==================== KONTROL PANELİ ====================

    /**
     * Üst kontrol panelini oluşturur.
     * Butonlar: JSON Yükle, H-Index Başlat, K-Core, Betweenness, Tam Graf, Sıfırla
     * 
     * @param primaryStage Ana pencere (dosya seçici için gerekli)
     * @return Kontrol paneli HBox
     */
    private HBox createControls(Stage primaryStage) {
        HBox controls = new HBox(15);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setStyle("-fx-padding: 15; -fx-background-color: #2c3e50; " +
                         "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 0, 0, 0, 5);");

        // JSON Yükle Butonu
        Button uploadBtn = createStyledButton("📂 JSON Yükle", "#3498db");
        uploadBtn.setOnAction(e -> handleFileUpload(primaryStage));
        
        // H-Index Başlatma Alanı
        TextField hIndexField = new TextField();
        hIndexField.setPromptText("Makale ID");
        hIndexField.setMaxWidth(120);
        hIndexField.setStyle("-fx-padding: 7; -fx-background-radius: 4;");
        
        Button startHIndexBtn = createStyledButton("🔍 H-Index Başlat", "#27ae60");
        startHIndexBtn.setOnAction(e -> handleStartHIndex(hIndexField.getText()));

        // K-Core Alanı
        Label kLabel = new Label("K:");
        kLabel.setTextFill(Color.WHITE);
        kLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        
        TextField kField = new TextField("2");
        kField.setMaxWidth(50);
        kField.setStyle("-fx-padding: 7; -fx-background-radius: 4;");

        Button kcoreBtn = createStyledButton("⚡ K-Core", "#e67e22");
        kcoreBtn.setOnAction(e -> handleKCore(kField.getText()));

        // Betweenness Centrality Butonu
        Button bcBtn = createStyledButton("📊 Betweenness", "#9b59b6");
        bcBtn.setOnAction(e -> handleBetweennessCentrality());
        
        // Tam Graf Butonu
        Button fullGraphBtn = createStyledButton("🌐 Tam Graf", "#1abc9c");
        fullGraphBtn.setOnAction(e -> {
            visibleNodes.clear();
            previouslyAddedNodes.clear();
            buildSmartRadialGraph();
            updateStats();
        });
        
        // Sıfırla Butonu
        Button resetBtn = createStyledButton("↺ Sıfırla", "#7f8c8d");
        resetBtn.setOnAction(e -> {
            visibleNodes.clear();
            previouslyAddedNodes.clear();
            resetColors();
            resetEdgeColors();
        });

        controls.getChildren().addAll(
            uploadBtn, hIndexField, startHIndexBtn, 
            kLabel, kField, kcoreBtn, bcBtn, fullGraphBtn, resetBtn
        );
        return controls;
    }
    
    /**
     * H-Index hesaplamasını başlatır.
     * Kullanıcının girdiği ID ile genişletme modunu aktifleştirir.
     * 
     * @param idStr Makale ID string'i
     */
    private void handleStartHIndex(String idStr) {
        try {
            Long id = Long.parseLong(idStr.trim());
            
            // Görünür düğümleri sıfırla
            visibleNodes.clear();
            previouslyAddedNodes.clear();
            visibleNodes.add(id);
            
            // H-Index hesapla ve grafı başlat
            handleNodeClick(id);
        } catch (NumberFormatException e) {
            showAlert("Hata", "Geçerli bir Makale ID girin.");
        }
    }

    /**
     * Stil uygulanmış buton oluşturur.
     * 
     * @param text Buton metni
     * @param colorHex Arka plan rengi (hex)
     * @return Stilize edilmiş Button
     */
    private Button createStyledButton(String text, String colorHex) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + colorHex + "; -fx-text-fill: white; " +
                    "-fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 4; -fx-cursor: hand;");
        
        // Hover efekti
        btn.setOnMouseEntered(e -> btn.setOpacity(0.9));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }

    // ==================== ZOOM ÖZELLİĞİ ====================

    /**
     * Fare tekerleği ile zoom özelliğini ekler.
     * Scroll up: Yakınlaştır
     * Scroll down: Uzaklaştır
     */
    private void addZoomFunctionality() {
        graphPane.setOnScroll(event -> {
            event.consume();
            
            double zoomFactor = event.getDeltaY() > 0 ? 1.05 : 0.95;

            double newScaleX = graphPane.getScaleX() * zoomFactor;
            double newScaleY = graphPane.getScaleY() * zoomFactor;

            // Zoom limitlerini kontrol et
            if (newScaleX > 0.05 && newScaleX < 5) {
                graphPane.setScaleX(newScaleX);
                graphPane.setScaleY(newScaleY);
            }
        });
    }

    // ==================== DOSYA YÜKLEME ====================

    /**
     * JSON dosyası yükler ve grafı oluşturur.
     * 
     * @param primaryStage Dosya seçici için ana pencere
     */
    private void handleFileUpload(Stage primaryStage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Dosyaları", "*.json")
        );
        File file = fileChooser.showOpenDialog(primaryStage);

        if (file != null) {
            try {
                graphService.loadFromJson(new FileInputStream(file));
                buildSmartRadialGraph(); 
                updateStats();
            } catch (IOException ex) {
                showAlert("Hata", "Dosya yüklenemedi: " + ex.getMessage());
            }
        }
    }

    // ==================== İSTATİSTİK GÜNCELLEME ====================

    /**
     * Sağ paneldeki istatistikleri günceller.
     */
    private void updateStats() {
        Map<String, Object> stats = graphService.getStats();
        if (stats == null || stats.isEmpty()) return;
        
        String statsText = String.format(
            "• Toplam Makale: %s\n" +
            "• Toplam Referans: %s\n\n" +
            "• En Çok Atıf Alan:\n  ID: %s\n  Sayı: %s\n\n" +
            "• En Çok Referans Veren:\n  ID: %s\n  Sayı: %s",
            stats.get("nodeCount"), stats.get("totalReferences"),
            stats.get("mostReceivedId"), stats.get("mostReceivedCount"),
            stats.get("mostGivenId"), stats.get("mostGivenCount")
        );
        statsLabel.setText(statsText);
    }

    // ==================== H-INDEX İŞLEME ====================

    /**
     * Düğüm tıklamasını işler - H-Index hesaplama ve graf genişletme.
     * 
     * İşlem Adımları:
     * 1. H-Index, H-Core, H-Median hesapla
     * 2. Yeni düğümleri belirle (H-Core'dan)
     * 3. Görünür düğüm kümesini güncelle
     * 4. Grafı yeniden çiz
     * 5. Sonuçları göster
     * 
     * @param id Tıklanan makale ID
     */
    private void handleNodeClick(Long id) {
        // H-Index hesapla
        Map<String, Object> hData = graphService.computeHIndex(id);

        @SuppressWarnings("unchecked")
        List<Long> hCore = (List<Long>) hData.get("hCore");
        
        // Yeni eklenen düğümleri bul
        Set<Long> newlyAdded = new HashSet<>();
        for (Long coreId : hCore) {
            if (!visibleNodes.contains(coreId)) {
                newlyAdded.add(coreId);
            }
        }
        
        // Önceki düğümleri kaydet
        previouslyAddedNodes.addAll(visibleNodes);
        
        // Görünür listeyi güncelle
        visibleNodes.addAll(newlyAdded);
        visibleNodes.add(id);
        
        // Grafı yeniden çiz
        rebuildExpandedGraph(id, hCore, newlyAdded);
        
        // İstatistikleri güncelle
        updateExpandedStats();

        // Sonuç dialog'unu göster
        showAlert("H-Index Analizi",
            "Makale ID: " + id + "\n" +
            "H-Index: " + hData.get("hIndex") + "\n" +
            "H-Median: " + hData.get("hMedian") + "\n" +
            "H-Core Sayısı: " + hCore.size() + "\n" +
            "H-Core Makaleler: " + hCore + "\n" +
            "Yeni Eklenen: " + newlyAdded.size() + " düğüm"
        );
    }

    // ==================== K-CORE İŞLEME ====================

    /**
     * K-Core hesaplama ve görselleştirme.
     * K-Core düğümlerini mor renkte vurgular.
     * K-Core içindeki kenarları da mor renkte gösterir.
     * 
     * @param kStr K değeri string'i
     */
    private void handleKCore(String kStr) {
        try {
            int k = Integer.parseInt(kStr);
            Set<Long> kCoreNodes = graphService.computeKCore(k);
            
            // Renkleri sıfırla
            resetColors();
            resetEdgeColors();
            
            // K-Core düğümlerini vurgula
            int count = 0;
            for (Long id : kCoreNodes) {
                if (nodeSprites.containsKey(id)) {
                    nodeSprites.get(id).circle.setFill(KCORE_COLOR);
                    count++;
                }
            }
            
            // K-Core içindeki kenarları vurgula
            for (EdgeSprite edge : edgeSprites) {
                if (kCoreNodes.contains(edge.sourceId) && kCoreNodes.contains(edge.targetId)) {
                    edge.line.setStroke(KCORE_COLOR);
                    edge.line.setStrokeWidth(2.0);
                    edge.line.setOpacity(1.0);
                    edge.arrowhead.setFill(KCORE_COLOR);
                }
            }
            
            // Sonucu göster
            if (count == 0) {
                showAlert("Sonuç", "K=" + k + " için düğüm bulunamadı.");
            } else {
                showAlert("K-Core Sonucu", "K=" + k + " için " + count + " düğüm bulundu.");
            }
        } catch (NumberFormatException e) {
            showAlert("Hata", "Geçerli bir K değeri girin.");
        }
    }

    // ==================== BETWEENNESS CENTRALITY ====================

    /**
     * Betweenness Centrality hesaplama ve en yüksek 10 sonucu gösterme.
     */
    private void handleBetweennessCentrality() {
        Map<Long, Double> scores = graphService.computeBetweennessCentrality();
        
        // En yüksek 10 değeri al ve formatla
        String result = scores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(10)
            .map(e -> String.format("ID %d: %.2f", e.getKey(), e.getValue()))
            .collect(Collectors.joining("\n"));
        
        showAlert("Top 10 Betweenness", result.isEmpty() ? "Hesaplanamadı" : result);
    }

    // ==================== RENK SIFIRLAMA ====================

    /**
     * Tüm düğümleri orijinal renklerine sıfırlar.
     */
    private void resetColors() {
        nodeSprites.values().forEach(NodeSprite::resetColor);
    }
    
    /**
     * Tüm kenarları orijinal renklerine sıfırlar.
     */
    private void resetEdgeColors() {
        edgeSprites.forEach(EdgeSprite::resetColor);
    }
    
    // ==================== GENİŞLETİLMİŞ GRAF ÇİZİMİ ====================

    /**
     * Genişletilmiş graf çizimi - H-Core tıklamalarında kullanılır.
     * 
     * Bu metod, sadece görünür düğümleri dairesel düzende çizer.
     * Tıklanan düğüm merkeze, H-Core düğümleri iç halkaya,
     * yeni eklenenler özel renkle gösterilir.
     * 
     * @param clickedId Tıklanan makale ID
     * @param hCore H-Core düğüm listesi
     * @param newlyAdded Yeni eklenen düğümler
     */
    private void rebuildExpandedGraph(Long clickedId, List<Long> hCore, Set<Long> newlyAdded) {
        // Temizlik
        graphPane.getChildren().clear();
        nodeSprites.clear();
        edgeSprites.clear();

        Map<Long, Set<Long>> adj = graphService.getAdjListForDrawing();
        Map<Long, Set<Long>> reverseAdj = graphService.getReverseAdjListForDrawing();
        
        if (visibleNodes.isEmpty()) return;
        
        // Alan boyutunu hesapla
        int nodeCount = visibleNodes.size();
        double spreadFactor = 60.0;
        double maxRadius = Math.sqrt(nodeCount) * spreadFactor + 100;
        double paneSize = (maxRadius * 2) + 400;
        double centerX = paneSize / 2;
        double centerY = paneSize / 2;

        graphPane.setPrefSize(paneSize, paneSize);
        graphPane.setMinSize(paneSize, paneSize);
        
        // Düğümleri önem sırasına göre sırala
        List<Long> sortedVisible = new ArrayList<>(visibleNodes);
        sortedVisible.sort((a, b) -> {
            int dA = reverseAdj.containsKey(a) ? reverseAdj.get(a).size() : 0;
            int dB = reverseAdj.containsKey(b) ? reverseAdj.get(b).size() : 0;
            return Integer.compare(dB, dA);
        });
        
        // Düğümleri dairesel düzende yerleştir
        int idx = 0;
        for (Long nodeId : sortedVisible) {
            double angle = (2 * Math.PI * idx) / nodeCount;
            double radius = 100 + (idx * 30.0 / Math.max(1, nodeCount));
            
            // Tıklanan düğüm merkeze
            if (nodeId.equals(clickedId)) {
                radius = 0;
                angle = 0;
            }
            
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);

            NodeSprite sprite = new NodeSprite(nodeId, x, y);
            
            // RENK BELİRLEME
            if (nodeId.equals(clickedId)) {
                // Tıklanan düğüm: Parlak Kırmızı
                sprite.setColor(CLICKED_NODE_COLOR);
                sprite.circle.setRadius(22);
            } else if (newlyAdded.contains(nodeId)) {
                // Yeni eklenen: Yeşil
                sprite.setColor(NEW_NODE_COLOR);
                sprite.circle.setRadius(18);
            } else if (hCore.contains(nodeId)) {
                // H-Core: Turuncu
                sprite.setColor(HCORE_COLOR);
                sprite.circle.setRadius(16);
            } else {
                // Diğerleri: Orijinal renk
                int inDegree = reverseAdj.containsKey(nodeId) ? reverseAdj.get(nodeId).size() : 0;
                if (inDegree > 15) sprite.setColor(CENTER_COLOR);
                else if (inDegree > 5) sprite.setColor(MID_COLOR);
                else sprite.setColor(OUTER_COLOR);
            }

            nodeSprites.put(nodeId, sprite);
            idx++;
        }

        // Kenarları çiz (sadece görünür düğümler arasında)
        for (Long sourceId : visibleNodes) {
            NodeSprite source = nodeSprites.get(sourceId);
            Set<Long> targets = adj.get(sourceId);
            if (targets != null) {
                for (Long targetId : targets) {
                    if (visibleNodes.contains(targetId)) {
                        NodeSprite target = nodeSprites.get(targetId);
                        if (source != null && target != null && !sourceId.equals(targetId)) {
                            EdgeSprite edge = new EdgeSprite(source, target, false);
                            edgeSprites.add(edge);
                            graphPane.getChildren().addAll(edge.line, edge.arrowhead);
                        }
                    }
                }
            }
        }
        
        // Sıralama kenarlarını çiz (yeşil)
        List<Long> sortedByIdForSequential = new ArrayList<>(visibleNodes);
        Collections.sort(sortedByIdForSequential);
        for (int i = 0; i < sortedByIdForSequential.size() - 1; i++) {
            Long fromId = sortedByIdForSequential.get(i);
            Long toId = sortedByIdForSequential.get(i + 1);
            NodeSprite source = nodeSprites.get(fromId);
            NodeSprite target = nodeSprites.get(toId);
            if (source != null && target != null) {
                EdgeSprite sequentialEdge = new EdgeSprite(source, target, true);
                edgeSprites.add(sequentialEdge);
                graphPane.getChildren().addAll(sequentialEdge.line, sequentialEdge.arrowhead);
            }
        }

        // Düğümleri en üste ekle
        for (NodeSprite n : nodeSprites.values()) {
            graphPane.getChildren().add(n.stack);
        }

        // Kamerayı merkeze odakla
        Platform.runLater(() -> {
            scrollPane.setHvalue(0.5);
            scrollPane.setVvalue(0.5);
        });
    }
    
    // ==================== GENİŞLETİLMİŞ GRAF İSTATİSTİKLERİ ====================

    /**
     * Genişletilmiş graf modunda istatistikleri günceller.
     * Sadece görünür düğümler için hesaplama yapar.
     */
    private void updateExpandedStats() {
        Map<Long, Set<Long>> adj = graphService.getAdjListForDrawing();
        Map<Long, Set<Long>> reverseAdj = graphService.getReverseAdjListForDrawing();
        
        int nodeCount = visibleNodes.size();
        
        // İstatistik değişkenleri
        int edgeCount = 0;
        int totalGiven = 0;
        int totalReceived = 0;
        Long mostReceivedId = null;
        int mostReceivedCount = 0;
        Long mostGivenId = null;
        int mostGivenCount = 0;
        
        for (Long nodeId : visibleNodes) {
            // Verilen referanslar (out-degree)
            Set<Long> given = adj.get(nodeId);
            if (given != null) {
                int givenInVisible = (int) given.stream().filter(visibleNodes::contains).count();
                edgeCount += givenInVisible;
                totalGiven += givenInVisible;
                if (givenInVisible > mostGivenCount) {
                    mostGivenCount = givenInVisible;
                    mostGivenId = nodeId;
                }
            }
            
            // Alınan referanslar (in-degree)
            Set<Long> received = reverseAdj.get(nodeId);
            if (received != null) {
                int receivedInVisible = (int) received.stream().filter(visibleNodes::contains).count();
                totalReceived += receivedInVisible;
                if (receivedInVisible > mostReceivedCount) {
                    mostReceivedCount = receivedInVisible;
                    mostReceivedId = nodeId;
                }
            }
        }
        
        // İstatistik metnini oluştur
        String statsText = String.format(
            "📊 GENİŞLETİLMİŞ GRAF\n\n" +
            "• Görünen Makale: %d\n" +
            "• Toplam Referans: %d\n" +
            "• Toplam Verilen: %d\n" +
            "• Toplam Alınan: %d\n\n" +
            "• En Çok Atıf Alan:\n  ID: %s (%d)\n\n" +
            "• En Çok Referans Veren:\n  ID: %s (%d)",
            nodeCount, edgeCount, totalGiven, totalReceived,
            mostReceivedId != null ? mostReceivedId : "-", mostReceivedCount,
            mostGivenId != null ? mostGivenId : "-", mostGivenCount
        );
        statsLabel.setText(statsText);
    }

    // ==================== YARDIMCI METODLAR ====================

    /**
     * Bilgi dialog'u gösterir.
     * 
     * @param title Dialog başlığı
     * @param content Dialog içeriği
     */
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
