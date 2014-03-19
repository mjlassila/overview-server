wd = require('wd')

Constants =
  ajaxTimeout: 2000 # timeout waiting for AJAX requests
  pollLength: 50 # milliseconds between condition checks

options =
  desiredCapabilities:
    browserName: 'phantomjs'

wd.addPromiseChainMethod 'acceptingNextAlert', ->
  @executeFunction ->
    window.acceptingNextAlert =
      alert: window.alert
      confirm: window.confirm

    after = ->
      for k, v of window.acceptingNextAlert
        window[k] = v
      delete window.acceptingNextAlert

    window.alert = -> after(); undefined
    window.confirm = -> after(); true

wd.addAsyncMethod 'dumpLog', ->
  cb = wd.findCallback(arguments)

  @log 'browser', (__, entries) ->
    for entry in entries
      console.log(entry.timestamp, entry.level, entry.message)
    cb()

# Call this, then do stuff, then call waitForJqueryAjaxComplete.
wd.addPromiseChainMethod 'listenForJqueryAjaxComplete', ->
  @executeFunction ->
    if 'listenForJqueryAjaxComplete' not of window
      window.listenForJqueryAjaxComplete =
        current: 0 # number of times we've listened
        total: 0   # number of ajax requests that completed since we first
                   # called listenForAjaxComplete
      $(document).ajaxComplete ->
        window.listenForJqueryAjaxComplete.total += 1
    else
      # Skip all unhandled ajax-complete events
      x = window.listenForJqueryAjaxComplete
      x.current = x.total

# Finishes when an $.ajaxComplete method is fired.
#
# Before calling this, you must call listenForJqueryAjaxComplete(). Starting at
# that exact moment, waitForJqueryAjaxComplete() will finish once for every
# jQuery AJAX request that completes.
#
# Note the danger of a race. If a request was pending before you called
# listenForJqueryAjaxComplete(), this method will finish once that pending ajax
# method completes. To avoid races, call listenForJqueryAjaxComplete() when
# there are no pending AJAX requests.
wd.addPromiseChainMethod 'waitForJqueryAjaxComplete', ->
  @
    .waitForFunctionToReturnTrueInBrowser((-> window.listenForJqueryAjaxComplete.current < window.listenForJqueryAjaxComplete.total), Constants.ajaxTimeout, Constants.pollLength)
    .executeFunction(-> window.listenForJqueryAjaxComplete.current += 1)

wrapJsFunction = (js) -> "(#{js})()"

wd.addPromiseChainMethod 'executeFunction', (js) ->
  @execute(wrapJsFunction(js))

wd.addPromiseChainMethod 'waitForFunctionToReturnTrueInBrowser', (js, timeout, pollLength) ->
  @waitForConditionInBrowser(wrapJsFunction(js), timeout, pollLength)

argsToXPath = (args) ->
  tag = args.tag ? '*'

  attrs = []
  if args.contains
    attrs.push("contains(., '#{args.contains.replace(/'/g, "\\'")}')")

  for className in [ args.className, args['class'] ]
    if className
      attrs.push("contains(concat(' ', @class, ' '), ' #{className} ')")

  xpath = "//#{tag}"
  for attr in attrs
    xpath += "[#{attr}]"
  xpath

# Finds an element by lots of wonderful stuff.
#
# For instance:
#
#   .elementBy(tag: 'a', contains: 'Reset') # tag name a, text _contains_ 'Reset'
wd.addAsyncMethod 'elementBy', (args) ->
  xpath = argsToXPath(args)
  cb = wd.findCallback(arguments)

  @elementByXPath(xpath, cb)

# Waits for an element by lots of wonderful stuff.
#
# For instance:
#
#   .waitForElementBy(tag: 'a', contains: 'Reset')
wd.addAsyncMethod 'waitForElementBy', (args) ->
  xpath = argsToXPath(args)
  cb = wd.findCallback(arguments)

  @waitForElementByXPath(xpath, cb)

module.exports =
  baseUrl: 'http://localhost:9000'
  adminLogin:
    email: 'admin@overviewproject.org'
    password: 'admin@overviewproject.org'

  # Returns a promise of a browser.
  create: ->
    wd
      .promiseChainRemote()
      .init(options.desiredCapabilities)
      .configureHttp
        baseUrl: module.exports.baseUrl
        timeout: 15000
        retries: 1
        retryDelay: 10
      .setImplicitWaitTimeout(0)   # we only wait explicitly! We don't want to code race conditions
      .setAsyncScriptTimeout(5000) # in case there are HTTP requests
      .setPageLoadTimeout(15000)   # in case, on a slow computer, something slow happens