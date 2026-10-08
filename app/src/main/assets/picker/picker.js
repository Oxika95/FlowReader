(function () {
  if (window.__flowPicker) return;
  var P = (window.__flowPicker = {});
  var COLORS = { title: '#7e57c2', body: '#43a047', prev: '#1e88e5', next: '#fb8c00', remove: '#e53935' };
  var Z = '2147483647';

  var layer = document.createElement('div');
  layer.setAttribute('data-flow-picker', '');
  layer.style.cssText = 'position:absolute;left:0;top:0;width:0;height:0;pointer-events:none;z-index:' + Z;
  document.documentElement.appendChild(layer);

  var cursor = document.createElement('div');
  cursor.style.cssText =
    'position:absolute;pointer-events:none;box-sizing:border-box;border:2px solid #e91e63;' +
    'background:rgba(233,30,99,0.18);display:none;z-index:' + Z;
  layer.appendChild(cursor);

  var current = null;
  var widened = [];
  var marks = {};

  function place(box, el) {
    var r = el.getBoundingClientRect();
    box.style.left = r.left + window.scrollX + 'px';
    box.style.top = r.top + window.scrollY + 'px';
    box.style.width = r.width + 'px';
    box.style.height = r.height + 'px';
  }

  function pathOf(el) {
    var nodes = [];
    for (var e = el; e && e.nodeType === 1; e = e.parentElement) {
      var n = 1;
      for (var s = e.previousElementSibling; s; s = s.previousElementSibling) {
        if (s.tagName === e.tagName) n++;
      }
      var cls = [];
      for (var i = 0; i < e.classList.length; i++) cls.push(e.classList[i]);
      nodes.unshift({ tag: e.tagName.toLowerCase(), id: e.id || '', classes: cls, nth: n });
    }
    return nodes;
  }

  function select(el, keepStack) {
    if (!el || el === layer || layer.contains(el)) return;
    if (!keepStack) widened = [];
    current = el;
    cursor.style.display = 'block';
    place(cursor, el);
    var text = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 120);
    window.FlowPicker.onPick(JSON.stringify({ path: pathOf(el), text: text }));
  }

  function block(e) {
    e.preventDefault();
    e.stopPropagation();
    if (e.stopImmediatePropagation) e.stopImmediatePropagation();
  }

  document.addEventListener('click', function (e) { block(e); select(e.target); }, true);
  ['mousedown', 'mouseup', 'submit', 'auxclick', 'dblclick'].forEach(function (t) {
    document.addEventListener(t, block, true);
  });

  P.wider = function () {
    if (!current) return;
    var parent = current.parentElement;
    if (!parent || parent === document.documentElement) return;
    widened.push(current);
    select(parent, true);
  };

  P.narrower = function () {
    if (!current) return;
    if (widened.length) select(widened.pop(), true);
    else if (current.firstElementChild) select(current.firstElementChild, true);
  };

  P.count = function (css) {
    try { return document.querySelectorAll(css).length; } catch (e) { return -1; }
  };

  P.highlight = function (field, css) {
    (marks[field] || []).forEach(function (b) { b.remove(); });
    marks[field] = [];
    if (!css) return;
    var hits;
    try { hits = document.querySelectorAll(css); } catch (e) { return; }
    var color = COLORS[field] || '#e91e63';
    for (var i = 0; i < hits.length && i < 300; i++) {
      var b = document.createElement('div');
      b.style.cssText =
        'position:absolute;pointer-events:none;box-sizing:border-box;border:2px dashed ' + color + ';';
      place(b, hits[i]);
      layer.insertBefore(b, cursor);
      marks[field].push(b);
    }
  };
})();
