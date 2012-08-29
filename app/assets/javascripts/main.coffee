AnimatedFocus = require('models/animated_focus').AnimatedFocus
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator
TransactionQueue = require('models/transaction_queue').TransactionQueue
RemoteTagList = require('models/remote_tag_list').RemoteTagList
World = require('models/world').World

transaction_queue = new TransactionQueue()

world = new World()

remote_tag_list = new RemoteTagList(world.cache, transaction_queue, world.cache.server)

world.cache.on_demand_tree.demand_root()

log = require('globals').logger

jQuery ($) ->
  log_controller = require('controllers/log_controller').log_controller
  log_controller(log, world.cache.server)

  interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
  animator = new Animator(interpolator)
  focus = new AnimatedFocus(animator)

  $('#tag-list').each () ->
    tag_list_controller = require('controllers/tag_list_controller').tag_list_controller
    tag_list_controller(this, remote_tag_list, world.state)
  $('#focus').each () ->
    focus_controller = require('controllers/focus_controller').focus_controller
    focus_controller(this, focus)
  $('#tree').each () ->
    tree_controller = require('controllers/tree_controller').tree_controller
    tree_controller(this, world.cache.on_demand_tree, focus, world.state)
  $('#document-list').each () ->
    document_list_controller = require('controllers/document_list_controller').document_list_controller
    document_list_controller(this, world.cache, world.state)
  $('#document').each () ->
    document_contents_controller = require('controllers/document_contents_controller').document_contents_controller
    document_contents_controller(this, world.state, world.cache.server.router)
