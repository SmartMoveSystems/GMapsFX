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
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import com.lynden.gmapsfx.shapes.Polyline;
import com.lynden.gmapsfx.shapes.PolylineOptions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class GpsTrackFXMLController implements Initializable {

    @FXML
    private Label latitudeLabel;

    @FXML
    private Label longitudeLabel;

    @FXML
    private GoogleMapView googleMapView;

    @FXML
    private CheckBox rawCheckbox;

    @FXML
    private CheckBox rejectedCheckbox;

    @FXML
    private CheckBox acceptedCheckbox;

    private GoogleMap map;

    private DecimalFormat formatter = new DecimalFormat("###.00000");

    private  List<LocationReason> raw = new ArrayList<>();
    private List<LocationReason> accepted = new ArrayList<>();
    private List<LocationReason> rejected = new ArrayList<>();
    private List<MapShape> lines = new ArrayList<>();

    private boolean showRaw = true;
    private boolean showAccepted = true;
    private boolean showRejected = true;
    private PointType type = null;

    private enum PointType {
        RAW,
        ACCEPTED,
        REJECTED
    }

    private class LocationReason {
        private LatLong location;
        private String reason;
        private String time;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        googleMapView.addMapInitializedListener(() -> configureMap());
        rawCheckbox.selectedProperty().addListener((observable, oldValue, newValue) ->
        {
            showRaw = newValue;
            redraw();
        });
        rejectedCheckbox.selectedProperty().addListener((observable, oldValue, newValue) ->
        {
            showRejected = newValue;
            redraw();
        });
        acceptedCheckbox.selectedProperty().addListener((observable, oldValue, newValue) ->
        {
            showAccepted = newValue;
            redraw();
        });
    }

    protected void configureMap() {
        MapOptions mapOptions = new MapOptions();

        mapOptions.center(new LatLong(47.6097, -122.3331))
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

    private void importPoints(Path path) {
        try {
            raw = new ArrayList<>();
            accepted = new ArrayList<>();
            rejected = new ArrayList<>();
            Files.lines(path).forEach(s -> {
                if (s.startsWith("----")) {
                    if (s.contains("UNFILTERED")) {
                        type = PointType.RAW;
                    }
                    else if (s.contains("FILTERED")) {
                        type = PointType.ACCEPTED;
                    }
                    else {
                        type = PointType.REJECTED;
                    }
                }
                else if (type != null) {
                    String[] split = s.split(";");
                    double lat = Double.parseDouble(split[0]);
                    double lon = Double.parseDouble(split[1]);
                    LatLong point = new LatLong(lat, lon);
                    LocationReason locationReason = new LocationReason();
                    locationReason.location = point;
                    locationReason.reason = split[2];
                    locationReason.time = split[3];
                    switch (type) {
                        case RAW:
                            raw.add(locationReason);
                            break;
                        case ACCEPTED:
                            accepted.add(locationReason);
                            break;
                        case REJECTED:
                            rejected.add(locationReason);
                            break;
                    }

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
            if ((raw.isEmpty() && accepted.isEmpty() && rejected.isEmpty()) ||
                    (!showRaw && !showAccepted && !showRejected)) {
                return;
            }
            LatLongBounds bounds = new LatLongBounds();
            if (showRaw) {
                drawRoute(raw, "yellow", bounds);
            }
            if (showAccepted) {
                drawRoute(accepted, "green", bounds);
            }
            if (showRejected) {
                drawRoute(rejected, "red", bounds);
            }
            map.fitBounds(bounds);
        });
    }

    private InfoWindow openInfoWindow = null;

    private LatLongBounds drawRoute(List<LocationReason> points, String color, LatLongBounds bounds) {
        LatLong prevPoint = null;
        for (final LocationReason point : points) {
            MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(point.location)
                    .title(point.location.getLatitude() + ", " + point.location.getLongitude())
//                    .label(point.reason)
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
        return bounds;
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
