import cProfile
import logging
import pstats 
import StringIO
import wsgiref.handlers

from controllers import processing
from controllers import site
from controllers import api
from google.appengine.api import users
from google.appengine.ext import webapp


URL_BINDINGS = [
                 ('/tasks/cleanup/tiles', processing.CleanupTileHandler),
                 ('/tasks/cleanup/nonce_tiles', processing.CleanupNonceTileHandler),
                 ('/tasks/cleanup/games', processing.CleanupGameHandler),
                 ('/join', site.JoinHandler),
                 ('/new', site.NewHandler),
                 ('/rpc/get', api.GetHandler),
                 ('/rpc/put', api.PutHandler),
                 ('/rpc/start', api.StartHandler),
                 ('/rpc/addFriend', api.AddFriendHandler),
                 (r'/.*', site.HomepageHandler),
               ]
REVERSE_URL_BINDINGS = {}


def GetApplication():
  for (url, clazz) in URL_BINDINGS:
    REVERSE_URL_BINDINGS[clazz] = url
  return webapp.WSGIApplication(
      URL_BINDINGS,
      debug=True)


def real_main():
  wsgiref.handlers.CGIHandler().run(GetApplication())


def profile_main():
  prof = cProfile.Profile()
  prof = prof.runctx("real_main()", globals(), locals())
  stream = StringIO.StringIO()
  stats = pstats.Stats(prof, stream=stream)
  stats.strip_dirs()
  stats.sort_stats("cumulative")
  stats.print_stats(20)
  logging.debug("Profile data:\n%s", stream.getvalue())


if __name__ == '__main__':
  real_main()
