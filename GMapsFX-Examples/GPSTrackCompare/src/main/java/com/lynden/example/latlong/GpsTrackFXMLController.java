package com.lynden.example.latlong;

import com.lynden.gmapsfx.GoogleMapView;
import com.lynden.gmapsfx.javascript.event.GMapMouseEvent;
import com.lynden.gmapsfx.javascript.event.UIEventType;
import com.lynden.gmapsfx.javascript.object.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;

import com.lynden.gmapsfx.shapes.Polyline;
import com.lynden.gmapsfx.shapes.PolylineOptions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Simple tool for comparing up to 4 GPS tracks from a text file with a particular format:
 *
 * File format:
 * ----{TRACK NAME 1}----
 * {lat, long, note, timeString}
 * {lat, long, note, timeString}
 * ...
 * ----{TRACK NAME 2}----
 * {lat, long, note, timeString}
 * {lat, long, note, timeString}
 * ...
 *
 *
 */
public class GpsTrackFXMLController implements Initializable {

    @FXML
    private Label latitudeLabel;

    @FXML
    private Label longitudeLabel;

    @FXML
    private GoogleMapView googleMapView;

    @FXML
    private CheckBox set1Checkbox;

    @FXML
    private CheckBox set2Checkbox;

    @FXML
    private CheckBox set3Checkbox;

    @FXML
    private CheckBox set4Checkbox;

    private List<CheckBox> checkBoxes = new ArrayList<>();

    private GoogleMap map;

    private DecimalFormat formatter = new DecimalFormat("###.00000");

    private List<MapShape> lines = new ArrayList<>();

    private Map<String, List<LocationReason>> pointSets;

    private Map<String, Boolean> toggles = new HashMap<>();

    private List<String> colours = new ArrayList<>();

    private class LocationReason {
        private LatLong location;
        private String reason;
        private String time;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        googleMapView.addMapInitializedListener(() -> configureMap());
        checkBoxes.add(set1Checkbox);
        checkBoxes.add(set2Checkbox);
        checkBoxes.add(set3Checkbox);
        checkBoxes.add(set4Checkbox);
        checkBoxes.forEach(checkBox -> {
            checkBox.setVisible(false);
            checkBox.selectedProperty().addListener((observable, oldValue, newValue) ->
            {
                toggles.put(checkBox.getText(), newValue);
                redraw();
            });
        });
        colours.add("yellow");
        colours.add("green");
        colours.add("red");
        colours.add("blue");
    }

    protected void configureMap() {
        MapOptions mapOptions = new MapOptions();

        mapOptions.center(new LatLong(-34.927572, 138.597218))
                .mapType(MapTypeIdEnum.ROADMAP)
                .zoom(9);
        map = googleMapView.createMap(mapOptions, false);

        map.addMouseEventHandler(UIEventType.click, (GMapMouseEvent event) -> {
            LatLong latLong = event.getLatLong();
            latitudeLabel.setText(formatter.format(latLong.getLatitude()));
            longitudeLabel.setText(formatter.format(latLong.getLongitude()));
        });
    }

    private Stage getStage()
    {
        return (Stage) googleMapView.getScene().getWindow();
    }


    @FXML
    public void showSourceChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select source directory");
        File file = fileChooser.showOpenDialog(getStage());
        if (file != null && file.exists() && !file.isDirectory()) {
            importPoints(file.toPath());
        }
    }

    private String setName = null;

    private void importPoints(Path path) {
        try {
            setName = null;
            pointSets = new HashMap<>();
            Files.lines(path).forEach(s -> {
                Iterator<CheckBox> checkBoxIterator = checkBoxes.iterator();
                if (s.startsWith("----")) {
                    setName = s.replaceAll("----","").trim();
                    pointSets.put(setName, new ArrayList<>());
                    toggles.put(setName, true);
                    if (checkBoxIterator.hasNext()) {
                        checkBoxIterator.next().setSelected(true);
                    }
                }
                else if (setName != null) {
                    String[] split = s.split(";");
                    double lat = Double.parseDouble(split[0]);
                    double lon = Double.parseDouble(split[1]);
                    LatLong point = new LatLong(lat, lon);
                    LocationReason locationReason = new LocationReason();
                    locationReason.location = point;
                    locationReason.reason = split[2];
                    locationReason.time = split[3];
                    pointSets.get(setName).add(locationReason);
                }
            });
            redraw();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void redraw() {
        Platform.runLater(() -> {
            if (map == null ) {
                return;
            }
            map.clearMarkers();
            lines.forEach(line -> {
                map.removeMapShape(line);
            });

            if ((pointSets.isEmpty())) {
                map.setCenter(new LatLong(-34.927572, 138.597218));
                map.setZoom(9);
                return;
            }

            LatLongBounds bounds = new LatLongBounds();
            Iterator<CheckBox> checkBoxIterator = checkBoxes.iterator();
            Iterator<String> colourIterator = colours.iterator();
            for (String name : pointSets.keySet()) {
                if (checkBoxIterator.hasNext()) {
                    CheckBox checkBox =  checkBoxIterator.next();
                    checkBox.setVisible(true);
                    checkBox.setText(name);
                }
                boolean draw = toggles.get(name);
                String colour = colourIterator.next();
                if (draw) {
                    drawRoute(pointSets.get(name), colour, bounds);
                }
            }

            while (checkBoxIterator.hasNext()) {
                checkBoxIterator.next().setVisible(false);
            }

            map.fitBounds(bounds);
        });
    }

    private InfoWindow openInfoWindow = null;

    private void drawRoute(List<LocationReason> points, String color, LatLongBounds bounds) {
        LatLong prevPoint = null;
        for (final LocationReason point : points) {
            MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(point.location)
                    .title(point.location.getLatitude() + ", " + point.location.getLongitude())
                    .visible(true);

            final Marker marker = new Marker(markerOptions);

            map.addMarker(marker);

            map.addUIEventHandler(marker, UIEventType.click, jsObject -> {
                if (openInfoWindow != null) {
                    openInfoWindow.close();
                }
                openInfoWindow = new InfoWindow();
                openInfoWindow.setContent(point.time + ": " + point.reason);
                openInfoWindow.open(map, marker);
            });
            drawLine(prevPoint, point.location, color);
            prevPoint = point.location;
            bounds.extend(point.location);
        }
    }

    private void drawLine(LatLong prevPoint, LatLong point, String color) {
        if (prevPoint != null && point != null) {
            LatLong[] path = new LatLong[]{prevPoint, point};
            MVCArray array = new MVCArray(path);
            PolylineOptions polylineOptions = new PolylineOptions()
                    .path(array)
                    .strokeColor(color)
                    .strokeWeight(2);
            Polyline line = new Polyline(polylineOptions);
            lines.add(line);
            map.addMapShape(line);
        }
    }
}
