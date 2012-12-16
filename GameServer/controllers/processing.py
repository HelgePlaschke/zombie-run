import datetime
import logging

from models import game

from google.appengine import runtime
from google.appengine.api.labs import taskqueue
from google.appengine.ext import db
from google.appengine.ext import webapp


MAX_DATASTORE_ENTITY_AGE = datetime.timedelta(7, 0, 0)


class BaseCleanupHandler(webapp.RequestHandler):
  
  def get(self):
    self.post()
  
  def post(self):
    entities = self._GetQuery().fetch(50)
    if len(entities) == 0:
      return
    db.delete(entities)
    task = taskqueue.Task(url=self._GetTaskUrl())
    task.add(queue_name="cleanup")
  
  def _GetTaskUrl(self):
    raise Exception("Must implement.")

  def _GetQuery(self):
    raise Exception("Must implement.")


class CleanupTileHandler(BaseCleanupHandler):

  def _GetTaskUrl(self):
    return "/tasks/cleanup/tiles"

  def _GetQuery(self):
    query = game.GameTile.all()
    query.filter("last_update_time < ", 
                 datetime.datetime.now() - MAX_DATASTORE_ENTITY_AGE)
    return query


class CleanupNonceTileHandler(webapp.RequestHandler):

  def _GetTaskUrl(self):
    return "/tasks/cleanup/nonce_tiles"

  def _GetQuery(self):
    nonce_tile = game.GameTile.get_by_key_name("g1_gt-1")
    if nonce_tile:
      nonce_tile.delete()


class CleanupGameHandler(BaseCleanupHandler):
  
  def _GetTaskUrl(self):
    return "/tasks/cleanup/games"

  def _GetQuery(self):
    query = game.Game.all()
    query.filter("last_update_time < ",
                 datetime.datetime.now() - MAX_DATASTORE_ENTITY_AGE)
    return query
