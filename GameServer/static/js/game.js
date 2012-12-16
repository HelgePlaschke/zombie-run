/**
 * The Game handles drawing, updating, and fetching the game state from the
 * game server.
 */
var Game = Class.create({
  initialize: function(map, message_handler, game_id, debug) {
    this.map = map;
    this.message_handler = message_handler;
    this.location = false;
    this.game_data = new Object;
    this.zombies = new Array;
    this.players = new Array;
    this.players_f = new Array;
    this.updating = false;
    this.game_id = game_id;
    this.is_owner = false;
    this.fortify_signal = false;
    // time of last fortification
    this.last_fortified = 0;
    
    this.failed_requests = 0;
    this.messages_to_show = new Array;
    
    this.tiles = new Array;
    
    this.debug = debug;
  },
  
  // start the game -- initialize location services.
  start: function() {
    this.locationProvider = new LocationProvider();
    if (!this.locationProvider.addListener(this)) {
      return false;
    }
    
    this.update();    
    setInterval(this.update.bind(this), 5000);
    return true;
  },
  
  fortify: function() {
    var now = new Date().getTime();
    for (var i = 0; i < this.players.length; ++i) {
      if (this.players[i].email == this.game_data.player &&
    	  this.players[i].infected) {
    	this.showMessage(new ZombiesCantFortifyMessage(), null);
    	return;
      }
    }
    if ((now - this.last_fortified) < 10 * 60 * 1000) {
      this.showMessage(new TooFrequentFortificationMessage(), null);
    } else {
	  this.showMessage(new FortifyingMessage(), null);
      this.fortify_signal = true;
      this.last_fortified = now;
    }
  },
  
  update: function() {
    if (this.updating) {
      return;
    }
  
    this.updating = true;
    var parameters = { 'gid': this.game_id };
    
    if (this.debug) {
      parameters["d"] = "1";
    }
    
    if (this.location) {
      parameters["lat"] = this.location.lat();
      parameters["lon"] = this.location.lng();
      if (this.fortify_signal) {
        parameters["fortify"] = "1";
        this.fortify_signal = false;
      }
      this.request("/rpc/put", parameters);
    } else {
      this.request("/rpc/get", parameters);
    }
  },
  
  gameUpdate: function(transport) {
    var json = eval('(' + transport.responseText + ')');
    if (json) {
      // Compute messages that should be shown.
      var messages = [];
      for (var i = 0; i < Game.all_messages.length; ++i) {
        message = Game.all_messages[i];
        if (message.shouldShow(this.game_data, json)) {
          messages.push(message);
        }
      }
      
      // If there were any messages to show, then show them.
      if (messages.length > 0) {
        this.showMessages(messages, json);
      }
      
      // Update our game state.
      this.game_data = json;
    }

    // Note that we got a successful request through.
    this.failed_requests = 0;
    
    // This is our attempt at synchronization, because I don't understand those
    // issues in JavaScript.
    this.updating = false;

    this.is_owner = this.game_data.owner == this.game_data.player;

    // Draw the fucker.
    this.draw();
  },
  
  /**
   * Show an array of Messages.
   * 
   * @param messages: an array of AbstractMessage objects.
   * @param ngs: The new game state, to be compared with
   *     this.game_data.  Will be used to calculate the message representation.
   */
  showMessages: function(messages, ngs) {
    for (var i = 0; i < messages.length; ++i) {
      this.showMessage(messages[i], ngs);
    }
  },
  
  /**
   * Show a single Message.
   * 
   * @param message: a single AbstractMessage object.
   * @param ngs: The new game state, to be compared with
   *     this.game_data.  Will be used to calculate the message representation.
   */
  showMessage: function(message, ngs) {
    message.computeMessage(this.game_data, ngs);
    this.message_handler.showMessage(message);
  },
  
  failedGameUpdate: function() {
    console.log("Failed request.");
    this.failed_requests += 1;
    
    if (this.failed_requests > 10) {
      showMessage(new TooManyFailedRequestsMessage(), null);
    }
      
    this.updating = false;
  },
  
  request: function(url, parameters) {
    // Requests should try to include the bounds of the current viewport, so
    // that the server can include a restricted set of the data in its
    // response, to minimize network traffic.
    var bounds = null;
    if (this.map) {
      bounds = this.map.getBounds();
    }
    if (bounds) {
      parameters["swLat"] = bounds.getSouthWest().lat();
      parameters["swLon"] = bounds.getSouthWest().lng();
      parameters["neLat"] = bounds.getNorthEast().lat();
      parameters["neLon"] = bounds.getNorthEast().lng();
    }
     
    new Ajax.Request(url,
      {
        method: 'get',
        parameters: parameters,
        onSuccess: this.gameUpdate.bind(this),
        onFailure: this.failedGameUpdate.bind(this),
      });
  },
  
  draw: function() {
    // Update the location of all the Zombie icons
    if (!this.game_data.zombies) {
      this.game_data.zombies = [];
    } 
    while (this.zombies.length > this.game_data.zombies.length) {
      this.zombies.pop().remove();
    }
    for (i = 0; i < this.game_data.zombies.length; ++i) {
      zombie = this.game_data.zombies[i];
      latLng = new google.maps.LatLng(zombie.lat, zombie.lon);
      isNoticingPlayer = zombie.chasing != null;
      if (i < this.zombies.length) {
        this.zombies[i].locationUpdate(latLng);
        this.zombies[i].setIsNoticingPlayer(isNoticingPlayer);
      } else {
        this.zombies[this.zombies.length] =
            new Zombie(this.map, latLng, isNoticingPlayer);
      }
    }
    
    // Update the location of all the player icons
    if (!this.game_data.players) {
      this.game_data.players = [];
    }
    while (this.players &&
           this.players.length > 0 &&
           this.players.length > this.game_data.players.length - 1) {
      // game_data.players.length - 1 because we don't want to draw the current
      // user.
      this.players.pop().remove();
    }
    var fortifications = [];
    for (i = 0; i < this.game_data.players.length; ++i) {
      player = this.game_data.players[i];
      latLng = new google.maps.LatLng(player.lat, player.lon);
      if (player.fortification) {
        fortifications[fortifications.length] = player.fortification;
      }

      if (i < this.players.length) {
        this.players[i].locationUpdate(latLng);
      } else {
    	hide = false;
        if (player.email == this.game_data.player) {
          // Don't draw the current user.  We show the current user's location
          // with a blue dot.
          hide = true;
        }

        this.players[this.players.length] = 
        	new Player(this.map, latLng, hide);
      }
    }

    while (this.players_f.length > fortifications.length) {
      this.players_f.pop().remove();
    }
    for (var i = 0; i < fortifications.length; ++i) {
      fortification = fortifications[i];
      var fLatLng = new google.maps.LatLng(fortification.lat, fortification.lon);
      if (i < this.players_f.length) {
        this.players_f[i].locationUpdate(fLatLng);
      } else {
        this.players_f[this.players_f.length] =
            new Fortification(this.map, fLatLng);
      }
    }

    if (this.game_data.destination) {
      var destination_latlng = new google.maps.LatLng(
          this.game_data.destination.lat,
          this.game_data.destination.lon);
      if (this.destination) {
        this.destination.locationUpdate(destination_latlng);
        this.destination_f.locationUpdate(destination_latlng);
      } else {
        this.destination = new Destination(this.map, destination_latlng, false);
        this.destination_f = new Fortification(this.map, destination_latlng);
      }
    }
    
    while (this.tiles.length > 0) {
      this.tiles.pop().remove();
    }
    if (this.game_data.debug && this.game_data.debug.tiles) {
      for (var i = 0; i < this.game_data.debug.tiles.length; ++i) {
        tile = this.game_data.debug.tiles[i];
        var ne = new google.maps.LatLng(tile.ne.lat, tile.ne.lon);
        var sw = new google.maps.LatLng(tile.sw.lat, tile.sw.lon);
        this.tiles[this.tiles.length] = new Tile(this.map, ne, sw);
      }
    }
  },
  
  locationUpdate: function(location) {
    if (!this.location) {
      this.location = location;
      this.moveToCurrentLocation();
    } else {
      this.location = location;
    }

    if (!this.locationMarker) {
      this.locationMarker = new MyLocation(this.map, this.location);
    } else {
      this.locationMarker.locationUpdate(this.location);
    }

    // Put location to the server.
    this.update();
    
    if (this.game_data &&
        !this.game_data.destination &&
        this.is_owner &&
        !this.destinationPickClickListener) {
      this.destinationPickClickListener =
          google.maps.event.addListener(this.map,
              "click",
              this.locationSelectedByClick.bind(this));
      this.showMessage(new ChooseDestinationMessage(this), null);
    }
  },
  
  moveToCurrentLocation: function() {
    this.map.setCenter(this.location);
    this.map.setZoom(15);
  },
  
  locationSelectedByClick: function(mouseEvent) {
    this.locationSelected(mouseEvent.latLng);
  },
  
  locationSelected: function(latLng) {
    if (this.destination) {
      this.destination.remove();
      this.destination_f.remove();
    }
    this.destination = new Destination(this.map, latLng, false);
    this.destination_f = new Fortification(this.map, latLng);
    
    google.maps.event.removeListener(this.destinationPickClickListener);
     
    // TODO: on request failure, re-issue this RPC.
    this.request("/rpc/start",
        { 
          "gid": this.game_id,
          "lat": latLng.lat(),
          "lon": latLng.lng(),
        });
    this.showMessage(new DestinationChosenMessage(), null);
  },
  
  addFriend: function() {
    var email = prompt("What is your friend's email?");
    if (email == null) {
      return;
    }
    new Ajax.Request("/rpc/addFriend",
      {
        method: 'get',
        parameters: { "gid": this.game_id,
                      "email": email },
        onSuccess: this.addFriendSuccess.bind(this),
        onFailure: this.addFriendFailed.bind(this),
      });
  },
  
  addFriendSuccess: function(transport) {
    this.showMessage(new SuccessfullyInvitedFriendMessage(), null);
  },
  
  addFriendFailed: function() {
    this.showMessage(new FailedToInviteFriendMessage(), null);
  },
  
  startDebuggingLocation: function() {
    this.locationProvider.startDebuggingLocation(this.map);
  },
});

Object.extend(Game, {
  all_messages: [],
});


/*
 * Game Messages
 */
var AbstractMessage = Class.create({
  /**
   * Determine whether or not this Message should be shown for this transition
   * between game states.
   * 
   * Return: true or false.
   */
  shouldShow: function(ogs, ngs) {
    return false;
  },
  
  /**
   * Compute the string that this message should display.  Will only be called
   * if shouldShow(ogs, ngs) returns True.
   */
  computeMessage: function(ogs, ngs) { },
  
  /**
   * Populate the provided DOM node with whatever contents the message should
   * contain.
   * 
   * @param dom_node The node in the DOM that the message should be appended
   *     into.
   * @param dismiss_callback The callback that should be run to dismiss the
   *     message.
   */
  populateMessage: function(dom_node, dismiss_callback) {
    // This is up to you, my friend.
  },
});

var MenuMessage = Class.create(AbstractMessage, {
  populateMessage: function(dom_node, dismiss_callback) {
    this.appendLink(dom_node, "/new", "New Game", dismiss_callback);
    this.appendLink(dom_node,
    		"/join?gid=1", 
    		"Join Global Game", 
    		dismiss_callback);
    this.appendLink(dom_node, "#", "dismiss", dismiss_callback);
  },
  
  appendLink: function(dom_node, href, text, callback) {
    var p = document.createElement("p");
    dom_node.appendChild(p);
    var new_a = document.createElement("a");
    p.appendChild(new_a);
    new_a.setAttribute("href", href);
    new_a.onclick = callback;
    new_a.appendChild(document.createTextNode(text));
  },
});

var ChooseDestinationMessage = Class.create(AbstractMessage, {
  initialize: function(game) {
    this.game = game;
  },
  
  populateMessage: function(dom_node, dismiss_callback) {
	this.dom_node = dom_node;
    this.dismiss_callback = dismiss_callback;
    
    var p = document.createElement("p");
    dom_node.appendChild(p);
    p.appendChild(document.createTextNode("What is your destination?"));
    
    var form = document.createElement("form");
    dom_node.appendChild(form);
    form.setAttribute("action", "#");
    form.onsubmit = this.processForm.bind(this);
    
    var input = document.createElement("input");
    form.appendChild(input);
    input.setAttribute("type", "text");
    input.setAttribute("id", "destination_input");
    this.destination_input = input;
    
    form.appendChild(document.createElement("br"));
    
    var submit = document.createElement("input");
    this.submit_node = submit;
    form.appendChild(submit);
    submit.setAttribute("type", "submit");
    submit.setAttribute("value", "Find destination.");
    
    // The following code is just a slightly complicated way of saying:
    // <p>Or, just <a href="#" onclick=...>tap on the map</a>.</p>
    //
    // But it doesn't work very well right now, so let's just go with the
    // dialog box.
    //
    // var click_p = document.createElement("p");
    // dom_node.appendChild(click_p);
    // click_p.appendChild(document.createTextNode("Or, just "));
    
    // var click_a = document.createElement("a");
    // click_p.appendChild(click_a);
    // click_a.setAttribute("href", "#");
    // click_a.onclick = this.dismiss_callback;
    // click_a.appendChild(document.createTextNode("tap on the map"));
    
    // click_p.appendChild(document.createTextNode("."));
  },
  
  processForm: function() {
	this.submit_node.setAttribute("active", "false");
    request = {
                "address": this.destination_input.value,
                "bounds": this.game.map.getBounds(),
              };
    new google.maps.Geocoder().geocode(request,
            this.doneProcessingForm.bind(this));
    return false;
  },
  
  doneProcessingForm: function(geocoder_responses, geocoder_status) {
    if (geocoder_status == google.maps.GeocoderStatus.OK &&
      geocoder_responses.length > 0) {
      var latLng = geocoder_responses[0].geometry.location;
      this.game.locationSelected(latLng);
      this.dismiss_callback();
    } else {
      // Clear out the contents of the dom node.
      while (this.dom_node.hasChildNodes()) {
        this.dom_node.removeChild(this.dom_node.firstChild);
      }
      
      // Put an error message at the top.
      var error = document.createElement("p");
      this.dom_node.appendChild(error);
      error.appendChild(
          document.createTextNode("Oh No!  Something went wrong, try again?"));
      this.dom_node.appendChild(document.createElement("br"));
      
      // Repopulate our original message.
      this.populateMessage(this.dom_node, this.dismiss_callback);
    }
  },
});
// The above message is not actually dependent on the game state, so we don't
// register it in the list of the Game's messages.

/**
 * An AbstractMessage implementation that asks for a simple string, and
 * populates the message dom node with a paragraph containing that message (and
 * a dismiss link).
 */
var SimpleParagraphMessage = Class.create(AbstractMessage, {
  initialize: function() {
    this.message = "";
  },
  
  /**
   * Implementations should override this method.
   */
  getSimpleMessage: function(ogs, ngs) {
    return "";
  },
  
  computeMessage: function(ogs, ngs) {
    this.message = this.getSimpleMessage(ogs, ngs);
  },
  
  populateMessage: function(dom_node, dismiss_callback) {
    var p = document.createElement("p");
    p.appendChild(document.createTextNode(this.message));
    dom_node.appendChild(p);
      
    var a = document.createElement("a");
    a.appendChild(document.createTextNode("dismiss"));
    a.setAttribute("href", "#");
    a.onclick = dismiss_callback;
    dom_node.appendChild(a);
  },
});

var WaitingForFirstFixMessage = Class.create(SimpleParagraphMessage, {
  getSimpleMessage: function(ogs, ngs) {
    return "Thank you for using the Zombie, Run! Zombie Finder!  We'll show " +
    	"you your current location as soon as we get an accurate gps fix.  " +
    	"Please be patient, this can take a few minutes, and works best if " +
    	"you're outside with a clear view of the sky.";
  },
});
// The above message is not actually dependent on the game state, so we don't
// register it in the list of the Game's messages.

var DestinationChosenMessage = Class.create(SimpleParagraphMessage, {
  getSimpleMessage: function(ogs, ngs) {
    return "Now, put your shoes on, get outside, and get to your " +
        "destination before the zombies eat your brains!";
  },
});
// The above message is not actually dependent on the game state, so we don't
// register it in the list of the Game's messages.

var TooManyFailedRequestsMessage = Class.create(SimpleParagraphMessage, {
  getSimpleMessage: function(ogs, ngs) {
    return "There's been a problem connecting to central intelligence.  " +
        "Reinitializing systems.";
  },
});
// The above message is not actually dependent on the game state, so we don't
// register it in the list of the Game's messages.

var SuccessfullyInvitedFriendMessage = Class.create(SimpleParagraphMessage, {
  getSimpleMessage: function(ogs, ngs) {
    return "We have successfully invited your friend.  Tell them to check " +
        "their email soon.";
  },
});
//The above message is not actually dependent on the game state, so we don't
//register it in the list of the Game's messages.

var FailedToInviteFriendMessage = Class.create(SimpleParagraphMessage, {
  getSimpleMessage: function(ogs, ngs) {
    return "There was a problem inviting your friend.  Please try again soon.";
  },
});
//The above message is not actually dependent on the game state, so we don't
//register it in the list of the Game's messages.

var FortifyingMessage = Class.create(SimpleParagraphMessage, {
  getSimpleMessage: function(ogs, ngs) {
    return "Fortifying.  You can fortify again after 10 minutes.";
  },
});
//The above message is not actually dependent on the game state, so we don't
//register it in the list of the Game's messages.

var TooFrequentFortificationMessage = Class.create(SimpleParagraphMessage, {
  getSimpleMessage: function(ogs, ngs) {
    return "You can only fortify once every ten minutes.";
  },
});
//The above message is not actually dependent on the game state, so we don't
//register it in the list of the Game's messages.


var ZombiesCantFortifyMessage = Class.create(SimpleParagraphMessage, {
  getSimpleMessage: function(ogs, ngs) {
    return "Infected players can't fortify!  You're a zombie now, whose team " +
    	"do you think you're on??";
  },
});
//The above message is not actually dependent on the game state, so we don't
//register it in the list of the Game's messages.

var HumanInfectedMessage = Class.create(SimpleParagraphMessage, {
  shouldShow: function($super, ogs, ngs) {
	this.getNewlyInfectedPlayers(ogs, ngs).length > 0;
  },
  
  getNewlyInfectedPlayers: function(ogs, ngs) {
    if (!ogs.players || !ngs.players) {
      return false;
    }
    
    var player_states = [];
    for (var i = 0; i < ogs.players.length; ++i) {
      player = ogs.players[i];
      player_states[player.email] = 0;
      if (player.infected) {
        player_states[player.email] = 1;
      }
    }
    
    var newly_infected = [];
    for (var i = 0; i < ngs.players.length; ++i) {
      player = ngs.players[i];
      if (player_states[player] &&
		  player_states[player] == 0 &&
          player.infected) {
        newly_infected[newly_infected.length] = player.email;
      }
    }
    
    return newly_infected;
  },
  
  getSimpleMessage: function(ogs, ngs) {
    var newly_infected_players = this.getNewlyInfectedPlayers();
    var base_message = "";
    if (newly_infected_players.length == 1) {
      if (newly_infected_players[0] == ngs.player) {
        base_message = "You were just infected by a zombie!";
      } else {
        base_message = newly_infected_players[0] + 
        	" was just infected by a zombie!";
      }
    } else {
      base_message = newly_infected_players.join(", ") +
          " were just infected by zombies!";
    }
    base_message += "<br />";
    base_message += "(players receive an antidote after an hour)";
  }
});
Game.all_messages.push(new HumanInfectedMessage());

var PlayerJoinedGameMessage = Class.create(SimpleParagraphMessage, {
  shouldShow: function($super, ogs, ngs) {
    return ogs.started && this.getOtherNewPlayerNames(ogs, ngs).length > 0;
  },
  
  getSimpleMessage: function(ogs, ngs) {
    var new_players = this.getOtherNewPlayerNames(ogs, ngs);
    if (new_players.length == 1) {
      return new_players[0] + " just joined the game.";
    } else {
      return new_players.join(" and ") + " have just joined the game.";
    }
  },
  
  /**
   * Get the names of the players that have joined the game since the last game
   * update, excluding the current player.
   */
  getOtherNewPlayerNames: function(ogs, ngs) {
    var new_players = [];
    var i = 0;
    if (ogs.players) {
      i = ogs.players.length;
    }
    for (; i < ngs.players.length; ++i) {
      if (ngs.players[i].email != ngs.player) {
        new_players.push(ngs.players[i].email);
      }
    }
    return new_players;
  },
});
Game.all_messages.push(new PlayerJoinedGameMessage());

var PlayerReachesDestinationMessage = Class.create(SimpleParagraphMessage, {
  shouldShow: function($super, ogs, ngs) {
    return this.getNewlyFinishedPlayers(ogs, ngs).length > 0;
  },
  getSimpleMessage: function(ogs, ngs) {
    newlyFinished = this.getNewlyFinishedPlayers(ogs, ngs);
    if (newlyFinished.length > 1) {
      last = newlyFinished[newlyFinished.length - 1];
      newlyFinished[newlyFinished.length - 1] = "and " + last;
      return newlyFinished.join(", ") + " have reached the destination!";
    } else if (newlyFinished[0] == ngs.player) {
      return "You have reached the destination!";
    } else {
      return newlyFinished[0] + " has reached the destination!";
    }
  },
  getNewlyFinishedPlayers: function(ogs, ngs) {
    var ostate = [];
    if (ogs != null && ogs.players != null) {
      for (var i = 0; i < ogs.players.length; i++) {
        player = ogs.players[i];
        ostate[player.email] = player.reached_destination;
      }
    }
    
    var newlyFinished = [];
    if (ngs != null && ngs.players != null) {
      for (var i = 0; i < ngs.players.length; i++) {
        player = ngs.players[i];
        if (player.reached_destination && !ostate[player.email]) {
          newlyFinished[newlyFinished.length] = player.email;
        }
      }
    }
    return newlyFinished;
  },
});
Game.all_messages.push(new PlayerReachesDestinationMessage());


/**
 * Base Message for all messages that relate to the end of the game.
 */
var AbstractGameOverMessage = Class.create(SimpleParagraphMessage, {
  shouldShow: function($super, ogs, ngs) {
    return ogs.started &&
           !ogs.done &&
           ngs.done;
  }
});

var AllHumansSurviveMessage = Class.create(AbstractGameOverMessage, {
  shouldShow: function($super, ogs, ngs) {
    if (!$super(ogs, ngs)) {
      return false;
    }
    for (var i = 0; i < ngs.players.length; i++) {
      if (ngs.players[i].infected) {
        // At least one player was infected.
        return false;
      }
    }
    // No players were infected!
    return true;
  },
  getSimpleMessage: function(ogs, ngs) {
    return "All uninfected humans have reached the destination!  Humanity is " +
        "safe!";
  }
});
Game.all_messages.push(new AllHumansSurviveMessage());

var AllHumansInfectedMessage = Class.create(AbstractGameOverMessage, {
  shouldShow: function($super, ogs, ngs) {
    if (!$super(ogs, ngs)) {
      return false;
    }
    for (var i = 0; i < ngs.players.length; i++) {
      if (!ngs.players[i].infected) {
        // At least one player was not infected.
        return false;
      }
    }
    // All players were infected!
    return true;
  },
  
  getSimpleMessage: function(ogs, ngs) {
    return "All humans infected!  Humanity is lost!";
  }
});
Game.all_messages.push(new AllHumansInfectedMessage());