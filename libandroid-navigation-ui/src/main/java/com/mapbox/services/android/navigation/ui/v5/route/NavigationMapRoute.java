package com.mapbox.services.android.navigation.ui.v5.route;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;

import com.mapbox.directions.v5.models.DirectionsRoute;
import com.mapbox.directions.v5.models.RouteLeg;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.style.functions.Function;
import com.mapbox.mapboxsdk.style.functions.stops.Stop;
import com.mapbox.mapboxsdk.style.functions.stops.Stops;
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.Constants;
import com.mapbox.services.android.navigation.ui.v5.R;
import com.mapbox.services.android.navigation.ui.v5.utils.MapImageUtils;
import com.mapbox.services.android.navigation.ui.v5.utils.MapUtils;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.commons.geojson.Feature;
import com.mapbox.services.commons.geojson.FeatureCollection;
import com.mapbox.services.commons.geojson.LineString;
import com.mapbox.services.commons.geojson.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.mapbox.mapboxsdk.style.functions.stops.Stop.stop;
import static com.mapbox.mapboxsdk.style.functions.stops.Stops.categorical;
import static com.mapbox.mapboxsdk.style.functions.stops.Stops.exponential;

/**
 * Provide a route using {@link NavigationMapRoute#addRoute(DirectionsRoute)} and a route will be
 * drawn using runtime styling. The route will automatically be placed below all labels independent
 * of specific style. If the map styles changed when a routes drawn on the map, the route will
 * automatically be redrawn onto the new map style. If during a navigation session, the user gets
 * re-routed, the route line will be redrawn to reflect the new geometry. To remove the route from
 * the map, use {@link NavigationMapRoute#removeRoute()}.
 * <p>
 * You are given the option when first constructing an instance of this class to pass in a style
 * resource. This allows for custom colorizing and line scaling of the route. Inside your
 * applications {@code style.xml} file, you extend {@code <style name="NavigationMapRoute">} and
 * change some or all the options currently offered. If no style files provided in the constructor,
 * the default style will be used.
 *
 * @since 0.4.0
 */
public class NavigationMapRoute implements ProgressChangeListener, MapView.OnMapChangedListener {

  private static final String CONGESTION_KEY = "congestion";
  private static final String SOURCE_KEY = "source";

  @StyleRes
  private int styleRes;
  @ColorInt
  private int routeDefaultColor;
  @ColorInt
  private int routeModerateColor;
  @ColorInt
  private int routeSevereColor;
  @ColorInt
  private int alternativeRouteDefaultColor;
  @ColorInt
  private int alternativeRouteModerateColor;
  @ColorInt
  private int alternativeRouteSevereColor;
  @ColorInt
  private int alternativeRouteShieldColor;
  @ColorInt
  private int routeShieldColor;

  private List<DirectionsRoute> directionsRoutes;
  private final MapboxNavigation navigation;
  private final MapboxMap mapboxMap;
  private List<String> layerIds;
  private final MapView mapView;
  private int primaryRouteIndex;
  private float routeScale;
  private float alternativeRouteScale;
  private String belowLayer;
  private boolean visible;

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param mapView   the MapView to apply the route to
   * @param mapboxMap the MapboxMap to apply route with
   * @since 0.4.0
   */
  public NavigationMapRoute(@NonNull MapView mapView, @NonNull MapboxMap mapboxMap) {
    this(null, mapView, mapboxMap, R.style.NavigationMapRoute);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @param belowLayer optionally pass in a layer id to place the route line below
   * @since 0.4.0
   */
  public NavigationMapRoute(@NonNull MapView mapView, @NonNull MapboxMap mapboxMap,
                            @Nullable String belowLayer) {
    this(null, mapView, mapboxMap, R.style.NavigationMapRoute, belowLayer);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param navigation an instance of the {@link MapboxNavigation} object. Passing in null means
   *                   your route won't consider rerouting during a navigation session.
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @since 0.4.0
   */
  public NavigationMapRoute(@Nullable MapboxNavigation navigation, @NonNull MapView mapView,
                            @NonNull MapboxMap mapboxMap) {
    this(navigation, mapView, mapboxMap, R.style.NavigationMapRoute);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param navigation an instance of the {@link MapboxNavigation} object. Passing in null means
   *                   your route won't consider rerouting during a navigation session.
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @param belowLayer optionally pass in a layer id to place the route line below
   * @since 0.4.0
   */
  public NavigationMapRoute(@Nullable MapboxNavigation navigation, @NonNull MapView mapView,
                            @NonNull MapboxMap mapboxMap, @Nullable String belowLayer) {
    this(navigation, mapView, mapboxMap, R.style.NavigationMapRoute, belowLayer);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param navigation an instance of the {@link MapboxNavigation} object. Passing in null means
   *                   your route won't consider rerouting during a navigation session.
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @param styleRes   a style resource with custom route colors, scale, etc.
   */
  public NavigationMapRoute(@Nullable MapboxNavigation navigation, @NonNull MapView mapView,
                            @NonNull MapboxMap mapboxMap, @StyleRes int styleRes) {
    this(navigation, mapView, mapboxMap, styleRes, null);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param navigation an instance of the {@link MapboxNavigation} object. Passing in null means
   *                   your route won't consider rerouting during a navigation session.
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @param styleRes   a style resource with custom route colors, scale, etc.
   * @param belowLayer optionally pass in a layer id to place the route line below
   */
  public NavigationMapRoute(@Nullable MapboxNavigation navigation, @NonNull MapView mapView,
                            @NonNull MapboxMap mapboxMap, @StyleRes int styleRes,
                            @Nullable String belowLayer) {
    this.styleRes = styleRes;
    this.mapView = mapView;
    this.mapboxMap = mapboxMap;
    this.navigation = navigation;
    this.belowLayer = belowLayer;
    addListeners();
    initialize();
  }

  /**
   * Adds source and layers to the map.
   */
  private void initialize() {
    layerIds = new ArrayList<>();
    getAttributes();
  }

  // TODO Prevent list with no directions routes
  public void addRoutes(@NonNull List<DirectionsRoute> directionsRoutes) {
    this.directionsRoutes = directionsRoutes;
    final List<FeatureCollection> featureCollections = new ArrayList<>();
    // Each route contains traffic information and should be recreated considering this traffic
    // information.
    for (int i = 0; i < directionsRoutes.size(); i++) {
      featureCollections.add(addTrafficToSource(directionsRoutes.get(i), i));

      final List<Feature> waypointFeatures = new ArrayList<>();
      for (RouteLeg leg : directionsRoutes.get(i).legs()) {
        waypointFeatures.add(getPointFromLineString(leg, 0));
        waypointFeatures.add(getPointFromLineString(leg, leg.steps().size() - 1));
      }
      featureCollections.add(FeatureCollection.fromFeatures(waypointFeatures));
    }

    // Add all the sources
    for (FeatureCollection collection : featureCollections) {
      if (!collection.getFeatures().isEmpty()) {
        MapUtils.updateMapSourceFromFeatureCollection(
          mapboxMap, collection, collection.getFeatures().get(0).getStringProperty(SOURCE_KEY)
        );
      }
    }

    // TODO adjust width of route lines to match google maps
    // TODO move things below to initialization section
    placeRouteBelow();
    // Alternative routes
    addRouteShieldLayer(alternativeRouteScale, NavigationMapLayers.NAVIGATION_ROUTE_SHIELD_LAYER + "-1", "mapbox-plugin-navigation-route-source-1", 1);
    addRouteLayer(alternativeRouteScale, NavigationMapRoute.NavigationMapLayers.NAVIGATION_ROUTE_LAYER + "-1", "mapbox-plugin-navigation-route-source-1", 1);

    // Primary route
    addRouteShieldLayer(routeScale, NavigationMapLayers.NAVIGATION_ROUTE_SHIELD_LAYER + "-0", "mapbox-plugin-navigation-route-source-0", 0);
    addRouteLayer(routeScale, NavigationMapRoute.NavigationMapLayers.NAVIGATION_ROUTE_LAYER + "-0", "mapbox-plugin-navigation-route-source-0", 0);

    MapUtils.updateMapSourceFromFeatureCollection(mapboxMap, featureCollections.get(featureCollections.size() - 1), "navigation-waypoint");
    drawWaypointMarkers(mapboxMap,
      ContextCompat.getDrawable(mapView.getContext(), R.drawable.ic_route_origin),
      ContextCompat.getDrawable(mapView.getContext(), R.drawable.ic_route_destination)
    );
  }

  /**
   * If the {@link DirectionsRoute} request contains congestion information via annotations, breakup the source into
   * pieces so data-driven styling can be used to change the route colors accordingly.
   */
  private static FeatureCollection addTrafficToSource(DirectionsRoute route, int index) {
    final List<Feature> features = new ArrayList<>();
    LineString originalGeometry = LineString.fromPolyline(route.geometry(), Constants.PRECISION_6);
    Feature feat = Feature.fromGeometry(originalGeometry);
    feat.addStringProperty(SOURCE_KEY, String.format(Locale.US, "%s-%d", NavigationMapSources.NAVIGATION_ROUTE_SOURCE, index));
    features.add(feat);

    LineString lineString = LineString.fromPolyline(route.geometry(), Constants.PRECISION_6);
    for (RouteLeg leg : route.legs()) {
      if (leg.annotation() != null) {
        if (leg.annotation().congestion() != null) {
          for (int i = 0; i < leg.annotation().congestion().size(); i++) {
            // See https://github.com/mapbox/mapbox-navigation-android/issues/353
            if (leg.annotation().congestion().size() + 1 <= lineString.getCoordinates().size()) {
              double[] startCoord = lineString.getCoordinates().get(i).getCoordinates();
              double[] endCoord = lineString.getCoordinates().get(i + 1).getCoordinates();

              LineString congestionLineString = LineString.fromCoordinates(new double[][] {startCoord, endCoord});
              Feature feature = Feature.fromGeometry(congestionLineString);
              feature.addStringProperty(CONGESTION_KEY, leg.annotation().congestion().get(i));
              feature.addStringProperty(SOURCE_KEY, String.format(Locale.US, "%s-%d", NavigationMapSources.NAVIGATION_ROUTE_SOURCE, index));
              features.add(feature);
            }
          }
        }
      } else {
        Feature feature = Feature.fromGeometry(lineString);
        features.add(feature);
      }
    }
    return FeatureCollection.fromFeatures(features);
  }

  /**
   * Add the route layer to the map either using the custom style values or the default.
   */
  private void addRouteLayer(float scale, String layerId, String sourceId, int index) {
    Layer routeLayer = new LineLayer(layerId, sourceId).withProperties(
      PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
      PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
//      PropertyFactory.visibility(Property.NONE),
      PropertyFactory.lineWidth(Function.zoom(
        exponential(
          stop(4f, PropertyFactory.lineWidth(3f * scale)),
          stop(10f, PropertyFactory.lineWidth(4f * scale)),
          stop(13f, PropertyFactory.lineWidth(6f * scale)),
          stop(16f, PropertyFactory.lineWidth(10f * scale)),
          stop(19f, PropertyFactory.lineWidth(14f * scale)),
          stop(22f, PropertyFactory.lineWidth(18f * scale))
        ).withBase(1.5f))
      ),
      PropertyFactory.lineColor(
        Function.property(CONGESTION_KEY, categorical(
          stop("moderate", PropertyFactory.lineColor(
            index == primaryRouteIndex ? routeModerateColor : alternativeRouteModerateColor)),
          stop("heavy", PropertyFactory.lineColor(
            index == primaryRouteIndex ? routeSevereColor : alternativeRouteSevereColor)),
          stop("severe", PropertyFactory.lineColor(
            index == primaryRouteIndex ? routeSevereColor : alternativeRouteSevereColor))
        )).withDefaultValue(PropertyFactory.lineColor(
          index == primaryRouteIndex ? routeDefaultColor : alternativeRouteDefaultColor)))
    );
    addLayerToMap(routeLayer, belowLayer);
  }

  /**
   * Add the route shield layer to the map either using the custom style values or the default.
   */
  private void addRouteShieldLayer(float scale, String layerId, String sourceId, int index) {
    Layer routeLayer = new LineLayer(layerId, sourceId).withProperties(
      PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
      PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
//      PropertyFactory.visibility(Property.NONE),
      PropertyFactory.lineWidth(Function.zoom(
        exponential(
          stop(10f, PropertyFactory.lineWidth(7f)),
          stop(14f, PropertyFactory.lineWidth(10.5f * scale)),
          stop(16.5f, PropertyFactory.lineWidth(15.5f * scale)),
          stop(19f, PropertyFactory.lineWidth(24f * scale)),
          stop(22f, PropertyFactory.lineWidth(29f * scale))
        ).withBase(1.5f))
      ),
      PropertyFactory.lineColor(
        index == primaryRouteIndex ? routeShieldColor : alternativeRouteShieldColor)
    );
    addLayerToMap(routeLayer, belowLayer);
  }


  private void getAttributes() {
    Context context = mapView.getContext();
    TypedArray typedArray
      = context.obtainStyledAttributes(styleRes, R.styleable.NavigationMapRoute);

    // Primary Route attributes
    routeDefaultColor = typedArray.getColor(R.styleable.NavigationMapRoute_routeColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_layer_blue));
    routeModerateColor = typedArray.getColor(R.styleable.NavigationMapRoute_routeModerateCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_layer_congestion_yellow));
    routeSevereColor = typedArray.getColor(R.styleable.NavigationMapRoute_routeSevereCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_layer_congestion_red));
    routeShieldColor = typedArray.getColor(R.styleable.NavigationMapRoute_routeShieldColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_shield_layer_color));
    routeScale = typedArray.getFloat(R.styleable.NavigationMapRoute_routeScale, 1.0f);

    // Secondary Routes attributes
    alternativeRouteDefaultColor = typedArray.getColor(R.styleable.NavigationMapRoute_alternativeRouteColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_color));
    alternativeRouteModerateColor = typedArray.getColor(R.styleable.NavigationMapRoute_alternativeRouteModerateCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_congestion_yellow));
    alternativeRouteSevereColor = typedArray.getColor(R.styleable.NavigationMapRoute_alternativeRouteSevereCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_congestion_red));
    alternativeRouteShieldColor = typedArray.getColor(R.styleable.NavigationMapRoute_alternativeRouteShieldColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_shield_color));
    alternativeRouteScale = typedArray.getFloat(R.styleable.NavigationMapRoute_alternativeRouteScale, 1.0f);

    typedArray.recycle();
  }

  /**
   * Iterate through map style layers backwards till the first not-symbol layer is found.
   */
  private String placeRouteBelow() {
    if (belowLayer == null || belowLayer.isEmpty()) {
      List<Layer> styleLayers = mapboxMap.getLayers();
      for (int i = styleLayers.size() - 1; i >= 0; i--) {
        if (!(styleLayers.get(i) instanceof SymbolLayer)) {
          return styleLayers.get(i).getId();
        }
      }
    }
    return belowLayer;
  }






































  private void drawWaypointMarkers(@NonNull MapboxMap mapboxMap, @NonNull Drawable originMarker, @NonNull Drawable destinationMarker) {
    SymbolLayer waypointLayer = mapboxMap.getLayerAs("navigation-waypoint");
    if (waypointLayer == null) {
      Bitmap bitmap = MapImageUtils.getBitmapFromDrawable(originMarker);
      mapboxMap.addImage("originMarker", bitmap);
      bitmap = MapImageUtils.getBitmapFromDrawable(destinationMarker);
      mapboxMap.addImage("destinationMarker", bitmap);

      waypointLayer = new SymbolLayer("navigation-waypoint", "navigation-waypoint").withProperties(
        PropertyFactory.iconImage(Function.property(
          "waypoint",
          categorical(
            stop("origin", PropertyFactory.iconImage("originMarker")),
            stop("destination", PropertyFactory.iconImage("destinationMarker"))
          )
        )),
        PropertyFactory.iconSize(Function.zoom(
          Stops.exponential(
            Stop.stop(22f, PropertyFactory.iconSize(2.8f)),
            Stop.stop(12f, PropertyFactory.iconSize(1.3f)),
            Stop.stop(10f, PropertyFactory.iconSize(0.8f)),
            Stop.stop(0f, PropertyFactory.iconSize(0.6f))
          ).withBase(1.5f))),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true)
      );
      addLayerToMap(waypointLayer, null);
    }
  }
























  private static Feature getPointFromLineString(RouteLeg leg, int stepIndex) {
    Feature feature = Feature.fromGeometry(Point.fromCoordinates(
      new double[] {
        leg.steps().get(stepIndex).maneuver().location().longitude(),
        leg.steps().get(stepIndex).maneuver().location().latitude()
      }));
    feature.addStringProperty(SOURCE_KEY, "waypoint-source");
    feature.addStringProperty("waypoint",
      stepIndex == 0 ? "origin" : "destination"
    );
    return feature;
  }
























  /**
   * Adds the necessary listeners
   */
  private void addListeners() {
    if (navigation != null) {
      navigation.addProgressChangeListener(this);
    }
    mapView.addOnMapChangedListener(this);
  }

  /**
   * Remove the route line from the map style, note that this only changes the visibility and does not remove any layers
   * or sources.
   *
   * @since 0.4.0
   */
  public void removeRoute() {
    setLayerVisibility(false);
  }

  /**
   * Get the current route being used to draw the route, if one hasn't been added to the map yet, this will return
   * {@code null}
   *
   * @return the {@link DirectionsRoute} used to draw the route line
   * @since 0.4.0
   */
  public DirectionsRoute getRoute() {
//    return route;
    return null;
  }

  /**
   * Called when a map change events occurs. Used specifically to detect loading of a new style, if applicable reapply
   * the route line source and layers.
   *
   * @param change the map change event that occurred
   * @since 0.4.0
   */
  @Override
  public void onMapChanged(int change) {
    if (change == MapView.DID_FINISH_LOADING_STYLE) {
      initialize();
      setLayerVisibility(visible);
    }
  }

  /**
   * Called when the user makes new progress during a navigation session. Used to determine whether or not a re-route
   * has occurred and if so the route is redrawn to reflect the change.
   *
   * @param location      the users current location
   * @param routeProgress a {@link RouteProgress} reflecting the users latest progress along the route
   * @since 0.4.0
   */
  @Override
  public void onProgressChange(Location location, RouteProgress routeProgress) {
    // Check if the route's the same as the route currently drawn
//    if (!routeProgress.directionsRoute().equals(route)) {
//      route = routeProgress.directionsRoute();
//      addSource(route);
//    }
  }

  /**
   * Toggle whether or not the route lines visible or not, used in {@link NavigationMapRoute#addRoute(DirectionsRoute)}
   * and {@link NavigationMapRoute#removeRoute()}.
   *
   * @param visible true if you want the route to be visible, else false
   */
  private void setLayerVisibility(boolean visible) {
    this.visible = visible;
    List<Layer> layers = mapboxMap.getLayers();
    String id;

    for (Layer layer : layers) {
      id = layer.getId();
      if (layerIds.contains(layer.getId()) || id.equals(NavigationMapLayers.NAVIGATION_ROUTE_LAYER)
        || id.equals(NavigationMapLayers.NAVIGATION_ROUTE_SHIELD_LAYER)) {
        layer.setProperties(PropertyFactory.visibility(visible ? Property.VISIBLE : Property.NONE));
      }
    }
  }

  /**
   * Generic method for adding layers to the map.
   */
  private void addLayerToMap(@NonNull Layer layer, @Nullable String idBelowLayer) {
    if (idBelowLayer == null) {
      mapboxMap.addLayer(layer);
    } else {
      mapboxMap.addLayerBelow(layer, idBelowLayer);
    }
    layerIds.add(layer.getId());
  }





  /**
   * Pass in a {@link DirectionsRoute} and display the route geometry on your map.
   *
   * @param route a {@link DirectionsRoute} used to draw the route line
   * @since 0.4.0
   */
  public void addRoute(@NonNull DirectionsRoute route) {
//    this.route = route;
//    addSource(route);
//    setLayerVisibility(true);
  }



  /**
   * Layer id constants.
   */
  public static class NavigationMapLayers {
    public static final String NAVIGATION_ROUTE_SHIELD_LAYER = "mapbox-plugin-navigation-route-shield-layer";
    public static final String NAVIGATION_ROUTE_LAYER = "mapbox-plugin-navigation-route-layer";
    public static final String ALTERNATIVE_ROUTE_SHIELD_LAYER = "navigation-ui-alternative-route-shield-layer";
    public static final String ALTERNATIVE_ROUTE_LAYER = "navigation-ui-alternative-route-layer";
  }

  /**
   * Source id constants.
   */
  public static class NavigationMapSources {
    public static final String NAVIGATION_ROUTE_SOURCE = "mapbox-plugin-navigation-route-source";
    public static final String ALTERNATIVE_ROUTE_SOURCE = "navigation-ui-alternative-route-source";
  }
}
