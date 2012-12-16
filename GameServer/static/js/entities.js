var MarkerEntity = Class.create({
  initialize: function(map, location, hidden) {
    this.map = map;
    this.location = location;
    this.hidden = hidden
    if (hidden) {
      this.marker = this.getMarker(null, this.location);
    } else {
      this.marker = this.getMarker(this.map, this.location);
    }
  },
  
  getMarker: function(map, latLng) {
    alert("please override me.");
  },
  
  remove: function() {
    this.marker.setMap(null)
  },
  
  locationUpdate: function(location) {
    this.location = location;
    this.marker.setPosition(this.location);
  },
});

var Fortification = Class.create({
  initialize: function(map, location) {
	this.map = map;
	this.location = location;
	this.circle = this.getCircle(this.map, this.location);
  },
  
  getCircle: function(map, latLng) {
    return new google.maps.Circle({
        center: latLng,
        fillColor: "#CC1623",
        fillOpacity: 0.2,
        map: map,
        radius: 100,
        strokeColor: "#CC1623",
        strokeOpacity: 1.0,
        strokeWeight: 2,
        zIndex: 0,
      });
  },
  
  remove: function() {
    this.circle.setMap(null)
  },
  
  locationUpdate: function(location) {
    this.location = location;
    this.circle.setCenter(this.location);
  },
});

var Tile = Class.create({
  initialize: function(map, ne, sw) {
	this.map = map;
	this.ne = ne;
	this.sw = sw;
	this.rectangle = this.getRectangle(this.map, ne, sw);
  },
  
  getRectangle: function(map, ne, sw) {
	var bounds = new google.maps.LatLngBounds(sw, ne);
    return new google.maps.Rectangle({
        bounds: bounds,
        fillColor: "#FF0000",
        fillOpacity: 0.1,
        map: map,
        strokeColor: "#FF0000",
        strokeOpacity: 1.0,
        strokeWeight: 2,
        zIndex: 0,
      });
  },
  
  remove: function() {
    this.rectangle.setMap(null)
  },
});

var Player = Class.create(MarkerEntity, {
  getMarker: function(map, latLng) {
    var markerimage = new google.maps.MarkerImage(
        "res/player.png",
        new google.maps.Size(16, 40));
    return new google.maps.Marker({
        position:latLng,
        map:map,
        title:"You",
        icon:markerimage,
        // TODO: shadow image.
      });
  },
});

var Zombie = Class.create(MarkerEntity, {
  initialize: function($super, map, location, isNoticingPlayer) {
    this.meanderingIcon = new google.maps.MarkerImage(
        "res/zombie_meandering.png",
        new google.maps.Size(14, 30))
    this.chasingIcon = new google.maps.MarkerImage(
        "res/zombie_chasing.png",
        new google.maps.Size(14, 30))
    this.isNoticingPlayer = isNoticingPlayer;
    $super(map, location, false);
  },
  
  setIsNoticingPlayer: function(isNoticingPlayer) {
    this.isNoticingPlayer = isNoticingPlayer;
    if (this.isNoticingPlayer) {
      this.marker.setIcon(this.chasingIcon);
    } else {
      this.marker.setIcon(this.meanderingIcon);
    }
  },
  
  getMarker: function(map, latLng) {
    var markerimage =
        this.isNoticingPlayer ? this.chasingIcon : this.meanderingIcon;
    return new google.maps.Marker({
        position:latLng,
        map:map,
        title:"Zombie",
        icon:markerimage,
        // TODO: shadow image.
      });
  },
});

var Destination = Class.create(MarkerEntity, {
  getMarker: function(map, latLng) {
    var markerimage = new google.maps.MarkerImage(
        "res/flag.png",
        new google.maps.Size(62, 35));
    return new google.maps.Marker({
        position:latLng,
        map:map,
        title:"Destination",
        icon:markerimage,
        // TODO: shadow image.
      });
  },
});

var MyLocation = Class.create(MarkerEntity, {
  getMarker: function(map, latLng) {
    var markerimage = new google.maps.MarkerImage(
        "res/YourLocationDot.png",
        new google.maps.Size(16, 16),
        null,
        new google.maps.Point(8, 8));
    return new google.maps.Marker({
        position:latLng,
        map:map,
        title:"Destination",
        icon:markerimage,
        // TODO: shadow image.
      });
  },
});