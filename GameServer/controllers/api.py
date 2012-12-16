import datetime
import logging
import math
import os
import random
import wsgiref.handlers
import yaml

from django.utils import simplejson as json

from models import game as game_module
from models.game import Destination
from models.game import Entity
from models.game import Game
from models.game import Player
from models.game import Zombie

from google.appengine.api import mail
from google.appengine.api import memcache
from google.appengine.api import users
from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template


GAME_ID_PARAMETER = "gid"
LATITUDE_PARAMETER = "lat"
LONGITUDE_PARAMETER = "lon"
NUMBER_OF_ZOMBIES_PARAMETER = "num_zombies"
AVERAGE_SPEED_OF_ZOMBIES_PARAMETER = "average_zombie_speed"
SW_LAT_PARAM = "swLat"
SW_LON_PARAM = "swLon"
NE_LAT_PARAM = "neLat"
NE_LON_PARAM = "neLon"
FORTIFY_PARAMETER = "fortify"
DEBUG_PARAMETER = "d"


class Error(Exception):
  """Base error."""

class MalformedRequestError(Error):
  """The incoming request was missing some required field or referenced data
  that does not exist."""

class GameNotFoundError(Error):
  """The game was not found."""

class GameStartedError(Error):
  """The game has already started, precluding the attempted operation."""
  
class GameStateError(Error):
  """There was some error in the game's state."""

class AuthorizationError(Error):
  """The current user was not allowed to execute this action."""


class GameHandler(webapp.RequestHandler):
  """The GameHandler is really a base RequestHandler for the other
  RequestHandlers in the API.  It's not capable of handling anything on its own,
  but rather provides a series of methods that are useful to the rest of the
  handlers."""
  
  def __init__(self):
    self.game = None

  def GetGameKeyName(self, game_id):
    """For a given game id, get a string representing the game's key name.
    
    Args:
      game_id: The integer game id.
    
    Returns:
      A string with which one can create a db.Key for the game.
    """
    return "g%d" % game_id
  
  # HACK: authorize defaulted to false so that any player can join the global
  # game.  Need to special-case that more effectively.
  def GetGame(self, game_id=None, authorize=False):
    """Determines the game id and retreives the game data from the db.
    
    Args:
      game_id: If not None, gets the game with this id.  If None, takes the
          game id from the request
      validate_secret_key: If true, checks the request's SECRET_KEY_PARAMETER
          and validates it against the stored secret key in the database.  If
          there is a mismatch, raises SecretKeyMismatchError.
    
    Raises:
      MalformedRequestError: If the game id or secret key is not present in the
          request.
      GameNotFoundError: If the provided game id is not present in the
          datastore.
    """
    if self.game:
      return self.game
    
    lat = 0
    lon = 0
    if game_id is None:
      try:
        game_id = self.GetGameIdFromRequest()
        lat = float(self.request.get(LATITUDE_PARAMETER, lat))
        lon = float(self.request.get(LONGITUDE_PARAMETER, lon))
      except TypeError, e:
        raise MalformedRequestError("lat, or lon not present in "
                                    "request or not properly valued.")
      except ValueError, e:
        raise MalformedRequestError("lat, or lon not present in "
                                    "request or not properly valued.")
    
    game_key = self.GetGameKeyName(game_id)
    
    if self.LoadFromMemcache(game_key) or self.LoadFromDatastore(game_key):
      self.game.SetWindowLatLon(lat, lon)
      if authorize:
        self.Authorize(self.game)
      return self.game
    else:
      raise GameNotFoundError()
  
  def GetGameIdFromRequest(self):
    try:
      # Try to get and parse an integer from the URL's hash tag.
      return int(self.request.get(GAME_ID_PARAMETER, None))
    except TypeError, e:
      raise MalformedRequestError("Game id not present in "
                                  "request or not properly valued.")
    except ValueError, e:
      raise MalformedRequestError("Game id not present in "
                                  "request or not properly valued.")
    return None
  
  def LoadFromMemcache(self, key):
    """Try loading the game from memcache.  Sets self.game, returns true if
    self.game was successfully set.  If false, self.game was not set."""
    encoded = memcache.get(key)
    
    if not encoded:
      logging.warn("Memcache miss.")
      return False
    
    try:
      self.game = db.model_from_protobuf(encoded)
      return True
    except db.Error, e:
      logging.warn("Game Model decode from protobuf error: %s" % e)
      return False
    
  def LoadFromDatastore(self, key):
    """Load the game from the database.  Sets self.game, returns true if
    self.game was successfully set.  If false, self.game was not set."""
    logging.info("Getting game from datastore.")
    game = Game.get_by_key_name(key)
    if game:
      self.game = game
      return True
    else:
      return False
  
  def PutGame(self, game, force_db_put):
    game.Put(force_db_put)
    
  def Authorize(self, game):
    user = users.get_current_user()
    if not user:
      raise AuthorizationError("Request to get a game by a non-logged-in-user.")
    else:
      authorized = (game.GetPlayer(user.email()) is not None)
      if not authorized:
        raise AuthorizationError(
            "Request to get a game by a user who is not part of the game "
            "(unauthorized user: %s)." % user.email())
  
  def OutputGame(self, game):
    """Write the game data to the output, serialized as YAML.
    
    Args:
      game: The Game that should be written out.
    """
    dictionary = {}
    dictionary["game_id"] = game.Id()
    dictionary["owner"] = game.owner.email()
    dictionary["player"] = users.get_current_user().email()
    
    # We always return the location of all the players, as it's important that
    # the clients see all player state changes.
    dictionary["players"] = [x.DictForJson() for 
                             x in 
                             game.Players()]

    dictionary["zombies"] = [x.DictForJson() for 
                             x in 
                             game.Zombies() if 
                             game.IsVisible(x)]
    
    if game.destination is not None:
      destination_dict = json.loads(game.destination)
      dictionary["destination"] = destination_dict
    
    if self.request.get(DEBUG_PARAMETER):
      dictionary["debug"] = game.GetDebugMap()
    
    self.Output(json.dumps(dictionary))
  
  def Output(self, output):
    """Write the game to output."""
    self.response.headers["Content-Type"] = "text/plain; charset=utf-8"
    # logging.info("Response: %s" % output)
    self.response.out.write(output)
    
  def LoginUrl(self, landing=None):
    return users.create_login_url(landing or self.request.uri)
    
  def RedirectToLogin(self):
    self.redirect(self.LoginUrl())
  
  def RedirectToGame(self):
    self.redirect(self.request.host_url)
  
  def UrlForGameJoin(self, game):
    return "%s/join?%s=%d" % (self.request.host_url,
                              GAME_ID_PARAMETER,
                              game.Id())


class GetHandler(GameHandler):
  """Handles getting the current game state."""
  
  def get(self):
    """Task: encode the game data in the output.
    
    GAME_ID_PARAMETER must be present in the request and in the datastore.
    """
    game = self._GetAndAdvance()
    self.OutputGame(game)
  
  def _GetAndAdvance(self):
    game = self.GetGame()
    game = self.AdvanceAndPutGame(game)
    return game
  
  def AdvanceAndPutGame(self, game):
    players_infected = self._PlayersInfected(game)
    players_converted = self._PlayersConverted(game)
    game.Advance()
    # For some reason we lose some state when we only put the game to memcache,
    # so for now we force put it to the datastore every time.
    self.PutGame(game, False)
    return game
  
  def _PlayersInfected(self, game):
      return len(filter(lambda p: p.IsInfected(),
                        game.Players()))

  def _PlayersConverted(self, game):
      return len(filter(lambda p: p.IsZombie(),
                        game.Players()))


class PutHandler(GetHandler):
  """The PutHandler handles registering updates to the game state.
  
  All update requests return the current game state, to avoid having to make
  two requests to update and get the game state.
  """
  
  def get(self):
    """Task: Parse the input data and update the game state."""
    user = users.get_current_user()
    if user:
      game = self._PutAndAdvanceGame()
      self.OutputGame(game)
    else:
      self.RedirectToLogin()
      
  def UpdateCurrentPlayer(self, game):
    user = users.get_current_user()

    lat = None
    lon = None
    try:
      lat = float(self.request.get(LATITUDE_PARAMETER))
      lon = float(self.request.get(LONGITUDE_PARAMETER))
    except ValueError, e:
      raise MalformedRequestError(e)
    
    player = game.GetPlayer(user.email())
    player.SetLocation(float(self.request.get(LATITUDE_PARAMETER)),
                       float(self.request.get(LONGITUDE_PARAMETER)))
    if self.request.get(FORTIFY_PARAMETER):
      player.Fortify()
    game.SetPlayer(player)
  
  def _PutAndAdvanceGame(self):
    game = self.GetGame()
    self.UpdateCurrentPlayer(game)

    # TODO: compute the zombie density in an area around each player, and if
    # the zombie density drops below a certain level, add more zombies at
    # the edge of that area.  This should ensure that players can travel
    # long distances without running away from the original zombie
    # population.
    game = self.AdvanceAndPutGame(game)

    return game
  

class StartHandler(PutHandler):
  """Handles starting a game."""
  
  def get(self):
    game = self.GetGame()
    if users.get_current_user() != game.owner:
      raise AuthorizationError("Only the game owner can start the game.")
    
    # Set the destination
    lat = None
    lon = None
    try:
      lat = float(self.request.get(LATITUDE_PARAMETER))
      lon = float(self.request.get(LONGITUDE_PARAMETER))
    except ValueError, e:
      raise MalformedRequestError(e)
    
    destination = Destination()
    destination.SetLocation(lat, lon)
    game.SetDestination(destination)

    self.PutGame(game, True)
    self.OutputGame(game)
    

class AddFriendHandler(GameHandler):
  def get(self):
    game = self.GetGame()
    
    to_addr = self.request.get("email")
    if not mail.is_email_valid(to_addr):
      # 403: Forbidden.
      self.error(403)
      return
    
    message = mail.EmailMessage()
    message.sender = users.get_current_user().email()
    message.to = to_addr
    message.subject = ("%s wants to save you from Zombies!" % 
                       users.get_current_user().nickname())
    
    game_link = self.UrlForGameJoin(game)
    # TODO: This should be a rendered Django template.
    message.body = """%s wants to save you from Zombies!
    
    Click on this link on your iPhone or Android device to run far, far away: %s
    """ % (users.get_current_user().nickname(), game_link)

    message.send()