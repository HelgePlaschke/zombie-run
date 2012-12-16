var kRadiusOfEarthMeters = 6378100;

// Takes the position object from the navigator's location services,
// returns a google.maps.LatLng
function latLngFromPosition(position) {
  return new google.maps.LatLng(position.coords.latitude,
                                position.coords.longitude);
}

// Convert degrees to radians.
function toRadians(degrees) {
  return degrees * Math.PI / 180;
}

// Distance, in meters, between points a and b
//
// a and b are both google.maps.LatLng
function distance(a, b) {
  // Haversine formula, from http://mathforum.org/library/drmath/view/51879.html
  dLat = a.lat() - b.lat();
  dLon = a.lng() - b.lng();
  var x = Math.pow(Math.sin(toRadians(dLat / 2)), 2) +
      Math.cos(toRadians(a.lat())) *
        Math.cos(toRadians(b.lat())) *
          Math.pow(Math.sin(toRadians(dLon / 2)), 2);
  var greatCircleDistance = 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1-x));
  return kRadiusOfEarthMeters * greatCircleDistance;
}

// origin and target are google.maps.LatLng, distanceMeters is numeric
function latLngTowardTarget(origin, target, distanceMeters) {
  dLat = target.lat() - origin.lat();
  dLon = target.lng() - origin.lng();
  
  dist = distance(origin, target);
  offsetLat = dLat * distanceMeters / dist;
  offsetLng = dLon * distanceMeters / dist;
  return new google.maps.LatLng(origin.lat() + offsetLat, origin.lng() + offsetLng);
}

// Get a random latitude and longitude near the given point.
function randomLatLngNearLocation(origin, distanceMeters) {
  var randLat = origin.lat() + Math.random() - 0.5;
  var randLng = origin.lng() + Math.random() - 0.5;
  var targetLoc = new google.maps.LatLng(randLat, randLng);
  return latLngTowardTarget(origin, targetLoc, distanceMeters);
}

// The class than handles the display logic of our messages.
var MessageHandler = Class.create({
  initialize: function() {
    this.messages = [];
    this.showing_message = false;
  },
  
  showMessage: function(message) {
    this.messages.push(message);
    this.showHeadOfMessageQueue();
  },

  showHeadOfMessageQueue: function() {
    if (this.messages.length == 0 || this.showing_message) {
      return;
    }
        
    this.showing_message = true;
    $("message").style.width = "100%";
    $("message").style.height = "100%";
    $("message_background").style.display = "block";
    $("message_container").style.display = "block";
    
    var messages_node = $("message_container");
    while (messages_node.hasChildNodes()) {
      messages_node.removeChild(messages_node.firstChild);
    }
    
    message = this.messages.pop();

    var dismiss_callback = function() {
        this.hideMessage();
        this.showHeadOfMessageQueue();
      }.bind(this);

    message.populateMessage(messages_node, dismiss_callback);
  },
  
  hideMessage: function() {
    $("message").style.width = 0;
    $("message").style.height = 0;
    $("message_background").style.display = "none";
    $("message_container").style.display = "none";
    this.showing_message = false;
  },
});

// A LocationProvider, when created, requests periodic location updates continuously and provides
// them to all registered listeners.
//
// A listener should implement:
//   function locationUpdate(location);
// where location is of the type google.maps.LatLng
var LocationProvider = Class.create({
  initialize: function() {
    this.listeners = new Array;

    // TODO: both of these initialization methods should return a boolean value indicating whether
    // or not they were able to successfully start requesting location updates (i.e., if we were
    // given permission to get location updates by the user).  If not, we should handle that error
    // condition with some notifications.
    this.isActive = true;
    if (navigator.geolocation) {
      this.initializeW3CLocationUpdates();
    } else if (window.google && google.gears) {
      this.initializeGoogleGearsLocationUpdates();
    } else {
      this.isActive = false;
    }
  },

  addListener: function(listener) {
    if (this.isActive) {
      this.listeners.push(listener);
      return true;
    } else {
      return false;
    }
  },
  
  removeListener: function(listener) {
    // There's gotta be a better way to do this...
    var subtractedListeners = new Array;
    this.listeners.each(function(l) {
        if (l != listener) {
          subtractedListeners.push(l);
        }
      });
    this.listeners = subtractedListeners;
  },

  // TODO: provide a removeListener method
  
  updateListeners: function(latLng) {
    this.listeners.each(function(listener) {
        listener.locationUpdate(latLng);
      });
  },
  
  //
  // W3C Location Handling
  //
  initializeW3CLocationUpdates: function() {
    // Get our intial position, and initialize the map at that point.
    navigator.geolocation.getCurrentPosition(
        this.w3CLocationChanged.bind(this),
        this.w3CLocationError.bind(this),
        { enableHighAccuracy:true, maximumAge:0, timeout:0 });
  
    // Get updates of the user's location at least every 10 seconds.
    navigator.geolocation.watchPosition(
        this.w3CLocationChanged.bind(this),
        this.w3CLocationError.bind(this),
        { enableHighAccuracy:true, maximumAge: 10 * 1000, timeout:0 });
  },
  
  // position is the object returned to the method from the navigator.geoLocation.getCurrentPosition
  w3CLocationChanged: function(position) {
    var latLng = latLngFromPosition(position);
    this.updateListeners(latLng);
  },
  
  w3CLocationError: function(error) {
    return;
  },

  //
  // Google Gears Location Handling
  //
  initializeGoogleGearsLocationUpdates: function() {
    var geo = google.gears.factory.create('beta.geolocation');
    var options = { enableHighAccuracy: true };
    try {
      geo.getCurrentPosition(this.googleGearsLocationChanged.bind(this),
                             this.googleGearsLocationError.bind(this),
                             options);
      geo.watchPosition(this.googleGearsLocationChanged.bind(this),
                        this.googleGearsLocationError.bind(this),
                        options);
    } catch (e) {
      alert(e);
      return;
    }
  },
  
  googleGearsLocationChanged: function(position) {
    var latLng = new google.maps.LatLng(position.latitude, position.longitude);
    this.updateListeners(latLng);
  },
  
  googleGearsLocationError: function(error) {
    return;
  },
  
  //
  // Debug location handling
  //
  startDebuggingLocation: function(map) {
    google.maps.event.addListener(map,
        "click",
        this.onClickLocationChanged.bind(this));
  },
  
  onClickLocationChanged: function(mouseEvent) {
    this.updateListeners(mouseEvent.latLng);
  },
});