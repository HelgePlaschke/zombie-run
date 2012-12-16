import datetime
import logging
import math
import random
import time
import uuid

from django.utils import simplejson as json
from google.appengine.api import memcache
from google.appengine.api import users
from google.appengine.ext import db

RADIUS_OF_EARTH_METERS = 6378100
TRIGGER_DISTANCE_METERS = 15
ZOMBIE_VISION_DISTANCE_METERS = 200
PLAYER_VISION_DISTANCE_METERS = 500
MAX_TIME_INTERVAL_SECS = 60 * 10  # 10 minutes

ZOMBIE_SPEED_VARIANCE = 0.2
MIN_NUM_ZOMBIES = 20
MIN_ZOMBIE_DISTANCE_FROM_PLAYER = 50
MAX_ZOMBIE_CLUSTER_SIZE = 4
MAX_ZOMBIE_CLUSTER_RADIUS = 30

DEFAULT_ZOMBIE_SPEED = 3 * 0.447  # x miles per hour in meters per second
DEFAULT_ZOMBIE_DENSITY = 20.0  # zombies per square kilometer

INFECTED_PLAYER_TRANSITION_SECONDS = 120

ZOMBIE_PLAYER_HEAL_SECONDS = 1 * 60 * 60

DEFAULT_FORTIFICATION_RADIUS_METERS = 100

# The size of a GameTile.  A GameTile will span an area that is defined by these
# degree constants.  Note that pushing a changing to this parameter will
# invalidate all previously recorded games with undefined consequences.
#
# 360 / GAME_TILE_LON_SPAN and 180 / GAME_TILE_LAT_SPAN must be integer
# values.
GAME_TILE_LAT_SPAN = 0.005
assert (180 / GAME_TILE_LAT_SPAN) % 1 == 0
GAME_TILE_LON_SPAN = 0.02
assert (360 / GAME_TILE_LON_SPAN) % 1 == 0

# The id of the tile that stores 'unlocated' entities.
UNLOCATED_TILE_ID = -1

class Error(Exception):
  """Base error class for all model errors."""

class ModelStateError(Error):
  """A model was in an invalid state."""

class InvalidLocationError(Error):
  """A latitude or longitude was invalid."""


def DistanceBetween(aLat, aLon, bLat, bLon):
    dlat = aLat - bLat
    dlon = aLon - bLon
    a = math.sin(math.radians(dlat/2)) ** 2 + \
        math.cos(math.radians(aLat)) * \
        math.cos(math.radians(bLat)) * \
        math.sin(math.radians(dlon / 2)) ** 2
    greatCircleDistance = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
    distance = RADIUS_OF_EARTH_METERS * greatCircleDistance
    return distance


class Entity():
  """An Entity is the base class of every entity in the game.
  
  Entities have a location and a last location update timestamp.
  """
  def __init__(self, encoded=None):
    self.location = (None, None)
    if encoded:
      self.FromString(encoded)
      
  def DictForJson(self):
    return {"lat": self.Lat(), "lon": self.Lon()}
  
  def ToString(self):
    encoded = json.dumps(self.DictForJson())
    return encoded
  
  def FromString(self, encoded):
    obj = json.loads(encoded)
    if obj["lat"] and obj["lon"]:
      self.SetLocation(obj["lat"], obj["lon"])
    return obj
  
  def Invalidate(self, timedelta):
    """Called to invalidate the current state, after some amount of time has
    passed.
    
    Args:
      timedelta: The amount of time that has passed since Invalidate was last
          called.  A datetime.timedelta object.
    """
  
  def Lat(self):
    return self.location[0]
  
  def Lon(self):
    return self.location[1]

  def SetLocation(self, lat, lon):
    if lat is None or lon is None:
      raise InvalidLocationError("Lat and Lon must not be None.")
    if lat > 90 or lat < -90:
      raise InvalidLocationError("Invalid latitude: %s" % lat)
    if lon > 180 or lon < -180:
      raise InvalidLocationError("Invalid longitude: %s" % lon)
    
    self.location = (lat, lon)
  
  def DistanceFrom(self, other):
    """Compute the distance to another Entity."""
    return self.DistanceFromLatLon(other.Lat(), other.Lon())
  
  def DistanceFromLatLon(self, lat, lon):
    return DistanceBetween(self.Lat(), self.Lon(), lat, lon)


class Trigger(Entity):
  """A trigger is an element that can trigger some game action, when reached.
  
  For example: a destination is an entity in the game that triggers the 'win
    game' state.  A Zombie is an entity in the game that triggers the 'lose
    game' state.
  
  Triggers should implement the Process interface method, which gives it
  a hook to modify the game state at each elapsed interval.
  """
  
  def Trigger(self, player):
    """Process any state changes that should occur in the game when this
    trigger interacts with the specified Player."""
    # By default, no action.
    pass   


class Fortification(Trigger):
  """A Fortification is a place that a player has secured, and which zombies
  will stay away from.
  
  It's descended from a Trigger so that the Destination can also be a
  Fortification.  As a trigger, it does nothing.
  """


class Player(Trigger):
  """A player is a player of the game, obviously, I hope."""

  def __init__(self, encoded=None, user=None, tile=None):
    self.infected = False
    self.is_zombie = False
    self.reached_destination = False
    self.fortification = None
    if user:
      self.email = user.email()
    self.tile = tile
    Entity.__init__(self, encoded)
  
  def DictForJson(self):
    if self.email is None:
      raise ModelStateError("User must be set before the Player is encoded.")
    dict = Entity.DictForJson(self)
    dict["email"] = self.email
    dict["infected"] = self.infected
    if self.infected:
      dict["infected_time"] = self.infected_time
    dict["is_zombie"] = self.is_zombie
    dict["reached_destination"] = self.reached_destination
    if self.fortification:
      dict["fortification"] = self.fortification.DictForJson()
    return dict

  def FromString(self, encoded):
    obj = Entity.FromString(self, encoded)
    self.email = obj["email"]
    self.infected = obj["infected"]
    if self.infected:
      self.infected_time = obj["infected_time"]
    self.is_zombie = obj["is_zombie"]
    self.reached_destination = obj["reached_destination"]
    if "fortification" in obj:
      # Hacky, but done for the one time that we encode a sub-dictionary in the
      # json dictionary.
      fstring = json.dumps(obj["fortification"])
      self.fortification = Fortification(fstring)
  
  def _Commit(self):
    if self.tile:
      self.tile.SetPlayer(self)
  
  def Email(self):
    return self.email
  
  def Invalidate(self, timedelta):
    # Has the player transitioned from an infected person to a zombie?
    if self.infected and \
       not self.is_zombie and \
       time.time() - self.infected_time > \
           INFECTED_PLAYER_TRANSITION_SECONDS:
      self.is_zombie = True
      
    if self.is_zombie and \
       time.time() - self.infected_time > \
           ZOMBIE_PLAYER_HEAL_SECONDS:
      self.is_zombie = False
      self.infected = False

    # Zombies can't fortify.
    if self.is_zombie:
      self.fortification = None
    
    if self.fortification and \
        not self.fortification.Lat() and \
        not self.fortification.Lon() and \
        self.Lat() and \
        self.Lon():
      # When we create a user, we don't have a location to set on the
      # fortification, so we set a sentinal fortification that doesn't have
      # a location.  If we see that we're in that situation, re-fortify until
      # we have a location to set on the fortification.
      self.Fortify()

    # A fortification expires when the player leaves it.
    if self.GetFortification() and \
        self.DistanceFrom(self.GetFortification()) > \
        DEFAULT_FORTIFICATION_RADIUS_METERS:
      self.fortification = None

    self._Commit()
      
  def Infect(self):
    """Call to trigger this Player getting infected by a zombie."""
    if not self.IsInfected():
      self.infected = True
      self.infected_time = time.time()
      self._Commit()
    
  def ReachedDestination(self):
    """Call to indicate that this player has reached the game's destination."""
    logging.info("Player reached destination.")
    self.reached_destination = True
    self._Commit()
  
  def HasReachedDestination(self):
    return self.reached_destination
  
  def IsInfected(self):
    return self.infected
  
  def IsZombie(self):
    return self.is_zombie
  
  def Trigger(self, player):
    if self.IsZombie():
      player.Infect()
  
  def Fortify(self):
    logging.info("Player %s fortifying." % self.ToString())
    if not self.fortification:
      self.fortification = Fortification()
    
    if self.Lat() and self.Lon():
      self.fortification.SetLocation(self.Lat(), self.Lon())
    logging.info("Player fortified: %s" % self.ToString())
    self._Commit()
  
  def GetFortification(self):
    return self.fortification


class Zombie(Trigger):
  
  def __init__(self, encoded=None, speed=None, guid=None):
    if speed:
      self.speed = speed
    if guid:
      self.guid = guid

    self.chasing = None
    self.chasing_email = None
    Entity.__init__(self, encoded)
  
  def Id(self):
    return self.guid
  
  def Advance(self, seconds, player_iter, fortifications_iter):
    """Meander some distance.
    
    Args:
      timedelta: a datetime.timedelta object indicating how much time has
          elapsed since the last time we've advanced the game.
      player_iter: An iterator that will walk over the players in the game.
    """
    # Flatten the iterator to a list so that we can iterate over it several
    # times.
    players = [player for player in player_iter]
    fortifications = [f for f in fortifications_iter]

    # Advance in 1-second increments.
    while seconds > 0:
      self.ComputeChasing(players)
      
      vector = [0, 0]
      
      time = min(seconds, 1)
      distance = time * self.speed
      if self.chasing:
        # If we're chasing someone, move toward them.  But scale this by 1/2 so
        # that if a zombie happens to be inside a fortification with a player,
        # the zombie moves away instead of being perfectly balanced
        vector[0] += (self.chasing.Lat() - self.Lat()) / 2
        vector[1] += (self.chasing.Lon() - self.Lon()) / 2

      for fortification in fortifications:
        if self.DistanceFrom(fortification) < \
            DEFAULT_FORTIFICATION_RADIUS_METERS:
          logging.debug("Zombie %s moving away from fortification." % self.Id())
          vector[0] -= fortification.Lat() - self.Lat()
          vector[1] -= fortification.Lon() - self.Lon()

      # If we don't have a particular goal in mind, just meander randomly.
      if vector[0] == 0 and vector[1] == 0:
        vector[0] = random.uniform(-1, 1)
        vector[1] = random.uniform(-1, 1)

      lat = self.Lat() + vector[0]
      lon = self.Lon() + vector[1]

      # Don't travel more than the distance to our determined destination
      distance = min(self.DistanceFromLatLon(lat, lon), distance)

      self.MoveTowardsLatLon(lat, lon, distance)
      
      seconds = seconds - 1
      
  def MoveTowardsLatLon(self, lat, lon, distance):
    dstToLatLon = self.DistanceFromLatLon(lat, lon)
    magnitude = 0
    if dstToLatLon > 0:
      magnitude = distance / dstToLatLon
    dLat = (lat - self.Lat()) * magnitude
    dLon = (lon - self.Lon()) * magnitude
    self.SetLocation(self.Lat() + dLat, self.Lon() + dLon)
  
  def ComputeChasing(self, player_iter):
    min_distance = None
    min_player = None
    for player in player_iter:
      distance = self.DistanceFrom(player)
      if min_distance is None or distance < min_distance:
        min_distance = distance
        min_player = player
    
    if min_distance and min_distance < ZOMBIE_VISION_DISTANCE_METERS:
      self.chasing = min_player
      self.chasing_email = min_player.Email()
    else:
      self.chasing = None
      self.chasing_email = None

  def Trigger(self, player):
    player.Infect()
  
  def DictForJson(self):
    dict = Entity.DictForJson(self)
    dict["speed"] = self.speed
    dict["guid"] = self.guid
    if self.chasing_email:
      dict["chasing"] = self.chasing_email
    return dict
  
  def FromString(self, encoded):
    obj = Entity.FromString(self, encoded)
    self.speed = float(obj["speed"])
    self.guid = obj["guid"]
    if obj.has_key("chasing"):
      self.chasing_email = obj["chasing"]


class Destination(Fortification):
  
  def Trigger(self, player):
    player.ReachedDestination()


class Game(db.Model):
  """A Game contains all the information about a ZombieRun game."""
  
  owner = db.UserProperty(auto_current_user_add=True)
  
  destination = db.StringProperty()
  
  # Meters per Second
  average_zombie_speed = db.FloatProperty(default=DEFAULT_ZOMBIE_SPEED)
  
  # Zombies / km^2
  zombie_density = db.FloatProperty(default=DEFAULT_ZOMBIE_DENSITY)
  
  game_creation_time = db.DateTimeProperty(auto_now_add=True)
  last_update_time = db.DateTimeProperty(auto_now=True)
  
  def __init__(self, *args, **kwargs):
    db.Model.__init__(self, *args, **kwargs)
    self.lat = None
    self.lon = None
    self.window = None
    
  def Put(self, force_db_put):
    """Put this game and the tiles in its window to the datastore.
    """
    self._GameTileWindow().PutTiles(not self.is_saved() or force_db_put)
    
    now = datetime.datetime.now()
    tdelta = now - self.last_update_time
    if force_db_put or tdelta.seconds > 30:
      logging.debug("Putting game to datastore.")
      self.put()
      self.last_update_time = now

    encoded = db.model_to_protobuf(self)
    if not memcache.set(self.key().name(), encoded):
      logging.warn("Game set to Memcache failed.")


  def SetWindowLatLon(self, lat, lon):
    """Set the latitude and longitude of the game's operating window's center,
    which will be used to retrieve the appropriate and necessary data for all
    game operations."""
    self.lat = lat
    self.lon = lon
  
  def _GameTileWindow(self):
    assert self.lat is not None
    assert self.lon is not None
    if (self.window is None or 
        self.window.Lat() != self.lat or 
        self.window.Lon() != self.lon):
      logging.debug("Constructing GameTileWindow for lat, lon (%f, %f)" %
                    (self.lat, self.lon))
      self.window = GameTileWindow(self, 
                                   self.lat, 
                                   self.lon, 
                                   PLAYER_VISION_DISTANCE_METERS)
    return self.window
  
  def IsVisible(self, entity):
    if entity.Lat() is None or entity.Lon() is None:
      logging.debug("Excluding an entity outside the visible window because "
                    "it doesn't have a location.")
      return False
    return DistanceBetween(self.lat,
                           self.lon, 
                           entity.Lat(),
                           entity.Lon()) < PLAYER_VISION_DISTANCE_METERS

  
  def Id(self):
    # Drop the "g" at the beginning of the game key name.
    return int(self.key().name()[1:])
  
  def GetPlayer(self, email):
    """Get a specific player, regardless of the player's current location (and
    specifically regardless of whether or not we've got the game tile with the
    player loaded in view from the normal tile load conditions)."""
    return self._GameTileWindow().GetPlayer(email)
  
  def Players(self):
    for player in self._GameTileWindow().Players():
      yield player
  
  def ZombiePlayers(self):
    for player in self.Players():
      if player.IsZombie():
        yield player
  
  def PlayersInPlay(self):
    """Iterate over the Players in the Game which have locations set, have not
    reached the destination, and are not infected.
    
    Returns:
        Iterable of (player_index, player) tuples.
    """
    for player in self.Players():
      if (self.IsVisible(player) and
          not player.HasReachedDestination() and
          not player.IsInfected()):
        yield player
  
  def Fortifications(self):
    for player in self.PlayersInPlay():
      if player.GetFortification():
        yield player.GetFortification()
    if self.destination:
      yield self.Destination()
  
  def AddPlayer(self, player):
    self._GameTileWindow().AddPlayer(player)
  
  def SetPlayer(self, player):
    self._GameTileWindow().SetPlayer(player)
  
  def Zombies(self):
    for zombie in self._GameTileWindow().Zombies():
      yield zombie
  
  def NumZombies(self):
    return self._GameTileWindow().NumZombies()
  
  def ZombiesAndInfectedPlayers(self):
    entities = []
    entities.extend(self.Zombies())
    entities.extend(self.ZombiePlayers())
    return entities
  
  def SetZombie(self, zombie):
    self._GameTileWindow().SetZombie(zombie)
  
  def Destination(self):
    return Destination(self.destination)
  
  def SetDestination(self, destination):
    self.destination = destination.ToString()
  
  def Entities(self):
    """Iterate over all Entities in the game."""
    for zombie in self.Zombies():
      yield zombie
    for player in self.Players():
      yield player
    yield self.Destination()
  
  def VisibleEntities(self):
    for entity in self.Entities():
      if self.IsVisible(entity):
        yield entity
  
  def Advance(self):
    timedelta = datetime.datetime.now() - self.last_update_time
    seconds = timedelta.seconds + timedelta.microseconds / float(1e6)
    seconds_to_move = min(seconds, MAX_TIME_INTERVAL_SECS)
    
    for entity in self.VisibleEntities():
      entity.Invalidate(timedelta)
    for player in self.Players():
      self.SetPlayer(player)
    for zombie in self.Zombies():
      self.SetZombie(zombie)

    updated_zombies = []
    for zombie in self.Zombies():
      zombie.Advance(seconds_to_move, 
                     self.PlayersInPlay(), 
                     self.Fortifications())
      updated_zombies.append(zombie)
      
    for zombie in updated_zombies:
      self.SetZombie(zombie)
      
    # Perform triggers on the current user.
    player = self.GetPlayer(users.get_current_user().email())
    if player.Lat() is not None and player.Lon() is not None:
      # Trigger destination first, so that when a player has reached the
      # destination at the same time they were caught by a zombie, we give them
      # the benefit of the doubt.
      destination = self.Destination()
      if destination.Lat() is not None and destination.Lon() is not None and \
          player.DistanceFrom(destination) < TRIGGER_DISTANCE_METERS:
        destination.Trigger(player)
  
      for zombie in self.ZombiesAndInfectedPlayers():
        if player.DistanceFrom(zombie) < TRIGGER_DISTANCE_METERS:
          zombie.Trigger(player)
      self.SetPlayer(player)
    
    # self._GameTileWindow().RepopulateZombies()
  
  def GetDebugMap(self):
    return self._GameTileWindow().GetDebugMap();


def ZombieEquals(a, b):
  return a.Id() == b.Id()


class GameTile(db.Model):
  """A GameTile represents a small geographical section of a ZombieRun game.

  There is a lot of copy-paste here, the only thing changing generally is the
  accessor to the id of the Zombie or of the Player.  That should be refactored.
  """

  # The list of player emails, for querying.
  player_emails = db.StringListProperty()
  
  # The actual encoded player data.
  players = db.StringListProperty()

  zombies = db.StringListProperty()

  last_update_time = db.DateTimeProperty(auto_now=True)
  
  nw = db.GeoPtProperty()
  
  game = db.ReferenceProperty(Game)
  
  def __init__(self, *args, **kwargs):
    db.Model.__init__(self, *args, **kwargs)
    self.decoded_players = None
    self.decoded_zombies = None
    
  def Id(self):
    """Get the id of the game tile.  The id is specific to a game, and cannot
    be used outside of that context."""
    return int(self.key().name().split("_")[1][2:])
    
  def AreaSqKm(self):
    return self._Width() / 1000 * self._Height() / 1000
  
  def _Width(self):
    return DistanceBetween(self.NW()[0], self.NW()[1],
                           self.NW()[0], self.SE()[1])
  
  def _Height(self):
    return DistanceBetween(self.NW()[0], self.NW()[1],
                           self.SE()[0], self.NW()[1])
  
  def NW(self):
    if self.Id() == UNLOCATED_TILE_ID:
      return (0, 0)
    return self.nw.lat, self.nw.lon
  
  def SE(self):
    """Note may return invalid lat/lon coordinates, but can be used to calculate
    distances."""
    return self.NW()[0] - GAME_TILE_LAT_SPAN, self.NW()[1] + GAME_TILE_LON_SPAN
  
  def Players(self):
    return [Player(e, tile=self) for e in self.players]
  
  def AddPlayer(self, player):
    logging.debug("Adding player %s to tile %s" %
                  (player.ToString(), self.Id()))
    if self.HasPlayer(player):
      self.RemovePlayer(player)
    self.players.append(player.ToString())
    self.player_emails.append(player.Email())
  
  def HasPlayer(self, player):
    for p in self.Players():
      if p.Email() == player.Email():
        return True
    return False
  
  def RemovePlayer(self, player):
    while self.HasPlayer(player):
      for i, p in enumerate(self.Players()):
        if p.Email() == player.Email():
          logging.debug("Removing player %s from tile %d" %
                        (player.Email(), self.Id()))
          self.players.pop(i)
          self.player_emails.pop(i)
          break
    
  def SetPlayer(self, player):
    self.RemovePlayer(player)
    self.AddPlayer(player)

  def Zombies(self):
    if self.decoded_zombies is not None:
      return self.decoded_zombies
    
    logging.debug("Decoding Zombies in game tile %d." % self.Id())
    self.decoded_zombies = [Zombie(e) for e in self.zombies]
    return self.decoded_zombies
  
  def NumZombies(self):
    return len(self.zombies)
  
  def ZombiesPerSqKm(self):
    count = self.NumZombies()
    area = self.AreaSqKm()
    return count / area
  
  def _AddZombie(self, zombie):
    if not self.HasZombie(zombie):
      self.zombies.append(zombie.ToString())
      self.decoded_zombies.append(zombie)
  
  def HasZombie(self, zombie):
    for z in self.Zombies():
      if z.Id() == zombie.Id():
        return True
    return False
  
  def RemoveZombie(self, zombie):
    for i, z in enumerate(self.Zombies()):
      if z.Id() == zombie.Id():
        self.zombies.pop(i)
        self.decoded_zombies.pop(i)
        return
    logging.warn("Could not find zombie %s in game tile %d" %
                 (zombie.ToString(), self.Id()))
  
  def SetZombie(self, zombie):
    for i, z in enumerate(self.Zombies()):
      if ZombieEquals(z, zombie):
        self.zombies[i] = zombie.ToString()
        self.decoded_zombies[i] = zombie
        return
    logging.warn("Could not find zombie %s in game tile %d" %
                 (zombie.ToString(), self.Id()))
    logging.info("Zombies: %s" % [z.ToString() for z in self.Zombies()])
    
  def PopulateZombies(self):
    if self.Id() == UNLOCATED_TILE_ID:
      logging.debug("Not populating zombies in the unlocated tile.")
      return
    
    logging.debug("Populating zombies in tile %d" % self.Id())
    while self.ZombiesPerSqKm() < DEFAULT_ZOMBIE_DENSITY:
      zombie_cluster_size = random.randint(1, MAX_ZOMBIE_CLUSTER_SIZE)
      cluster_added = False
      while not cluster_added:
        cluster_added = self._AddZombieCluster(zombie_cluster_size)
  
  def _AddZombieCluster(self, num_zombies):
    cluster_lat = self.NW()[0] - random.uniform(0, GAME_TILE_LAT_SPAN)
    cluster_lon = self.NW()[1] + random.uniform(0, GAME_TILE_LON_SPAN)
    
    for player in self.Players():
      if DistanceBetween(player.Lat(), 
                         player.Lon(), 
                         cluster_lat, 
                         cluster_lon) < MIN_ZOMBIE_DISTANCE_FROM_PLAYER:
        logging.debug("Declining to add zombie cluster due to player "
                      "proximity.")
        return False
    
    logging.debug("Adding zombie cluster to tile %d of size %d with center "
                  "(%f, %f)" %
                  (self.Id(), num_zombies, cluster_lat, cluster_lon))
    for i in xrange(num_zombies):
      self._AddZombieAt(cluster_lat,
                        cluster_lon)
    return True
  
  def _AddZombieAt(self, center_lat, center_lon):
    speed = (DEFAULT_ZOMBIE_SPEED + 
             random.uniform(-ZOMBIE_SPEED_VARIANCE, ZOMBIE_SPEED_VARIANCE))

    distance_from_center = random.uniform(0, MAX_ZOMBIE_CLUSTER_RADIUS)

    lat, lon = self._RandomPointNear(center_lat, 
                                     center_lon, 
                                     distance_from_center)
    zombie = Zombie(speed=speed, guid=str(uuid.uuid4()))
    zombie.SetLocation(lat, lon)

    logging.debug("Adding zombie %s to tile %d." % 
                  (zombie.ToString(), self.Id()))
    
    self._AddZombie(zombie)

  def _RandomPointNear(self, lat, lon, distance):
    radians = math.pi * 2 * random.random()
    to_lat = lat + math.sin(radians)
    to_lon = lon + math.cos(radians)

    base_to_distance = DistanceBetween(lat, lon, to_lat, to_lon)
    magnitude = distance / base_to_distance
    
    dLat = (to_lat - lat) * magnitude
    dLon = (to_lon - lon) * magnitude
    
    return (lat + dLat, lon + dLon) 
  

class GameTileWindow():
  """A GameTileWindow is a utility class for dealing with a set of GameTiles."""

  def __init__(self, game, lat, lon, radius_meters):
    logging.debug("Initializing GameTileWindow for lat, lon (%f, %f)" %
                 (lat, lon))
    self.game = game
    
    # Map from game tile id to game tile instance.
    self.tiles = {}
    
    # Map from player email to game tile id.
    self.players = {}
    
    self.lat = lat
    self.lon = lon
    
    nLat = lat
    wLon = lon
    sLat = lat
    eLon = lon
    
    # Expand nLat and sLat to span a distance that is >= radius_meters
    while DistanceBetween(lat, lon, nLat, lon) < radius_meters:
      nLat += GAME_TILE_LAT_SPAN
      sLat -= GAME_TILE_LAT_SPAN

    # Expand wLon and eLon to span a distance that is >= radius_meters
    while DistanceBetween(lat, lon, lat, wLon) < radius_meters:
      wLon -= GAME_TILE_LON_SPAN
      eLon += GAME_TILE_LON_SPAN
    
    tileLat = nLat
    tileLon = wLon
    # Walk from the north-west to the south-east corner of the bounding box,
    # loading the tiles in sequence.
    while tileLat >= sLat:
      while tileLon <= eLon:
        self._TileForLatLon(tileLat, tileLon)
        tileLon += GAME_TILE_LON_SPAN
      tileLat -= GAME_TILE_LAT_SPAN
    logging.debug("Loaded %d GameTiles." % len(self.tiles))
    
  def Lat(self):
    return self.lat
  
  def Lon(self):
    return self.lon
    
  def PutTiles(self, force_datastore_put=True):
    logging.debug("Putting %d game tiles." % len(self.tiles))
    self._PutTilesToDatastore(force_datastore_put)
    self._PutTilesToMemcache()
    
  def _PutTilesToDatastore(self, force_datastore_put):
    datastore_tiles = []

    if force_datastore_put:
      logging.debug("Forcing game tile datastore put.")
      datastore_tiles = self.tiles.values()
    else:
      now = datetime.datetime.now()
      for tile in self.tiles.values():
        tdelta = now - tile.last_update_time
        if tdelta.seconds > 30 or not tile.is_saved():
          datastore_tiles.append(tile)
          tile.last_update_time = now

    if datastore_tiles:
      logging.info("Putting %d game tiles to datastore." % len(datastore_tiles))
      db.put(datastore_tiles)
    else:
      logging.debug("No game tiles put to datastore.")
  
  def _PutTilesToMemcache(self):
    mapping = {}
    for tile in self.tiles.values():
      key = self._GetGameTileKeyName(tile.Id())
      mapping[key] = db.model_to_protobuf(tile)

    for email in self.players:
      key = self._GetPlayerTileLocationKeyName(email)
      mapping[key] = self.players[email]
      
    if len(mapping):
      logging.debug("Putting %d game tiles to memcache." % len(mapping))
      memcache.set_multi(mapping)
    else:
      logging.debug("Not putting any game tiles to memcache.")
  
  def GetPlayer(self, email):
    def FindInLoadedTiles(email):
      # Do we already have this player in view?
      for player in self.Players():
        if player.Email() == email:
          logging.debug("Found player %s in preloaded game tiles." % email)
          return player
    
    player = FindInLoadedTiles(email)
    if player:
      return player
    
    # Do we have the player's location registered in memcache?
    tile_id = memcache.get(self._GetPlayerTileLocationKeyName(email))
    if tile_id:
      logging.info("Found location of player %s from memcache." % email)
      self._LoadGameTile(tile_id)
      player = FindInLoadedTiles(email)
      if player:
        return player

    # Query the datastore for the game tile that contains the player, and load
    # it.
    logging.info("Querying datastore for game tile containing player %s" % 
                 email)
    query = GameTile.all()
    query.filter("player_emails = ", email)
    query.filter("game = ", self.game)
    query.order("-last_update_time")
    tile = query.get()
    if tile is not None:
      logging.info("Found player %s in game tile %d from datastore." %
                   (email, tile.Id()))
      self.tiles[tile.Id()] = tile
      player = FindInLoadedTiles(email)
      if player:
        return player

    logging.warn("Did not find player %s in any game tiles." % email)
    return None
  
  def Players(self):
    for tile in self.tiles.itervalues():
      for player in tile.Players():
        yield player

  def AddPlayer(self, player):
    tile = self._TileForEntity(player)
    logging.debug("Adding player %s to tile %d" % (player.Email(), tile.Id()))
    tile.AddPlayer(player)
  
  def RemovePlayer(self, player):
    old_player = self.GetPlayer(player.Email())
    for tile in self.tiles.itervalues():
      tile.RemovePlayer(player)
  
  def SetPlayer(self, player):
    logging.debug("Setting player %s" % player.Email())
    old_tile = self._TileForEntity(self.GetPlayer(player.Email()))
    new_tile = self._TileForEntity(player)
    self.RemovePlayer(player)
    self.AddPlayer(player)
    
    if old_tile != new_tile:
      self._PutTilesToMemcache()
      self._PutTilesToDatastore(True)

  def Zombies(self):
    zombies = []
    for tile in self.tiles.itervalues():
      for zombie in tile.Zombies():
        zombies.append(zombie)
    return zombies
  
  def NumZombies(self):
    return sum([tile.NumZombies() for tile in self.tiles.itervalues()])
  
  def SetZombie(self, zombie):
    # First find the zombie in the tile it exists right now, and determine
    # whether or not the zombie is moving from one tile to another.
    original_tile = None
    for tile in self.tiles.itervalues():
      if tile.HasZombie(zombie):
        original_tile = tile
    
    new_tile = self._TileForEntity(zombie)
    if new_tile != original_tile:
      logging.debug("Zombie moved from tile %s to tile %s." %
                    (original_tile.Id(), new_tile.Id()))
      original_tile.RemoveZombie(zombie)
      new_tile._AddZombie(zombie)
    else:
      new_tile.SetZombie(zombie)
  
  def RepopulateZombies(self):
    for tile in self.tiles.itervalues():
      tile.PopulateZombies()
  
  def GetDebugMap(self):
    tiles = []
    for tile in self.tiles.itervalues():
      tiles.append({"ne": {"lat": tile.NW()[0],
                           "lon": tile.SE()[1]},
                    "sw": {"lat": tile.SE()[0],
                           "lon": tile.NW()[1]}
                   })
    return {"tiles": tiles}
    
  def _TileForEntity(self, entity):
    return self._TileForLatLon(entity.Lat(), entity.Lon())

  def _TileForLatLon(self, lat, lon):
    if lat is None or lon is None:
      # operations on things that don't have lat/lon values get assigned to the
      # "unlocated" tile (tile id UNLOCATED_TILE_ID)
      return self._GetOrCreateGameTile(UNLOCATED_TILE_ID)
    return self._GetOrCreateGameTile(self._TileIdForLatLon(lat, lon))

  def _TileIdForLatLon(self, lat, lon):
    # We assume in these calculations that 360 / GAME_TILE_LON_SPAN and
    # 180 / GAME_TILE_LAT_SPAN both come out to an integer value.
    
    # identify the column of GameTiles at longitude -180 to be column 0.
    # we have a total of 360 / GAME_TILE_LON_SPAN columns.
    # 
    # So, the column that this entity lies in is:
    #    portion_into_columns * num_columns =
    #    (lon / 360) * (360 / GAME_TILE_LON_SPAN) =
    #    lon / GAME_TILE_LON_SPAN
    #
    # Which is then rounded down to an integer id.
    #
    # Tiles are identified by their NW corner.
    
    lon += 180
    column = int(lon / GAME_TILE_LON_SPAN)
    
    # Similar logic for the row
    lat += 90
    row = int(lat / GAME_TILE_LAT_SPAN)

    # ID of the game tile is defined as:
    #
    # column * NUM_ROWS_PER_COLUMN + row
    id = int((column * 180 / GAME_TILE_LAT_SPAN) + row)
    return id
  
  def _NWLatLonForTileId(self, id):
    rows_per_column = 180 / GAME_TILE_LAT_SPAN
    row = id % rows_per_column
    column = (id - row) / rows_per_column
    lat = row * GAME_TILE_LAT_SPAN
    lat -= 90
    lon = column * GAME_TILE_LON_SPAN
    lon -= 180
    return (lat, lon)
  
  def _SELatLonForTileId(self, id):
    nwLat, nwLon = self._NWLatLonForTileId(id)
    seLat, seLon = nwLat - GAME_TILE_LAT_SPAN, nwLon + GAME_TILE_LON_SPAN
    if seLat < -90:
      # Translate -91 to -89
      seLat += (-90 + seLat) * 2
    if seLon > 180:
      seLon -= 360
    return (seLat, seLon)
    
  def _GetOrCreateGameTile(self, id):
    if self.tiles.has_key(id):
      return self.tiles[id]

    if self._LoadGameTile(id):
      return self._GetOrCreateGameTile(id)
  
  def _LoadGameTile(self, id):
    logging.debug("Loading game tile %d" % id)
    if (self._LoadGameTileFromMemcache(id) or
        self._LoadGameTileFromDatastore(id)):
      # Build our player tile id cache.
      for tile in self.tiles.itervalues():
        for player in tile.Players():
          self.players[player.Email()] = tile.Id()
      
      return True
    return False
    
  def _LoadGameTileFromMemcache(self, id):
    key = self._GetGameTileKeyName(id)
    logging.debug("Looking up entry %s in memcache." % key)
    encoded = memcache.get(self._GetGameTileKeyName(id))
    
    if not encoded:
      logging.info("Memcache game tile miss.")
      return False
    logging.debug("Memcache game tile hit.")
    
    try:
      tile = db.model_from_protobuf(encoded)
      self.tiles[id] = tile
      return True
    except db.Error, e:
      logging.error("Protobuf Decode Error on GameTile: %s" % e)
      return False
  
  def _LoadGameTileFromDatastore(self, id):
    logging.info("Getting game tile %d from datastore.", id)

    # It would be nice to run this in a transaction, but we access this method
    # from the "create game entry" method, which would result in a nested
    # transaction.  For now, let's just let it be, and we'll deal with the
    # consequences later.  This will hopefully be an edge case.
    tile_key = self._GetGameTileKeyName(id)
    logging.debug("Loading game tile %s from datastore." % tile_key)
    tile = GameTile.get_by_key_name(tile_key)
    if tile is None:
      logging.debug("Initializing new game tile %d" % id)
      
      geopt = None
      if id != UNLOCATED_TILE_ID:
        geopt = db.GeoPt(*self._NWLatLonForTileId(id))
      else:
        logging.debug("Initializing the 'unlocated' tile.")
        
      tile = GameTile(key_name=tile_key, game=self.game, nw=geopt)
      tile.PopulateZombies()

    self.tiles[id] = tile
    return True
  
  def _GetGameTileKeyName(self, tile_id):
    return "g%d_gt%d" % (self.game.Id(), tile_id)
  
  def _GetPlayerTileLocationKeyName(self, email):
    return "g%d_p%s" % (self.game.Id(), email)
