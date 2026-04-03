package economicSimulationApp; 
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class EconomicSimulationApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("日本経済戦略ダッシュボード - Strategic Simulator");

        // --- サイドバー（コントロールパネル） ---
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(25));
        sidebar.setPrefWidth(320);
        sidebar.setStyle("-fx-background-color: #2c3e50;"); // サイドバー背景

        // パラメーター設定
        Label gLabel = new Label("AI・技術進歩率 (g)");
        Slider gSlider = new Slider(0, 0.05, 0.015);
        setupSlider(gSlider);

        Label taxLabel = new Label("AI・教育への投資配分 (税): 2.0%");
        Slider taxSlider = new Slider(0, 0.10, 0.02);
        setupSlider(taxSlider);
        taxSlider.valueProperty().addListener((o, ov, nv) -> 
            taxLabel.setText(String.format("AI・教育への投資配分 (税): %.1f%%", nv.doubleValue() * 100)));

        Label nLabel = new Label("定年延長効果 (労働力緩和)");
        Slider nSlider = new Slider(0, 0.01, 0.002);
        setupSlider(nSlider);

        Button runButton = new Button("シミュレーション実行");
        runButton.getStyleClass().add("run-button");
        runButton.setMaxWidth(Double.MAX_VALUE);

        Button saveButton = new Button("結果を画像で保存");
        saveButton.getStyleClass().add("save-button");
        saveButton.setMaxWidth(Double.MAX_VALUE);

        Button clearButton = new Button("グラフをリセット");
        clearButton.getStyleClass().add("clear-button");
        clearButton.setMaxWidth(Double.MAX_VALUE);

        sidebar.getChildren().addAll(
            gLabel, gSlider, 
            taxLabel, taxSlider, 
            nLabel, nSlider, 
            new Separator(),
            runButton, saveButton, clearButton
        );

        // --- メインチャートエリア ---
        LineChart<Number, Number> totalGdpChart = createChart("年次", "総GDP (兆円)", "日本全体の経済規模推移");
        LineChart<Number, Number> perCapitaGdpChart = createChart("年次", "一人当たりGDP (万円)", "国民の豊かさの指標");

        VBox chartBox = new VBox(15, totalGdpChart, perCapitaGdpChart);
        chartBox.setPadding(new Insets(15));
        HBox.setHgrow(chartBox, Priority.ALWAYS);

        // --- ロジックの実装 ---
        runButton.setOnAction(e -> calculateGrowth(
            totalGdpChart, perCapitaGdpChart, 
            gSlider.getValue(), taxSlider.getValue(), nSlider.getValue()
        ));

        clearButton.setOnAction(e -> {
            totalGdpChart.getData().clear();
            perCapitaGdpChart.getData().clear();
        });

        saveButton.setOnAction(e -> saveSnapshot(chartBox));

        // レイアウト構成
        HBox root = new HBox(sidebar, chartBox);
        Scene scene = new Scene(root, 1200, 850);
        
        // CSSの適用 (ファイル名: style.css)
        try {
            scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        } catch (Exception ex) {
            System.out.println("CSSファイルが見つかりません。スタイルなしで実行します。");
        }

        stage.setScene(scene);
        stage.show();
    }

    private void setupSlider(Slider s) {
        s.setShowTickLabels(true);
        s.setShowTickMarks(true);
        s.setMajorTickUnit(0.01);
    }

    private LineChart<Number, Number> createChart(String xLabel, String yLabel, String title) {
        NumberAxis xAxis = new NumberAxis(2025, 2075, 5);
        xAxis.setLabel(xLabel);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setCreateSymbols(false);
        
        return chart;
    }

    private void calculateGrowth(LineChart<Number, Number> c1, LineChart<Number, Number> c2, double baseG, double tax, double nMitigation) {
        XYChart.Series<Number, Number> s1 = new XYChart.Series<>();
        XYChart.Series<Number, Number> s2 = new XYChart.Series<>();
        
        String legend = String.format("AI投資:%.1f%% / 労働緩和:%.1f%%", tax*100, nMitigation*100);
        s1.setName(legend);
        s2.setName(legend);

        // 初期値パラメーター (2025年日本想定)
        double k = 2500.0; 
        double l = 67.0;  
        double a = 5.0;   
        double alpha = 0.35;
        double delta = 0.04;
        double baseS = 0.22; // 民間投資率

        for (int t = 2025; t <= 2075; t++) {
            double y = a * Math.pow(k, alpha) * Math.pow(l, 1 - alpha);
            s1.getData().add(new XYChart.Data<>(t, y * 100)); // 兆円スケール
            s2.getData().add(new XYChart.Data<>(t, (y * 100) / l * 100)); // 万円スケール

            // 政策トレードオフ
            double effectiveS = baseS - tax; // 教育/AI投資分、物理資本投資が減る
            double effectiveG = baseG + (Math.sqrt(tax) * 0.08); // 投資による生産性向上
            
            // 人口動態 (自然減 + 政策による緩和)
            double n = -0.008 + nMitigation; 

            k = k + (effectiveS * y) - (delta * k);
            l = l * (1 + n);
            a = a * (1 + effectiveG);
        }
        c1.getData().add(s1);
        c2.getData().add(s2);
    }

    private void saveSnapshot(VBox node) {
        FileChooser fc = new FileChooser();
        fc.setTitle("シミュレーション結果を保存");
        fc.setInitialFileName("economic_report.png");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNGファイル", "*.png"));
        File file = fc.showSaveDialog(null);

        if (file != null) {
            try {
                WritableImage image = node.snapshot(new SnapshotParameters(), null);
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "画像を保存しました。");
                alert.show();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) { launch(args); }
}
