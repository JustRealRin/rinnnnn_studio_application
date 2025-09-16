(function(){
  // Extended adapter: keeps latest gamepad & hand state and provides simple polyfill APIs.
  window.NativeXR = window.NativeXR || {};

  var state = {
    gamepads: {}, // keyed by id or 'default'
    hands: { left: null, right: null }
  };

  function notifyUpdate(detail) {
    var ev = new CustomEvent('nativexr-update', { detail: detail });
    window.dispatchEvent(ev);
  }

  window.NativeXR.onInput = function(payload){
    try {
      var data = (typeof payload === 'string') ? JSON.parse(payload) : payload;
      // Update internal caches based on payload type
      if (data.type === 'gamepad') {
        // simple: keep under 'default'
        state.gamepads['default'] = data.axes || {};
      } else if (data.type === 'key') {
        // keys can be forwarded as an event
        var k = { type: 'key', action: data.action, keyCode: data.keyCode };
        notifyUpdate(k);
      } else if (data.type === 'hand') {
        if (data.handedness === 'left') state.hands.left = data;
        else state.hands.right = data;
      }
      // Always notify listeners with the raw data as well
      notifyUpdate(data);
    } catch (e) {
      console.error('[NativeXR] failed to parse payload', e, payload);
    }
  };

  // page API: get current gamepads (simple emulation)
  navigator.getGamepads = navigator.getGamepads || function() {
    var list = [];
    for (var k in state.gamepads) {
      var obj = { id: k, axes: [], buttons: [] };
      var axesObj = state.gamepads[k];
      // push numeric axes in predictable order if present
      ['lx','ly','rx','ry','rz','hatX','hatY'].forEach(function(name){
        obj.axes.push(axesObj[name] || 0);
      });
      list.push(obj);
    }
    return list;
  };

  // page API: get latest hands
  window.NativeXR.getHands = function() {
    return { left: state.hands.left, right: state.hands.right };
  };

  // helper for page to send messages back to the Android bridge
  window.NativeXR.postToAndroid = function(obj){
    try {
      var s = JSON.stringify(obj);
      if (window.AndroidBridge && window.AndroidBridge.postMessage) {
        window.AndroidBridge.postMessage(s);
      }
    } catch (e) {
      console.error('[NativeXR] postToAndroid failed', e);
    }
  };

  // convenience: expose a lightweight event for inputs
  console.log('[NativeXR] extended adapter loaded');
})();