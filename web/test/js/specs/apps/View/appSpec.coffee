define [
  'backbone'
  'apps/View/app'
], (Backbone, ViewApp) ->
  describe 'apps/View/app', ->
    class View extends Backbone.Model

    beforeEach ->
      @view = new View
        type: 'view'
        id: '123'
        apiToken: 'api-token'
        title: 'title'
        url: 'http://localhost:9876/base/mock-plugin'
        filter: null
        serverUrlFromPlugin: null

      @el = document.createElement('div')
      @main = document.createElement('main')
      @main.innerHTML = [
        '<div id="tree-app-left"></div>',
        '<div id="tree-app-vertical-split"></div>',
        '<div id="tree-app-right"></div>',
        '<div id="tree-app-vertical-split-2"></div>',
        '<div id="tree-app-right-pane"></div>',
      ].join('')

      document.body.appendChild(@el)
      document.body.appendChild(@main)

      @sandbox = sinon.sandbox.create()

      @createViewApp = () =>
        @viewApp = new ViewApp
          documentSetId: 234
          el: @el
          main: @main
          view: @view

    afterEach ->
      @viewApp?.remove()
      document.body.removeChild(@main) # we don't want viewApp.remove() to remove this
      @sandbox.restore()

    it 'should clear tree-app-right-pane on start', ->
      pane = @main.querySelector('#tree-app-right-pane')
      pane.innerHTML = 'here is some content'
      @createViewApp()
      expect(pane.innerHTML).to.eq('')

    it 'should show an iframe', ->
      @createViewApp()
      $iframe = @viewApp.$('iframe')
      expect($iframe.length).to.exist

    it 'should use server={origin} by default', ->
      @createViewApp()
      $iframe = @viewApp.$('iframe')
      expect($iframe.attr('src')).to.contain("server=#{encodeURIComponent(window.location.origin)}")

    it 'should use server={serverUrlFromPlugin} when specified', ->
      @view.set(serverUrlFromPlugin: 'http://server-url')
      @createViewApp()
      $iframe = @viewApp.$('iframe')
      expect($iframe.attr('src')).to.contain("server=#{encodeURIComponent('http://server-url')}")

    it 'should set origin={window.location.origin} even if serverUrlFromPlugin is specified', ->
      @view.set(serverUrlFromPlugin: 'http://server-url')
      @createViewApp()
      $iframe = @viewApp.$('iframe')
      expect($iframe.attr('src')).to.contain("origin=#{encodeURIComponent(window.location.origin)}")

    describe 'setDocumentDetailLink', ->
      it 'should change attributes.filter', ->
        @createViewApp()
        @sandbox.stub(Backbone, 'sync')
        @view.on('change:documentDetailLink', onChange = sinon.spy())
        @viewApp.setDocumentDetailLink({ foo: 'bar' })
        expect(onChange).to.have.been.calledWith(@view, { foo: 'bar' })

      it 'should PATCH the filter to the server', ->
        @createViewApp()
        @view.save = sinon.spy()
        @viewApp.setDocumentDetailLink({ foo: 'bar' })
        expect(@view.save).to.have.been.calledWith({ documentDetailLink: { foo: 'bar' } }, { patch: true })

    describe 'setTitle', ->
      it 'should change attributes.title', ->
        @createViewApp()
        @sandbox.stub(Backbone, 'sync')
        @view.on('change:title', onChange = sinon.spy())
        @viewApp.setTitle('bar')
        expect(onChange).to.have.been.calledWith(@view, 'bar')

      it 'should PATCH the title to the server', ->
        @createViewApp()
        @view.save = sinon.spy()
        @viewApp.setTitle('bar')
        expect(@view.save).to.have.been.calledWith({ title: 'bar' }, { patch: true })

    describe 'setViewFilter', ->
      it 'should change attributes.filter', ->
        @createViewApp()
        @sandbox.stub(Backbone, 'sync')
        @view.on('change:filter', onChange = sinon.spy())
        @viewApp.setViewFilter({ foo: 'bar' })
        expect(onChange).to.have.been.calledWith(@view, { foo: 'bar' })

      it 'should PATCH the filter to the server', ->
        @createViewApp()
        @view.save = sinon.spy()
        @viewApp.setViewFilter({ foo: 'bar' })
        expect(@view.save).to.have.been.calledWith({ filter: { foo: 'bar' } }, { patch: true })

    describe 'setViewFilterChoices', ->
      it 'should change attributes.filter', ->
        @createViewApp()
        @sandbox.stub(Backbone, 'sync')
        @viewApp.setViewFilter({ foo: 'bar', choices: [] })
        @view.on('change:filter', onChange = sinon.spy())
        @viewApp.setViewFilterChoices([ { id: '1', name: 'One', color: '#abcdef' } ])
        expect(onChange).to.have.been.calledWith(@view, { foo: 'bar', choices: [ { id: '1', name: 'One', color: '#abcdef' } ] })

      it 'should PATCH the filter to the server', ->
        @createViewApp()
        @sandbox.stub(Backbone, 'sync')
        @viewApp.setViewFilter({ foo: 'bar' })
        @view.save = sinon.spy() # reset it
        @viewApp.setViewFilterChoices([ { id: '1', name: 'One', color: '#abcdef' } ])
        expect(@view.save).to.have.been.calledWith({ filter: { foo: 'bar', choices: [ { id: '1', name: 'One', color: '#abcdef' } ] } }, { patch: true })

      it 'should no-op when no filter is set', ->
        @createViewApp()
        @view.save = sinon.spy()
        @viewApp.setViewFilterChoices([ { id: '1', name: 'One', color: '#abcdef' } ])
        expect(@view.save).not.to.have.been.called

    describe 'after setRightPane', ->
      beforeEach (done) ->
        @rightPane = @main.querySelector('#tree-app-right-pane')
        @createViewApp()
        @viewApp.setRightPane({ url: 'http://localhost:9876/base/mock-plugin/right-pane.html' })

        iframe = @main.querySelector('#view-app-right-pane-iframe')

        continueOnceIframeLoads = ->
          if iframe.contentWindow.location.href == 'about:blank'
            setTimeout(continueOnceIframeLoads, 1)
          else
            done()
        continueOnceIframeLoads()


      it 'should add "has-right-pane" class to main', ->
        expect(@main.className).to.match(/\bhas-right-pane\b/)

      it 'should add an iframe to the right pane', ->
        expect(@rightPane.querySelector('iframe#view-app-right-pane-iframe')).not.to.be.null

      it 'should postMessage to the right-pane iframe', (done) ->
        iframe = @rightPane.querySelector('iframe')
        iframe.contentWindow.addEventListener 'message', (e) ->
          expect(e.data).to.deep.eq({ event: 'notify:documentListParams', args: [ { foo: 'bar' } ] })
          done()
        @viewApp.notifyDocumentListParams({ foo: 'bar' })

      it 'should allow setting right pane to blank', ->
        @viewApp.setRightPane({ url: null })
        expect(@main.className).not.to.match(/\bhas-right-pane\b/)
        expect(@rightPane.innerHTML).to.eq('')
        expect(=> @viewApp.notifyDocumentListParams({ foo: 'bar' })).not.to.throw

    describe 'after setModalDialog', ->
      beforeEach (done) ->
        @createViewApp()
        @viewApp.setModalDialog({ url: 'http://localhost:9876/base/mock-plugin/modal-dialog.html' })

        iframe = document.querySelector('#view-app-modal-dialog-iframe')

        continueOnceIframeLoads = =>
          if iframe.contentWindow.location.href == 'about:blank'
            setTimeout(continueOnceIframeLoads, 1)
          else
            @modal = document.querySelector('#view-app-modal-dialog')
            done()
        continueOnceIframeLoads()

      it 'should add a modal dialog to <body>', ->
        expect(@modal).not.to.be.null

      it 'should add an iframe to the modal', ->
        expect(@modal.querySelector('iframe')).not.to.be.null

      it 'should postMessage to the modal iframe', (done) ->
        iframe = @modal.querySelector('iframe')
        iframe.contentWindow.addEventListener 'message', (e) ->
          expect(e.data).to.deep.eq({ event: 'notify:documentListParams', args: [ { foo: 'bar' } ] })
          done()
        @viewApp.notifyDocumentListParams({ foo: 'bar' })

      it 'should allow setting right pane to blank with { url: null }', ->
        @viewApp.setModalDialog({ url: null })
        expect(document.querySelector('#view-app-modal-dialog')).to.be.null
        expect(=> @viewApp.notifyDocumentListParams({ foo: 'bar' })).not.to.throw

      it 'should allow setting right pane to blank with null', ->
        @viewApp.setModalDialog(null)
        expect(document.querySelector('#vieapp-modal-dialog')).to.be.null
        expect(=> @viewApp.notifyDocumentListParams({ foo: 'bar' })).not.to.throw

      it 'should let iframes pass messages to each other', (done) ->
        iframe = @modal.querySelector('iframe')
        iframe.contentWindow.addEventListener 'message', (e) ->
          expect(e.data).to.deep.eq({ foo: 'bar' })
          done()
        @viewApp.postMessageToPluginIframes({ foo: 'bar' })
